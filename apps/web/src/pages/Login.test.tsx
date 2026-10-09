import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { vi, beforeEach } from 'vitest';
import Login from './Login';
import { apiFetch } from '../lib/api';
import { signInWithPopup } from 'firebase/auth';
import type { UserCredential } from 'firebase/auth';

vi.mock('firebase/auth', () => ({
  signInWithPopup: vi.fn(),
  GoogleAuthProvider: vi.fn(),
}));

// Evita inicializar Firebase real en tests (la API key de .env no es válida aquí).
vi.mock('../lib/firebase', () => ({
  auth: {},
  app: {},
}));

vi.mock('../lib/api', () => ({
  apiFetch: vi.fn(),
}));

const mockedSignIn = vi.mocked(signInWithPopup);
const mockedApi = vi.mocked(apiFetch);

function renderLogin() {
  return render(
    <MemoryRouter initialEntries={['/login']}>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="/dashboard" element={<p>Dashboard OK</p>} />
        <Route path="/invite/:code" element={<p>Invitación OK</p>} />
      </Routes>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  localStorage.clear();
});

describe('Login', () => {
  it('renders login button', () => {
    renderLogin();
    expect(screen.getByText('Iniciar sesión con Google')).toBeInTheDocument();
  });

  it('guarda el token y navega al dashboard si /auth/verify responde ok', async () => {
    mockedSignIn.mockResolvedValueOnce({
      user: { getIdToken: () => Promise.resolve('tok-123') },
    } as unknown as UserCredential);
    mockedApi.mockResolvedValueOnce({ user: { id: 'u1' }, isRegistered: true });
    renderLogin();
    await userEvent.click(screen.getByText('Iniciar sesión con Google'));
    await waitFor(() => expect(screen.getByText('Dashboard OK')).toBeInTheDocument());
    expect(localStorage.getItem('tint_token')).toBe('tok-123');
  });

  it('muestra el error y no navega si /auth/verify falla', async () => {
    mockedSignIn.mockResolvedValueOnce({
      user: { getIdToken: () => Promise.resolve('tok-123') },
    } as unknown as UserCredential);
    mockedApi.mockRejectedValueOnce(new Error('Invalid token'));
    renderLogin();
    await userEvent.click(screen.getByText('Iniciar sesión con Google'));
    await waitFor(() => expect(screen.getByRole('alert')).toBeInTheDocument());
    expect(screen.getByText(/Invalid token/)).toBeInTheDocument();
    expect(localStorage.getItem('tint_token')).toBeNull();
  });

  it('entra con link de invitación absoluto sin pedir cuenta', async () => {
    renderLogin();
    await userEvent.type(screen.getByPlaceholderText(/link o el código/i), 'http://localhost:5173/invite/abc123XYZ');
    await userEvent.click(screen.getByText('Continuar sin cuenta'));
    await waitFor(() => expect(screen.getByText('Invitación OK')).toBeInTheDocument());
    expect(localStorage.getItem('tint_token')).toBeNull();
  });

  it('entra con el código pelado', async () => {
    renderLogin();
    await userEvent.type(screen.getByPlaceholderText(/link o el código/i), 'abc123XYZ');
    await userEvent.click(screen.getByText('Continuar sin cuenta'));
    await waitFor(() => expect(screen.getByText('Invitación OK')).toBeInTheDocument());
  });

  it('no navega y avisa si el link no es válido', async () => {
    renderLogin();
    await userEvent.type(screen.getByPlaceholderText(/link o el código/i), 'hola');
    await userEvent.click(screen.getByText('Continuar sin cuenta'));
    await waitFor(() => expect(screen.getByText(/no parece válido/i)).toBeInTheDocument());
    expect(screen.queryByText('Invitación OK')).not.toBeInTheDocument();
  });
});
