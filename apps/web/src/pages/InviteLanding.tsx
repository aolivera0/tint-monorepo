import { useState } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { apiFetch } from '../lib/api';
import { inviteClaimErrorMessage } from '../lib/invite';
import { Button } from '../components/ui/Button';
import { Field, ErrorAlert, Card } from '../components/ui/Primitives';

export default function InviteLanding() {
  const { code } = useParams();
  const [nick, setNick] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();
  const handleJoin = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      const res = await apiFetch('/invitations/claim', {
        method: 'POST',
        body: JSON.stringify({ code, nick }),
      });
      localStorage.removeItem('tint_token');
      localStorage.setItem('tint_guest_token', res.token);
      navigate(`/watch/${res.roomId}`);
    } catch (err) {
      setError(inviteClaimErrorMessage(err));
    } finally {
      setLoading(false);
    }
  };
  return (
    <div className="grid min-h-[100dvh] bg-tint-bg text-tint-muted md:grid-cols-[1.1fr_1fr]">
      <section className="relative hidden overflow-hidden md:block" aria-hidden="true">
        <img
          src="https://picsum.photos/seed/tint-guest/1200/1400"
          alt=""
          className="absolute inset-0 h-full w-full object-cover opacity-45"
          loading="eager"
        />
        <div className="absolute inset-0 bg-gradient-to-t from-tint-bg via-tint-bg/60 to-transparent" />
        <div className="absolute inset-x-0 bottom-0 p-10">
          <h1 className="max-w-[18ch] text-4xl font-bold leading-[1.05] tracking-tighter text-white">
            Te invitaron a ver algo juntos
          </h1>
          <p className="mt-3 max-w-[44ch] text-sm leading-relaxed">
            Elige un nick y entra como participante. Sin cuenta, sin fricción.
          </p>
        </div>
      </section>

      <section className="flex items-center justify-center px-4 py-10">
        <Card className="w-full max-w-md p-8">
          <p className="text-[11px] uppercase tracking-[0.18em] text-tint-accent">Invitación</p>
          <h2 className="mt-2 text-2xl font-bold tracking-tight text-white">Unirse a la sala</h2>
          <p className="mt-2 text-sm leading-relaxed">
            Código <span className="rounded-md bg-white/10 px-2 py-0.5 font-mono text-[13px] text-white">{code}</span>
          </p>

          <form onSubmit={handleJoin} className="mt-6 space-y-5">
            <Field label="Nick" htmlFor="nick" helper="Así te verán en el chat y las reacciones.">
              <input
                id="nick"
                className="w-full rounded-xl border border-white/10 bg-tint-bg px-4 py-2.5 text-white placeholder:text-tint-muted/50 focus:border-tint-accent focus:outline-none"
                value={nick}
                onChange={(e) => setNick(e.target.value)}
                placeholder="Nick"
                autoComplete="nickname"
                required
                minLength={2}
                maxLength={24}
              />
            </Field>
            <Button className="w-full" type="submit" disabled={loading || nick.trim().length < 2}>
              {loading ? 'Uniendo…' : 'Unirse'}
            </Button>
            {error && <ErrorAlert message={error} />}
          </form>

          <p className="mt-6 border-t border-white/10 pt-4 text-[13px]">
            ¿Eres el host?{' '}
            <Link to="/login" className="text-tint-accent underline-offset-4 hover:underline">
              Inicia sesión
            </Link>
          </p>
        </Card>
      </section>
    </div>
  );
}
