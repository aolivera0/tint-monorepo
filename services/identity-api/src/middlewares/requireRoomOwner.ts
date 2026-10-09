import { Request, Response, NextFunction } from 'express';
import { isOwner } from '../modules/rooms/rooms.repo.js';

export async function requireRoomOwner(req: Request, res: Response, next: NextFunction) {
  try {
    const rawId = req.params.id;
    const roomId = Array.isArray(rawId) ? rawId[0] : rawId;
    if (!roomId || !req.user?.id) {
      return res.status(403).json({ error: 'Forbidden' });
    }
    const owner = await isOwner(roomId, req.user.id);
    if (!owner) {
      return res.status(403).json({ error: 'Forbidden' });
    }
    next();
  } catch (err) {
    return res.status(403).json({ error: 'Forbidden' });
  }
}
