import { INestApplication } from "@nestjs/common";
import { Test } from "@nestjs/testing";
import request from "supertest";
import { AUTH_401_BODY } from "../src/auth-errors";
import { AppModule, configureApp } from "../src/app.module";
import { TEST_CONFIG } from "./test-config";

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

  async function login(): Promise<{ accessToken: string; refreshToken: string }> {
    const res = await request(app.getHttpServer())
      .post("/auth/login")
      .send({ username: "alice", password: "mission123" })
      .expect(200);
    return res.body;
  }

  describe("GET /auth/me (protected)", () => {
    it("returns the verified claims for a valid token", async () => {
      const { accessToken } = await login();
      const res = await request(app.getHttpServer())
        .get("/auth/me")
        .set("Authorization", `Bearer ${accessToken}`)
        .expect(200);

      expect(res.body).toMatchObject({ sub: "alice", roles: ["MISSION_OPERATOR", "ADMIN"], iss: TEST_CONFIG.issuer });
    });

    it.each([
      ["no Authorization header", undefined],
      ["the Basic scheme", "Basic YWxpY2U6bWlzc2lvbjEyMw=="],
      ["a malformed token", "Bearer not-a-jwt"],
    ])("answers AUTH-401 with the same body for %s", async (_label, header) => {
      const req = request(app.getHttpServer()).get("/auth/me");
      const res = await (header ? req.set("Authorization", header) : req).expect(401);
      expect(res.body).toEqual(AUTH_401_BODY);
    });

    it("refuses a refresh token used as a bearer token", async () => {
      const { refreshToken } = await login();
      const res = await request(app.getHttpServer())
        .get("/auth/me")
        .set("Authorization", `Bearer ${refreshToken}`)
        .expect(401);
      expect(res.body).toEqual(AUTH_401_BODY);
    });
  });

  describe("POST /auth/refresh", () => {
    it("rotates the refresh token, and refuses the old one afterwards", async () => {
      const { refreshToken: old } = await login();

      const rotated = await request(app.getHttpServer()).post("/auth/refresh").send({ refreshToken: old }).expect(200);
      expect(rotated.body.refreshToken).not.toBe(old);

      await request(app.getHttpServer())
        .get("/auth/me")
        .set("Authorization", `Bearer ${rotated.body.accessToken}`)
        .expect(200);
      await request(app.getHttpServer())
        .post("/auth/refresh")
        .send({ refreshToken: rotated.body.refreshToken })
        .expect(200);

      const replay = await request(app.getHttpServer()).post("/auth/refresh").send({ refreshToken: old }).expect(401);
      expect(replay.body).toEqual(AUTH_401_BODY);
    });

    it("refuses an access token presented as a refresh token", async () => {
      const { accessToken } = await login();
      const res = await request(app.getHttpServer())
        .post("/auth/refresh")
        .send({ refreshToken: accessToken })
        .expect(401);
      expect(res.body).toEqual(AUTH_401_BODY);
    });
  });
});
