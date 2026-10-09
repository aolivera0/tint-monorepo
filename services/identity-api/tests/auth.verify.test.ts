import { describe, it, expect, vi, beforeEach } from 'vitest';
import jwt from 'jsonwebtoken';
import { generateKeyPairSync } from 'node:crypto';
import type { Response } from 'express';
import { requireAuthHost } from '../src/middlewares/auth.js';
import { env } from '../src/config/env.js';

vi.mock('../src/modules/auth/jwks.js', () => ({
  getSigningKey: vi.fn(),
}));

vi.mock('../src/modules/users/users.repo.js', () => ({
  upsertByProvider: vi.fn(),
}));

import { getSigningKey } from '../src/modules/auth/jwks.js';
import { upsertByProvider } from '../src/modules/users/users.repo.js';

const { publicKey, privateKey } = generateKeyPairSync('rsa', {
  modulusLength: 2048,
  publicKeyEncoding: { type: 'spki', format: 'pem' },
  privateKeyEncoding: { type: 'pkcs8', format: 'pem' },
});

// Otra clave para simular firma inválida.
const { privateKey: otherPrivateKey } = generateKeyPairSync('rsa', {
  modulusLength: 2048,
  publicKeyEncoding: { type: 'spki', format: 'pem' },
  privateKeyEncoding: { type: 'pkcs8', format: 'pem' },
});

function firebaseToken(payloadOverrides: Record<string, unknown> = {}, key = privateKey) {
  return jwt.sign(
    {
      sub: 'firebase-uid-123',
      email: 'test@test.com',
      name: 'Test User',
      picture: 'http://pic/avatar.png',
      aud: env.FIREBASE_PROJECT_ID,
      iss: `https://securetoken.google.com/${env.FIREBASE_PROJECT_ID}`,
      ...payloadOverrides,
    },
    key,
    { algorithm: 'RS256', header: { kid: 'test-kid', alg: 'RS256' }, expiresIn: '1h' },
  );
}

function mockReq(token?: string) {
  return { headers: token ? { authorization: `Bearer ${token}` } : {} } as any;
}

function mockRes() {
  const res: any = {};
  res.status = vi.fn().mockReturnValue(res);
  res.json = vi.fn().mockReturnValue(res);
  return res as Response & { status: any; json: any };
}

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(getSigningKey).mockResolvedValue(publicKey);
  vi.mocked(upsertByProvider).mockResolvedValue({
    id: 'user-1',
    provider: 'firebase',
    providerSub: 'firebase-uid-123',
    email: 'test@test.com',
    displayName: 'Test User',
    avatarUrl: 'http://pic/avatar.png',
    createdAt: new Date(),
    updatedAt: new Date(),
  } as any);
});

describe('requireAuthHost', () => {
  it('401 si no hay header Authorization', async () => {
    const res = mockRes();
    const next = vi.fn();
    await requireAuthHost(mockReq(), res, next);
    expect(res.status).toHaveBeenCalledWith(401);
    expect(next).not.toHaveBeenCalled();
  });

  it('401 si el token no tiene kid', async () => {
    const token = jwt.sign({ sub: 'x' }, 'secret');
    const res = mockRes();
    const next = vi.fn();
    await requireAuthHost(mockReq(token), res, next);
    expect(res.status).toHaveBeenCalledWith(401);
    expect(res.json).toHaveBeenCalledWith({ error: 'Invalid token' });
    expect(next).not.toHaveBeenCalled();
  });

  it('401 si la firma es inválida', async () => {
    const token = firebaseToken({}, otherPrivateKey);
    const res = mockRes();
    const next = vi.fn();
    await requireAuthHost(mockReq(token), res, next);
    expect(res.status).toHaveBeenCalledWith(401);
    expect(next).not.toHaveBeenCalled();
  });

  it('401 si el audience no coincide con el proyecto (regresión: fallback a "tint")', async () => {
    // Simula el bug anterior: backend verificando contra iss/aud equivocados.
    const token = firebaseToken({ aud: 'otro-proyecto' });
    const res = mockRes();
    const next = vi.fn();
    await requireAuthHost(mockReq(token), res, next);
    expect(res.status).toHaveBeenCalledWith(401);
    expect(res.json).toHaveBeenCalledWith({ error: 'Invalid token' });
    expect(next).not.toHaveBeenCalled();
  });

  it('401 si el token no trae sub', async () => {
    const token = jwt.sign(
      {
        aud: env.FIREBASE_PROJECT_ID,
        iss: `https://securetoken.google.com/${env.FIREBASE_PROJECT_ID}`,
      },
      privateKey,
      { algorithm: 'RS256', header: { kid: 'test-kid', alg: 'RS256' }, expiresIn: '1h' },
    );
    const res = mockRes();
    const next = vi.fn();
    await requireAuthHost(mockReq(token), res, next);
    expect(res.status).toHaveBeenCalledWith(401);
    expect(next).not.toHaveBeenCalled();
  });

  it('inyecta req.user y llama next con token válido', async () => {
    const token = firebaseToken();
    const req = mockReq(token);
    const res = mockRes();
    const next = vi.fn();
    await requireAuthHost(req, res, next);
    expect(upsertByProvider).toHaveBeenCalledWith({
      providerSub: 'firebase-uid-123',
      email: 'test@test.com',
      displayName: 'Test User',
      avatarUrl: 'http://pic/avatar.png',
    });
    expect(req.user).toMatchObject({ id: 'user-1', providerSub: 'firebase-uid-123' });
    expect(next).toHaveBeenCalled();
  });

  it('500 si la DB falla en el upsert (antes se enmascaraba como 401)', async () => {
    vi.mocked(upsertByProvider).mockRejectedValueOnce(new Error('conn refused'));
    const token = firebaseToken();
    const res = mockRes();
    const next = vi.fn();
    await requireAuthHost(mockReq(token), res, next);
    expect(res.status).toHaveBeenCalledWith(500);
    expect(res.json).toHaveBeenCalledWith({ error: 'Internal error' });
    expect(next).not.toHaveBeenCalled();
  });
});
