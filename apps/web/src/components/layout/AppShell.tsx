import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';

export function BrandMark() {
  return (
    <Link to="/dashboard" className="flex items-center gap-3" aria-label="TINT inicio">
      <span aria-hidden="true" className="grid h-9 w-9 place-items-center rounded-xl bg-tint-accent">
        <span className="block h-3.5 w-3.5 rounded-[4px] bg-tint-bg" />
      </span>
      <span className="flex flex-col leading-none">
        <span className="text-[17px] font-bold tracking-tight text-white">TINT</span>
        <span className="text-[11px] tracking-[0.14em] text-tint-muted/80">WATCH PARTY</span>
      </span>
    </Link>
  );
}

export function TopBar({ actions }: { actions?: ReactNode }) {
  return (
    <header className="sticky top-0 z-30 border-b border-white/10 bg-tint-bg/90 backdrop-blur">
      <div className="mx-auto flex h-16 max-w-7xl items-center justify-between gap-4 px-4">
        <BrandMark />
        <div className="flex items-center gap-3">{actions}</div>
      </div>
    </header>
  );
}

export function AppShell({ children, actions }: { children: ReactNode; actions?: ReactNode }) {
  return (
    <div className="min-h-[100dvh] bg-tint-bg text-tint-muted antialiased">
      <TopBar actions={actions} />
      <main className="mx-auto w-full max-w-7xl px-4 pb-16">{children}</main>
    </div>
  );
}
