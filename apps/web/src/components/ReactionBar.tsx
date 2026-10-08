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
          className="rounded-full bg-tint-surface px-3 py-2 text-xl transition hover:scale-110 hover:shadow-[0_0_12px_#66FCF1] focus:outline-none focus-visible:ring-2 focus-visible:ring-tint-accent"
        >
          {r}
        </button>
      ))}
    </div>
  );
}
