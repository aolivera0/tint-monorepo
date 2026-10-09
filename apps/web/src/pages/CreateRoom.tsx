import { useEffect, useState } from 'react';
import { apiFetch, ApiError } from '../lib/api';
import { toAbsoluteInviteLink } from '../lib/invite';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { AppShell } from '../components/layout/AppShell';
import { Button } from '../components/ui/Button';
import { Field, ErrorAlert, Card } from '../components/ui/Primitives';

type Invitation = { code: string; link: string; expiresAt: string };

export default function CreateRoom() {
  const { id: roomId } = useParams();
  const isManage = Boolean(roomId);
  const navigate = useNavigate();
  const [title, setTitle] = useState('');
  const [invitation, setInvitation] = useState<Invitation | null>(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [copied, setCopied] = useState(false);
  const [pageLoading, setPageLoading] = useState(isManage);
  const [gone, setGone] = useState(false);
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    if (!roomId) return;
    let cancelled = false;
    Promise.all([apiFetch(`/rooms/${roomId}`), apiFetch(`/rooms/${roomId}/invitations`)])
      .then(([roomRes, invRes]) => {
        if (cancelled) return;
        setTitle(roomRes.room?.title ?? '');
        const list = (invRes.invitations ?? []) as Invitation[];
        setInvitation(list[0] ?? null);
        setCopied(false);
      })
      .catch((e: unknown) => {
        if (cancelled) return;
        if (e instanceof ApiError && (e.status === 404 || e.status === 410)) {
          setGone(true);
        } else {
          setError(e instanceof Error ? e.message : 'No se pudo cargar la sala');
        }
      })
      .finally(() => {
        if (!cancelled) setPageLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [roomId]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setSaved(false);
    setLoading(true);
    try {
      if (isManage) {
        const res = await apiFetch(`/rooms/${roomId}`, {
          method: 'PATCH',
          body: JSON.stringify({ title }),
        });
        setTitle(res.room?.title ?? title);
        setSaved(true);
      } else {
        const res = await apiFetch('/rooms', {
          method: 'POST',
          body: JSON.stringify({ title }),
        });
        setInvitation(res.invitation);
        setCopied(false);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : isManage ? 'No se pudo guardar' : 'No se pudo crear la sala');
    } finally {
      setLoading(false);
    }
  };

  const handleNewLink = async () => {
    if (!roomId) return;
    setError('');
    setLoading(true);
    try {
      const res = await apiFetch(`/rooms/${roomId}/invitations`, { method: 'POST', body: '{}' });
      setInvitation({ code: res.code, link: res.link, expiresAt: res.expiresAt });
      setCopied(false);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'No se pudo generar el link');
    } finally {
      setLoading(false);
    }
  };

  const handleDelete = async () => {
    if (!roomId) return;
    if (!window.confirm('¿Eliminar esta sala? Los links dejarán de funcionar.')) return;
    setError('');
    setLoading(true);
    try {
      await apiFetch(`/rooms/${roomId}`, { method: 'DELETE' });
      navigate('/dashboard');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'No se pudo eliminar la sala');
    } finally {
      setLoading(false);
    }
  };

  const copyLink = async () => {
    if (!invitation) return;
    try {
      await navigator.clipboard.writeText(toAbsoluteInviteLink(invitation.link));
      setCopied(true);
    } catch {
      setCopied(false);
    }
  };

  const heading = isManage ? 'Gestionar sala' : 'Crear sala';
  const intro = isManage
    ? 'Cambia el nombre, obtén el link de nuevo o elimina la sala. Al eliminarla, los links dejan de funcionar.'
    : 'Ponle un nombre claro. Al crearla obtienes un código y un link para invitar.';

  return (
    <AppShell>
      <div className="grid gap-8 pt-10 lg:grid-cols-[1fr_380px]">
        <section>
          <Link
            to="/dashboard"
            aria-label="Volver al dashboard"
            className="inline-flex items-center gap-1 text-sm text-tint-muted transition hover:text-white"
          >
            ← Volver
          </Link>
          <h1 className="mt-3 text-3xl font-bold tracking-tighter text-white md:text-4xl">{heading}</h1>
          <p className="mt-2 max-w-[65ch] text-sm leading-relaxed">{intro}</p>

          {pageLoading && <p className="mt-6 text-sm">Cargando la sala…</p>}

          {gone && !pageLoading && (
            <div className="mt-6 max-w-xl">
              <ErrorAlert message="Esta sala fue eliminada. Sus links ya no funcionan." />
              <Link
                to="/dashboard"
                className="mt-4 inline-flex items-center justify-center whitespace-nowrap rounded-full bg-tint-accent px-5 py-2.5 text-sm font-semibold text-tint-bg"
              >
                Volver al dashboard
              </Link>
            </div>
          )}

          {!pageLoading && !gone && (
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
                  {loading ? (isManage ? 'Guardando…' : 'Creando…') : isManage ? 'Guardar nombre' : 'Crear'}
                </Button>
                {saved && <p className="text-sm text-tint-success">Nombre actualizado.</p>}
                {error && <ErrorAlert message={`Error: ${error}`} />}
              </form>
            </Card>
          )}

          {invitation && !gone && (
            <Card className="mt-4 max-w-xl border-tint-accent/30 p-6">
              <h2 className="text-lg font-semibold tracking-tight text-white">Sala lista para compartir</h2>
              <dl className="mt-4 space-y-3 text-sm">
                <div className="flex items-center justify-between gap-4">
                  <dt className="text-tint-muted">Código:</dt>
                  <dd className="rounded-md bg-white/10 px-3 py-1 font-mono text-white">{invitation.code}</dd>
                </div>
                <div className="flex flex-col gap-1">
                  <dt className="text-tint-muted">Link:</dt>
                  <dd className="break-all text-white">{toAbsoluteInviteLink(invitation.link)}</dd>
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
                {isManage && (
                  <Button type="button" onClick={handleNewLink} variant="quiet" disabled={loading}>
                    {loading ? 'Generando…' : 'Generar nuevo link'}
                  </Button>
                )}
              </div>
            </Card>
          )}

          {isManage && !pageLoading && !gone && (
            <Card className="mt-4 max-w-xl border-red-500/20 p-6">
              <h2 className="text-lg font-semibold tracking-tight text-white">Zona de peligro</h2>
              <p className="mt-1 text-sm leading-relaxed">
                Al eliminar la sala, sus links de invitación dejan de funcionar de inmediato.
              </p>
              <div className="mt-4">
                <Button type="button" onClick={handleDelete} variant="ghost" disabled={loading}>
                  Eliminar sala
                </Button>
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
