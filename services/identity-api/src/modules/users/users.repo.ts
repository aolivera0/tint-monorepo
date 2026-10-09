import { eq, and } from 'drizzle-orm';
import { db } from '../../db/client.js';
import { users } from '../../db/schema.js';

export type User = typeof users.$inferSelect;
export type NewUser = typeof users.$inferInsert;

export async function findByProviderSub(providerSub: string, provider: 'firebase' = 'firebase') {
  const result = await db.select().from(users).where(and(eq(users.providerSub, providerSub), eq(users.provider, provider)));
  return result[0] || null;
}

export async function findById(id: string) {
  const result = await db.select().from(users).where(eq(users.id, id));
  return result[0] || null;
}

export async function create(user: NewUser) {
  const result = await db.insert(users).values(user).returning();
  return result[0];
}

export async function upsertByProvider(userData: { providerSub: string; email?: string | null; displayName?: string | null; avatarUrl?: string | null; provider?: 'firebase' }) {
  const provider = userData.provider || 'firebase';
  const existing = await findByProviderSub(userData.providerSub, provider);
  if (existing) {
    return existing;
  }
  return create({
    provider,
    providerSub: userData.providerSub,
    email: userData.email,
    displayName: userData.displayName,
    avatarUrl: userData.avatarUrl,
  });
}
