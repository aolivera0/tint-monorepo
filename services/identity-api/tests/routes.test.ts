import { describe, it, expect, vi, beforeEach } from 'vitest';
import request from 'supertest';

vi.mock('../src/middlewares/auth.js', () => ({
  requireAuthHost: (req: any, _res: any, next: any) => {
    req.user = { id: 'host-1', providerSub: 'sub-1', email: 'h@t.c', displayName: 'Host' };
    next();
  },
}));

vi.mock('../src/modules/rooms/rooms.repo.js', () => ({
  create: vi.fn(),
  listByHost: vi.fn(),
  findById: vi.fn(),
  isOwner: vi.fn(),
}));

vi.mock('../src/modules/invitations/invitations.repo.js', () => ({
  create: vi.fn(),
  findByCode: vi.fn(),
  setRevoked: vi.fn(),
}));

import app from '../src/app.js';
import { create as createRoom, listByHost, findById, isOwner } from '../src/modules/rooms/rooms.repo.js';
import { create as createInvitation, findByCode, setRevoked } from '../src/modules/invitations/invitations.repo.js';

const room = { id: 'room-1', hostId: 'host-1', title: 'Noche de cine' };
const invitation = {
  id: 'inv-1',
  code: 'abc123def456',
  roomId: 'room-1',
  createdBy: 'host-1',
  expiresAt: new Date(Date.now() + 24 * 60 * 60 * 1000),
  revoked: false,
};

beforeEach(() => {
  vi.clearAllMocks();
});

describe('rutas v1', () => {
  it('GET /healthz responde ok', async () => {
    const res = await request(app).get('/api/v1/healthz');
    expect(res.status).toBe(200);
    expect(res.body).toEqual({ status: 'ok', service: 'identity-api' });
  });

  it('POST /auth/verify retorna el usuario y isRegistered', async () => {
    const res = await request(app).post('/api/v1/auth/verify').send({});
    expect(res.status).toBe(200);
    expect(res.body.isRegistered).toBe(true);
    expect(res.body.user.id).toBe('host-1');
  });

  it('POST /rooms 400 si el título está vacío', async () => {
    const res = await request(app).post('/api/v1/rooms').send({ title: '  ' });
    expect(res.status).toBe(400);
  });

  it('POST /rooms crea sala + invitación inicial con link relativo', async () => {
    vi.mocked(createRoom).mockResolvedValueOnce(room as any);
    vi.mocked(createInvitation).mockResolvedValueOnce(invitation as any);
    const res = await request(app).post('/api/v1/rooms').send({ title: 'Noche de cine' });
    expect(res.status).toBe(200);
    expect(res.body.room).toMatchObject({ id: 'room-1', title: 'Noche de cine' });
    expect(res.body.invitation.code).toBe('abc123def456');
    expect(res.body.invitation.link).toBe('/invite/abc123def456');
  });

  it('GET /rooms lista las salas del host', async () => {
    vi.mocked(listByHost).mockResolvedValueOnce([room] as any);
    const res = await request(app).get('/api/v1/rooms');
    expect(res.status).toBe(200);
    expect(res.body.rooms).toHaveLength(1);
    expect(listByHost).toHaveBeenCalledWith('host-1');
  });

  it('POST /rooms/:id/invitations 403 si no es dueño', async () => {
    vi.mocked(isOwner).mockResolvedValueOnce(false);
    const res = await request(app).post('/api/v1/rooms/room-1/invitations').send({});
    expect(res.status).toBe(403);
  });

  it('POST /rooms/:id/invitations genera invitación si es dueño', async () => {
    vi.mocked(isOwner).mockResolvedValueOnce(true);
    vi.mocked(createInvitation).mockResolvedValueOnce(invitation as any);
    const res = await request(app).post('/api/v1/rooms/room-1/invitations').send({ ttlHours: 6 });
    expect(res.status).toBe(200);
    expect(res.body.link).toBe('/invite/abc123def456');
  });

  it('POST /invitations/:code/revoke 404 si no existe', async () => {
    vi.mocked(findByCode).mockResolvedValueOnce(null as any);
    const res = await request(app).post('/api/v1/invitations/xxx/revoke');
    expect(res.status).toBe(404);
  });

  it('POST /invitations/:code/revoke 403 si no es dueño de la sala', async () => {
    vi.mocked(findByCode).mockResolvedValueOnce(invitation as any);
    vi.mocked(findById).mockResolvedValueOnce({ ...room, hostId: 'otro-host' } as any);
    const res = await request(app).post('/api/v1/invitations/abc123def456/revoke');
    expect(res.status).toBe(403);
  });

  it('POST /invitations/:code/revoke revoca si es dueño', async () => {
    vi.mocked(findByCode).mockResolvedValueOnce(invitation as any);
    vi.mocked(findById).mockResolvedValueOnce(room as any);
    vi.mocked(setRevoked).mockResolvedValueOnce({ ...invitation, revoked: true } as any);
    const res = await request(app).post('/api/v1/invitations/abc123def456/revoke');
    expect(res.status).toBe(200);
    expect(res.body.success).toBe(true);
    expect(setRevoked).toHaveBeenCalledWith('inv-1', true);
  });

  it('POST /invitations/claim 404 si el código no existe', async () => {
    vi.mocked(findByCode).mockResolvedValueOnce(null as any);
    const res = await request(app).post('/api/v1/invitations/claim').send({ code: 'xxx', nick: 'Ana' });
    expect(res.status).toBe(404);
  });

  it('POST /invitations/claim 409 si está revocado', async () => {
    vi.mocked(findByCode).mockResolvedValueOnce({ ...invitation, revoked: true } as any);
    const res = await request(app).post('/api/v1/invitations/claim').send({ code: 'abc', nick: 'Ana' });
    expect(res.status).toBe(409);
  });

  it('POST /invitations/claim 410 si expiró', async () => {
    vi.mocked(findByCode).mockResolvedValueOnce({
      ...invitation,
      expiresAt: new Date(Date.now() - 1000),
    } as any);
    const res = await request(app).post('/api/v1/invitations/claim').send({ code: 'abc', nick: 'Ana' });
    expect(res.status).toBe(410);
  });

  it('POST /invitations/claim 400 si el nick está vacío', async () => {
    const res = await request(app).post('/api/v1/invitations/claim').send({ code: 'abc', nick: '  ' });
    expect(res.status).toBe(400);
  });

  it('POST /invitations/claim emite guest token acotado (mismo nick permitido)', async () => {
    vi.mocked(findByCode).mockResolvedValue({ ...invitation } as any);
    const first = await request(app).post('/api/v1/invitations/claim').send({ code: 'abc', nick: 'Ana' });
    const second = await request(app).post('/api/v1/invitations/claim').send({ code: 'abc', nick: 'Ana' });
    expect(first.status).toBe(200);
    expect(second.status).toBe(200);
    expect(first.body.roomId).toBe('room-1');
    expect(first.body.nick).toBe('Ana');
    expect(typeof first.body.token).toBe('string');
  });
});
