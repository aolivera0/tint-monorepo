import request from 'supertest';
import { describe, it, expect } from 'vitest';
import app from '../src/app.js';

describe('healthz', () => {
  it('returns ok', async () => {
    const res = await request(app).get('/api/v1/healthz');
    expect(res.status).toBe(200);
    expect(res.body).toEqual({ status: 'ok', service: 'identity-api' });
  });
});
