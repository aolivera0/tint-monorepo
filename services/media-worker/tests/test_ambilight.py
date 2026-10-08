import pytest

from media_worker.ambilight import Region, average_color, edge_regions, sample_timestamps_ms


def test_edge_regions_are_ten_percent_bands():
    regions = edge_regions(1920, 1080)
    assert regions["top"] == Region(0, 0, 1920, 108)
    assert regions["bottom"] == Region(0, 972, 1920, 108)
    assert regions["left"] == Region(0, 0, 192, 1080)
    assert regions["right"] == Region(1728, 0, 192, 1080)


def test_edge_regions_minimum_one_pixel():
    assert edge_regions(4, 4)["top"].height == 1


@pytest.mark.parametrize(
    ("width", "height", "ratio"), [(0, 10, 0.1), (10, -1, 0.1), (10, 10, 0), (10, 10, 0.6)]
)
def test_edge_regions_rejects_invalid_input(width, height, ratio):
    with pytest.raises(ValueError):
        edge_regions(width, height, ratio)


def test_average_color():
    assert average_color([(255, 0, 0), (0, 0, 255)]) == "#800080"


def test_average_color_requires_pixels():
    with pytest.raises(ValueError):
        average_color([])


def test_sample_timestamps_one_fps():
    assert sample_timestamps_ms(3500) == [0, 1000, 2000, 3000]


@pytest.mark.parametrize(("duration", "fps"), [(-1, 1), (1000, 0)])
def test_sample_timestamps_rejects_invalid_input(duration, fps):
    with pytest.raises(ValueError):
        sample_timestamps_ms(duration, fps)
