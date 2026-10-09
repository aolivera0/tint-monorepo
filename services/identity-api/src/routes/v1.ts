import { Router } from 'express';
import { z } from 'zod';
import { env } from '../config/env.js';
import { validate } from '../middlewares/validate.js';
import { requireAuthHost } from '../middlewares/auth.js';
import { requireRoomOwner } from '../middlewares/requireRoomOwner.js';
import { upsertByProvider } from '../modules/users/users.repo.js';
import { create as createRoom, listByHost, findById } from '../modules/rooms/rooms.repo.js';
import { create as createInvitation, findByCode, setRevoked } from '../modules/invitations/invitations.repo.js';
import { issueGuestToken } from '../modules/auth/auth.service.js';

const router = Router();

router.get('/healthz', (req, res) => {
  res.json({ status: 'ok', service: 'identity-api' });
});

const verifySchema = z.object({});
router.post('/auth/verify', requireAuthHost, validate(verifySchema), async (req, res) => {
  res.json({ user: req.user, isRegistered: true });
});

const createRoomSchema = z.object({
  title: z.string().trim().min(1),
});
router.post('/rooms', requireAuthHost, validate(createRoomSchema), async (req, res) => {
  const { title } = req.body;
  const room = await createRoom({ title, hostId: req.user!.id });
  const ttlHours = parseInt(env.INVITATION_DEFAULT_TTL_HOURS, 10);
  const expiresAt = new Date(Date.now() + ttlHours * 60 * 60 * 1000);
  const invitation = await createInvitation({
    roomId: room.id,
    createdBy: req.user!.id,
    expiresAt,
    revoked: false,
  });
  res.json({
    room,
    invitation: {
      code: invitation.code,
      expiresAt: invitation.expiresAt,
      link: `/invite/${invitation.code}`,
    },
  });
});

router.get('/rooms', requireAuthHost, async (req, res) => {
  const rooms = await listByHost(req.user!.id);
  res.json({ rooms });
});

const createInvitationSchema = z.object({
  ttlHours: z.number().positive().optional(),
});
router.post('/rooms/:id/invitations', requireAuthHost, requireRoomOwner, validate(createInvitationSchema), async (req, res) => {
  const rawId = req.params.id;
  const roomId = Array.isArray(rawId) ? rawId[0] : rawId;
  const ttlHours = req.body.ttlHours || parseInt(env.INVITATION_DEFAULT_TTL_HOURS, 10);
  const expiresAt = new Date(Date.now() + ttlHours * 60 * 60 * 1000);
  const invitation = await createInvitation({
    roomId,
    createdBy: req.user!.id,
    expiresAt,
    revoked: false,
  });
  res.json({
    code: invitation.code,
    expiresAt: invitation.expiresAt,
    link: `/invite/${invitation.code}`,
  });
});

router.post('/invitations/:code/revoke', requireAuthHost, async (req, res) => {
  const rawCode = req.params.code;
  const code = Array.isArray(rawCode) ? rawCode[0] : rawCode;
  const invitation = await findByCode(code);
  if (!invitation) {
    return res.status(404).json({ error: 'Not found' });
  }
  const room = await findById(invitation.roomId);
  if (!room || room.hostId !== req.user!.id) {
    return res.status(403).json({ error: 'Forbidden' });
  }
  await setRevoked(invitation.id, true);
  res.json({ success: true });
});

const claimSchema = z.object({
  code: z.string().trim(),
  nick: z.string().trim().min(1),
});
router.post('/invitations/claim', validate(claimSchema), async (req, res) => {
  const { code, nick } = req.body;
  const invitation = await findByCode(code);
  if (!invitation) {
    return res.status(404).json({ error: 'Not found' });
  }
  if (invitation.revoked) {
    return res.status(409).json({ error: 'Revoked' });
  }
  if (invitation.expiresAt < new Date()) {
    return res.status(410).json({ error: 'Expired' });
  }
  const { token } = issueGuestToken(invitation.roomId, nick);
  res.json({ token, roomId: invitation.roomId, nick });
});

export default router;
