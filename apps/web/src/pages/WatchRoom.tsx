import { useParams } from 'react-router-dom';

export default function WatchRoom() {
  const { roomId } = useParams();
  // SIMULACIÓN: Chat y streaming aún no implementados.
  // Se integrará con sync-core (/ws/sync) y social-core (/ws/social) cuando aplique.
  // Por ahora solo mostramos info de sala + rol + nick.
  const guestToken = localStorage.getItem('tint_guest_token');
  const isGuest = !!guestToken;
  return (
    <div className="min-h-screen bg-tint-bg p-8 text-tint-muted">
      <h1 className="mb-4 text-2xl">Sala {roomId}</h1>
      <p>Rol: {isGuest ? 'participante (invitado)' : 'host (registrado)'}</p>
      {/* TODO: conectar a /ws/sync (sync-core) */}
      {/* TODO: conectar a /ws/social (social-core) + chat/reacciones */}
      <div className="mt-8 grid grid-cols-3 gap-4">
        <div className="col-span-2 rounded bg-tint-surface p-4">
          <p>Reproductor (simulado)</p>
        </div>
        <div className="rounded bg-tint-surface p-4">
          <p>Chat (simulado)</p>
        </div>
      </div>
    </div>
  );
}
