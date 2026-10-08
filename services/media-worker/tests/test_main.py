from media_worker.__main__ import main


def test_main_reports_ready(capsys):
    assert "media-worker listo" in main()
    assert "failed" in capsys.readouterr().out
