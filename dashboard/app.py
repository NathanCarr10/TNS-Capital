"""TNS Capital trading dashboard (Streamlit).

It shows every user's trades, so it is for admins only: users sign in through
the auth service and only a token with the ADMIN role opens the page. Support
staff (SUPPORT role) work in the Angular trading app instead; the two roles are
kept apart (docs/DECISIONS.md, "Roles").

A reload keeps the admin signed in: the tokens stay on the server (sessions.py) and
the browser keeps only a session id in a cookie, for up to 8 hours.

The sidebar switches between three views:
- Live (live_page.py): what is happening now and what needs an admin, from the trading tables
- Batch (batch_page.py): trends and reports over the whole history, from the nightly ETL
- Monitoring (monitoring_page.py): alerts, metrics and logs from the monitoring stack
The chosen view is kept in the URL (?view=...), so a reload stays on it.

Run with:  streamlit run app.py
"""

import streamlit as st

import auth
import batch_page
import live_page
import monitoring_page
import sessions
import theme

st.set_page_config(page_title="TNS Capital Dashboard", page_icon=str(theme.LOGO_FAVICON), layout="wide")
theme.apply()


def sign_out():
    st.session_state.pop("token", None)
    session_id = st.session_state.pop("session_id", None)
    if session_id:
        refresh_token = sessions.STORE.remove(session_id)
        if refresh_token:
            auth.logout(refresh_token)  # revoked at the auth service too
        st.session_state["cookie"] = None  # cleared on the next run


def resume_session():
    """After a reload: the browser's session cookie leads back to the admin's tokens."""
    session_id = sessions.browser_cookie()
    token = sessions.STORE.access_token(session_id)
    if token:
        st.session_state["session_id"] = session_id
        st.session_state["token"] = token


def write_cookie():
    """Sets or clears the session cookie when sign-in or sign-out asked for it."""
    if "cookie" in st.session_state:
        st.html(sessions.cookie_script(st.session_state.pop("cookie")), unsafe_allow_javascript=True)


def signed_in_claims():
    """The token's claims, refreshing an expired token once; None (and signed out) if that fails."""
    token = st.session_state["token"]
    try:
        return auth.verify(token)  # every rerun, so an expired session ends here
    except auth.AuthError as error:
        message = str(error)
    fresh = sessions.STORE.refresh(st.session_state.get("session_id"), token, auth.refresh)
    if fresh:
        st.session_state["token"] = fresh
        try:
            return auth.verify(fresh)
        except auth.AuthError as error:
            message = str(error)
    sign_out()
    st.session_state["auth_message"] = message
    return None


def require_admin():
    """Stop the page unless an admin is signed in; show the sign-in form if nobody is."""
    write_cookie()
    if "token" not in st.session_state:
        resume_session()
    if st.session_state.get("token"):
        claims = signed_in_claims()
        if claims:
            if auth.is_admin(claims):
                return claims
            st.error(f"Signed in as {claims['sub']}, but this dashboard is for admins only.")
            if auth.is_support(claims):
                st.info("Support staff look after traders' accounts in the trading app, not here.")
            st.button("Sign out", on_click=sign_out)
            st.stop()

    # One centred card, like the trading app's login page
    theme.sign_in_page()
    _, card, _ = st.columns([1, 1, 1])
    with card:
        theme.logo(width=260)
        st.title("Analytics")
        st.caption("Admins only. Sign in with your TNS Capital account.")
        if message := st.session_state.pop("auth_message", None):
            st.warning(message)
        with st.form("sign_in"):
            email = st.text_input("Email", placeholder="you@example.com")
            password = st.text_input("Password", type="password")
            submitted = st.form_submit_button("Sign in", type="primary", width="stretch")
    if submitted:
        try:
            access_token, refresh_token = auth.login(email, password)
        except auth.AuthError as error:
            card.error(str(error))
        else:
            session_id = sessions.STORE.create(access_token, refresh_token)
            st.session_state.update(token=access_token, session_id=session_id, cookie=session_id)
            st.rerun()
    st.stop()


VIEWS = {"Live": live_page, "Batch": batch_page, "Monitoring": monitoring_page}

admin = require_admin()
with st.sidebar:
    theme.logo()
    # The view is kept in the URL (?view=Batch), so a reload opens on the same one
    linked = st.query_params.get("view")
    view = st.segmented_control("View", list(VIEWS), default=linked if linked in VIEWS else "Live",
                                key="view", width="stretch")
    st.query_params["view"] = view or "Live"
    monitoring_page.alert_badge()
    st.divider()
    st.write(f"Signed in as **{admin['sub']}**")
    st.button("Sign out", on_click=sign_out, icon=":material/logout:", width="stretch")

VIEWS[view or "Live"].render()
