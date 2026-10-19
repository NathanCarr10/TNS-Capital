"""The server-side session store that keeps an admin signed in across reloads."""

import threading

import sessions


class Clock:
    def __init__(self):
        self.now = 1000.0

    def __call__(self):
        return self.now


def test_a_session_gives_back_its_access_token_until_it_ends():
    clock = Clock()
    store = sessions.SessionStore(lifetime_seconds=60, clock=clock)
    session_id = store.create("access-1", "refresh-1")

    assert len(session_id) >= 43  # 256 random bits
    assert store.access_token(session_id) == "access-1"
    clock.now += 61
    assert store.access_token(session_id) is None


def test_unknown_and_missing_ids_have_no_session():
    store = sessions.SessionStore()
    assert store.access_token("made-up") is None
    assert store.access_token(None) is None
    assert store.remove(None) is None


def test_refresh_rotates_both_tokens():
    store = sessions.SessionStore()
    session_id = store.create("access-1", "refresh-1")

    assert store.refresh(session_id, "access-1", lambda refresh: ("access-2", "refresh-2")) == "access-2"
    assert store.remove(session_id) == "refresh-2"


def test_a_second_tab_reuses_the_first_tabs_refresh():
    store = sessions.SessionStore()
    session_id = store.create("access-1", "refresh-1")
    calls = []

    def refresh(token):
        calls.append(token)
        return f"access-{len(calls) + 1}", f"refresh-{len(calls) + 1}"

    threads = [threading.Thread(target=store.refresh, args=(session_id, "access-1", refresh)) for _ in range(5)]
    for thread in threads:
        thread.start()
    for thread in threads:
        thread.join()

    assert calls == ["refresh-1"]  # never presented twice: that would look like token theft


def test_a_refused_refresh_ends_the_session():
    store = sessions.SessionStore()
    session_id = store.create("access-1", "refresh-1")

    def refused(token):
        raise RuntimeError("401")

    assert store.refresh(session_id, "access-1", refused) is None
    assert store.access_token(session_id) is None


def test_the_cookie_holds_only_the_session_id():
    script = sessions.cookie_script("abc")
    assert f"{sessions.COOKIE}=abc; Path=/analytics; Max-Age={int(sessions.SESSION_HOURS * 3600)}; SameSite=Strict" in script
    assert "Max-Age=0" in sessions.cookie_script(None)
