import { JwtService } from '@nestjs/jwt';
import * as jwt from 'jsonwebtoken';
import { ACCESS_TOKEN_TTL_SECONDS, JWT_ISSUER, TokenService } from '../src/auth/token.service';
import { ApiException } from '../src/common/api.exception';

const SECRET = 'token-service-test-secret-at-least-32-bytes';

describe('TokenService', () => {
  const tokens = new TokenService(
    new JwtService({
      secret: SECRET,
      signOptions: { algorithm: 'HS256', expiresIn: ACCESS_TOKEN_TTL_SECONDS, issuer: JWT_ISSUER },
    }),
  );
  const user = { username: 'trader', roles: ['USER' as const] };

  describe('issue and verify', () => {
    it('verifies a token it issued', async () => {
      const payload = await tokens.verify(await tokens.issue(user));

      expect(payload).toMatchObject({ sub: 'trader', roles: ['USER'], typ: 'access', iss: JWT_ISSUER });
      expect(payload.exp - payload.iat).toBe(15 * 60);
    });

    it('uses the auth service as the issuer', () => {
      expect(JWT_ISSUER).toBe('urn:tns-capital:auth-service');
    });

    it('gives every token a unique jti', async () => {
      const first = await tokens.verify(await tokens.issue(user));
      const second = await tokens.verify(await tokens.issue(user));

      expect(first.jti).toMatch(/^[0-9a-f-]{36}$/);
      expect(first.jti).not.toBe(second.jti);
    });

    it.each([
      ['garbage', 'not-a-jwt'],
      ['a token with a tampered payload', 'tampered'],
    ])('rejects %s with AUTH-401', async (_case, kind) => {
      let token = kind;
      if (kind === 'tampered') {
        const [header, , signature] = (await tokens.issue(user)).split('.');
        const forged = Buffer.from(JSON.stringify({ sub: 'admin', roles: ['ADMIN'], iss: JWT_ISSUER })).toString(
          'base64url',
        );
        token = `${header}.${forged}.${signature}`;
      }

      const error = await tokens.verify(token).catch((e: ApiException) => e);
      expect(error).toBeInstanceOf(ApiException);
      expect((error as ApiException).errorCode).toBe('AUTH-401');
    });

    const signed = (claims: object) => jwt.sign(claims, SECRET, { issuer: JWT_ISSUER, expiresIn: 60 });
    const valid = { sub: 'trader', roles: ['USER'], typ: 'access', jti: 'some-id' };

    it.each([
      ['a non-string subject', { ...valid, sub: 42 }],
      ['no typ claim', { ...valid, typ: undefined }],
      ['a typ other than access', { ...valid, typ: 'refresh' }],
      ['no jti claim', { ...valid, jti: undefined }],
    ])('rejects a correctly signed token with %s', async (_case, claims) => {
      await expect(tokens.verify(signed(claims))).rejects.toBeInstanceOf(ApiException);
    });

    it('accepts a correctly signed token with every required claim', async () => {
      await expect(tokens.verify(signed(valid))).resolves.toMatchObject({ sub: 'trader' });
    });
  });

  describe('decode', () => {
    it('reads the claims without checking the signature', async () => {
      const [header, payload] = (await tokens.issue(user)).split('.');

      expect(tokens.decode(`${header}.${payload}.invalid-signature`)).toMatchObject({ sub: 'trader' });
    });

    it('returns null for something that is not a JWT', () => {
      expect(tokens.decode('not-a-jwt')).toBeNull();
    });
  });

  describe('refresh tokens', () => {
    it('generates a 256-bit random token and its SHA-256 hash', () => {
      const { token, hash } = tokens.generateRefreshToken();

      expect(Buffer.from(token, 'base64url')).toHaveLength(32);
      expect(hash).toMatch(/^[0-9a-f]{64}$/);
      expect(tokens.hashRefreshToken(token)).toBe(hash);
    });

    it('never generates the same token twice', () => {
      const generated = new Set(Array.from({ length: 100 }, () => tokens.generateRefreshToken().token));
      expect(generated.size).toBe(100);
    });
  });
});
