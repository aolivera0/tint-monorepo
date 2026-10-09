import { useEffect, useState } from 'react';
import { apiFetch, ApiError } from '../lib/api';
import { Link, useNavigate } from 'react-router-dom';

export default function Dashboard() {
  const [rooms, setRooms] = useState<{ id: string; title: string }[]>([]);
  const [error, setError] = useState('');
  const navigate = useNavigate();
  useEffect(() => {
    apiFetch('/rooms')
      .then((r) => setRooms(r.rooms || []))
      .catch((e: unknown) => {
        // Sesión inválida o expirada: limpiar y volver al login.
        if (e instanceof ApiError && (e.status === 401 || e.status === 403)) {
          localStorage.removeItem('tint_token');
          navigate('/login');
        } else {
          setError(e instanceof Error ? e.message : 'Error desconocido');
        }
      });
  }, [navigate]);
  return (
    <div className="min-h-screen bg-tint-bg p-8 text-tint-muted">
      <h1 className="mb-4 text-2xl">Mis salas</h1>
      <Link to="/rooms/new" className="rounded bg-tint-accent px-4 py-2 text-black">
        Crear sala
      </Link>
      <ul className="mt-4 space-y-2">
        {rooms.map((room) => (
          <li key={room.id}>{room.title}</li>
        ))}
      </ul>
      {error && (
        <p role="alert" className="mt-4 text-sm text-red-400">
          Error: {error}
        </p>
      )}
    </div>
  );
}
