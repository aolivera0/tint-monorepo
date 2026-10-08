#!/usr/bin/env bash
# Falla si la cobertura total de un perfil de Go está por debajo del mínimo.
# Uso: check-go-coverage.sh <coverage.out> <minimo>
set -euo pipefail

profile="${1:-coverage.out}"
min="${2:-85}"

total=$(go tool cover -func="$profile" | awk '/^total:/ {gsub("%", "", $3); print $3}')
echo "Cobertura total: ${total}% (mínimo requerido: ${min}%)"

if awk -v t="$total" -v m="$min" 'BEGIN { exit !(t < m) }'; then
  echo "::error::La cobertura (${total}%) está por debajo del ${min}%"
  exit 1
fi
