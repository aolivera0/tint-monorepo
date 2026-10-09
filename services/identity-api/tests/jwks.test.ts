import { describe, it, expect, vi } from 'vitest';

vi.mock('jwks-rsa', () => {
  const getSigningKey = (kid: string, cb: (err: Error | null, key: unknown) => void) => {
    if (kid === 'good-kid') {
      cb(null, { getPublicKey: () => 'pem-key' });
    } else {
      cb(new Error('kid no encontrado'), null);
    }
  };
  return { default: () => ({ getSigningKey }) };
});

import { getSigningKey } from '../src/modules/auth/jwks.js';

describe('getSigningKey', () => {
  it('resuelve la clave pública si el kid existe', async () => {
    await expect(getSigningKey('good-kid')).resolves.toBe('pem-key');
  });

  it('rechaza si el kid no existe', async () => {
    await expect(getSigningKey('bad-kid')).rejects.toThrow();
  });
});
