import { Request, Response, NextFunction } from 'express';
import jwt from 'jsonwebtoken';
import { getSigningKey } from '../modules/auth/jwks.js';
import { env } from '../config/env.js';
import { upsertByProvider } from '../modules/users/users.repo.js';

export interface AuthUser {
  id: string;
  providerSub: string;
  email?: string | null;
  displayName?: string | null;
}

declare module 'express-serve-static-core' {
  interface Request {
    user?: AuthUser;
  }
}

export async function requireAuthHost(req: Request, res: Response, next: NextFunction) {
  try {
    const header = req.headers.authorization;
    if (!header || !header.startsWith('Bearer ')) {
      return res.status(401).json({ error: 'Unauthorized' });
    }
    const token = header.substring(7);
    const decoded = jwt.decode(token, { complete: true }) as any;
    if (!decoded?.header?.kid) {
      return res.status(401).json({ error: 'Invalid token' });
    }
    const key = await getSigningKey(decoded.header.kid);
    let verified: any;
    try {
      verified = jwt.verify(token, key, {
        algorithms: ['RS256'],
        issuer: `https://securetoken.google.com/${env.FIREBASE_PROJECT_ID}`,
        audience: env.FIREBASE_PROJECT_ID,
      }) as any;
    } catch (err) {
      console.error('[auth] fallo al verificar JWT de Firebase:', (err as Error).message);
      return res.status(401).json({ error: 'Invalid token' });
    }
    const providerSub = verified.sub || verified.uid;
    if (!providerSub) {
      return res.status(401).json({ error: 'Invalid token' });
    }
    let user;
    try {
      user = await upsertByProvider({
        providerSub,
        email: verified.email,
        displayName: verified.name || verified.displayName,
        avatarUrl: verified.picture,
      });
    } catch (err) {
      console.error('[auth] fallo de infraestructura (DB) en upsert de usuario:', (err as Error).message);
      return res.status(500).json({ error: 'Internal error' });
    }
    req.user = {
      id: user.id,
      providerSub: user.providerSub,
      email: user.email,
      displayName: user.displayName,
    };
    next();
  } catch (err) {
    console.error('[auth] error inesperado en requireAuthHost:', (err as Error).message);
    return res.status(401).json({ error: 'Unauthorized' });
  }
}
