import { ExecutionContext, UnauthorizedException } from "@nestjs/common";
import http from "http";
import https from "https";
import * as jwt from "jsonwebtoken";
import net from "net";
import { AUTH_401 } from "../src/auth-errors";
import { AuthenticatedRequest, JwtAuthGuard } from "../src/jwt-auth.guard";
import { TokenService } from "../src/token.service";
import { TEST_CONFIG } from "./test-config";

function contextFor(authorization?: string): { context: ExecutionContext; request: Partial<AuthenticatedRequest> } {
  const request: Partial<AuthenticatedRequest> = { headers: authorization === undefined ? {} : { authorization } };
  const context = {
    switchToHttp: () => ({ getRequest: () => request }),
  } as unknown as ExecutionContext;
  return { context, request };
}

function rejectionBody(guard: JwtAuthGuard, authorization?: string): unknown {
  try {
    guard.canActivate(contextFor(authorization).context);
  } catch (e) {
    expect(e).toBeInstanceOf(UnauthorizedException);
    const error = e as UnauthorizedException;
    expect(error.getStatus()).toBe(401);
    return error.getResponse();
  }
  throw new Error(`guard let through: ${authorization}`);
}

function signAccess(overrides: { secret?: string; options?: jwt.SignOptions; payload?: object } = {}): string {
  const options: jwt.SignOptions = {
    algorithm: "HS256",
    expiresIn: 60,
    issuer: TEST_CONFIG.issuer,
    subject: "alice",
    jwtid: "jti-1",
    ...overrides.options,
  };
  const definedOptions = Object.fromEntries(Object.entries(options).filter(([, v]) => v !== undefined));
  return jwt.sign(
    { roles: ["MISSION_OPERATOR"], typ: "access", ...overrides.payload },
    overrides.secret ?? TEST_CONFIG.accessSecret,
    definedOptions,
  );
}

describe("JwtAuthGuard", () => {
  const tokens = new TokenService(TEST_CONFIG);
  const guard = new JwtAuthGuard(tokens);

  it("lets a valid token through and attaches the verified claims", () => {
    const token = tokens.issueAccessToken("alice", ["MISSION_OPERATOR", "ADMIN"]);
    const { context, request } = contextFor(`Bearer ${token}`);

    expect(guard.canActivate(context)).toBe(true);
    expect(request.user).toMatchObject({
      sub: "alice",
      roles: ["MISSION_OPERATOR", "ADMIN"],
      typ: "access",
      iss: TEST_CONFIG.issuer,
    });
  });

  it("accepts the Bearer scheme in any case", () => {
    const token = tokens.issueAccessToken("alice", ["MISSION_OPERATOR"]);
    expect(guard.canActivate(contextFor(`bearer ${token}`).context)).toBe(true);
  });

  it("refuses an expired token with AUTH-401", () => {
    const expired = signAccess({ payload: { exp: Math.floor(Date.now() / 1000) - 10 }, options: { expiresIn: undefined } });
    expect(rejectionBody(guard, `Bearer ${expired}`)).toEqual(AUTH_401);
  });

  it("refuses a token signed with the wrong secret with AUTH-401", () => {
    const forged = signAccess({ secret: "some-other-secret-at-least-32-characters" });
    expect(rejectionBody(guard, `Bearer ${forged}`)).toEqual(AUTH_401);
  });

  it("refuses a token whose payload was tampered with", () => {
    const [header, , signature] = tokens.issueAccessToken("bob", ["GUEST"]).split(".");
    const escalated = Buffer.from(
      JSON.stringify({ sub: "bob", roles: ["ADMIN"], typ: "access", iss: TEST_CONFIG.issuer, jti: "x", exp: 9999999999 }),
    ).toString("base64url");
    expect(rejectionBody(guard, `Bearer ${header}.${escalated}.${signature}`)).toEqual(AUTH_401);
  });

  it.each([
    ["not a JWT at all", "Bearer not-a-jwt"],
    ["two segments", "Bearer aaa.bbb"],
    ["garbage base64", "Bearer !!!.@@@.###"],
  ])("refuses a malformed token (%s)", (_label, header) => {
    expect(rejectionBody(guard, header)).toEqual(AUTH_401);
  });

  it("refuses a request with no Authorization header", () => {
    expect(rejectionBody(guard, undefined)).toEqual(AUTH_401);
  });

  it.each([
    ["Basic credentials", "Basic YWxpY2U6bWlzc2lvbjEyMw=="],
    ["a bare token with no scheme", "TOKEN_PLACEHOLDER"],
    ["Bearer with no token", "Bearer "],
    ["extra parts", "Bearer TOKEN_PLACEHOLDER extra"],
  ])("refuses a wrong or broken scheme (%s)", (_label, header) => {
    const token = tokens.issueAccessToken("alice", ["MISSION_OPERATOR"]);
    expect(rejectionBody(guard, header.replace("TOKEN_PLACEHOLDER", token))).toEqual(AUTH_401);
  });

  it("refuses a token from a different issuer", () => {
    const foreign = signAccess({ options: { issuer: "someone-else" } });
    expect(rejectionBody(guard, `Bearer ${foreign}`)).toEqual(AUTH_401);
  });

  it("refuses an unsigned (alg: none) token", () => {
    const unsigned = jwt.sign(
      { sub: "alice", roles: ["ADMIN"], typ: "access", iss: TEST_CONFIG.issuer, jti: "x" },
      "",
      { algorithm: "none", expiresIn: 60 },
    );
    expect(rejectionBody(guard, `Bearer ${unsigned}`)).toEqual(AUTH_401);
  });

  it("refuses a refresh token presented as an access token", () => {
    const { token } = tokens.issueRefreshToken("alice", "family-1");
    expect(rejectionBody(guard, `Bearer ${token}`)).toEqual(AUTH_401);
  });

  it("refuses a correctly signed token that is not typed as an access token", () => {
    const untyped = signAccess({ payload: { typ: undefined } });
    expect(rejectionBody(guard, `Bearer ${untyped}`)).toEqual(AUTH_401);
  });

  it("returns the identical AUTH-401 body whatever the reason", () => {
    const bodies = [
      rejectionBody(guard, undefined),
      rejectionBody(guard, "Basic abc"),
      rejectionBody(guard, "Bearer not-a-jwt"),
      rejectionBody(guard, `Bearer ${signAccess({ payload: { exp: 1 }, options: { expiresIn: undefined } })}`),
      rejectionBody(guard, `Bearer ${signAccess({ secret: "some-other-secret-at-least-32-characters" })}`),
      rejectionBody(guard, `Bearer ${signAccess({ options: { issuer: "someone-else" } })}`),
    ];
    for (const body of bodies) {
      expect(JSON.stringify(body)).toBe(JSON.stringify(AUTH_401));
    }
  });

  describe("verification is local", () => {
    afterEach(() => jest.restoreAllMocks());

    it("makes no network call when checking good and bad tokens", () => {
      const spies = [
        jest.spyOn(http, "request"),
        jest.spyOn(https, "request"),
        jest.spyOn(net, "connect"),
        jest.spyOn(net, "createConnection"),
        jest.spyOn(globalThis, "fetch"),
      ];

      guard.canActivate(contextFor(`Bearer ${tokens.issueAccessToken("alice", ["MISSION_OPERATOR"])}`).context);
      rejectionBody(guard, `Bearer ${signAccess({ secret: "some-other-secret-at-least-32-characters" })}`);

      for (const spy of spies) {
        expect(spy).not.toHaveBeenCalled();
      }
    });

    it("depends only on the token service, which holds no store or connection", () => {
      expect(Reflect.getMetadata("design:paramtypes", JwtAuthGuard)).toEqual([TokenService]);
      expect(Reflect.getMetadata("design:paramtypes", TokenService)).toEqual([Object]);
    });
  });
});
