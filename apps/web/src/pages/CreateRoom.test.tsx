import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { vi, beforeEach } from 'vitest';
import CreateRoom from './CreateRoom';
import { apiFetch } from '../lib/api';

vi.mock('../lib/api', () => ({
  apiFetch: vi.fn(),
}));

const mockedApi = vi.mocked(apiFetch);

beforeEach(() => {
  vi.clearAllMocks();
  localStorage.clear();
});

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
});
