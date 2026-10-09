import { vi, beforeEach, describe, it, expect } from 'vitest';
import { apiFetch, ApiError } from './api';

const mockedFetch = vi.fn();
vi.stubGlobal('fetch', mockedFetch);

beforeEach(() => {
  vi.clearAllMocks();
  localStorage.clear();
});

describe('apiFetch', () => {
  it('retorna el json si la respuesta es ok', async () => {
    mockedFetch.mockResolvedValueOnce({ ok: true, json: () => Promise.resolve({ a: 1 }) });
    await expect(apiFetch('/x')).resolves.toEqual({ a: 1 });
  });

  it('lanza ApiError con status y mensaje del backend si la respuesta falla', async () => {
    mockedFetch.mockResolvedValueOnce({
      ok: false,
      status: 401,
      json: () => Promise.resolve({ error: 'Unauthorized' }),
    });
    const err = await apiFetch('/x').catch((e: unknown) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect((err as ApiError).status).toBe(401);
    expect((err as ApiError).message).toBe('Unauthorized');
  });

  it('envía el token de host en Authorization', async () => {
    localStorage.setItem('tint_token', 'tok-1');
    mockedFetch.mockResolvedValueOnce({ ok: true, json: () => Promise.resolve({}) });
    await apiFetch('/rooms', { method: 'POST', body: JSON.stringify({}) });
    const [, opts] = mockedFetch.mock.calls[0] as [unknown, { headers: Headers }];
    expect(opts.headers.get('Authorization')).toBe('Bearer tok-1');
    expect(opts.headers.get('Content-Type')).toBe('application/json');
  });

  it('envía el guest token si no hay token de host', async () => {
    localStorage.setItem('tint_guest_token', 'guest-1');
    mockedFetch.mockResolvedValueOnce({ ok: true, json: () => Promise.resolve({}) });
    await apiFetch('/rooms');
    const [, opts] = mockedFetch.mock.calls[0] as [unknown, { headers: Headers }];
    expect(opts.headers.get('Authorization')).toBe('Bearer guest-1');
  });
});
