"""Copies the services' CloudWatch log lines into VictoriaLogs, for the dashboard's log search.

Locally Fluent Bit tails Docker's log files (observability/fluent-bit). On Fargate there are
no log files to tail: every container logs to CloudWatch, so this polls the log groups
(/ecs/<prefix>/<service>) and sends new lines to VictoriaLogs in the same shape the Lua
filter gives them: service, level (error, warn, info or debug) and message, plus the
fields of JSON lines.
"""
import json
import logging
import os
import time
import urllib.request
from datetime import datetime, timezone

LOG_GROUP_PREFIX = os.getenv("LOG_GROUP_PREFIX", "/ecs/tns-capital/")
VICTORIALOGS_URL = os.getenv("VICTORIALOGS_URL", "http://victorialogs:9428")
POLL_SECONDS = float(os.getenv("POLL_SECONDS", "5"))
# On start, also copy the last few minutes, so the search isn't empty after a restart
BACKFILL_MINUTES = int(os.getenv("BACKFILL_MINUTES", "15"))
INSERT_PATH = "/insert/jsonline?_stream_fields=service&_msg_field=message&_time_field=date"

# As docker_metadata.lua: every spelling of a level, to one of four
LEVELS = {
    "error": "error", "fatal": "error", "panic": "error", "severe": "error",
    "warn": "warn", "warning": "warn",
    "info": "info", "log": "info", "notice": "info",
    "debug": "debug", "trace": "debug", "verbose": "debug",
}

log = logging.getLogger("log-forwarder")


def level_of(record, message):
    """The line's level: a level field if it has one, else an upper-case level word near the start."""
    level = record.get("log.level") or record.get("level")
    if level is None and isinstance(record.get("log"), dict):
        level = record["log"].get("level")
    if level is None and isinstance(message, str):
        for word in "".join(c if c.isalpha() else " " for c in message[:120]).split():
            if word.lower() in LEVELS and word == word.upper():
                level = word
                break
    return LEVELS.get(str(level or "info").lower(), "info")


def to_record(service, timestamp_ms, line):
    """One VictoriaLogs record for one CloudWatch event."""
    line = line.rstrip()
    try:
        parsed = json.loads(line)
    except ValueError:
        parsed = None
    record = parsed if isinstance(parsed, dict) else {}
    message = record.get("message")
    if message is None:
        message = record.get("msg") if isinstance(record.get("msg"), str) else line
    record["message"] = message
    record["level"] = level_of(record, message)
    record["service"] = service
    record["date"] = datetime.fromtimestamp(timestamp_ms / 1000, tz=timezone.utc).isoformat()
    return record


class Forwarder:
    def __init__(self, logs_client, send):
        self.logs = logs_client
        self.send = send
        start = int((time.time() - BACKFILL_MINUTES * 60) * 1000)
        self.since = {}  # log group -> timestamp (ms) of the newest event copied
        self.seen = {}  # log group -> event IDs at that timestamp, so none is copied twice
        self.start = start

    def groups(self):
        names = []
        for page in self.logs.get_paginator("describe_log_groups").paginate(logGroupNamePrefix=LOG_GROUP_PREFIX):
            names += [g["logGroupName"] for g in page["logGroups"]]
        return names

    def poll(self, group):
        since = self.since.get(group, self.start)
        seen = self.seen.get(group, set())
        service = group[len(LOG_GROUP_PREFIX):]
        records, newest, at_newest = [], since, set(seen)
        for page in self.logs.get_paginator("filter_log_events").paginate(logGroupName=group, startTime=since):
            for event in page["events"]:
                if event["eventId"] in seen:
                    continue
                records.append(to_record(service, event["timestamp"], event["message"]))
                if event["timestamp"] > newest:
                    newest, at_newest = event["timestamp"], set()
                if event["timestamp"] == newest:
                    at_newest.add(event["eventId"])
        if records:
            self.send(records)
        self.since[group], self.seen[group] = newest, at_newest
        return len(records)

    def run_once(self):
        total = 0
        for group in self.groups():
            try:
                total += self.poll(group)
            except Exception as error:  # one bad group must not stop the others
                log.warning("Skipped %s this round: %s", group, error)
        return total


def send_to_victorialogs(records):
    body = "\n".join(json.dumps(r, default=str) for r in records).encode()
    request = urllib.request.Request(VICTORIALOGS_URL + INSERT_PATH, data=body, method="POST",
                                     headers={"Content-Type": "application/stream+json"})
    with urllib.request.urlopen(request, timeout=10) as response:
        response.read()


def main():
    import boto3

    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    forwarder = Forwarder(boto3.client("logs"), send_to_victorialogs)
    log.info("Copying %s* to %s every %ss", LOG_GROUP_PREFIX, VICTORIALOGS_URL, POLL_SECONDS)
    while True:
        try:
            forwarder.run_once()
        except Exception as error:  # VictoriaLogs restarting, a throttled call: try again next round
            log.warning("Round failed: %s", error)
        time.sleep(POLL_SECONDS)


if __name__ == "__main__":
    main()
