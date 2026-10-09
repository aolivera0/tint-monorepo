import { useState } from 'react';
import { apiFetch } from '../lib/api';

export default function CreateRoom() {
  const [title, setTitle] = useState('');
  const [invitation, setInvitation] = useState<{ code: string; link: string; expiresAt: string } | null>(null);
  const [error, setError] = useState('');
  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    try {
      const res = await apiFetch('/rooms', {
        method: 'POST',
        body: JSON.stringify({ title }),
      });
      setInvitation(res.invitation);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'No se pudo crear la sala');
    }
  };
  return (
    <div className="min-h-screen bg-tint-bg p-8 text-tint-muted">
      <h1 className="mb-4 text-2xl">Crear sala</h1>
      <form onSubmit={handleSubmit} className="space-y-4">
        <input
          className="rounded bg-tint-surface p-2"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          placeholder="Título"
        />
        <button className="rounded bg-tint-accent px-4 py-2 text-black" type="submit">
          Crear
        </button>
      </form>
      {error && (
        <p role="alert" className="mt-4 text-sm text-red-400">
          Error: {error}
        </p>
      )}
      {invitation && (
        <div className="mt-4">
          <p>Código: {invitation.code}</p>
          <p>Link: {invitation.link}</p>
          <p>Expira: {invitation.expiresAt}</p>
        </div>
      )}
    </div>
  );
}
