import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import App from './App';

describe('App', () => {
  it('muestra la marca y el estado inicial', () => {
    render(<App />);
    expect(screen.getByRole('heading', { name: 'TINT' })).toBeInTheDocument();
    expect(screen.getByText('Reacciona a la escena')).toBeInTheDocument();
  });

  it('muestra la última reacción enviada', async () => {
    render(<App />);
    await userEvent.click(screen.getByRole('button', { name: '🔥' }));
    expect(screen.getByText('Última reacción: 🔥')).toBeInTheDocument();
  });
});
