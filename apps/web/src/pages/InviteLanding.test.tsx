import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { vi, beforeEach } from 'vitest';
import InviteLanding from './InviteLanding';
import { apiFetch } from '../lib/api';

vi.mock('../lib/api', () => ({
  apiFetch: vi.fn(),
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
});
