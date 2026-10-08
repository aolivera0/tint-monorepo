from types import SimpleNamespace
from unittest.mock import Mock

import pytest

from media_worker.probe import (
    FFPROBE_CMD,
    ProbeError,
    VideoStatus,
    inspect_status,
    parse_duration_ms,
    probe_duration_ms,
)


def fake_run(returncode=0, stdout="", stderr=""):
    return Mock(return_value=SimpleNamespace(returncode=returncode, stdout=stdout, stderr=stderr))


def test_parse_duration_ms_rounds_to_milliseconds():
    assert parse_duration_ms('{"format": {"duration": "12.3456"}}') == 12346


@pytest.mark.parametrize(
    "payload",
    [
        "not json",
        "{}",
        '{"format": {}}',
        '{"format": {"duration": "abc"}}',
        '{"format": {"duration": "0"}}',
    ],
)
def test_parse_duration_ms_rejects_invalid_output(payload):
    with pytest.raises(ProbeError):
        parse_duration_ms(payload)


def test_probe_duration_ms_invokes_ffprobe():
    runner = fake_run(stdout='{"format": {"duration": "2.5"}}')
    assert probe_duration_ms("movie.mp4", runner) == 2500
    args, kwargs = runner.call_args
    assert args[0] == [*FFPROBE_CMD, "movie.mp4"]
    assert kwargs["capture_output"] is True


def test_probe_duration_ms_raises_on_ffprobe_failure():
    with pytest.raises(ProbeError, match="moov atom not found"):
        probe_duration_ms("broken.mp4", fake_run(returncode=1, stderr="moov atom not found\n"))


def test_probe_duration_ms_default_error_message():
    with pytest.raises(ProbeError, match="ffprobe falló"):
        probe_duration_ms("broken.mp4", fake_run(returncode=1))


def test_inspect_status_ready():
    runner = fake_run(stdout='{"format": {"duration": "1"}}')
    assert inspect_status("ok.mp4", runner) == (VideoStatus.READY, 1000)


def test_inspect_status_failed_on_corrupt_file():
    assert inspect_status("bad.mp4", fake_run(returncode=1)) == (VideoStatus.FAILED, None)
