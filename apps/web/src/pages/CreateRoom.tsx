import { useState } from 'react';
import { apiFetch } from '../lib/api';
import { Link } from 'react-router-dom';
import { AppShell } from '../components/layout/AppShell';
import { Button } from '../components/ui/Button';
import { Field, ErrorAlert, Card } from '../components/ui/Primitives';

export default function CreateRoom() {
  const [title, setTitle] = useState('');
  const [invitation, setInvitation] = useState<{ code: string; link: string; expiresAt: string } | null>(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [copied, setCopied] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      const res = await apiFetch('/rooms', {
        method: 'POST',
        body: JSON.stringify({ title }),
      });
      setInvitation(res.invitation);
      setCopied(false);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'No se pudo crear la sala');
    } finally {
      setLoading(false);
    }
  };

  const copyLink = async () => {
    if (!invitation) return;
    try {
      await navigator.clipboard.writeText(invitation.link);
      setCopied(true);
    } catch {
      setCopied(false);
    }
  };

  return (
    <AppShell>
      <div className="grid gap-8 pt-10 lg:grid-cols-[1fr_380px]">
        <section>
          <h1 className="text-3xl font-bold tracking-tighter text-white md:text-4xl">Crear sala</h1>
          <p className="mt-2 max-w-[65ch] text-sm leading-relaxed">
            Ponle un nombre claro. Al crearla obtienes un código y un link para invitar.
          </p>

          <Card className="mt-6 max-w-xl p-6">
            <form onSubmit={handleSubmit} className="space-y-5">
              <Field
                label="Título de la sala"
                htmlFor="room-title"
                helper="Ejemplo: Noche de cine, Final del torneo, Estreno juntos."
                error={undefined}
              >
                <input
                  id="room-title"
                  className="w-full rounded-xl border border-white/10 bg-tint-bg px-4 py-2.5 text-white placeholder:text-tint-muted/50 focus:border-tint-accent focus:outline-none"
                  value={title}
                  onChange={(e) => setTitle(e.target.value)}
                  placeholder="Título"
                  required
                  minLength={3}
                  maxLength={80}
                  autoComplete="off"
                />
              </Field>
              <Button type="submit" disabled={loading || title.trim().length < 3} className="w-full sm:w-auto">
                {loading ? 'Creando…' : 'Crear'}
              </Button>
              {error && <ErrorAlert message={`Error: ${error}`} />}
            </form>
          </Card>

          {invitation && (
            <Card className="mt-4 max-w-xl border-tint-accent/30 p-6">
              <h2 className="text-lg font-semibold tracking-tight text-white">Sala lista para compartir</h2>
              <dl className="mt-4 space-y-3 text-sm">
                <div className="flex items-center justify-between gap-4">
                  <dt className="text-tint-muted">Código:</dt>
                  <dd className="rounded-md bg-white/10 px-3 py-1 font-mono text-white">{invitation.code}</dd>
                </div>
                <div className="flex flex-col gap-1">
                  <dt className="text-tint-muted">Link:</dt>
                  <dd className="break-all text-white">{invitation.link}</dd>
                </div>
                <div className="flex items-center justify-between gap-4">
                  <dt className="text-tint-muted">Expira:</dt>
                  <dd className="text-white">{invitation.expiresAt}</dd>
                </div>
              </dl>
              <div className="mt-5 flex flex-wrap gap-3">
                <Button type="button" onClick={copyLink} variant="ghost">
                  {copied ? 'Copiado' : 'Copiar link'}
                </Button>
                <Link
                  to={`/invite/${invitation.code}`}
                  className="inline-flex items-center justify-center whitespace-nowrap rounded-full px-5 py-2.5 text-sm text-tint-muted transition hover:text-white"
                >
                  Ver invitación
                </Link>
              </div>
            </Card>
          )}
        </section>

        <aside className="hidden lg:block">
          <div className="overflow-hidden rounded-2xl border border-white/10">
            <img
              src="https://picsum.photos/seed/tint-create/760/900"
              alt=""
              loading="lazy"
              className="h-[420px] w-full object-cover opacity-60"
            />
          </div>
          <p className="mt-3 text-[13px] leading-relaxed text-tint-muted/80">
            Los invitados entran con nick. Tú controlas la reproducción como host.
          </p>
        </aside>
      </div>
    </AppShell>
  );
}
