import { eq } from 'drizzle-orm';
import { db } from '../../db/client.js';
import { invitations } from '../../db/schema.js';
import { nanoid } from 'nanoid';

export type Invitation = typeof invitations.$inferSelect;
export type NewInvitation = typeof invitations.$inferInsert;

export async function create(invitation: Omit<NewInvitation, 'code' | 'id'>) {
  const code = nanoid(12);
  const result = await db.insert(invitations).values({ ...invitation, code }).returning();
  return result[0];
}

export async function findByCode(code: string) {
  const result = await db.select().from(invitations).where(eq(invitations.code, code));
  return result[0] || null;
}

export async function setRevoked(id: string, revoked = true) {
  const result = await db.update(invitations).set({ revoked }).where(eq(invitations.id, id)).returning();
  return result[0] || null;
}

export async function listByRoom(roomId: string) {
  const result = await db.select().from(invitations).where(eq(invitations.roomId, roomId));
  return result;
}
