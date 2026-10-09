import { describe, it, expect, vi, beforeEach } from 'vitest';

const { whereMock, returningMock } = vi.hoisted(() => ({
  whereMock: vi.fn(),
  returningMock: vi.fn(),
}));

vi.mock('../src/db/client.js', () => ({
  db: {
    select: () => ({ from: () => ({ where: whereMock }) }),
    insert: () => ({ values: () => ({ returning: returningMock }) }),
    update: () => ({ set: () => ({ where: () => ({ returning: returningMock }) }) }),
  },
  pool: { end: () => Promise.resolve() },
}));

import {
  findByProviderSub,
  findById,
  create,
  upsertByProvider,
} from '../src/modules/users/users.repo.js';

const row = {
  id: 'u1',
  provider: 'firebase',
  providerSub: 'sub-1',
  email: 'a@b.c',
  displayName: 'AB',
  avatarUrl: null,
};

beforeEach(() => {
  vi.clearAllMocks();
});

describe('users.repo', () => {
  it('findByProviderSub retorna la fila o null', async () => {
    whereMock.mockResolvedValueOnce([row]);
    await expect(findByProviderSub('sub-1')).resolves.toEqual(row);
    whereMock.mockResolvedValueOnce([]);
    await expect(findByProviderSub('nope')).resolves.toBeNull();
  });

  it('findById retorna la fila o null', async () => {
    whereMock.mockResolvedValueOnce([row]);
    await expect(findById('u1')).resolves.toEqual(row);
    whereMock.mockResolvedValueOnce([]);
    await expect(findById('missing')).resolves.toBeNull();
  });

  it('create inserta y retorna', async () => {
    returningMock.mockResolvedValueOnce([row]);
    await expect(
      create({ provider: 'firebase', providerSub: 'sub-1' }),
    ).resolves.toEqual(row);
  });

  it('upsertByProvider retorna el existente sin insertar', async () => {
    whereMock.mockResolvedValueOnce([row]);
    const result = await upsertByProvider({ providerSub: 'sub-1' });
    expect(result).toEqual(row);
    expect(returningMock).not.toHaveBeenCalled();
  });

  it('upsertByProvider crea si no existe', async () => {
    whereMock.mockResolvedValueOnce([]);
    returningMock.mockResolvedValueOnce([row]);
    const result = await upsertByProvider({ providerSub: 'sub-1', email: 'a@b.c' });
    expect(result).toEqual(row);
    expect(returningMock).toHaveBeenCalled();
  });
});
