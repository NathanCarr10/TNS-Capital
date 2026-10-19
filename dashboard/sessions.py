"""Keeps an admin signed in across page reloads.

Streamlit's session state lives as long as one browser connection, so a reload used to
sign the admin out. Now, at sign-in, the access and refresh tokens are kept here, on the
server, under a random session id. The browser only gets that id, in a cookie scoped to
/analytics (app.py), and a reload finds the tokens again by it.

- The refresh token never reaches the browser.
- An expired access token is refreshed through the auth service, under a lock, so two
  tabs of one session never present the same refresh token twice. The auth service
  would treat that as token theft (OWASP A09-1) and end the session.
- A session lasts SESSION_HOURS (default 8) from sign-in, however it is used. Sign-out
  removes it. A restart of the dashboard forgets every session.
"""

import os
import secrets
import threading
import time

SESSION_HOURS = float(os.getenv("DASHBOARD_SESSION_HOURS", "8"))


class SessionStore:
    def __init__(self, lifetime_seconds=SESSION_HOURS * 3600, clock=time.time):
        self.lifetime_seconds = lifetime_seconds
        self.clock = clock
        self._sessions = {}
        self._lock = threading.Lock()

    def create(self, access_token, refresh_token):
        """A new session's id: 256 random bits, safe to put in a cookie."""
        session_id = secrets.token_urlsafe(32)
        with self._lock:
            self._drop_expired()
            self._sessions[session_id] = {"access": access_token, "refresh": refresh_token,
                                          "expires": self.clock() + self.lifetime_seconds}
        return session_id

    def access_token(self, session_id):
        """The session's current access token, or None if the session is unknown or over."""
        with self._lock:
            session = self._live(session_id)
            return session["access"] if session else None

    def refresh(self, session_id, stale_access_token, refresh):
        """A fresh access token for the session, or None if it cannot be refreshed.

        refresh(refresh_token) returns (access_token, refresh_token) or raises. If another
        tab already replaced stale_access_token, its new token is returned instead.
        """
        with self._lock:
            session = self._live(session_id)
            if session is None:
                return None
            if session["access"] != stale_access_token:
                return session["access"]
            try:
                session["access"], session["refresh"] = refresh(session["refresh"])
            except Exception:
                del self._sessions[session_id]
                return None
            return session["access"]

    def remove(self, session_id):
        """Ends the session; returns its refresh token (to revoke it), or None."""
        with self._lock:
            session = self._sessions.pop(session_id, None)
        return session["refresh"] if session else None

    def _live(self, session_id):
        session = self._sessions.get(session_id) if session_id else None
        if session and session["expires"] <= self.clock():
            del self._sessions[session_id]
            return None
        return session

    def _drop_expired(self):
        now = self.clock()
        for session_id in [key for key, session in self._sessions.items() if session["expires"] <= now]:
            del self._sessions[session_id]
