/** Umbral a partir del cual el cliente se re-alinea con el reloj maestro. */
export const DRIFT_THRESHOLD_MS = 500;

/**
 * Calcula el desfase (ms) entre la posición local y la posición esperada según el
 * estado maestro, compensando el tiempo transcurrido desde que se emitió.
 */
export function computeDrift(
  localPositionMs: number,
  masterPositionMs: number,
  masterUpdatedAtMs: number,
  nowMs: number,
  playing: boolean,
): number {
  const elapsed = playing ? Math.max(0, nowMs - masterUpdatedAtMs) : 0;
  return localPositionMs - (masterPositionMs + elapsed);
}

export function shouldResync(driftMs: number, thresholdMs = DRIFT_THRESHOLD_MS): boolean {
  return Math.abs(driftMs) > thresholdMs;
}
