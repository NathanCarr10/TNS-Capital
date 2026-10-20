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


def test_a_support_token_is_valid_but_not_admin():
    claims = auth.verify(token(roles=["SUPPORT"]))

    assert auth.is_support(claims)
    assert not auth.is_admin(claims)


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

    valid = token()

    with pytest.raises(auth.AuthError, match="missing JWT_SECRET"):
        auth.verify(valid)


class FakeResponse:
    def __init__(self, status_code, body=None):
        self.status_code = status_code
        self.body = body

    def json(self):
        return self.body


def test_refuses_to_trust_the_placeholder_secret(monkeypatch):
    placeholder = "change-me-to-a-random-value-of-at-least-32-bytes"
    monkeypatch.setenv("JWT_SECRET", placeholder)
    forged = jwt.encode({"sub": "attacker", "roles": ["ADMIN"], "typ": "access", "iss": auth.ISSUER,
                         "iat": int(time.time()), "exp": int(time.time()) + 900}, placeholder, algorithm="HS256")

    with pytest.raises(auth.AuthError, match="placeholder"):
        auth.verify(forged)


def test_login_returns_the_access_and_refresh_tokens(monkeypatch):
    calls = []

    def post(url, json, timeout):
        calls.append((url, json))
        return FakeResponse(200, {"accessToken": "abc", "refreshToken": "def"})

    monkeypatch.setattr(auth.requests, "post", post)

    assert auth.login("admin@tns-capital.local", "pw") == ("abc", "def")
    assert calls == [(f"{auth.AUTH_URL}/auth/login", {"email": "admin@tns-capital.local", "password": "pw"})]


@pytest.mark.parametrize("status, message", [
    (401, "Invalid email or password"),
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


def test_refresh_rotates_the_refresh_token(monkeypatch):
    calls = []

    def post(url, json, timeout):
        calls.append((url, json))
        return FakeResponse(200, {"accessToken": "new-access", "refreshToken": "new-refresh"})

    monkeypatch.setattr(auth.requests, "post", post)

    assert auth.refresh("old-refresh") == ("new-access", "new-refresh")
    assert calls == [(f"{auth.AUTH_URL}/auth/refresh", {"refreshToken": "old-refresh"})]


def test_a_refused_refresh_ends_the_session(monkeypatch):
    monkeypatch.setattr(auth.requests, "post", lambda url, json, timeout: FakeResponse(401))

    with pytest.raises(auth.AuthError, match="expired"):
        auth.refresh("spent")


def test_logout_revokes_the_session_and_never_fails(monkeypatch):
    calls = []
    monkeypatch.setattr(auth.requests, "post", lambda url, json, timeout: calls.append((url, json)))
    auth.logout("refresh")
    assert calls == [(f"{auth.AUTH_URL}/auth/logout", {"refreshToken": "refresh"})]

    def down(url, json, timeout):
        raise requests.ConnectionError("refused")

    monkeypatch.setattr(auth.requests, "post", down)
    auth.logout("refresh")  # no exception
