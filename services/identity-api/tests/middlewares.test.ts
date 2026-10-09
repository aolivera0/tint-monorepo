import { describe, it, expect, vi } from 'vitest';
import { z } from 'zod';
import { validate } from '../src/middlewares/validate.js';
import { errorHandler } from '../src/middlewares/error.js';

function mockRes() {
  const res: any = {};
  res.status = vi.fn().mockReturnValue(res);
  res.json = vi.fn().mockReturnValue(res);
  return res;
}

describe('validate', () => {
  const schema = z.object({ title: z.string().trim().min(1) });

  it('llama next y normaliza el body si es válido', () => {
    const req: any = { body: { title: '  Hola  ' } };
    const next = vi.fn();
    validate(schema)(req, mockRes(), next);
    expect(next).toHaveBeenCalled();
    expect(req.body.title).toBe('Hola');
  });

  it('responde 400 si es inválido', () => {
    const req: any = { body: { title: '' } };
    const res = mockRes();
    const next = vi.fn();
    validate(schema)(req, res, next);
    expect(res.status).toHaveBeenCalledWith(400);
    expect(next).not.toHaveBeenCalled();
  });
});

describe('errorHandler', () => {
  it('responde 500 con el mensaje', () => {
    const res = mockRes();
    errorHandler(new Error('boom'), {} as any, res, (() => {}) as any);
    expect(res.status).toHaveBeenCalledWith(500);
    expect(res.json).toHaveBeenCalledWith({ error: 'boom' });
  });
});
