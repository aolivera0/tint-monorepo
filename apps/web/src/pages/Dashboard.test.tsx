import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { vi, beforeEach } from 'vitest';
import Dashboard from './Dashboard';
import Login from './Login';
import { apiFetch, ApiError } from '../lib/api';

vi.mock('../lib/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../lib/api')>();
  return { ...actual, apiFetch: vi.fn() };
});

// Evita inicializar Firebase real en tests (la ruta /login renderiza Login).
vi.mock('../lib/firebase', () => ({
  auth: {},
  app: {},
}));

const mockedApi = vi.mocked(apiFetch);

function renderDashboard() {
  return render(
    <MemoryRouter initialEntries={['/dashboard']}>
      <Routes>
        <Route path="/dashboard" element={<Dashboard />} />
        <Route path="/login" element={<Login />} />
      </Routes>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  localStorage.clear();
});

describe('Dashboard', () => {
  it('lista las salas del host', async () => {
    mockedApi.mockResolvedValueOnce({ rooms: [{ id: '1', title: 'Sala 1' }] });
    renderDashboard();
    await waitFor(() => expect(screen.getByText('Sala 1')).toBeInTheDocument());
    expect(screen.getByText('Mis salas')).toBeInTheDocument();
    expect(screen.getByText('Crear sala')).toBeInTheDocument();
  });

  it('limpia el token y vuelve al login si la sesión es inválida (401)', async () => {
    localStorage.setItem('tint_token', 'viejo');
    mockedApi.mockRejectedValueOnce(new ApiError(401, 'Unauthorized'));
    renderDashboard();
    await waitFor(() =>
      expect(screen.getByText('Iniciar sesión con Google')).toBeInTheDocument(),
    );
    expect(localStorage.getItem('tint_token')).toBeNull();
  });

  it('muestra el error sin redirigir si falla por otra causa', async () => {
    mockedApi.mockRejectedValueOnce(new Error('DB caída'));
    renderDashboard();
    await waitFor(() => expect(screen.getByRole('alert')).toBeInTheDocument());
    expect(screen.getByText(/DB caída/)).toBeInTheDocument();
    expect(screen.getByText('Mis salas')).toBeInTheDocument();
  });

  it('cada sala enlaza a Gestionar para obtener el link, renombrar o eliminar', async () => {
    mockedApi.mockResolvedValueOnce({ rooms: [{ id: '1', title: 'Sala 1' }] });
    renderDashboard();
    await waitFor(() => expect(screen.getByText('Sala 1')).toBeInTheDocument());
    const manage = screen.getByRole('link', { name: /gestionar sala 1/i });
    expect(manage).toHaveAttribute('href', '/rooms/1');
  });

  it('elimina la sala con confirmación y la quita de la lista', async () => {
    const user = (await import('@testing-library/user-event')).default;
    vi.spyOn(window, 'confirm').mockReturnValueOnce(true);
    mockedApi.mockResolvedValueOnce({ rooms: [{ id: '1', title: 'Sala 1' }] });
    mockedApi.mockResolvedValueOnce({ success: true });
    renderDashboard();
    await waitFor(() => expect(screen.getByText('Sala 1')).toBeInTheDocument());
    await user.click(screen.getByRole('button', { name: /eliminar sala 1/i }));
    await waitFor(() => expect(mockedApi).toHaveBeenCalledWith('/rooms/1', { method: 'DELETE' }));
    await waitFor(() => expect(screen.queryByText('Sala 1')).not.toBeInTheDocument());
  });

  it('muestra error si eliminar falla', async () => {
    const user = (await import('@testing-library/user-event')).default;
    vi.spyOn(window, 'confirm').mockReturnValueOnce(true);
    mockedApi.mockResolvedValueOnce({ rooms: [{ id: '1', title: 'Sala 1' }] });
    mockedApi.mockRejectedValueOnce(new ApiError(403, 'Forbidden'));
    renderDashboard();
    await waitFor(() => expect(screen.getByText('Sala 1')).toBeInTheDocument());
    await user.click(screen.getByRole('button', { name: /eliminar sala 1/i }));
    await waitFor(() => expect(screen.getByRole('alert')).toBeInTheDocument());
    expect(screen.getByText('Sala 1')).toBeInTheDocument();
  });

  it('no elimina si se cancela la confirmación', async () => {
    const user = (await import('@testing-library/user-event')).default;
    vi.spyOn(window, 'confirm').mockReturnValueOnce(false);
    mockedApi.mockResolvedValueOnce({ rooms: [{ id: '1', title: 'Sala 1' }] });
    renderDashboard();
    await waitFor(() => expect(screen.getByText('Sala 1')).toBeInTheDocument());
    await user.click(screen.getByRole('button', { name: /eliminar sala 1/i }));
    expect(mockedApi).toHaveBeenCalledTimes(1);
    expect(screen.getByText('Sala 1')).toBeInTheDocument();
  });
});
