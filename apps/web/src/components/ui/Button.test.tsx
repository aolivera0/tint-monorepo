import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import { Button } from './Button';

describe('Button', () => {
  it('renderiza con label en una linea y maneja click', async () => {
    const onClick = vi.fn();
    render(<Button onClick={onClick}>Crear sala</Button>);
    const btn = screen.getByRole('button', { name: 'Crear sala' });
    expect(btn).toBeInTheDocument();
    await userEvent.click(btn);
    expect(onClick).toHaveBeenCalledTimes(1);
  });
});
