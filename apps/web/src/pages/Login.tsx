import { useState } from 'react';
import { signInWithPopup, GoogleAuthProvider } from 'firebase/auth';
import { auth } from '../lib/firebase';
import { useNavigate, Link } from 'react-router-dom';
import { apiFetch } from '../lib/api';
import { Button } from '../components/ui/Button';
import { ErrorAlert } from '../components/ui/Primitives';

export default function Login() {
  const navigate = useNavigate();
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const handleLogin = async () => {
    setError('');
    setLoading(true);
    try {
      const provider = new GoogleAuthProvider();
      const result = await signInWithPopup(auth, provider);
      const token = await result.user.getIdToken();
      localStorage.setItem('tint_token', token);
      await apiFetch('/auth/verify', { method: 'POST', body: JSON.stringify({}) });
      navigate('/dashboard');
    } catch (e) {
      localStorage.removeItem('tint_token');
      setError(e instanceof Error ? e.message : 'No se pudo iniciar sesión');
    } finally {
      setLoading(false);
    }
  };
  return (
    <div className="grid min-h-[100dvh] bg-tint-bg text-tint-muted md:grid-cols-2">
      <section className="relative hidden overflow-hidden md:block" aria-hidden="true">
        <img
          src="https://picsum.photos/seed/tint-cinema/1200/1400"
          alt=""
          className="absolute inset-0 h-full w-full object-cover opacity-50"
          loading="eager"
        />
        <div className="absolute inset-0 bg-gradient-to-t from-tint-bg via-tint-bg/60 to-tint-bg/20" />
        <div className="absolute inset-x-0 bottom-0 p-10">
          <p className="text-[11px] uppercase tracking-[0.18em] text-tint-accent">
            Salas sincronizadas
          </p>
          <h1 className="mt-3 max-w-[16ch] text-4xl font-bold leading-[1.05] tracking-tighter text-white lg:text-5xl">
            Mira junto, siente al mismo tiempo
          </h1>
          <p className="mt-3 max-w-[42ch] text-sm leading-relaxed text-tint-muted">
            Crea una sala, invita con un link y mantén a todos en el mismo segundo.
          </p>
        </div>
      </section>

      <section className="flex items-center justify-center px-4 py-10">
        <div className="w-full max-w-md rounded-2xl border border-white/10 bg-tint-card/70 p-8">
          <div className="flex items-center gap-3">
            <span aria-hidden="true" className="grid h-10 w-10 place-items-center rounded-xl bg-tint-accent">
              <span className="block h-4 w-4 rounded-[5px] bg-tint-bg" />
            </span>
            <div className="leading-none">
              <p className="text-lg font-bold tracking-tight text-white">TINT</p>
              <p className="mt-1 text-[11px] tracking-[0.14em] text-tint-muted/80">WATCH PARTY</p>
            </div>
          </div>

          <h2 className="mt-8 text-2xl font-bold tracking-tight text-white">Bienvenido de nuevo</h2>
          <p className="mt-2 text-sm leading-relaxed">
            Accede con tu cuenta para crear salas y ser host.
          </p>

          <div className="mt-6">
            <Button onClick={handleLogin} disabled={loading} className="w-full" aria-label="Iniciar sesión con Google">
              {loading ? 'Conectando…' : 'Iniciar sesión con Google'}
            </Button>
            <p className="mt-3 text-center text-[13px] text-tint-muted/80">
              Solo usamos tu sesión para verificar tu identidad.
            </p>
          </div>

          {error && (
            <div className="mt-5">
              <ErrorAlert message={`Error: ${error}`} />
            </div>
          )}

          <p className="mt-6 border-t border-white/10 pt-4 text-[13px]">
            ¿Tienes un link de invitación?{' '}
            <span className="text-tint-accent">Pide el código a tu host, no necesitas cuenta.</span>
          </p>
          <Link to="/dashboard" className="sr-only">
            dashboard
          </Link>
        </div>
      </section>
    </div>
  );
}
