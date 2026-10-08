"""Cálculo de las franjas de borde usadas para la paleta Ambilight."""

from __future__ import annotations

from dataclasses import dataclass

EDGE_RATIO = 0.10
SAMPLE_FPS = 1


@dataclass(frozen=True)
class Region:
    """Rectángulo en píxeles (x, y, ancho, alto)."""

    x: int
    y: int
    width: int
    height: int


def edge_regions(width: int, height: int, ratio: float = EDGE_RATIO) -> dict[str, Region]:
    """Devuelve las franjas top/bottom/left/right del `ratio` de cada borde."""
    if width <= 0 or height <= 0:
        raise ValueError("las dimensiones deben ser positivas")
    if not 0 < ratio <= 0.5:
        raise ValueError("ratio debe estar en (0, 0.5]")
    band_h = max(1, round(height * ratio))
    band_w = max(1, round(width * ratio))
    return {
        "top": Region(0, 0, width, band_h),
        "bottom": Region(0, height - band_h, width, band_h),
        "left": Region(0, 0, band_w, height),
        "right": Region(width - band_w, 0, band_w, height),
    }


def average_color(pixels: list[tuple[int, int, int]]) -> str:
    """Promedia una lista de píxeles RGB y devuelve un color hex `#rrggbb`."""
    if not pixels:
        raise ValueError("se requiere al menos un píxel")
    n = len(pixels)
    r, g, b = (round(sum(p[i] for p in pixels) / n) for i in range(3))
    return f"#{r:02x}{g:02x}{b:02x}"


def sample_timestamps_ms(duration_ms: int, fps: int = SAMPLE_FPS) -> list[int]:
    """Instantes (ms) a muestrear para generar ambilight.json."""
    if duration_ms < 0 or fps <= 0:
        raise ValueError("duración y fps deben ser válidos")
    step = 1000 // fps
    return list(range(0, duration_ms, step))
