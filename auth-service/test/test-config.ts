import { AuthConfig } from "../src/config";

export const TEST_CONFIG: AuthConfig = {
  accessSecret: "test-access-secret-at-least-32-characters-long",
  refreshSecret: "test-refresh-secret-at-least-32-characters-long",
  issuer: "tns-capital-auth",
};
