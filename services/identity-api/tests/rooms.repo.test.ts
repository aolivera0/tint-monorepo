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

import { create, findById, listByHost, isOwner, softRemoveById, updateTitle } from '../src/modules/rooms/rooms.repo.js';

const room = { id: 'r1', hostId: 'h1', title: 'Sala', status: 'active' };

beforeEach(() => {
  vi.clearAllMocks();
});

describe('rooms.repo', () => {
  it('create inserta y retorna', async () => {
    returningMock.mockResolvedValueOnce([room]);
    await expect(create({ title: 'Sala', hostId: 'h1' })).resolves.toEqual(room);
  });

  it('findById retorna la fila o null', async () => {
    whereMock.mockResolvedValueOnce([room]);
    await expect(findById('r1')).resolves.toEqual(room);
    whereMock.mockResolvedValueOnce([]);
    await expect(findById('missing')).resolves.toBeNull();
  });

  it('listByHost retorna las salas del host', async () => {
    whereMock.mockResolvedValueOnce([room]);
    await expect(listByHost('h1')).resolves.toEqual([room]);
  });

  it('isOwner compara hostId', async () => {
    whereMock.mockResolvedValueOnce([room]);
    await expect(isOwner('r1', 'h1')).resolves.toBe(true);
    whereMock.mockResolvedValueOnce([room]);
    await expect(isOwner('r1', 'otro')).resolves.toBe(false);
    whereMock.mockResolvedValueOnce([]);
    await expect(isOwner('missing', 'h1')).resolves.toBe(false);
  });

  it('softRemoveById marca deleted y retorna la sala', async () => {
    returningMock.mockResolvedValueOnce([{ ...room, status: 'deleted' }]);
    await expect(softRemoveById('r1')).resolves.toMatchObject({ id: 'r1', status: 'deleted' });
  });

  it('softRemoveById retorna null si no existe', async () => {
    returningMock.mockResolvedValueOnce([]);
    await expect(softRemoveById('missing')).resolves.toBeNull();
  });

  it('updateTitle actualiza y retorna la sala', async () => {
    returningMock.mockResolvedValueOnce([{ ...room, title: 'Nuevo nombre' }]);
    await expect(updateTitle('r1', 'Nuevo nombre')).resolves.toMatchObject({ title: 'Nuevo nombre' });
  });

  it('updateTitle retorna null si no existe', async () => {
    returningMock.mockResolvedValueOnce([]);
    await expect(updateTitle('missing', 'X')).resolves.toBeNull();
  });
});
