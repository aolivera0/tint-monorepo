import { ApiError } from './api';

/**
 * Convierte el link relativo que devuelve identity-api (`/invite/:code`)
 * en una URL absoluta que funciona al pegarla en cualquier navegador.
 * Los links ya absolutos se devuelven intactos.
 */
export function toAbsoluteInviteLink(link: string): string {
  if (!link) return link;
  if (/^https?:\/\//i.test(link)) return link;
  const origin = typeof window !== 'undefined' && window.location?.origin ? window.location.origin : '';
  return `${origin}${link.startsWith('/') ? link : `/${link}`}`;
}

/**
 * Traduce los errores de `POST /invitations/claim` a mensajes
 * comprensibles: la sala pudo ser eliminada (borrado lógico),
 * el link pudo expirar o ser revocado por el host.
 */
export function inviteClaimErrorMessage(err: unknown): string {
  if (err instanceof ApiError) {
    if (err.status === 410) return 'Esta invitación expiró o la sala fue eliminada.';
    if (err.status === 404) return 'Esta invitación no existe o la sala fue eliminada.';
    if (err.status === 409) return 'Esta invitación fue revocada por el host.';
  }
  return err instanceof Error ? err.message : 'No se pudo unir a la sala';
}
