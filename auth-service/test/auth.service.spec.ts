import { UnauthorizedException } from "@nestjs/common";
import * as jwt from "jsonwebtoken";
import { AUTH_401 } from "../src/auth-errors";
import { AuthService } from "../src/auth.service";
import { ACCESS_TOKEN_TTL_SECONDS, REFRESH_TOKEN_TTL_SECONDS } from "../src/config";
import { hashToken, RefreshTokenStore } from "../src/refresh-token.store";
import { TokenService } from "../src/token.service";
import { UserStore } from "../src/user.store";
import { TEST_CONFIG } from "./test-config";

async function expectAuth401(promise: Promise<unknown>): Promise<void> {
  await expect(promise).rejects.toBeInstanceOf(UnauthorizedException);
  await promise.catch((e: UnauthorizedException) => expect(e.getResponse()).toEqual(AUTH_401));
}

describe("AuthService refresh", () => {
  let service: AuthService;
  let tokens: TokenService;
  let store: RefreshTokenStore;

  beforeAll(() => jest.spyOn(console, "log").mockImplementation(() => undefined));

  beforeEach(() => {
    tokens = new TokenService(TEST_CONFIG);
    store = new RefreshTokenStore();
    service = new AuthService(new UserStore(), tokens, store);
  });

  it("issues a new refresh token on every refresh, and the new one works", async () => {
    const first = await service.login("alice", "mission123");
    const second = await service.refresh(first.refreshToken);
    const third = await service.refresh(second.refreshToken);

    expect(second.refreshToken).not.toBe(first.refreshToken);
    expect(third.refreshToken).not.toBe(second.refreshToken);
    expect(third.token).not.toBe(second.token);
  });

  it("returns an access token with the full claim set", async () => {
    const { refreshToken } = await service.login("alice", "mission123");
    const { token: accessToken } = await service.refresh(refreshToken);

    const claims = jwt.verify(accessToken, TEST_CONFIG.accessSecret, { algorithms: ["HS256"] }) as jwt.JwtPayload;
    expect(claims).toEqual({
      sub: "alice",
      roles: ["MISSION_OPERATOR", "ADMIN"],
      typ: "access",
      iss: TEST_CONFIG.issuer,
      jti: expect.any(String),
      iat: expect.any(Number),
      exp: expect.any(Number),
    });
    expect(claims.exp! - claims.iat!).toBe(ACCESS_TOKEN_TTL_SECONDS);
    expect(tokens.verifyAccessToken(accessToken).sub).toBe("alice");
  });

  it("issues refresh tokens that live for the documented lifetime", async () => {
    const { refreshToken } = await service.login("alice", "mission123");
    const claims = jwt.decode(refreshToken) as jwt.JwtPayload;
    expect(claims.exp! - claims.iat!).toBe(REFRESH_TOKEN_TTL_SECONDS);
  });

  it("answers AUTH-401 to a second presentation of an old refresh token", async () => {
    const { refreshToken: old } = await service.login("alice", "mission123");
    await service.refresh(old);

    await expectAuth401(service.refresh(old));
  });

  it("revokes the whole session when an old refresh token is replayed", async () => {
    const { refreshToken: old } = await service.login("alice", "mission123");
    const { refreshToken: current } = await service.refresh(old);

    await expectAuth401(service.refresh(old));
    await expectAuth401(service.refresh(current));
  });

  it("does not affect other sessions when one is revoked", async () => {
    const phone = await service.login("alice", "mission123");
    const laptop = await service.login("alice", "mission123");
    await service.refresh(phone.refreshToken);
    await expectAuth401(service.refresh(phone.refreshToken));

    await expect(service.refresh(laptop.refreshToken)).resolves.toHaveProperty("refreshToken");
  });

  it("refuses an expired refresh token", async () => {
    const expired = jwt.sign(
      { typ: "refresh", fam: "family-1", exp: Math.floor(Date.now() / 1000) - 10 },
      TEST_CONFIG.refreshSecret,
      { algorithm: "HS256", issuer: TEST_CONFIG.issuer, subject: "alice", jwtid: "jti-1" },
    );
    store.save(expired, "alice", "family-1", Math.floor(Date.now() / 1000) + 60);

    await expectAuth401(service.refresh(expired));
  });

  it("refuses a tampered refresh token", async () => {
    const { refreshToken } = await service.login("bob", "wrongpermissions");
    const [header, , signature] = refreshToken.split(".");
    const payload = Buffer.from(
      JSON.stringify({ ...(jwt.decode(refreshToken) as object), sub: "alice" }),
    ).toString("base64url");

    await expectAuth401(service.refresh(`${header}.${payload}.${signature}`));
  });

  it("refuses a refresh token signed with the wrong secret", async () => {
    const forged = jwt.sign({ typ: "refresh", fam: "family-1" }, "some-other-secret-at-least-32-characters", {
      algorithm: "HS256",
      expiresIn: 60,
      issuer: TEST_CONFIG.issuer,
      subject: "alice",
      jwtid: "jti-1",
    });
    await expectAuth401(service.refresh(forged));
  });

  it.each(["", "not-a-jwt", "aaa.bbb.ccc"])("refuses a malformed refresh token (%p)", async (token) => {
    await expectAuth401(service.refresh(token));
  });

  it("refuses a validly signed refresh token it never issued", async () => {
    const { token } = tokens.issueRefreshToken("alice", "family-never-stored");
    await expectAuth401(service.refresh(token));
  });

  it("refuses an access token presented as a refresh token", async () => {
    const { token: accessToken } = await service.login("alice", "mission123");
    await expectAuth401(service.refresh(accessToken));
  });

  it("refuses an access-typed token even if signed with the refresh secret", async () => {
    const confused = jwt.sign({ roles: ["ADMIN"], typ: "access", fam: "family-1" }, TEST_CONFIG.refreshSecret, {
      algorithm: "HS256",
      expiresIn: 60,
      issuer: TEST_CONFIG.issuer,
      subject: "alice",
      jwtid: "jti-1",
    });
    await expectAuth401(service.refresh(confused));
  });

  it("stores refresh tokens only as hashes, never in plaintext", async () => {
    const { refreshToken } = await service.login("alice", "mission123");
    const stored = store["tokens"] as Map<string, object>;

    expect([...stored.keys()]).toEqual([hashToken(refreshToken)]);
    const dump = JSON.stringify([...stored.entries()]);
    expect(dump).not.toContain(refreshToken);
    expect(dump).not.toContain(refreshToken.split(".")[2]);
  });
});
