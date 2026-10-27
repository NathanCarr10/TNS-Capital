from forwarder import Forwarder, level_of, to_record


def test_spring_ecs_line_keeps_its_fields_and_level():
    line = '{"log":{"level":"WARN","logger":"x"},"message":"Kafka lag high","service":{"name":"api"}}'
    record = to_record("app", 1_700_000_000_000, line)
    assert record["level"] == "warn"
    assert record["message"] == "Kafka lag high"
    # The CloudWatch group names the service, as the Compose label does locally
    assert record["service"] == "app"
    assert record["date"].startswith("2023-11-14T22:13:20")


def test_plain_text_line_takes_the_level_word():
    record = to_record("etl", 0, "2026-10-27 11:30:39,964 ERROR Load failed\n")
    assert record["level"] == "error"
    assert record["message"] == "2026-10-27 11:30:39,964 ERROR Load failed"


def test_lower_case_words_are_not_levels():
    assert level_of({}, "the error count is zero") == "info"


def test_json_msg_field_is_the_message():
    record = to_record("auth-service", 0, '{"level":"error","msg":"Login failed"}')
    assert (record["level"], record["message"]) == ("error", "Login failed")


class FakeLogs:
    def __init__(self, events):
        self.events = events

    def get_paginator(self, name):
        logs = self

        class Paginator:
            def paginate(self, **kwargs):
                if name == "describe_log_groups":
                    return [{"logGroups": [{"logGroupName": "/ecs/tns-capital/app"}]}]
                return [{"events": [e for e in logs.events if e["timestamp"] >= kwargs["startTime"]]}]

        return Paginator()


def test_each_event_is_sent_once_across_polls():
    sent = []
    logs = FakeLogs([{"eventId": "1", "timestamp": 10**13, "message": "a"}])
    forwarder = Forwarder(logs, sent.extend)
    forwarder.run_once()
    # A second event at the same millisecond, and the first one again from the overlap
    logs.events.append({"eventId": "2", "timestamp": 10**13, "message": "b"})
    forwarder.run_once()
    forwarder.run_once()
    assert [r["message"] for r in sent] == ["a", "b"]
