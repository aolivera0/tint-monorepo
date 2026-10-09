import { z } from 'zod';

const envSchema = z.object({
  IDENTITY_API_PORT: z.string().default('8081'),
  NODE_ENV: z.enum(['development', 'test', 'production']).default('development'),
  AUTH_PROVIDER: z.enum(['firebase']).default('firebase'),
  JWT_ISSUER: z.string().default('tint-identity'),
  JWT_AUDIENCE: z.string().default('tint:room'),
  JWT_SECRET: z.string().default('dev-secret-change-in-prod'),
  GUEST_TOKEN_TTL_HOURS: z.string().default('4'),
  INVITATION_DEFAULT_TTL_HOURS: z.string().default('24'),
  DATABASE_URL: z.string().default('postgres://tint:tint@localhost:5432/tint'),
  REDIS_URL: z.string().default('redis://localhost:6379'),
  FIREBASE_PROJECT_ID: z.string().min(1, 'FIREBASE_PROJECT_ID es obligatorio'),
});

export const env = envSchema.parse(process.env);
