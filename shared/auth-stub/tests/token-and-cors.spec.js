const request = require('supertest');
const app = require('../server');

const decodePayload = (jwt) => JSON.parse(Buffer.from(jwt.split('.')[1], 'base64url').toString());

describe('POST /token - OAuth2 password grant (used by Swagger UI)', () => {
  test('issues a bearer token carrying the user\'s roles', async () => {
    const response = await request(app)
      .post('/token')
      .type('form')
      .send({ grant_type: 'password', username: 'john', password: 'customer123' })
      .expect(200);

    expect(response.body.token_type).toBe('Bearer');
    expect(response.body.expires_in).toBe(3600);
    const payload = decodePayload(response.body.access_token);
    expect(payload.sub).toBe('john');
    expect(payload.roles).toEqual(['CUSTOMER']);
  });

  test('issues an ADMIN token for the admin user', async () => {
    const response = await request(app)
      .post('/token')
      .type('form')
      .send({ grant_type: 'password', username: 'admin', password: 'adminPassword' })
      .expect(200);

    expect(decodePayload(response.body.access_token).roles).toEqual(['ADMIN']);
  });

  test('rejects a wrong password with invalid_grant', async () => {
    const response = await request(app)
      .post('/token')
      .type('form')
      .send({ grant_type: 'password', username: 'admin', password: 'wrong' })
      .expect(400);

    expect(response.body.error).toBe('invalid_grant');
    expect(response.body).not.toHaveProperty('access_token');
  });

  test('rejects grant types other than password', async () => {
    const response = await request(app)
      .post('/token')
      .type('form')
      .send({ grant_type: 'client_credentials' })
      .expect(400);

    expect(response.body.error).toBe('unsupported_grant_type');
  });
});

describe('CORS for the Swagger UI origin', () => {
  test('answers the preflight for an allowed origin', async () => {
    const response = await request(app)
      .options('/token')
      .set('Origin', 'http://localhost:3000')
      .set('Access-Control-Request-Method', 'POST')
      .expect(204);

    expect(response.headers['access-control-allow-origin']).toBe('http://localhost:3000');
  });

  test('does not allow other origins', async () => {
    const response = await request(app)
      .options('/token')
      .set('Origin', 'http://evil.example')
      .expect(204);

    expect(response.headers['access-control-allow-origin']).toBeUndefined();
  });
});
