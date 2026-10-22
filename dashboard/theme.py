"""The trading app's look on the dashboard: what .streamlit/config.toml can't set.

config.toml carries the colours, font and chart palettes (light and dark). This adds the
Angular app's shapes on top (frontend/src/styles.scss): metrics, charts and forms on white
cards, uppercase eyebrow labels, a topbar-like header and the sidebar's logo.
"""

import html
from pathlib import Path

import streamlit as st

LOGO = Path(__file__).parent / "static" / "TNS_Capital_horizontal_dark_transparent.png"
LOGO_FAVICON = Path(__file__).parent / "static" / "TNS_Capital_icon_transparent.png"

# The trading app's tokens (styles.scss), for the parts config.toml has no option for
TOKENS = {
    "light": {"bg": "#e9ede8", "surface": "#ffffff", "border": "#dde3dc", "muted": "#5d6d68", "soft": "#e2ebe4",
              "accent": "#84a98c", "shadow": "0 1px 2px rgba(47, 62, 70, 0.05), 0 4px 14px rgba(47, 62, 70, 0.04)",
              "glow": "rgba(132, 169, 140, 0.35)", "glow2": "rgba(82, 121, 111, 0.25)"},
    "dark": {"bg": "#1c2529", "surface": "#253238", "border": "#36474e", "muted": "#a3b2ad", "soft": "#304a47",
             "accent": "#84a98c", "shadow": "0 1px 2px rgba(0, 0, 0, 0.25)",
             "glow": "rgba(132, 169, 140, 0.16)", "glow2": "rgba(82, 121, 111, 0.18)"},
}

# Chart colours, as in config.toml's chartCategoricalColors, plus fixed ones for the values
# the trading app colours the same way everywhere (filled green, rejected red, ...)
SERIES = {
    "light": ["#52796f", "#2f3e46", "#84a98c", "#c0634a", "#b5c8b9", "#5d6d68", "#354f52"],
    "dark": ["#84a98c", "#cad2c5", "#52796f", "#d77b62", "#9fc3a6", "#a3b2ad", "#3d5a5c"],
}
MEANING = {
    "light": {"FILLED": "#52796f", "BUY": "#52796f", "REJECTED": "#c0634a", "SELL": "#2f3e46",
              "CANCELLED": "#b5c8b9", "NEW": "#84a98c", "PARTIALLY_FILLED": "#84a98c", "Market close": "#5d6d68"},
    "dark": {"FILLED": "#84a98c", "BUY": "#84a98c", "REJECTED": "#d77b62", "SELL": "#cad2c5",
             "CANCELLED": "#5b6b70", "NEW": "#52796f", "PARTIALLY_FILLED": "#52796f", "Market close": "#a3b2ad"},
}

CSS = """
:root {{
  --tns-surface: {surface};
  --tns-border: {border};
  --tns-muted: {muted};
  --tns-soft: {soft};
  --tns-accent: {accent};
  --tns-shadow: {shadow};
}}

/* Header: a plain bar like the trading app's topbar */
[data-testid="stHeader"] {{
  background: var(--tns-surface);
  border-bottom: 1px solid var(--tns-border);
}}
[data-testid="stMainBlockContainer"] {{
  padding-top: 4.5rem;
  max-width: 88rem;
}}

/* Page titles and section headings */
h1 {{ font-size: 1.85rem !important; letter-spacing: -0.01em; }}
[data-testid="stMain"] h3 {{
  margin-top: 1.25rem;
  padding-top: 0.5rem;
  font-size: 1.15rem !important;
}}
.tns-eyebrow {{
  margin: 0 0 -0.6rem;
  font-size: 0.68rem;
  font-weight: 700;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--tns-muted);
}}

/* Cards: metrics, charts, tables and forms sit on the surface colour */
[data-testid="stMetric"],
[data-testid="stElementContainer"]:has([data-testid="stPlotlyChart"]),
[data-testid="stForm"] {{
  padding: 1rem 1.15rem;
  border: 1px solid var(--tns-border);
  border-radius: 12px;
  background: var(--tns-surface);
  box-shadow: var(--tns-shadow);
}}
/* Charts: the card is the chart's container, with no padding; plot() sets the figure's margins,
   since Plotly sizes itself to the container and padding would clip its edge */
[data-testid="stElementContainer"]:has([data-testid="stPlotlyChart"]) {{ padding: 0; overflow: hidden; }}
[data-testid="stDataFrame"] {{
  border-radius: 12px;
  box-shadow: var(--tns-shadow);
}}
[data-testid="stExpander"] details {{
  border-radius: 12px;
  background: var(--tns-surface);
}}

/* Metric tiles: eyebrow label over a large number */
[data-testid="stMetricLabel"] p {{
  font-size: 0.68rem !important;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--tns-muted);
}}
[data-testid="stMetricValue"] {{
  font-size: 1.6rem;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}}

/* Alerts as soft notes, like .note in the trading app */
[data-testid="stAlertContainer"] {{ border-radius: 8px; }}

/* Sidebar: the view switcher as navigation */
[data-testid="stSidebar"] [data-testid="stButtonGroup"] button[kind$="Active"] {{
  box-shadow: inset 3px 0 0 var(--tns-accent);
}}
[data-testid="stSidebar"] hr {{ margin: 0.75rem 0; }}
"""

# The sign-in page: the trading app's soft gradient behind a single card
SIGN_IN_CSS = """
[data-testid="stApp"] {{
  background:
    radial-gradient(circle at 12% 18%, {glow}, transparent 38%),
    radial-gradient(circle at 88% 82%, {glow2}, transparent 42%),
    {bg};
}}
[data-testid="stHeader"] {{ background: transparent; border: none; }}
[data-testid="stForm"] {{ padding: 1.5rem 1.6rem; border-radius: 1rem; box-shadow: 0 18px 45px rgba(47, 62, 70, 0.12); }}
[data-testid="stForm"] [data-testid="InputInstructions"] {{ display: none; }}
[data-testid="stElementContainer"]:has([data-testid="stImage"]) {{ align-self: center; }}
[data-testid="stImage"] img {{ border-radius: 12px; }}
"""


def scheme():
    """light or dark, as the viewer has it (Streamlit's Settings menu or the browser)."""
    try:
        return "dark" if st.context.theme.type == "dark" else "light"
    except AttributeError:  # no browser yet, e.g. in tests
        return "light"


def apply():
    """Style the page; call once per run, before anything is drawn."""
    st.html(f"<style>{CSS.format(**TOKENS[scheme()])}</style>")


def sign_in_page():
    """The extra styling of the sign-in page."""
    st.html(f"<style>{SIGN_IN_CSS.format(**TOKENS[scheme()])}</style>")


def eyebrow(text):
    """The small uppercase line above a page title."""
    st.html(f'<p class="tns-eyebrow">{html.escape(text)}</p>')


def logo(where=st, width="stretch"):
    where.image(str(LOGO), width=width)


def plot(where, figure):
    """Draw a Plotly figure in the brand colours, on the card behind it."""
    series, meaning = SERIES[scheme()], MEANING[scheme()]
    for index, trace in enumerate(figure.data):
        if trace.type == "pie":
            trace.marker.colors = [meaning.get(label, series[i % len(series)]) for i, label in enumerate(trace.labels)]
            continue
        color = meaning.get(trace.name, series[index % len(series)])
        if trace.type == "bar" or trace.mode == "markers":
            trace.marker.color = color
        else:
            trace.line.color = color
    figure.update_layout(paper_bgcolor="rgba(0,0,0,0)", plot_bgcolor="rgba(0,0,0,0)",
                         margin={"l": 20, "r": 20, "t": 64, "b": 20}, title_x=0.02)
    where.plotly_chart(figure, width="stretch")
