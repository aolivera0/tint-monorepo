import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { vi, beforeEach } from 'vitest';
import InviteLanding from './InviteLanding';
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

function renderInvite() {
  return render(
    <MemoryRouter initialEntries={['/invite/ABC123']}>
      <Routes>
        <Route path="/invite/:code" element={<InviteLanding />} />
        <Route path="/watch/:roomId" element={<p>Sala lista</p>} />
      </Routes>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  localStorage.clear();
});

describe('InviteLanding', () => {
  it('canjea la invitación con el nick y guarda el guest token', async () => {
    mockedApi.mockResolvedValueOnce({ token: 'guest-tok', roomId: 'r1', nick: 'Ana' });
    renderInvite();
    await userEvent.type(screen.getByPlaceholderText('Nick'), 'Ana');
    await userEvent.click(screen.getByText('Unirse'));
    await waitFor(() => expect(screen.getByText('Sala lista')).toBeInTheDocument());
    expect(mockedApi).toHaveBeenCalledWith('/invitations/claim', {
      method: 'POST',
      body: JSON.stringify({ code: 'ABC123', nick: 'Ana' }),
    });
    expect(localStorage.getItem('tint_guest_token')).toBe('guest-tok');
  });

  it('muestra el error si el código expiró o es inválido', async () => {
    mockedApi.mockRejectedValueOnce(new Error('Expired'));
    renderInvite();
    await userEvent.type(screen.getByPlaceholderText('Nick'), 'Ana');
    await userEvent.click(screen.getByText('Unirse'));
    await waitFor(() => expect(screen.getByText(/Expired/)).toBeInTheDocument());
  });

  it('explica que expiró si la sala fue eliminada o el link caducó (410)', async () => {
    mockedApi.mockRejectedValueOnce(new ApiError(410, 'Deleted'));
    renderInvite();
    await userEvent.type(screen.getByPlaceholderText('Nick'), 'Ana');
    await userEvent.click(screen.getByText('Unirse'));
    await waitFor(() => expect(screen.getByText(/expir|eliminada/i)).toBeInTheDocument());
  });

  it('explica que no existe si el código es inválido (404)', async () => {
    mockedApi.mockRejectedValueOnce(new ApiError(404, 'Not found'));
    renderInvite();
    await userEvent.type(screen.getByPlaceholderText('Nick'), 'Ana');
    await userEvent.click(screen.getByText('Unirse'));
    await waitFor(() => expect(screen.getByText(/no existe|eliminada/i)).toBeInTheDocument());
  });

  it('explica que fue revocado por el host (409)', async () => {
    mockedApi.mockRejectedValueOnce(new ApiError(409, 'Revoked'));
    renderInvite();
    await userEvent.type(screen.getByPlaceholderText('Nick'), 'Ana');
    await userEvent.click(screen.getByText('Unirse'));
    await waitFor(() => expect(screen.getByText(/revocada/i)).toBeInTheDocument());
  });
});
