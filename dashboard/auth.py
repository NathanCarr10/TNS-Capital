"""Admin sign-in for the dashboard.

The dashboard shows every user's trades, so only admins may open it. A user
signs in through the auth service (POST /auth/login) and the dashboard then
verifies the access token itself, the same checks the auth service's guard
makes: HS256 signature with the shared JWT_SECRET, issuer, typ "access" and
expiry. The token's roles claim must include ADMIN; SUPPORT is not enough, as
support staff work in the trading app, not here.

The refresh token from the login keeps the session going: sessions.py holds both
tokens on the server and swaps the access token for a fresh one when it expires.
"""

import os
from pathlib import Path

import jwt
import requests
from dotenv import load_dotenv

load_dotenv(Path(__file__).resolve().parent.parent / ".env")

AUTH_URL = os.getenv("DASHBOARD_AUTH_URL", "http://localhost:4000")
ISSUER = "urn:tns-capital:auth-service"
ADMIN_ROLE = "ADMIN"
SUPPORT_ROLE = "SUPPORT"
TIMEOUT_SECONDS = 10


class AuthError(Exception):
    """Sign-in failed; the message is safe to show to the user."""


def login(email, password):
    """Exchange an email and password for (access token, refresh token) via the auth service."""
    try:
        response = requests.post(f"{AUTH_URL}/auth/login", json={"email": email.strip(), "password": password},
                                 timeout=TIMEOUT_SECONDS)
    except requests.RequestException as error:
        raise AuthError("The auth service is unreachable. Try again shortly.") from error
    if response.status_code == 401:
        raise AuthError("Invalid email or password.")
    if response.status_code == 400:
        raise AuthError("Enter a valid email address and your password.")
    if response.status_code == 429:
        raise AuthError("Too many sign-in attempts. Wait a minute and try again.")
    if response.status_code != 200:
        raise AuthError(f"Sign-in failed (HTTP {response.status_code}).")
    body = response.json()
    return body["accessToken"], body["refreshToken"]


def refresh(refresh_token):
    """Rotate a refresh token: (new access token, new refresh token). Raises AuthError if it is refused."""
    try:
        response = requests.post(f"{AUTH_URL}/auth/refresh", json={"refreshToken": refresh_token},
                                 timeout=TIMEOUT_SECONDS)
    except requests.RequestException as error:
        raise AuthError("The auth service is unreachable. Try again shortly.") from error
    if response.status_code != 200:
        raise AuthError("Your session has expired. Please sign in again.")
    body = response.json()
    return body["accessToken"], body["refreshToken"]


def logout(refresh_token):
    """Revoke the session at the auth service. Best effort: signing out here never fails."""
    try:
        requests.post(f"{AUTH_URL}/auth/logout", json={"refreshToken": refresh_token}, timeout=TIMEOUT_SECONDS)
    except requests.RequestException:
        pass


def verify(token):
    """The token's claims if it is a valid, unexpired access token; raises AuthError otherwise."""
    secret = os.getenv("JWT_SECRET")
    if not secret:
        raise AuthError("The dashboard is missing JWT_SECRET, so it cannot check sign-ins.")
    # Anyone could forge an admin token with the published placeholder (OWASP A02-5)
    if secret.lower().startswith(("change-me", "changeme")):
        raise AuthError("The dashboard's JWT_SECRET is the .env.example placeholder, so sign-ins are refused.")
    try:
        # The algorithm is pinned here, never taken from the token's header
        claims = jwt.decode(token, secret, algorithms=["HS256"], issuer=ISSUER,
                            options={"require": ["sub", "roles", "typ", "iss", "exp"]})
    except jwt.ExpiredSignatureError as error:
        raise AuthError("Your session has expired. Please sign in again.") from error
    except jwt.InvalidTokenError as error:
        raise AuthError("Your session is not valid. Please sign in again.") from error
    if claims["typ"] != "access":
        raise AuthError("Your session is not valid. Please sign in again.")
    return claims


def is_admin(claims):
    return ADMIN_ROLE in claims.get("roles", [])


def is_support(claims):
    return SUPPORT_ROLE in claims.get("roles", [])
