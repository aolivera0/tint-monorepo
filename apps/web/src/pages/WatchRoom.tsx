import { useParams, Link } from 'react-router-dom';
import { useState } from 'react';
import { ReactionBar } from '../components/ReactionBar';
import { Card } from '../components/ui/Primitives';

export default function WatchRoom() {
  const { roomId } = useParams();
  // SIMULACIÓN: Chat y streaming aún no implementados.
  // Se integrará con sync-core (/ws/sync) y social-core (/ws/social) cuando aplique.
  const guestToken = localStorage.getItem('tint_guest_token');
  const isGuest = !!guestToken;
  const [chat, setChat] = useState('');
  const [messages, setMessages] = useState<{ nick: string; text: string }[]>([]);

  const sendChat = (e: React.FormEvent) => {
    e.preventDefault();
    const text = chat.trim();
    if (!text) return;
    // Solo pre validación local. El estado maestro vive en social-core.
    setMessages((m) => [...m.slice(-49), { nick: isGuest ? 'Invitado' : 'Host', text }]);
    setChat('');
  };

  return (
    <div className="min-h-[100dvh] bg-tint-bg text-tint-muted">
      <header className="sticky top-0 z-30 border-b border-white/10 bg-tint-bg/90 backdrop-blur">
        <div className="mx-auto flex h-16 max-w-[1400px] items-center justify-between gap-4 px-4">
          <div className="flex min-w-0 items-center gap-3">
            <Link
              to="/dashboard"
              aria-label="Volver a salas"
              className="rounded-full border border-white/15 px-3 py-1.5 text-[13px] text-white transition hover:bg-white/10"
            >
              Atrás
            </Link>
            <h1 className="truncate text-[15px] font-semibold tracking-tight text-white">
              Sala {roomId}
            </h1>
            <span className="rounded-full border border-white/10 bg-white/5 px-3 py-1 text-[12px] text-tint-muted">
              Rol: {isGuest ? 'participante (invitado)' : 'host (registrado)'}
            </span>
          </div>
          <div className="flex items-center gap-2" aria-label="Estado de sincronía">
            <span aria-hidden="true" className="h-2 w-2 rounded-full bg-tint-success" />
            <span className="text-[13px] text-white">Sincronizado</span>
          </div>
        </div>
      </header>

      <main className="mx-auto grid w-full max-w-[1400px] gap-4 px-4 py-6 lg:grid-cols-[2fr_360px]">
        <section aria-label="Reproductor">
          <Card className="overflow-hidden">
            <div className="relative aspect-video bg-black">
              <img
                src={`https://picsum.photos/seed/tint-watch-${roomId}/1280/720`}
                alt=""
                className="absolute inset-0 h-full w-full object-cover opacity-40"
                loading="eager"
              />
              <div className="absolute inset-0 grid place-items-center">
                <div className="flex flex-col items-center gap-3">
                  <button
                    type="button"
                    aria-label="Reproducir"
                    className="grid h-16 w-16 place-items-center rounded-full bg-tint-accent text-tint-bg transition hover:bg-tint-accentHover active:scale-[0.98]"
                  >
                    <span aria-hidden="true" className="ml-1 text-xl">▶</span>
                  </button>
                  <p className="text-[13px]">Reproductor (simulado)</p>
                </div>
              </div>
              <div className="absolute inset-x-0 bottom-0 p-4">
                <div
                  className="h-1 overflow-hidden rounded-full bg-white/15"
                  role="progressbar"
                  aria-label="Progreso"
                  aria-valuenow={0}
                  aria-valuemin={0}
                  aria-valuemax={100}
                >
                  <div className="h-full w-0 bg-tint-accent" />
                </div>
              </div>
            </div>
            <div aria-hidden="true" className="h-1.5 bg-gradient-to-r from-tint-accentDark via-tint-accent to-tint-accentDark opacity-70 blur-[6px]" />
            <div className="flex flex-wrap items-center justify-between gap-4 p-4">
              <div>
                <p className="text-sm font-medium text-white">Reproducción sincronizada</p>
                <p className="mt-0.5 text-[13px]">
                  Controles de host listos para /ws/sync
                </p>
              </div>
              <ReactionBar onReact={() => {}} />
            </div>
          </Card>

          <div className="mt-4 grid gap-4 md:grid-cols-2">
            <Card className="p-5">
              <h2 className="text-[15px] font-semibold text-white">Trivia</h2>
              <p className="mt-1 text-[13px] leading-relaxed">
                Las preguntas de social-core aparecerán aquí durante la reproducción.
              </p>
            </Card>
            <Card className="p-5">
              <h2 className="text-[15px] font-semibold text-white">Ambilight</h2>
              <p className="mt-1 text-[13px] leading-relaxed">
                Visualización lista para ambilight.json de media-worker.
              </p>
              <div aria-hidden="true" className="mt-3 flex gap-1.5">
                {['#FF6B1A', '#C4520A', '#313338', '#B5BAC1', '#1E1F22'].map((c) => (
                  <span key={c} style={{ background: c }} className="h-6 flex-1 rounded-md border border-white/10" />
                ))}
              </div>
            </Card>
          </div>

          {/* TODO: conectar a /ws/sync (sync-core) */}
          {/* TODO: conectar a /ws/social (social-core) + chat/reacciones */}
        </section>

        <aside aria-label="Social" className="flex flex-col gap-4">
          <Card className="flex min-h-[420px] flex-1 flex-col p-5">
            <h2 className="text-[15px] font-semibold text-white">Chat (simulado)</h2>
            {messages.length === 0 ? (
              <p className="mt-3 rounded-xl border border-dashed border-white/15 bg-white/[0.02] p-4 text-[13px] leading-relaxed">
                Sin mensajes aún. Saluda y prueba la sincronía con tu grupo.
              </p>
            ) : (
              <ul className="mt-3 flex-1 space-y-2 overflow-auto">
                {messages.map((m, i) => (
                  <li key={i} className="rounded-xl bg-white/5 px-3 py-2 text-[13px]">
                    <span className="font-semibold text-white">{m.nick}: </span>
                    <span>{m.text}</span>
                  </li>
                ))}
              </ul>
            )}
            <form onSubmit={sendChat} className="mt-4 flex gap-2">
              <label htmlFor="chat-input" className="sr-only">
                Escribe un mensaje
              </label>
              <input
                id="chat-input"
                value={chat}
                onChange={(e) => setChat(e.target.value)}
                placeholder="Escribe un mensaje"
                maxLength={280}
                className="min-w-0 flex-1 rounded-xl border border-white/10 bg-tint-bg px-3 py-2 text-sm text-white placeholder:text-tint-muted/50 focus:border-tint-accent focus:outline-none"
              />
              <button
                type="submit"
                className="shrink-0 rounded-full bg-tint-accent px-4 py-2 text-sm font-semibold text-tint-bg transition hover:bg-tint-accentHover active:scale-[0.98]"
              >
                Enviar
              </button>
            </form>
          </Card>
        </aside>
      </main>
    </div>
  );
}
