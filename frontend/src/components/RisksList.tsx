import type { HealthInfo, HealthStatus } from "../types";
import { StatusPill } from "./StatusPill";

interface Props {
  rows: HealthInfo[];
  onSelect: (h: HealthInfo) => void;
}

const ORDER: HealthStatus[] = ["CRITICAL", "WARNING", "OUTDATED", "UNKNOWN"];

export function RisksList({ rows, onSelect }: Props) {
  const grouped = ORDER.map((s) => ({
    status: s,
    items: rows.filter((r) => r.status === s),
  })).filter((g) => g.items.length > 0);

  if (!grouped.length) {
    return (
      <div className="rounded-lg border border-emerald-500/30 bg-emerald-500/5 p-6 text-center text-emerald-300">
        ✓ No risks detected. All dependencies look healthy.
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {grouped.map((g) => (
        <section key={g.status}>
          <div className="mb-2 flex items-center gap-2">
            <StatusPill status={g.status} />
            <span className="text-sm text-slate-400">
              {g.items.length} dependenc{g.items.length === 1 ? "y" : "ies"}
            </span>
          </div>
          <ul className="divide-y divide-slate-800 rounded-lg border border-slate-800 bg-slate-900/40">
            {g.items.map((r) => (
              <li
                key={`${r.coordinate.groupId}:${r.coordinate.artifactId}:${r.coordinate.version}`}
                onClick={() => onSelect(r)}
                className="cursor-pointer px-4 py-3 hover:bg-slate-800/60"
              >
                <div className="flex items-baseline justify-between gap-3">
                  <div>
                    <div className="font-medium text-slate-100">
                      {r.coordinate.artifactId}{" "}
                      <span className="text-xs text-slate-500">
                        {r.coordinate.version}
                      </span>
                    </div>
                    <div className="text-xs text-slate-500">
                      {r.coordinate.groupId}
                    </div>
                  </div>
                  <span className="text-xs tabular-nums text-slate-400">
                    score {r.healthScore}
                  </span>
                </div>
                {r.reasons.length > 0 && (
                  <ul className="mt-1 list-disc pl-5 text-xs text-slate-300">
                    {r.reasons.slice(0, 4).map((reason) => (
                      <li key={reason}>{reason}</li>
                    ))}
                    {r.reasons.length > 4 && (
                      <li className="text-slate-500">
                        +{r.reasons.length - 4} more…
                      </li>
                    )}
                  </ul>
                )}
              </li>
            ))}
          </ul>
        </section>
      ))}
    </div>
  );
}
