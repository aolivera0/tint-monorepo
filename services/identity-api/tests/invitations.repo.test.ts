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

import { create, findByCode, setRevoked, listByRoom } from '../src/modules/invitations/invitations.repo.js';

const invitation = {
  id: 'i1',
  code: 'abcdefgh1234',
  roomId: 'r1',
  createdBy: 'h1',
  expiresAt: new Date(),
  revoked: false,
};

beforeEach(() => {
  vi.clearAllMocks();
});

describe('invitations.repo', () => {
  it('create genera un código de 12 caracteres', async () => {
    returningMock.mockResolvedValueOnce([invitation]);
    const result = await create({ roomId: 'r1', expiresAt: new Date(), revoked: false });
    expect(result).toEqual(invitation);
  });

  it('findByCode retorna la fila o null', async () => {
    whereMock.mockResolvedValueOnce([invitation]);
    await expect(findByCode('abcdefgh1234')).resolves.toEqual(invitation);
    whereMock.mockResolvedValueOnce([]);
    await expect(findByCode('missing')).resolves.toBeNull();
  });

  it('setRevoked actualiza y retorna', async () => {
    returningMock.mockResolvedValueOnce([{ ...invitation, revoked: true }]);
    const result = await setRevoked('i1', true);
    expect(result?.revoked).toBe(true);
    returningMock.mockResolvedValueOnce([]);
    await expect(setRevoked('missing')).resolves.toBeNull();
  });

  it('listByRoom retorna las invitaciones de la sala', async () => {
    whereMock.mockResolvedValueOnce([invitation]);
    await expect(listByRoom('r1')).resolves.toEqual([invitation]);
  });
});
