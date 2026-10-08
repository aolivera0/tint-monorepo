import { useState } from 'react';
import { ReactionBar, type Reaction } from './components/ReactionBar';

export default function App() {
  const [last, setLast] = useState<Reaction | null>(null);

  return (
    <main className="flex min-h-screen flex-col items-center justify-center gap-8 bg-tint-bg text-tint-muted">
      <header className="text-center">
        <h1 className="text-5xl font-light tracking-[0.3em] text-tint-accent">TINT</h1>
        <p className="mt-2 text-sm uppercase tracking-widest">Watch Party Experience</p>
      </header>
      <section
        aria-label="Reproductor"
        className="aspect-video w-full max-w-3xl rounded-2xl bg-tint-surface shadow-[0_0_80px_-10px_#45A29E]"
      />
      <ReactionBar onReact={setLast} />
      <p aria-live="polite" className="h-6 text-sm">
        {last ? `Última reacción: ${last}` : 'Reacciona a la escena'}
      </p>
    </main>
  );
}
