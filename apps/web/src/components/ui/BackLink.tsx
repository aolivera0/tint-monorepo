import { Link } from 'react-router-dom';

type BackLinkProps = {
  to?: string;
  label?: string;
};

/**
 * Enlace de retorno único de TINT: siempre dice "Volver" y apunta
 * al destino indicado (`/dashboard` por defecto). Usarlo en todas
 * las vistas en vez de botones ad-hoc ("Atrás", "Volver a...").
 */
export function BackLink({ to = '/dashboard', label = 'Volver' }: BackLinkProps) {
  return (
    <Link
      to={to}
      aria-label={label}
      className="group inline-flex items-center gap-1.5 text-sm text-tint-muted transition hover:text-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-tint-accent"
    >
      <span aria-hidden="true" className="transition-transform group-hover:-translate-x-0.5">←</span> Volver
    </Link>
  );
}
