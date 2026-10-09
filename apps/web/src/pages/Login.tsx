import { useState } from 'react';
import { signInWithPopup, GoogleAuthProvider } from 'firebase/auth';
import { auth } from '../lib/firebase';
import { useNavigate } from 'react-router-dom';
import { apiFetch } from '../lib/api';

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
    <div className="flex min-h-screen items-center justify-center bg-tint-bg text-tint-muted">
      <div className="flex flex-col items-center gap-4">
        <button
          onClick={handleLogin}
          disabled={loading}
          className="rounded bg-tint-accent px-6 py-3 text-black disabled:opacity-50"
        >
          {loading ? 'Conectando…' : 'Iniciar sesión con Google'}
        </button>
        {error && (
          <p role="alert" className="max-w-sm text-center text-sm text-red-400">
            Error: {error}
          </p>
        )}
      </div>
    </div>
  );
}
