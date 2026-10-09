import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { AppShell } from './AppShell';

describe('AppShell', () => {
  it('renderiza marca, contenido y nav en una sola linea', () => {
    render(
      <MemoryRouter>
        <AppShell actions={<button>Crear sala</button>}>
          <p>Hola</p>
        </AppShell>
      </MemoryRouter>,
    );
    expect(screen.getByText('TINT')).toBeInTheDocument();
    expect(screen.getByText('Hola')).toBeInTheDocument();
    expect(screen.getByText('Crear sala')).toBeInTheDocument();
  });
});
