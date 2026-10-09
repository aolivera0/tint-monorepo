import { useThrottle } from '../hooks/useThrottle';

export const REACTIONS = ['👏', '😂', '😮', '😢', '🔥'] as const;
export type Reaction = (typeof REACTIONS)[number];

interface ReactionBarProps {
  onReact: (reaction: Reaction) => void;
}

export function ReactionBar({ onReact }: ReactionBarProps) {
  const react = useThrottle(onReact);

  return (
    <div role="toolbar" aria-label="Reacciones" className="flex gap-2">
      {REACTIONS.map((r) => (
        <button
          key={r}
          type="button"
          onClick={() => react(r)}
          aria-label={`Reaccionar con ${r}`}
          className="rounded-full border border-white/10 bg-white/5 px-3 py-2 text-xl transition hover:scale-105 hover:bg-white/10 active:scale-[0.98] focus:outline-none focus-visible:ring-2 focus-visible:ring-tint-accent"
        >
          {r}
        </button>
      ))}
    </div>
  );
}
