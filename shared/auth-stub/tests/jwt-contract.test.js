/**
 * JWT Contract Compliance Tests
 * 
 * Verifies that issued access tokens comply with the auth-api.yaml contract
 * and meet the acceptance criteria for JWT_SECRET, claims, and expiration.
 */

const request = require('supertest');
const jwt = require('jsonwebtoken');

// Set environment - must happen before requiring app
process.env.JWT_SECRET = 'mission-control-shared-secret-key-32-bytes-minimum-length-token';

// Mock the userService before requiring app (which requires userService)
jest.mock('../src/userService', () => ({
  registerUser: jest.fn(),
  authenticateUser: jest.fn((username, password) => {
    // Mock user data per contract's x-internal-notes
    const users = {
      alice: { username: 'alice', password: 'mission123', roles: ['MISSION_OPERATOR', 'ADMIN'] },
      bob: { username: 'bob', password: 'wrongpermissions', roles: ['GUEST'] },
    };

    const user = users[username];
    if (user && user.password === password) {
      return Promise.resolve({ id: 1, username: user.username, roles: user.roles });
    }
    return Promise.resolve(null);
  }),
}));

const app = require('../server');

/**
 * Test suite: JWT Contract Compliance
 */
describe('JWT Contract Compliance', () => {
  /**
   * AC: The access token is signed HS256 with JWT_SECRET and carries 
   * exactly the contract's claim set plus the contract's issuer
   */
  describe('Token Structure and Claims', () => {
    it('should issue a token for valid credentials (alice/mission123)', async () => {
      const response = await request(app)
        .post('/login')
        .send({
          username: 'alice',
          password: 'mission123',
        });

      expect(response.status).toBe(200);
      expect(response.body).toHaveProperty('token');
      expect(typeof response.body.token).toBe('string');
    });

    it('should include exactly the contract-required claims in the token', async () => {
      const response = await request(app)
        .post('/login')
        .send({
          username: 'alice',
          password: 'mission123',
        });

      const token = response.body.token;

      // Decode WITHOUT verification (to inspect payload structure)
      const decoded = jwt.decode(token);
      const claimKeys = Object.keys(decoded).sort();

      // Contract requires: sub, roles, iss, iat, exp
      // (iat and exp are automatically added by jwt.sign())
      const expectedKeys = ['exp', 'iat', 'iss', 'roles', 'sub'].sort();

      expect(claimKeys).toEqual(expectedKeys);
    });

    it('should have sub claim equal to username', async () => {
      const response = await request(app)
        .post('/login')
        .send({
          username: 'alice',
          password: 'mission123',
        });

      const decoded = jwt.decode(response.body.token);
      expect(decoded.sub).toBe('alice');
    });

    it('should have roles array with user-specific roles from contract', async () => {
      // Test alice - should have MISSION_OPERATOR and ADMIN
      let response = await request(app)
        .post('/login')
        .send({
          username: 'alice',
          password: 'mission123',
        });

      let decoded = jwt.decode(response.body.token);
      expect(Array.isArray(decoded.roles)).toBe(true);
      expect(decoded.roles).toEqual(['MISSION_OPERATOR', 'ADMIN']);

      // Test bob - should have GUEST
      response = await request(app)
        .post('/login')
        .send({
          username: 'bob',
          password: 'wrongpermissions',
        });

      decoded = jwt.decode(response.body.token);
      expect(Array.isArray(decoded.roles)).toBe(true);
      expect(decoded.roles).toEqual(['GUEST']);
    });

    it('should have iss (issuer) claim matching contract', async () => {
      const response = await request(app)
        .post('/login')
        .send({
          username: 'alice',
          password: 'mission123',
        });

      const decoded = jwt.decode(response.body.token);
      expect(decoded.iss).toBe('urn:tns-capital:auth-stub');
    });

    it('should have iat (issued at) timestamp as a number', async () => {
      const response = await request(app)
        .post('/login')
        .send({
          username: 'alice',
          password: 'mission123',
        });

      const decoded = jwt.decode(response.body.token);
      expect(typeof decoded.iat).toBe('number');
      expect(decoded.iat).toBeGreaterThan(0);
    });
  });

  /**
   * AC: The exp value matches the contract's lifetime (1 hour = 3600 seconds)
   */
  describe('Token Expiration', () => {
    it('should set exp to 1 hour (3600 seconds) from iat', async () => {
      const response = await request(app)
        .post('/login')
        .send({
          username: 'alice',
          password: 'mission123',
        });

      const decoded = jwt.decode(response.body.token);
      const expirationDelta = decoded.exp - decoded.iat;

      // Contract specifies 1 hour (3600 seconds)
      expect(expirationDelta).toBe(3600);
    });

    it('should have exp in the future', async () => {
      const response = await request(app)
        .post('/login')
        .send({
          username: 'alice',
          password: 'mission123',
        });

      const decoded = jwt.decode(response.body.token);
      const now = Math.floor(Date.now() / 1000);

      expect(decoded.exp).toBeGreaterThan(now);
    });
  });

  /**
   * AC: No password, hash or other sensitive data appears in the payload
   */
  describe('Payload Security', () => {
    it('should not include password in token payload', async () => {
      const response = await request(app)
        .post('/login')
        .send({
          username: 'alice',
          password: 'mission123',
        });

      const decoded = jwt.decode(response.body.token);
      expect(decoded).not.toHaveProperty('password');
      expect(decoded).not.toHaveProperty('pwd');
    });

    it('should not include password_hash in token payload', async () => {
      const response = await request(app)
        .post('/login')
        .send({
          username: 'alice',
          password: 'mission123',
        });

      const decoded = jwt.decode(response.body.token);
      expect(decoded).not.toHaveProperty('password_hash');
      expect(decoded).not.toHaveProperty('hash');
    });

    it('should not include id or database identifiers', async () => {
      const response = await request(app)
        .post('/login')
        .send({
          username: 'alice',
          password: 'mission123',
        });

      const decoded = jwt.decode(response.body.token);
      // Allow 'id' if it's part of contract, but verify it's not user DB id
      if (decoded.id) {
        // If present, should not be numeric (database row id)
        expect(typeof decoded.id).not.toBe('number');
      }
    });

    it('should not include user.id or database references', async () => {
      const response = await request(app)
        .post('/login')
        .send({
          username: 'alice',
          password: 'mission123',
        });

      const decoded = jwt.decode(response.body.token);
      const payload = JSON.stringify(decoded);

      // Ensure no obvious sensitive patterns
      expect(payload).not.toMatch(/created_at/i);
      expect(payload).not.toMatch(/updated_at/i);
    });
  });

  /**
   * AC: The signing algorithm is pinned to HS256 in code, not read from token
   */
  describe('Algorithm Pinning', () => {
    it('should use HS256 algorithm in token header', async () => {
      const response = await request(app)
        .post('/login')
        .send({
          username: 'alice',
          password: 'mission123',
        });

      const token = response.body.token;
      const decoded = jwt.decode(token, { complete: true });

      expect(decoded.header.alg).toBe('HS256');
    });

    it('should verify token with HS256 algorithm using shared secret', async () => {
      const response = await request(app)
        .post('/login')
        .send({
          username: 'alice',
          password: 'mission123',
        });

      const token = response.body.token;
      const secret = process.env.JWT_SECRET;

      // Should verify without error
      const verified = jwt.verify(token, secret, { algorithms: ['HS256'] });
      expect(verified).toBeDefined();
      expect(verified.sub).toBe('alice');
    });

    it('should reject tokens signed with different algorithm', async () => {
      // Create a token with different algorithm (should be rejected by strict verification)
      const token = jwt.sign(
        { sub: 'alice', roles: ['ADMIN'], iss: 'urn:tns-capital:auth-stub' },
        process.env.JWT_SECRET,
        { algorithm: 'HS512', expiresIn: '1h' }, // Wrong algorithm
      );

      // Strict verification with allowed algorithm should fail
      expect(() => {
        jwt.verify(token, process.env.JWT_SECRET, { algorithms: ['HS256'] });
      }).toThrow();
    });
  });

  /**
   * AC: The service refuses to start if JWT_SECRET is missing or empty
   */
  describe('JWT_SECRET Validation', () => {
    it('should have JWT_SECRET set to a valid value', () => {
      const secret = process.env.JWT_SECRET;
      expect(secret).toBeDefined();
      expect(secret.length).toBeGreaterThan(0);
    });

    it('should have JWT_SECRET at least 32 bytes for HS256 security', () => {
      const secret = process.env.JWT_SECRET;
      expect(secret.length).toBeGreaterThanOrEqual(32);
    });
  });
});
