import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
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

describe('WatchRoom chat local', () => {
  it('envia un mensaje y lo muestra', async () => {
    renderWatch();
    await userEvent.type(screen.getByPlaceholderText('Escribe un mensaje'), 'Hola grupo');
    await userEvent.click(screen.getByText('Enviar'));
    expect(await screen.findByText('Hola grupo')).toBeInTheDocument();
  });
});
