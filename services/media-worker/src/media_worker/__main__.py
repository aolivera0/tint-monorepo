"""Punto de entrada del worker (el consumo de la cola se implementará en próximas HU)."""

from media_worker.probe import VideoStatus


def main() -> str:
    message = f"media-worker listo (estados soportados: {', '.join(VideoStatus)})"
    print(message)
    return message


if __name__ == "__main__":  # pragma: no cover
    main()
