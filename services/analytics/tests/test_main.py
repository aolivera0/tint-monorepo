from analytics.__main__ import main


def test_main_reports_stream(capsys):
    assert "analytics:events" in main()
    assert "analytics listo" in capsys.readouterr().out
