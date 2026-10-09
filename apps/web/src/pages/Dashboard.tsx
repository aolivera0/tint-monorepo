import { useEffect, useState } from 'react';
import { apiFetch, ApiError } from '../lib/api';
import { Link, useNavigate } from 'react-router-dom';
import { AppShell } from '../components/layout/AppShell';
import { Card, EmptyState, ErrorAlert, Skeleton } from '../components/ui/Primitives';

type Room = { id: string; title: string };

export default function Dashboard() {
  const [rooms, setRooms] = useState<Room[]>([]);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const navigate = useNavigate();

  useEffect(() => {
    apiFetch('/rooms')
      .then((r) => setRooms(r.rooms || []))
      .catch((e: unknown) => {
        if (e instanceof ApiError && (e.status === 401 || e.status === 403)) {
          localStorage.removeItem('tint_token');
          navigate('/login');
        } else {
          setError(e instanceof Error ? e.message : 'Error desconocido');
        }
      })
      .finally(() => setLoading(false));
  }, [navigate]);

  const handleLogout = () => {
    localStorage.removeItem('tint_token');
    navigate('/login');
  };

  return (
    <AppShell
      actions={
        <>
          <Link
            to="/rooms/new"
            className="inline-flex items-center justify-center whitespace-nowrap rounded-full bg-tint-accent px-5 py-2.5 text-sm font-semibold text-tint-bg transition hover:bg-tint-accentHover active:scale-[0.98]"
          >
            Crear sala
          </Link>
          <button
            type="button"
            onClick={handleLogout}
            className="rounded-full px-4 py-2.5 text-sm text-tint-muted transition hover:bg-white/5 hover:text-white"
          >
            Salir
          </button>
        </>
      }
    >
      <div className="pt-10">
        <h1 className="text-3xl font-bold tracking-tighter text-white md:text-4xl">Mis salas</h1>
        <p className="mt-2 max-w-[65ch] text-sm leading-relaxed">
          {loading
            ? 'Cargando tus salas…'
            : rooms.length === 0
              ? 'Aún no tienes salas activas. Crea la primera y comparte el link.'
              : `${rooms.length} ${rooms.length === 1 ? 'sala disponible' : 'salas disponibles'} para continuar viendo juntos.`}
        </p>

        {error && (
          <div className="mt-6 max-w-2xl">
            <ErrorAlert message={`Error: ${error}`} />
          </div>
        )}

        {loading && (
          <div className="mt-8 grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3" aria-hidden="true">
            {[0, 1, 2].map((i) => (
              <Skeleton key={i} className="h-44" />
            ))}
          </div>
        )}

        {!loading && rooms.length === 0 && !error && (
          <div className="mt-8 max-w-2xl">
            <EmptyState
              title="Sin salas por ahora"
              body="Crea una sala de watch party, obtén un código de invitación y comparte el link. Tus invitados entran con nick, sin cuenta."
              action={
                <Link
                  to="/rooms/new"
                  className="mt-2 inline-flex items-center justify-center whitespace-nowrap rounded-full bg-tint-accent px-5 py-2.5 text-sm font-semibold text-tint-bg"
                >
                  Crear sala
                </Link>
              }
            />
          </div>
        )}

        {!loading && rooms.length > 0 && (
          <ul className="mt-8 grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
            {rooms.map((room) => (
              <li key={room.id}>
                <Card className="group overflow-hidden">
                  <div className="relative h-32 overflow-hidden">
                    <img
                      src={`https://picsum.photos/seed/tint-${room.id}/640/360`}
                      alt=""
                      loading="lazy"
                      className="h-full w-full object-cover opacity-60 transition group-hover:opacity-75"
                    />
                    <div className="absolute inset-0 bg-gradient-to-t from-tint-card via-transparent to-transparent" />
                  </div>
                  <div className="flex items-center justify-between gap-3 p-4">
                    <p className="truncate text-[15px] font-semibold text-white" title={room.title}>
                      {room.title}
                    </p>
                    <Link
                      to={`/watch/${room.id}`}
                      aria-label={`Abrir ${room.title}`}
                      className="shrink-0 rounded-full border border-white/15 bg-white/5 px-4 py-1.5 text-[13px] font-medium text-white transition hover:bg-tint-accent hover:text-tint-bg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-tint-accent"
                    >
                      Abrir
                    </Link>
                  </div>
                </Card>
              </li>
            ))}
          </ul>
        )}
      </div>
    </AppShell>
  );
}
