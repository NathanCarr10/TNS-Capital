export interface LoginRequest {
  username: string;
  password: string;
}

export interface RegisterRequest {
  username: string;
  password: string;
  holderName?: string;
}

export interface RegisteredUser {
  username: string;
  accountId: number;
}

export interface TokenPair {
  accessToken: string;
  refreshToken: string;
}

export interface RefreshRequest {
  refreshToken: string;
}

export interface LogoutResponse {
  loggedOut: true;
}

export interface PlatformErrorResponse {
  errorCode: string;
  message: string;
  timestamp: string;
}

export interface JwtClaims {
  exp?: number;
  roles?: string[];
}
