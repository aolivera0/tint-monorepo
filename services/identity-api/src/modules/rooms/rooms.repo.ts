import { eq, and } from 'drizzle-orm';
import { db } from '../../db/client.js';
import { rooms } from '../../db/schema.js';

export type Room = typeof rooms.$inferSelect;
export type NewRoom = typeof rooms.$inferInsert;

export async function create(room: NewRoom) {
  const result = await db.insert(rooms).values(room).returning();
  return result[0];
}

export async function findById(id: string) {
  const result = await db.select().from(rooms).where(eq(rooms.id, id));
  return result[0] || null;
}

export async function listByHost(hostId: string) {
  return db.select().from(rooms).where(eq(rooms.hostId, hostId));
}

export async function isOwner(roomId: string, userId: string) {
  const room = await findById(roomId);
  return room ? room.hostId === userId : false;
}
