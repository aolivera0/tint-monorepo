import { computeDrift, DRIFT_THRESHOLD_MS, shouldResync } from './drift';

describe('computeDrift', () => {
  it('compensa el tiempo transcurrido cuando está reproduciendo', () => {
    expect(computeDrift(10_300, 10_000, 1_000, 1_200, true)).toBe(100);
  });

  it('ignora el tiempo transcurrido cuando está en pausa', () => {
    expect(computeDrift(10_300, 10_000, 1_000, 1_200, false)).toBe(300);
  });

  it('no usa tiempo negativo si el reloj local va atrasado', () => {
    expect(computeDrift(10_000, 10_000, 2_000, 1_000, true)).toBe(0);
  });
});

describe('shouldResync', () => {
  it.each([
    [0, false],
    [DRIFT_THRESHOLD_MS, false],
    [DRIFT_THRESHOLD_MS + 1, true],
    [-(DRIFT_THRESHOLD_MS + 1), true],
  ])('drift %i ms → %s', (drift, expected) => {
    expect(shouldResync(drift)).toBe(expected);
  });
});
