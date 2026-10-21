"""How the Live and Batch views write numbers, and the labels their charts share."""

import pandas as pd

VALUE_LABEL = "Value ($)"
ASSET_CLASS_LABEL = "Asset class"
FILL_RATE_LABEL = "Fill rate"


def money(value):
    return "n/a" if value is None else f"${value:,.2f}"


def percent(value):
    return "n/a" if value is None else f"{value:.0%}"


def duration(seconds):
    return "n/a" if seconds is None else f"{seconds:.1f} s"


def utc_now():
    """Now as a naive UTC timestamp, to compare with the database's UTC timestamps."""
    return pd.Timestamp.now(tz="UTC").tz_localize(None)
