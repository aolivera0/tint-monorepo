import { describe, it, expect } from 'vitest';
import { toAbsoluteInviteLink, inviteClaimErrorMessage } from './invite';
import { ApiError } from './api';

describe('toAbsoluteInviteLink', () => {
  it('convierte un link relativo en absoluto usable en el navegador', () => {
    const abs = toAbsoluteInviteLink('/invite/9DcMtgch79xC');
    expect(abs).toBe(`${window.location.origin}/invite/9DcMtgch79xC`);
    expect(abs).toMatch(/^https?:\/\/.+\/invite\/9DcMtgch79xC$/);
  });

  it('deja intacto un link que ya es absoluto', () => {
    const abs = 'https://tint.app/invite/abc123';
    expect(toAbsoluteInviteLink(abs)).toBe(abs);
  });

  it('retorna vacío si no hay link', () => {
    expect(toAbsoluteInviteLink('')).toBe('');
  });

  it('antepone el origin aunque falte la barra inicial', () => {
    expect(toAbsoluteInviteLink('invite/abc123')).toBe(`${window.location.origin}/invite/abc123`);
  });
});

describe('inviteClaimErrorMessage', () => {
  it('410 expirado o sala eliminada', () => {
    expect(inviteClaimErrorMessage(new ApiError(410, 'Expired'))).toMatch(/expir|eliminada/i);
  });

  it('404 código inexistente o sala eliminada', () => {
    expect(inviteClaimErrorMessage(new ApiError(404, 'Not found'))).toMatch(/no existe|eliminada/i);
  });

  it('409 revocado por el host', () => {
    expect(inviteClaimErrorMessage(new ApiError(409, 'Revoked'))).toMatch(/revocada/i);
  });

  it('conserva el mensaje de errores genéricos', () => {
    expect(inviteClaimErrorMessage(new Error('Fallo de red'))).toBe('Fallo de red');
  });

  it('mensaje por defecto si el fallo no es un Error', () => {
    expect(inviteClaimErrorMessage('cadena')).toBe('No se pudo unir a la sala');
  });
});
