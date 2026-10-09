import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { vi, beforeEach } from 'vitest';
import CreateRoom from './CreateRoom';
import { apiFetch, ApiError } from '../lib/api';

vi.mock('../lib/api', () => ({
  apiFetch: vi.fn(),
  ApiError: class ApiError extends Error {
    readonly status: number;
    constructor(status: number, message: string) {
      super(message);
      this.name = 'ApiError';
      this.status = status;
    }
  },
}));

const mockedApi = vi.mocked(apiFetch);

beforeEach(() => {
  vi.clearAllMocks();
  localStorage.clear();
  vi.unstubAllGlobals();
});

function renderManage() {
  return render(
    <MemoryRouter initialEntries={['/rooms/r1']}>
      <Routes>
        <Route path="/rooms/:id" element={<CreateRoom />} />
        <Route path="/dashboard" element={<p>Volver al dashboard</p>} />
      </Routes>
    </MemoryRouter>,
  );
}

function mockManageLoad() {
  mockedApi.mockResolvedValueOnce({ room: { id: 'r1', title: 'Cine' } });
  mockedApi.mockResolvedValueOnce({
    invitations: [{ code: 'abc123', link: '/invite/abc123', expiresAt: 'mañana' }],
  });
}

describe('CreateRoom', () => {
  it('muestra el código y el link al crear la sala', async () => {
    mockedApi.mockResolvedValueOnce({
      room: { id: 'r1' },
      invitation: { code: 'abc123', link: '/invite/abc123', expiresAt: 'mañana' },
    });
    render(
      <MemoryRouter>
        <CreateRoom />
      </MemoryRouter>,
    );
    await userEvent.type(screen.getByPlaceholderText('Título'), 'Noche de cine');
    await userEvent.click(screen.getByText('Crear'));
    await waitFor(() => expect(screen.getByText(/Código:/)).toBeInTheDocument());
    expect(screen.getByText(/\/invite\/abc123/)).toBeInTheDocument();
    expect(mockedApi).toHaveBeenCalledWith('/rooms', {
      method: 'POST',
      body: JSON.stringify({ title: 'Noche de cine' }),
    });
  });

  it('muestra el error si la creación falla', async () => {
    mockedApi.mockRejectedValueOnce(new Error('Unauthorized'));
    render(
      <MemoryRouter>
        <CreateRoom />
      </MemoryRouter>,
    );
    await userEvent.type(screen.getByPlaceholderText('Título'), 'Sala X');
    await userEvent.click(screen.getByText('Crear'));
    await waitFor(() => expect(screen.getByRole('alert')).toBeInTheDocument());
    expect(screen.getByText(/Unauthorized/)).toBeInTheDocument();
  });

  it('copia un link absoluto que funciona pegado en el navegador', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    vi.stubGlobal('navigator', { clipboard: { writeText } });
    mockedApi.mockResolvedValueOnce({
      room: { id: 'r1' },
      invitation: { code: 'abc123', link: '/invite/abc123', expiresAt: 'mañana' },
    });
    render(
      <MemoryRouter>
        <CreateRoom />
      </MemoryRouter>,
    );
    await userEvent.type(screen.getByPlaceholderText('Título'), 'Noche de cine');
    await userEvent.click(screen.getByText('Crear'));
    await waitFor(() => expect(screen.getByText(/Código:/)).toBeInTheDocument());
    expect(screen.getByText(`${window.location.origin}/invite/abc123`)).toBeInTheDocument();
    await userEvent.click(screen.getByText('Copiar link'));
    await waitFor(() => expect(writeText).toHaveBeenCalledWith(`${window.location.origin}/invite/abc123`));
  });

  it('modo gestionar: carga la sala y muestra el link absoluto para compartir', async () => {
    mockManageLoad();
    renderManage();
    await waitFor(() => expect(screen.getByDisplayValue('Cine')).toBeInTheDocument());
    expect(screen.getByText(`${window.location.origin}/invite/abc123`)).toBeInTheDocument();
    expect(mockedApi).toHaveBeenCalledWith('/rooms/r1');
    expect(mockedApi).toHaveBeenCalledWith('/rooms/r1/invitations');
  });

  it('modo gestionar: renombra con PATCH', async () => {
    mockManageLoad();
    mockedApi.mockResolvedValueOnce({ room: { id: 'r1', title: 'Cine 2' } });
    renderManage();
    const input = await screen.findByDisplayValue('Cine');
    await userEvent.clear(input);
    await userEvent.type(input, 'Cine 2');
    await userEvent.click(screen.getByText('Guardar nombre'));
    await waitFor(() =>
      expect(mockedApi).toHaveBeenCalledWith('/rooms/r1', {
        method: 'PATCH',
        body: JSON.stringify({ title: 'Cine 2' }),
      }),
    );
  });

  it('modo gestionar: genera un nuevo link', async () => {
    mockManageLoad();
    mockedApi.mockResolvedValueOnce({ code: 'nuevo99', link: '/invite/nuevo99', expiresAt: 'luego' });
    renderManage();
    await screen.findByDisplayValue('Cine');
    await userEvent.click(screen.getByText('Generar nuevo link'));
    await waitFor(() =>
      expect(mockedApi).toHaveBeenCalledWith('/rooms/r1/invitations', { method: 'POST', body: '{}' }),
    );
    await waitFor(() =>
      expect(screen.getByText(`${window.location.origin}/invite/nuevo99`)).toBeInTheDocument(),
    );
  });

  it('modo gestionar: elimina y vuelve al dashboard', async () => {
    vi.spyOn(window, 'confirm').mockReturnValueOnce(true);
    mockManageLoad();
    mockedApi.mockResolvedValueOnce({ success: true });
    renderManage();
    await screen.findByDisplayValue('Cine');
    await userEvent.click(screen.getByText('Eliminar sala'));
    await waitFor(() => expect(mockedApi).toHaveBeenCalledWith('/rooms/r1', { method: 'DELETE' }));
    await waitFor(() => expect(screen.getByText('Volver al dashboard')).toBeInTheDocument());
  });

  it('modo gestionar: avisa si la sala fue eliminada (410)', async () => {
    mockedApi.mockRejectedValueOnce(new ApiError(410, 'Deleted'));
    renderManage();
    await waitFor(() => expect(screen.getByText(/eliminada/i)).toBeInTheDocument());
  });

  it('modo crear: tiene enlace Volver al dashboard', async () => {
    render(
      <MemoryRouter>
        <CreateRoom />
      </MemoryRouter>,
    );
    expect(screen.getByRole('link', { name: 'Volver' })).toHaveAttribute('href', '/dashboard');
  });

  it('modo gestionar: tiene enlace Volver al dashboard', async () => {
    mockManageLoad();
    renderManage();
    await screen.findByDisplayValue('Cine');
    const backLinks = screen.getAllByRole('link', { name: 'Volver' });
    expect(backLinks).toHaveLength(1);
    expect(backLinks[0]).toHaveAttribute('href', '/dashboard');
  });

  it('sala eliminada: usa el mismo enlace Volver sin duplicados', async () => {
    mockedApi.mockRejectedValueOnce(new ApiError(410, 'Deleted'));
    renderManage();
    await waitFor(() => expect(screen.getByText(/eliminada/i)).toBeInTheDocument());
    const backLinks = screen.getAllByRole('link', { name: 'Volver' });
    expect(backLinks).toHaveLength(1);
    expect(backLinks[0]).toHaveAttribute('href', '/dashboard');
  });
});
