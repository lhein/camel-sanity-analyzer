import type { Summary } from "../types";

interface Props {
  summary: Summary;
}

interface Card {
  label: string;
  value: number;
  hint?: string;
  color: string;
  border: string;
}

export function Dashboard({ summary }: Props) {
  const cards: Card[] = [
    {
      label: "Total",
      value: summary.total,
      color: "text-slate-100",
      border: "border-slate-700",
    },
    {
      label: "Healthy",
      value: summary.healthy,
      color: "text-emerald-400",
      border: "border-emerald-500/40",
    },
    {
      label: "Outdated",
      value: summary.outdated,
      color: "text-amber-400",
      border: "border-amber-500/40",
    },
    {
      label: "Warning",
      value: summary.warning,
      color: "text-yellow-400",
      border: "border-yellow-500/40",
    },
    {
      label: "Critical",
      value: summary.critical,
      color: "text-red-400",
      border: "border-red-500/50",
    },
    {
      label: "With CVEs",
      value: summary.withVulnerabilities,
      hint: `${summary.totalVulnerabilities} total`,
      color: "text-red-400",
      border: "border-red-500/50",
    },
    {
      label: "Archived",
      value: summary.archivedRepos,
      hint: "GitHub repos",
      color: "text-red-400",
      border: "border-red-500/50",
    },
    {
      label: "Unknown",
      value: summary.unknown,
      hint: "no data",
      color: "text-slate-400",
      border: "border-slate-600/50",
    },
  ];

  if (summary.testOnly > 0) {
    cards.push({
      label: "Test-only",
      value: summary.testOnly,
      hint: "test scope",
      color: "text-sky-400",
      border: "border-sky-500/40",
    });
  }

  return (
    <div className="grid grid-cols-2 sm:grid-cols-4 lg:grid-cols-8 gap-3">
      {cards.map((c) => (
        <div
          key={c.label}
          className={`rounded-lg border bg-slate-900/60 px-4 py-3 ${c.border}`}
        >
          <div className="text-xs uppercase tracking-wide text-slate-400">
            {c.label}
          </div>
          <div className={`mt-1 text-2xl font-semibold ${c.color}`}>
            {c.value}
          </div>
          {c.hint && (
            <div className="text-[11px] text-slate-500">{c.hint}</div>
          )}
        </div>
      ))}
    </div>
  );
}
