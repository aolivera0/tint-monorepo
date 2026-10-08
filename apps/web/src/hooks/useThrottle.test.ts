import { act, renderHook } from '@testing-library/react';
import { MAX_REACTIONS_PER_SECOND, useThrottle } from './useThrottle';

describe('useThrottle', () => {
  it('permite como máximo 5 llamadas por segundo y libera la ventana', () => {
    let clock = 0;
    const fn = vi.fn();
    const { result } = renderHook(() => useThrottle(fn, undefined, undefined, () => clock));

    act(() => {
      for (let i = 0; i < MAX_REACTIONS_PER_SECOND; i++) {
        expect(result.current('👏')).toBe(true);
      }
      expect(result.current('👏')).toBe(false);
    });
    expect(fn).toHaveBeenCalledTimes(MAX_REACTIONS_PER_SECOND);
    expect(fn).toHaveBeenLastCalledWith('👏');

    clock = 1000;
    act(() => {
      expect(result.current('🔥')).toBe(true);
    });
    expect(fn).toHaveBeenCalledTimes(MAX_REACTIONS_PER_SECOND + 1);
  });

  it('usa Date.now por defecto', () => {
    const fn = vi.fn();
    const { result } = renderHook(() => useThrottle(fn));
    act(() => {
      expect(result.current()).toBe(true);
    });
    expect(fn).toHaveBeenCalledOnce();
  });
});
