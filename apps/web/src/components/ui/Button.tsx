import type { ButtonHTMLAttributes } from 'react';

// Regla de forma: botones pill, cards rounded-2xl, inputs rounded-xl.
type Variant = 'primary' | 'ghost' | 'quiet';

const styles: Record<Variant, string> = {
  primary: 'bg-tint-accent text-tint-bg hover:bg-tint-accentHover focus-visible:ring-tint-accent',
  ghost: 'border border-white/15 bg-white/5 text-white hover:bg-white/10 focus-visible:ring-tint-accent/60',
  quiet: 'text-tint-muted hover:text-white hover:bg-white/5 focus-visible:ring-white/30',
};

export function Button({
  variant = 'primary',
  className = '',
  ...rest
}: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: Variant }) {
  return (
    <button
      {...rest}
      className={`inline-flex max-w-full items-center justify-center gap-2 whitespace-nowrap rounded-full px-5 py-2.5 text-sm font-semibold transition active:scale-[0.98] focus:outline-none focus-visible:ring-2 focus-visible:ring-offset-2 focus-visible:ring-offset-tint-bg disabled:cursor-not-allowed disabled:opacity-50 ${styles[variant]} ${className}`}
    />
  );
}
