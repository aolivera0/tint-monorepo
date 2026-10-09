import jwt from 'jsonwebtoken';
import { env } from '../../config/env.js';
import { v4 as uuidv4 } from 'uuid';

export interface GuestTokenPayload {
  sub: string;
  room_id: string;
  role: 'participant';
  nick: string;
  jti: string;
  iss: string;
  aud: string;
  exp: number;
  iat: number;
}

export function issueGuestToken(roomId: string, nick: string): { token: string; payload: GuestTokenPayload } {
  const now = Math.floor(Date.now() / 1000);
  const ttlHours = parseInt(env.GUEST_TOKEN_TTL_HOURS, 10);
  const exp = now + ttlHours * 60 * 60;
  const payload: GuestTokenPayload = {
    sub: `guest:${uuidv4()}`,
    room_id: roomId,
    role: 'participant',
    nick: nick.trim(),
    jti: uuidv4(),
    iss: env.JWT_ISSUER,
    aud: env.JWT_AUDIENCE,
    exp,
    iat: now,
  };
  const token = jwt.sign(payload, env.JWT_SECRET);
  return { token, payload };
}
