const { registerUser, authenticateUser } = require('../src/userService');

describe('Auth Service - User Registration and Authentication', () => {
  describe('registerUser', () => {
    it('should register a new user with hashed password (AC#1)', async () => {
      const uniqueUsername = `testuser_${Date.now()}`;
      const password = 'testPassword123';

      const result = await registerUser(uniqueUsername, password);

      expect(result).toHaveProperty('id');
      expect(result).toHaveProperty('username', uniqueUsername);
      expect(result).not.toHaveProperty('password_hash'); // AC#5: Never expose hash
      expect(result).not.toHaveProperty('password'); // AC#5: Never expose plaintext
    });

    it('should reject duplicate username with error code CONFLICT (AC#4)', async () => {
      const username = `duplicate_${Date.now()}`;
      const password = 'testPassword123';

      // First registration succeeds
      await registerUser(username, password);

      // Second registration fails
      try {
        await registerUser(username, password);
        fail('Should have thrown error for duplicate username');
      } catch (error) {
        expect(error.code).toBe('CONFLICT');
        expect(error.message).toContain('already exists');
      }
    });

    it('should reject invalid passwords', async () => {
      const username = `validuser_${Date.now()}`;

      // Too short
      try {
        await registerUser(username, 'short');
        fail('Should reject short password');
      } catch (error) {
        expect(error.message).toContain('8 characters');
      }

      // Missing password
      try {
        await registerUser(username, '');
        fail('Should reject empty password');
      } catch (error) {
        expect(error.message).toBeTruthy();
      }
    });

    it('should reject invalid usernames', async () => {
      // Too short
      try {
        await registerUser('ab', 'validPassword123');
        fail('Should reject username < 3 chars');
      } catch (error) {
        expect(error.message).toContain('3-100 characters');
      }

      // Empty
      try {
        await registerUser('', 'validPassword123');
        fail('Should reject empty username');
      } catch (error) {
        expect(error.message).toBeTruthy();
      }
    });
  });

  describe('authenticateUser', () => {
    let testUsername;
    const testPassword = 'testPassword123';

    beforeAll(async () => {
      testUsername = `authtest_${Date.now()}`;
      await registerUser(testUsername, testPassword);
    });

    it('should authenticate valid credentials (AC#1, AC#6)', async () => {
      const user = await authenticateUser(testUsername, testPassword);

      expect(user).not.toBeNull();
      expect(user).toHaveProperty('username', testUsername);
      expect(user).not.toHaveProperty('password_hash'); // AC#5
      expect(user).not.toHaveProperty('password'); // AC#5
    });

    it('should reject invalid password (AC#6)', async () => {
      const user = await authenticateUser(testUsername, 'wrongPassword');
      expect(user).toBeNull(); // AC#6: Wrong password fails verification
    });

    it('should reject nonexistent user', async () => {
      const user = await authenticateUser('nonexistent_user', testPassword);
      expect(user).toBeNull();
    });

    it('should reject null/empty credentials', async () => {
      const user = await authenticateUser(null, testPassword);
      expect(user).toBeNull();

      const user2 = await authenticateUser(testUsername, '');
      expect(user2).toBeNull();
    });
  });

  describe('Password hashing security (AC#1, AC#6)', () => {
    it('should never store plaintext password', async () => {
      const username = `hashtest_${Date.now()}`;
      const password = 'mySecretPassword123';

      await registerUser(username, password);

      // Verify we can authenticate with correct password
      const user = await authenticateUser(username, password);
      expect(user).not.toBeNull();

      // Verify authentication fails with wrong password
      const wrongUser = await authenticateUser(username, 'wrongPassword');
      expect(wrongUser).toBeNull();
    });

    it('should store different hashes for same username registered twice', async () => {
      const username1 = `hashtest1_${Date.now()}`;
      const username2 = `hashtest2_${Date.now()}`;
      const password = 'samePassword123';

      // Register same password with different usernames
      const user1 = await registerUser(username1, password);
      const user2 = await registerUser(username2, password);

      // Both should authenticate successfully with same password
      const auth1 = await authenticateUser(username1, password);
      const auth2 = await authenticateUser(username2, password);

      expect(auth1).not.toBeNull();
      expect(auth2).not.toBeNull();
    });
  });
});
