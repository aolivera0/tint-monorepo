import { render, screen } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { beforeEach } from 'vitest';
import WatchRoom from './WatchRoom';

function renderWatch() {
  return render(
    <MemoryRouter initialEntries={['/watch/r1']}>
      <Routes>
        <Route path="/watch/:roomId" element={<WatchRoom />} />
      </Routes>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  localStorage.clear();
});

describe('WatchRoom', () => {
  it('muestra la sala y el rol host si no hay guest token', () => {
    renderWatch();
    expect(screen.getByText(/Sala r1/)).toBeInTheDocument();
    expect(screen.getByText(/host \(registrado\)/)).toBeInTheDocument();
  });

  it('muestra el rol participante si hay guest token', () => {
    localStorage.setItem('tint_guest_token', 'guest-tok');
    renderWatch();
    expect(screen.getByText(/participante \(invitado\)/)).toBeInTheDocument();
  });
});
