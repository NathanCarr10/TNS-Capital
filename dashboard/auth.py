"""Admin sign-in for the dashboard.

The dashboard shows every user's trades, so only admins may open it. A user
signs in through the auth service (POST /auth/login) and the dashboard then
verifies the access token itself, the same checks the auth service's guard
makes: HS256 signature with the shared JWT_SECRET, issuer, typ "access" and
expiry. The token's roles claim must include ADMIN.
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
TIMEOUT_SECONDS = 10


class AuthError(Exception):
    """Sign-in failed; the message is safe to show to the user."""


def login(username, password):
    """Exchange a username and password for an access token via the auth service."""
    try:
        response = requests.post(f"{AUTH_URL}/auth/login", json={"username": username, "password": password},
                                 timeout=TIMEOUT_SECONDS)
    except requests.RequestException as error:
        raise AuthError("The auth service is unreachable. Try again shortly.") from error
    if response.status_code == 401:
        raise AuthError("Invalid username or password.")
    if response.status_code == 429:
        raise AuthError("Too many sign-in attempts. Wait a minute and try again.")
    if response.status_code != 200:
        raise AuthError(f"Sign-in failed (HTTP {response.status_code}).")
    return response.json()["accessToken"]


def verify(token):
    """The token's claims if it is a valid, unexpired access token; raises AuthError otherwise."""
    secret = os.getenv("JWT_SECRET")
    if not secret:
        raise AuthError("The dashboard is missing JWT_SECRET, so it cannot check sign-ins.")
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
