import { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { apiFetch } from '../lib/api';

export default function InviteLanding() {
  const { code } = useParams();
  const [nick, setNick] = useState('');
  const [error, setError] = useState('');
  const navigate = useNavigate();
  const handleJoin = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      const res = await apiFetch('/invitations/claim', {
        method: 'POST',
        body: JSON.stringify({ code, nick }),
      });
      localStorage.removeItem('tint_token');
      localStorage.setItem('tint_guest_token', res.token);
      navigate(`/watch/${res.roomId}`);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'No se pudo unir a la sala');
    }
  };
  return (
    <div className="flex min-h-screen items-center justify-center bg-tint-bg text-tint-muted">
      <form onSubmit={handleJoin} className="space-y-4">
        <input
          className="rounded bg-tint-surface p-2"
          value={nick}
          onChange={(e) => setNick(e.target.value)}
          placeholder="Nick"
        />
        <button className="rounded bg-tint-accent px-4 py-2 text-black">Unirse</button>
        {error && <p>{error}</p>}
      </form>
    </div>
  );
}
