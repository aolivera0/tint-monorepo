import { eq, and, ne } from 'drizzle-orm';
import { db } from '../../db/client.js';
import { rooms } from '../../db/schema.js';

export type Room = typeof rooms.$inferSelect;
export type NewRoom = typeof rooms.$inferInsert;

export const ACTIVE_STATUSES = ['active'] as const;

export async function create(room: NewRoom) {
  const result = await db.insert(rooms).values(room).returning();
  return result[0];
}

export async function findById(id: string) {
  const result = await db.select().from(rooms).where(eq(rooms.id, id));
  return result[0] || null;
}

export async function listByHost(hostId: string) {
  return db
    .select()
    .from(rooms)
    .where(and(eq(rooms.hostId, hostId), ne(rooms.status, 'deleted')));
}

export async function isOwner(roomId: string, userId: string) {
  const room = await findById(roomId);
  return room ? room.hostId === userId : false;
}

export async function isDeleted(room: Pick<Room, 'status'> | null | undefined) {
  return !!room && room.status === 'deleted';
}

export async function softRemoveById(id: string) {
  const result = await db
    .update(rooms)
    .set({ status: 'deleted', updatedAt: new Date() })
    .where(eq(rooms.id, id))
    .returning();
  return result[0] || null;
}

export async function updateTitle(id: string, title: string) {
  const result = await db
    .update(rooms)
    .set({ title, updatedAt: new Date() })
    .where(eq(rooms.id, id))
    .returning();
  return result[0] || null;
}
