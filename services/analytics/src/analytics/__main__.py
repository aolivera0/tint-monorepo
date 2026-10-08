"""Punto de entrada del consumidor (XREADGROUP se implementará en próximas HU)."""

from analytics.sanitize import CONSUMER_GROUP, STREAM_KEY


def main() -> str:
    message = f"analytics listo (stream={STREAM_KEY}, group={CONSUMER_GROUP})"
    print(message)
    return message


if __name__ == "__main__":  # pragma: no cover
    main()
