import { INestApplication } from "@nestjs/common";
import { Test } from "@nestjs/testing";
import request from "supertest";
import { AUTH_401 } from "../src/auth-errors";
import { AppModule, configureApp } from "../src/app.module";
import { TEST_CONFIG } from "./test-config";

const ISO_8601 = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/;

function expectEnvelope(body: Record<string, unknown>, errorCode: string, message?: string): void {
  expect(Object.keys(body).sort()).toEqual(["errorCode", "message", "timestamp"]);
  expect(body.errorCode).toBe(errorCode);
  if (message !== undefined) {
    expect(body.message).toBe(message);
  }
  expect(body.timestamp).toMatch(ISO_8601);
}

function expectAuth401(body: Record<string, unknown>): void {
  expectEnvelope(body, AUTH_401.errorCode, AUTH_401.message);
}

describe("Auth service over HTTP", () => {
  let app: INestApplication;

  beforeAll(async () => {
    jest.spyOn(console, "log").mockImplementation(() => undefined);
    const moduleRef = await Test.createTestingModule({ imports: [AppModule.forConfig(TEST_CONFIG)] }).compile();
    app = moduleRef.createNestApplication();
    configureApp(app);
    await app.init();
  });

  afterAll(() => app.close());

  async function login(username = "alice", password = "mission123"): Promise<{ token: string; refreshToken: string }> {
    const res = await request(app.getHttpServer()).post("/login").send({ username, password }).expect(200);
    return res.body;
  }

  describe("POST /login", () => {
    it("returns the contract's token plus a refresh token", async () => {
      const body = await login();
      expect(Object.keys(body).sort()).toEqual(["refreshToken", "token"]);
      expect(body.token.split(".")).toHaveLength(3);
    });

    it("logs in the CUSTOMER users the trading API's ownership rules expect", async () => {
      const { token } = await login("john", "customer123");
      const res = await request(app.getHttpServer()).get("/me").set("Authorization", `Bearer ${token}`).expect(200);
      expect(res.body).toMatchObject({ sub: "john", roles: ["CUSTOMER"] });
    });

    it("answers AUTH-401 in the platform envelope for wrong credentials", async () => {
      const res = await request(app.getHttpServer())
        .post("/login")
        .send({ username: "alice", password: "wrong" })
        .expect(401);
      expectEnvelope(res.body, "AUTH-401", "invalid username or password");
    });

    it.each([
      ["a missing password", { username: "alice" }],
      ["an unknown property", { username: "alice", password: "mission123", extra: 1 }],
      ["a username over 100 characters", { username: "a".repeat(101), password: "mission123" }],
    ])("answers VAL-422 for %s", async (_label, body) => {
      const res = await request(app.getHttpServer()).post("/login").send(body).expect(400);
      expectEnvelope(res.body, "VAL-422");
    });

    it("answers VAL-422 for malformed JSON without leaking parser details", async () => {
      const res = await request(app.getHttpServer())
        .post("/login")
        .set("Content-Type", "application/json")
        .send('{"username": "alice",')
        .expect(400);
      expectEnvelope(res.body, "VAL-422", "Request body is missing or malformed");
    });
  });

  describe("GET /me (protected)", () => {
    it("returns the verified claims for a valid token", async () => {
      const { token } = await login();
      const res = await request(app.getHttpServer()).get("/me").set("Authorization", `Bearer ${token}`).expect(200);

      expect(res.body).toMatchObject({ sub: "alice", roles: ["MISSION_OPERATOR", "ADMIN"], iss: TEST_CONFIG.issuer });
    });

    it("answers the same AUTH-401 code and message whatever the reason", async () => {
      const { refreshToken } = await login();
      const headers = [undefined, "Basic YWxpY2U6bWlzc2lvbjEyMw==", "Bearer not-a-jwt", `Bearer ${refreshToken}`];

      for (const header of headers) {
        const req = request(app.getHttpServer()).get("/me");
        const res = await (header ? req.set("Authorization", header) : req).expect(401);
        expectAuth401(res.body);
      }
    });
  });

  describe("POST /refresh", () => {
    it("rotates the refresh token, and refuses the old one afterwards", async () => {
      const { refreshToken: old } = await login();

      const rotated = await request(app.getHttpServer()).post("/refresh").send({ refreshToken: old }).expect(200);
      expect(Object.keys(rotated.body).sort()).toEqual(["refreshToken", "token"]);
      expect(rotated.body.refreshToken).not.toBe(old);

      await request(app.getHttpServer()).get("/me").set("Authorization", `Bearer ${rotated.body.token}`).expect(200);
      await request(app.getHttpServer()).post("/refresh").send({ refreshToken: rotated.body.refreshToken }).expect(200);

      const replay = await request(app.getHttpServer()).post("/refresh").send({ refreshToken: old }).expect(401);
      expectAuth401(replay.body);
    });

    it("refuses an access token presented as a refresh token", async () => {
      const { token } = await login();
      const res = await request(app.getHttpServer()).post("/refresh").send({ refreshToken: token }).expect(401);
      expectAuth401(res.body);
    });

    it("refuses a malformed refresh token with AUTH-401, not a validation error", async () => {
      const res = await request(app.getHttpServer()).post("/refresh").send({ refreshToken: "garbage" }).expect(401);
      expectAuth401(res.body);
    });
  });

  it("answers NOT-404 in the platform envelope for an unknown route", async () => {
    const res = await request(app.getHttpServer()).get("/auth/me").expect(404);
    expectEnvelope(res.body, "NOT-404", "Resource not found: GET /auth/me");
  });
});
