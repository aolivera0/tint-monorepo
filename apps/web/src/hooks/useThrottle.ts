import { useCallback, useRef } from 'react';

/** Máximo de reacciones por usuario por segundo (regla anti-spam de TINT). */
export const MAX_REACTIONS_PER_SECOND = 5;

/**
 * Envuelve `fn` en una ventana deslizante: como máximo `limit` llamadas por `windowMs`.
 * Devuelve `true` si la llamada se ejecutó.
 */
export function useThrottle<A extends unknown[]>(
  fn: (...args: A) => void,
  limit = MAX_REACTIONS_PER_SECOND,
  windowMs = 1000,
  now: () => number = Date.now,
): (...args: A) => boolean {
  const calls = useRef<number[]>([]);

  return useCallback(
    (...args: A) => {
      const t = now();
      calls.current = calls.current.filter((c) => t - c < windowMs);
      if (calls.current.length >= limit) return false;
      calls.current.push(t);
      fn(...args);
      return true;
    },
    [fn, limit, windowMs, now],
  );
}
