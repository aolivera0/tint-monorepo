import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { BackLink } from './BackLink';

describe('BackLink', () => {
  it('renderiza Volver apuntando al dashboard por defecto', () => {
    render(
      <MemoryRouter>
        <BackLink />
      </MemoryRouter>,
    );
    const link = screen.getByRole('link', { name: 'Volver' });
    expect(link).toHaveAttribute('href', '/dashboard');
  });

  it('respeta destino y aria-label personalizados', () => {
    render(
      <MemoryRouter>
        <BackLink to="/watch/1" label="Volver a la sala" />
      </MemoryRouter>,
    );
    const link = screen.getByRole('link', { name: 'Volver a la sala' });
    expect(link).toHaveAttribute('href', '/watch/1');
    expect(link).toHaveTextContent('Volver');
  });
});
