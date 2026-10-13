"""Admin sign-in: token checks with real JWTs, and the auth service call with a fake HTTP client."""

import time

import pytest

jwt = pytest.importorskip("jwt")
requests = pytest.importorskip("requests")

import auth  # noqa: E402

SECRET = "test-secret-of-at-least-32-bytes-long!!"


@pytest.fixture(autouse=True)
def secret(monkeypatch):
    monkeypatch.setenv("JWT_SECRET", SECRET)


def token(key=SECRET, algorithm="HS256", expires_in=900, **overrides):
    now = int(time.time())
    claims = {"sub": "admin", "roles": ["ADMIN"], "typ": "access", "iss": auth.ISSUER, "iat": now,
              "exp": now + expires_in, **overrides}
    return jwt.encode({k: v for k, v in claims.items() if v is not None}, key, algorithm=algorithm)


def test_accepts_a_valid_admin_token():
    claims = auth.verify(token())

    assert claims["sub"] == "admin"
    assert auth.is_admin(claims)


def test_a_trader_token_is_valid_but_not_admin():
    assert not auth.is_admin(auth.verify(token(roles=["USER"])))


@pytest.mark.parametrize("bad_token, message", [
    (token(expires_in=-60), "expired"),
    (token(key="another-secret-that-is-32-bytes-long!!"), "not valid"),
    (token(iss="urn:someone-else"), "not valid"),
    (token(typ="refresh"), "not valid"),
    (token(roles=None), "not valid"),
    (token(key=SECRET * 2, algorithm="HS512"), "not valid"),
    ("not-a-jwt", "not valid"),
], ids=["expired", "wrong-signature", "wrong-issuer", "refresh-token", "no-roles", "other-algorithm", "garbage"])
def test_rejects_bad_tokens(bad_token, message):
    with pytest.raises(auth.AuthError, match=message):
        auth.verify(bad_token)


def test_refuses_to_check_tokens_without_a_secret(monkeypatch):
    monkeypatch.delenv("JWT_SECRET")

    with pytest.raises(auth.AuthError, match="missing JWT_SECRET"):
        auth.verify(token())


class FakeResponse:
    def __init__(self, status_code, body=None):
        self.status_code = status_code
        self.body = body

    def json(self):
        return self.body


def test_login_returns_the_access_token(monkeypatch):
    calls = []

    def post(url, json, timeout):
        calls.append((url, json))
        return FakeResponse(200, {"accessToken": "abc", "refreshToken": "def"})

    monkeypatch.setattr(auth.requests, "post", post)

    assert auth.login("admin", "pw") == "abc"
    assert calls == [(f"{auth.AUTH_URL}/auth/login", {"username": "admin", "password": "pw"})]


@pytest.mark.parametrize("status, message", [
    (401, "Invalid username or password"),
    (429, "Too many sign-in attempts"),
    (500, "HTTP 500"),
])
def test_login_explains_failures(monkeypatch, status, message):
    monkeypatch.setattr(auth.requests, "post", lambda url, json, timeout: FakeResponse(status))

    with pytest.raises(auth.AuthError, match=message):
        auth.login("admin", "pw")


def test_login_reports_an_unreachable_auth_service(monkeypatch):
    def down(url, json, timeout):
        raise requests.ConnectionError("refused")

    monkeypatch.setattr(auth.requests, "post", down)

    with pytest.raises(auth.AuthError, match="unreachable"):
        auth.login("admin", "pw")
