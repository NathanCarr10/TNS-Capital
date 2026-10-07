import { hashPassword, verifyPassword } from '../src/users/password-hasher';

describe('PasswordHasher', () => {
  describe('hashPassword', () => {
    it('should hash a password with Argon2id', async () => {
      const hash = await hashPassword('mySecurePassword123');

      // AC#3: Hash string includes algorithm, parameters, salt
      expect(hash).toMatch(/^\$argon2id\$/);
      expect(hash).toContain('m=65540'); // memory cost
      expect(hash).toContain('t=3'); // time cost
      expect(hash).toContain('p=4'); // parallelism
    });

    it('should produce different hashes for the same password (random salt)', async () => {
      const password = 'mySecurePassword123';
      const hash1 = await hashPassword(password);
      const hash2 = await hashPassword(password);

      // AC#6: Stored value differs from input
      expect(hash1).not.toEqual(hash2);
      expect(hash1).not.toBe(password);
    });

    it('should reject passwords shorter than 8 characters', async () => {
      await expect(hashPassword('short')).rejects.toThrow('at least 8 characters');
    });

    it('should reject null or empty passwords', async () => {
      await expect(hashPassword(null as unknown as string)).rejects.toThrow();
      await expect(hashPassword('')).rejects.toThrow();
    });
  });

  describe('verifyPassword', () => {
    it('should verify correct password against hash', async () => {
      const hash = await hashPassword('correctPassword123');
      expect(await verifyPassword('correctPassword123', hash)).toBe(true);
    });

    it('should reject incorrect password', async () => {
      const hash = await hashPassword('correctPassword123');
      expect(await verifyPassword('wrongPassword', hash)).toBe(false);
    });

    it('should return false for invalid hash format', async () => {
      expect(await verifyPassword('password', 'not-a-valid-hash')).toBe(false);
    });
  });
});
