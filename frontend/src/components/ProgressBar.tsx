import type { Progress } from "../types";

export function ProgressBar({ progress }: { progress: Progress }) {
  const pct =
    progress.total > 0
      ? Math.round((progress.completed / progress.total) * 100)
      : progress.phase === "RESOLVING"
      ? 5
      : 0;
  return (
    <div className="rounded-lg border border-slate-800 bg-slate-900/60 p-4">
      <div className="mb-1 flex items-baseline justify-between">
        <span className="text-sm font-medium text-slate-200">
          {progress.phase === "RESOLVING"
            ? "Resolving dependency tree…"
            : progress.phase === "ENRICHING"
            ? `Enriching: ${progress.completed}/${progress.total}`
            : "Done"}
        </span>
        <span className="text-xs text-slate-400">{pct}%</span>
      </div>
      <div className="h-2 overflow-hidden rounded-full bg-slate-800">
        <div
          className="h-full bg-emerald-500 transition-all"
          style={{ width: `${pct}%` }}
        />
      </div>
      <div className="mt-1 truncate text-xs text-slate-500">
        {progress.message}
      </div>
    </div>
  );
}
