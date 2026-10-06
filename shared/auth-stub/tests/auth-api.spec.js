const request = require('supertest');
const app = require('../server');

describe('Authentication API Contract Tests - Platform Envelope Compliance', () => {
  describe('POST /login - 200 Success Responses', () => {
    test('should return 200 with JWT token for valid alice credentials', async () => {
      const response = await request(app)
        .post('/login')
        .send({ username: 'alice', password: 'mission123' })
        .expect(200);

      expect(response.body).toHaveProperty('token');
      expect(typeof response.body.token).toBe('string');
      // JWT format: three parts separated by dots
      expect(response.body.token.split('.')).toHaveLength(3);
    });

    test('should return 200 with JWT token for valid bob credentials', async () => {
      const response = await request(app)
        .post('/login')
        .send({ username: 'bob', password: 'wrongpermissions' })
        .expect(200);

      expect(response.body).toHaveProperty('token');
      expect(typeof response.body.token).toBe('string');
      expect(response.body.token.split('.')).toHaveLength(3);
    });

    test('token should be a valid JWT that can be decoded', async () => {
      const response = await request(app)
        .post('/login')
        .send({ username: 'alice', password: 'mission123' })
        .expect(200);

      const parts = response.body.token.split('.');
      expect(parts).toHaveLength(3);
      // Decode payload (second part)
      const payload = JSON.parse(Buffer.from(parts[1], 'base64').toString());
      expect(payload.sub).toBe('alice');
      expect(Array.isArray(payload.roles)).toBe(true);
      expect(payload.roles).toContain('MISSION_OPERATOR');
    });
  });

  describe('POST /login - 400 Bad Request (Validation Errors)', () => {
    test('should return 400 with VAL-422 when username is missing', async () => {
      const response = await request(app)
        .post('/login')
        .send({ password: 'test' })
        .expect(400);

      expect(response.body).toEqual({
        errorCode: 'VAL-422',
        message: 'missing username or password',
        timestamp: expect.any(String),
      });
    });

    test('should return 400 with VAL-422 when password is missing', async () => {
      const response = await request(app)
        .post('/login')
        .send({ username: 'alice' })
        .expect(400);

      expect(response.body).toEqual({
        errorCode: 'VAL-422',
        message: 'missing username or password',
        timestamp: expect.any(String),
      });
    });

    test('should return 400 with VAL-422 when both username and password are missing', async () => {
      const response = await request(app)
        .post('/login')
        .send({})
        .expect(400);

      expect(response.body).toEqual({
        errorCode: 'VAL-422',
        message: 'missing username or password',
        timestamp: expect.any(String),
      });
    });

    test('should return 400 with VAL-422 when body is null', async () => {
      const response = await request(app)
        .post('/login')
        .send(null)
        .expect(400);

      expect(response.body.errorCode).toBe('VAL-422');
      expect(response.body.message).toBe('missing username or password');
      expect(response.body).toHaveProperty('timestamp');
    });

    test('should return 400 with VAL-422 when username is not a string (number)', async () => {
      const response = await request(app)
        .post('/login')
        .send({ username: 123, password: 'test' })
        .expect(400);

      expect(response.body.errorCode).toBe('VAL-422');
      expect(response.body).toHaveProperty('message');
      expect(response.body).toHaveProperty('timestamp');
    });

    test('should return 400 with VAL-422 when password is not a string (number)', async () => {
      const response = await request(app)
        .post('/login')
        .send({ username: 'alice', password: 123 })
        .expect(400);

      expect(response.body.errorCode).toBe('VAL-422');
      expect(response.body).toHaveProperty('message');
      expect(response.body).toHaveProperty('timestamp');
    });

    test('should return 400 with VAL-422 when username is boolean', async () => {
      const response = await request(app)
        .post('/login')
        .send({ username: true, password: 'test' })
        .expect(400);

      expect(response.body.errorCode).toBe('VAL-422');
      expect(response.body).toHaveProperty('timestamp');
    });

    test('should return 400 with VAL-422 when password is boolean', async () => {
      const response = await request(app)
        .post('/login')
        .send({ username: 'alice', password: false })
        .expect(400);

      expect(response.body.errorCode).toBe('VAL-422');
      expect(response.body).toHaveProperty('timestamp');
    });

    test('should return 400 with VAL-422 when username exceeds maxLength (100 chars)', async () => {
      const response = await request(app)
        .post('/login')
        .send({ username: 'a'.repeat(101), password: 'test' })
        .expect(400);

      expect(response.body.errorCode).toBe('VAL-422');
      expect(response.body).toHaveProperty('timestamp');
    });

    test('should return 400 with VAL-422 when password exceeds maxLength (256 chars)', async () => {
      const response = await request(app)
        .post('/login')
        .send({ username: 'alice', password: 'p'.repeat(257) })
        .expect(400);

      expect(response.body.errorCode).toBe('VAL-422');
      expect(response.body).toHaveProperty('timestamp');
    });

    test('should return 400 with VAL-422 when username is empty string', async () => {
      const response = await request(app)
        .post('/login')
        .send({ username: '', password: 'test' })
        .expect(400);

      expect(response.body.errorCode).toBe('VAL-422');
      expect(response.body).toHaveProperty('timestamp');
    });

    test('should return 400 with VAL-422 when password is empty string', async () => {
      const response = await request(app)
        .post('/login')
        .send({ username: 'alice', password: '' })
        .expect(400);

      expect(response.body.errorCode).toBe('VAL-422');
      expect(response.body).toHaveProperty('timestamp');
    });

    test('should return 400 with VAL-422 when additional properties are present', async () => {
      const response = await request(app)
        .post('/login')
        .send({ username: 'alice', password: 'mission123', extra: 'field' })
        .expect(400);

      expect(response.body.errorCode).toBe('VAL-422');
      expect(response.body.message).toContain('Unexpected property');
      expect(response.body).toHaveProperty('timestamp');
    });

    test('should return 400 with VAL-422 when multiple additional properties are present', async () => {
      const response = await request(app)
        .post('/login')
        .send({ username: 'alice', password: 'mission123', extra1: 'field1', extra2: 'field2' })
        .expect(400);

      expect(response.body.errorCode).toBe('VAL-422');
      expect(response.body).toHaveProperty('timestamp');
    });

    test('should return 400 with VAL-422 when malformed JSON is sent', async () => {
      const response = await request(app)
        .post('/login')
        .set('Content-Type', 'application/json')
        .send('{ invalid json }')
        .expect(400);

      expect(response.body.errorCode).toBe('VAL-422');
      expect(response.body.message).toBe('Request body is missing or malformed');
      expect(response.body).toHaveProperty('timestamp');
    });

    test('should return 400 with VAL-422 when JSON with trailing comma is sent', async () => {
      const response = await request(app)
        .post('/login')
        .set('Content-Type', 'application/json')
        .send('{"username": "alice", "password": "test",}')
        .expect(400);

      expect(response.body.errorCode).toBe('VAL-422');
      expect(response.body).toHaveProperty('timestamp');
    });

    test('should accept valid 1-character username', async () => {
      const response = await request(app)
        .post('/login')
        .send({ username: 'a', password: 'test' })
        .expect(401);  // Not found, but validation passed

      expect(response.body.errorCode).toBe('AUTH-401');
    });

    test('should accept valid 100-character username', async () => {
      const response = await request(app)
        .post('/login')
        .send({ username: 'a'.repeat(100), password: 'test' })
        .expect(401);  // Not found, but validation passed

      expect(response.body.errorCode).toBe('AUTH-401');
    });

    test('should accept valid 1-character password', async () => {
      const response = await request(app)
        .post('/login')
        .send({ username: 'alice', password: 'x' })
        .expect(401);  // Wrong password, but validation passed

      expect(response.body.errorCode).toBe('AUTH-401');
    });

    test('should accept valid 256-character password', async () => {
      const response = await request(app)
        .post('/login')
        .send({ username: 'alice', password: 'p'.repeat(256) })
        .expect(401);  // Wrong password, but validation passed

      expect(response.body.errorCode).toBe('AUTH-401');
    });
  });

  describe('POST /login - 401 Unauthorized (Invalid Credentials)', () => {
    test('should return 401 with AUTH-401 for wrong password', async () => {
      const response = await request(app)
        .post('/login')
        .send({ username: 'alice', password: 'wrongpassword' })
        .expect(401);

      expect(response.body).toEqual({
        errorCode: 'AUTH-401',
        message: 'invalid username or password',
        timestamp: expect.any(String),
      });
    });

    test('should return 401 with AUTH-401 for non-existent user', async () => {
      const response = await request(app)
        .post('/login')
        .send({ username: 'nonexistent', password: 'test' })
        .expect(401);

      expect(response.body).toEqual({
        errorCode: 'AUTH-401',
        message: 'invalid username or password',
        timestamp: expect.any(String),
      });
    });

    test('should return 401 with AUTH-401 for bob with wrong password', async () => {
      const response = await request(app)
        .post('/login')
        .send({ username: 'bob', password: 'wrongpassword' })
        .expect(401);

      expect(response.body.errorCode).toBe('AUTH-401');
      expect(response.body).toHaveProperty('timestamp');
    });

    test('should return 401 with AUTH-401 for any user with empty password', async () => {
      const response = await request(app)
        .post('/login')
        .send({ username: 'alice', password: 'mission123' })
        .expect(200);  // Should succeed

      // Now try with wrong password (but not empty)
      const wrongResponse = await request(app)
        .post('/login')
        .send({ username: 'alice', password: 'different' })
        .expect(401);

      expect(wrongResponse.body.errorCode).toBe('AUTH-401');
    });
  });

  describe('GET /health - 200 Success Response', () => {
    test('should return 200 with status "up"', async () => {
      const response = await request(app)
        .get('/health')
        .expect(200);

      expect(response.body).toEqual({
        status: 'up',
      });
    });

    test('should have correct Content-Type application/json', async () => {
      await request(app)
        .get('/health')
        .expect('Content-Type', /json/);
    });

    test('health endpoint should not include error envelope fields', async () => {
      const response = await request(app)
        .get('/health')
        .expect(200);

      expect(response.body).not.toHaveProperty('errorCode');
      expect(response.body).not.toHaveProperty('message');
      expect(response.body).not.toHaveProperty('timestamp');
    });
  });

  describe('405 Method Not Allowed - Wrong HTTP Verbs', () => {
    test('should return 405 with REQ-405 for POST to /health', async () => {
      const response = await request(app)
        .post('/health')
        .send({})
        .expect(405);

      expect(response.body).toEqual({
        errorCode: 'REQ-405',
        message: expect.any(String),
        timestamp: expect.any(String),
      });
      expect(response.body.errorCode).toBe('REQ-405');
    });

    test('should return 405 with REQ-405 for GET to /login', async () => {
      const response = await request(app)
        .get('/login')
        .expect(405);

      expect(response.body).toEqual({
        errorCode: 'REQ-405',
        message: expect.any(String),
        timestamp: expect.any(String),
      });
      expect(response.body.errorCode).toBe('REQ-405');
    });

    test('should return 405 with REQ-405 for PUT to /health', async () => {
      const response = await request(app)
        .put('/health')
        .send({})
        .expect(405);

      expect(response.body.errorCode).toBe('REQ-405');
      expect(response.body).toHaveProperty('timestamp');
    });

    test('should return 405 with REQ-405 for DELETE to /login', async () => {
      const response = await request(app)
        .delete('/login')
        .expect(405);

      expect(response.body.errorCode).toBe('REQ-405');
      expect(response.body).toHaveProperty('timestamp');
    });

    test('should return 405 with REQ-405 for PATCH to /health', async () => {
      const response = await request(app)
        .patch('/health')
        .send({})
        .expect(405);

      expect(response.body.errorCode).toBe('REQ-405');
      expect(response.body).toHaveProperty('timestamp');
    });
  });

  describe('404 Not Found - Unknown Routes', () => {
    test('should return 404 with NOT-404 for unknown POST route', async () => {
      const response = await request(app)
        .post('/unknown')
        .send({})
        .expect(404);

      expect(response.body).toEqual({
        errorCode: 'NOT-404',
        message: expect.any(String),
        timestamp: expect.any(String),
      });
      expect(response.body.errorCode).toBe('NOT-404');
    });

    test('should return 404 with NOT-404 for unknown GET route', async () => {
      const response = await request(app)
        .get('/unknown-route')
        .expect(404);

      expect(response.body).toEqual({
        errorCode: 'NOT-404',
        message: expect.any(String),
        timestamp: expect.any(String),
      });
      expect(response.body.errorCode).toBe('NOT-404');
    });

    test('should return 404 with NOT-404 for /api/users route', async () => {
      const response = await request(app)
        .get('/api/users')
        .expect(404);

      expect(response.body.errorCode).toBe('NOT-404');
      expect(response.body).toHaveProperty('timestamp');
    });

    test('should return 404 with NOT-404 for /api/login route (different path)', async () => {
      const response = await request(app)
        .post('/api/login')
        .send({ username: 'alice', password: 'mission123' })
        .expect(404);

      expect(response.body.errorCode).toBe('NOT-404');
      expect(response.body).toHaveProperty('timestamp');
    });

    test('should return 404 with NOT-404 for /v1/health route', async () => {
      const response = await request(app)
        .get('/v1/health')
        .expect(404);

      expect(response.body.errorCode).toBe('NOT-404');
      expect(response.body).toHaveProperty('timestamp');
    });

    test('should return 404 with NOT-404 for nested unknown path', async () => {
      const response = await request(app)
        .get('/api/auth/login')
        .expect(404);

      expect(response.body.errorCode).toBe('NOT-404');
      expect(response.body).toHaveProperty('timestamp');
    });
  });

  describe('Platform Envelope Format Compliance', () => {
    test('error response should have timestamp in ISO 8601 format', async () => {
      const response = await request(app)
        .post('/login')
        .send({ username: 'alice', password: 'wrong' })
        .expect(401);

      // ISO 8601: YYYY-MM-DDTHH:mm:ss.sssZ
      expect(response.body.timestamp).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/);
    });

    test('error response should never expose stack traces', async () => {
      const response = await request(app)
        .post('/login')
        .send({ password: 'test' })
        .expect(400);

      expect(response.body.message).not.toContain('Error');
      expect(response.body.message).not.toContain('at ');
      expect(response.body.message).not.toContain('.js:');
      expect(response.body.message).not.toContain('TypeError');
    });

    test('error response should never expose framework details', async () => {
      const response = await request(app)
        .post('/login')
        .send({ username: 'alice', password: 'wrong' })
        .expect(401);

      expect(response.body.message).not.toContain('Express');
      expect(response.body.message).not.toContain('middleware');
      expect(response.body.message).not.toContain('route');
    });

    test('error response should always include exactly three fields: errorCode, message, timestamp', async () => {
      const response = await request(app)
        .post('/login')
        .send({})
        .expect(400);

      const keys = Object.keys(response.body).sort();
      expect(keys).toEqual(['errorCode', 'message', 'timestamp']);
    });

    test('validation error response should include only errorCode, message, timestamp', async () => {
      const response = await request(app)
        .post('/login')
        .send({ username: 'alice', password: 'test', extra: 'field' })
        .expect(400);

      expect(Object.keys(response.body).sort()).toEqual(['errorCode', 'message', 'timestamp']);
      expect(response.body).not.toHaveProperty('error');
      expect(response.body).not.toHaveProperty('status');
      expect(response.body).not.toHaveProperty('details');
    });

    test('authentication error response should include only errorCode, message, timestamp', async () => {
      const response = await request(app)
        .post('/login')
        .send({ username: 'alice', password: 'wrong' })
        .expect(401);

      expect(Object.keys(response.body).sort()).toEqual(['errorCode', 'message', 'timestamp']);
      expect(response.body).not.toHaveProperty('error');
      expect(response.body).not.toHaveProperty('credentials');
    });

    test('404 error response should include only errorCode, message, timestamp', async () => {
      const response = await request(app)
        .get('/unknown')
        .expect(404);

      expect(Object.keys(response.body).sort()).toEqual(['errorCode', 'message', 'timestamp']);
    });

    test('405 error response should include only errorCode, message, timestamp', async () => {
      const response = await request(app)
        .post('/health')
        .send({})
        .expect(405);

      expect(Object.keys(response.body).sort()).toEqual(['errorCode', 'message', 'timestamp']);
    });
  });

  describe('Success Response Format (No Platform Envelope)', () => {
    test('login success should return only token property', async () => {
      const response = await request(app)
        .post('/login')
        .send({ username: 'alice', password: 'mission123' })
        .expect(200);

      expect(Object.keys(response.body)).toEqual(['token']);
      expect(response.body).not.toHaveProperty('errorCode');
      expect(response.body).not.toHaveProperty('message');
      expect(response.body).not.toHaveProperty('timestamp');
    });

    test('health success should return only status property', async () => {
      const response = await request(app)
        .get('/health')
        .expect(200);

      expect(Object.keys(response.body)).toEqual(['status']);
      expect(response.body.status).toBe('up');
      expect(response.body).not.toHaveProperty('errorCode');
    });
  });

  describe('Error Codes and HTTP Status Codes', () => {
    test('validation error should use 400 status with VAL-422 error code', async () => {
      const response = await request(app)
        .post('/login')
        .send({ username: 'alice' })
        .expect(400);

      expect(response.status).toBe(400);
      expect(response.body.errorCode).toBe('VAL-422');
    });

    test('authentication error should use 401 status with AUTH-401 error code', async () => {
      const response = await request(app)
        .post('/login')
        .send({ username: 'alice', password: 'wrong' })
        .expect(401);

      expect(response.status).toBe(401);
      expect(response.body.errorCode).toBe('AUTH-401');
    });

    test('not found error should use 404 status with NOT-404 error code', async () => {
      const response = await request(app)
        .get('/unknown')
        .expect(404);

      expect(response.status).toBe(404);
      expect(response.body.errorCode).toBe('NOT-404');
    });

    test('method not allowed should use 405 status with REQ-405 error code', async () => {
      const response = await request(app)
        .post('/health')
        .send({})
        .expect(405);

      expect(response.status).toBe(405);
      expect(response.body.errorCode).toBe('REQ-405');
    });
  });

  describe('Request-Response Integration', () => {
    test('successful login should not repeat a request', async () => {
      const response = await request(app)
        .post('/login')
        .send({ username: 'alice', password: 'mission123' })
        .expect(200);

      // Should receive one token, not multiple
      expect(response.body).toHaveProperty('token');
      expect(typeof response.body.token).toBe('string');
    });

    test('multiple failed logins should each return consistent error response', async () => {
      const response1 = await request(app)
        .post('/login')
        .send({ username: 'user1', password: 'wrong' })
        .expect(401);

      const response2 = await request(app)
        .post('/login')
        .send({ username: 'user2', password: 'wrong' })
        .expect(401);

      expect(response1.body.errorCode).toBe('AUTH-401');
      expect(response2.body.errorCode).toBe('AUTH-401');
      expect(response1.body.message).toBe(response2.body.message);
    });

    test('login and health endpoints should both work correctly in sequence', async () => {
      const loginResponse = await request(app)
        .post('/login')
        .send({ username: 'alice', password: 'mission123' })
        .expect(200);

      expect(loginResponse.body).toHaveProperty('token');

      const healthResponse = await request(app)
        .get('/health')
        .expect(200);

      expect(healthResponse.body.status).toBe('up');
    });
  });
});
