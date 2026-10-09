import { describe, it, expect } from 'vitest';
import jwt from 'jsonwebtoken';
import { issueGuestToken } from '../src/modules/auth/auth.service.js';
import { env } from '../src/config/env.js';

describe('issueGuestToken', () => {
  it('emite un guest JWT acotado a la sala con TTL de 4h', () => {
    const { token, payload } = issueGuestToken('room-123', '  Invitado1  ');
    expect(typeof token).toBe('string');
    expect(payload.room_id).toBe('room-123');
    expect(payload.role).toBe('participant');
    expect(payload.nick).toBe('Invitado1');
    expect(payload.iss).toBe(env.JWT_ISSUER);
    expect(payload.aud).toBe(env.JWT_AUDIENCE);
    expect(payload.sub.startsWith('guest:')).toBe(true);
    expect(payload.exp - payload.iat).toBe(4 * 60 * 60);
  });

  it('el token es verificable con JWT_SECRET', () => {
    const { token } = issueGuestToken('room-123', 'nick');
    const decoded = jwt.verify(token, env.JWT_SECRET) as any;
    expect(decoded.room_id).toBe('room-123');
    expect(decoded.role).toBe('participant');
  });
});
