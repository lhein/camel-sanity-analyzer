import { useMemo } from "react";
import type { HealthInfo } from "../types";
import { licenseCategoryColor, licenseCategoryLabel } from "../util";

interface Props {
  rows: HealthInfo[];
  onSelect: (h: HealthInfo) => void;
}

const CATEGORY_ORDER = [
  "PERMISSIVE",
  "WEAK_COPYLEFT",
  "COPYLEFT",
  "PUBLIC_DOMAIN",
  "PROPRIETARY",
  "UNKNOWN",
] as const;

export function LicensesView({ rows, onSelect }: Props) {
  // Group by license name (e.g. "Apache-2.0", "MIT", null) within each category.
  const groups = useMemo(() => {
    const byCategory = new Map<string, Map<string, HealthInfo[]>>();
    for (const r of rows) {
      const cat = r.licenseCategory ?? "UNKNOWN";
      const license = r.license ?? "(no license declared)";
      if (!byCategory.has(cat)) byCategory.set(cat, new Map());
      const inner = byCategory.get(cat)!;
      if (!inner.has(license)) inner.set(license, []);
      inner.get(license)!.push(r);
    }
    return CATEGORY_ORDER.map((cat) => ({
      category: cat,
      licenses: Array.from(byCategory.get(cat)?.entries() ?? []).sort((a, b) =>
        a[0].localeCompare(b[0]),
      ),
      total: Array.from(byCategory.get(cat)?.values() ?? []).reduce(
        (s, list) => s + list.length,
        0,
      ),
    })).filter((g) => g.total > 0);
  }, [rows]);

  if (!groups.length) {
    return (
      <div className="rounded-lg border border-slate-800 bg-slate-900/40 p-6 text-sm text-slate-500">
        No license data available.
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {groups.map((g) => (
        <section key={g.category}>
          <header className="mb-2 flex items-baseline gap-3">
            <span
              className={`rounded-full border px-2.5 py-0.5 text-xs font-semibold uppercase tracking-wide ${licenseCategoryColor(g.category)}`}
            >
              {licenseCategoryLabel(g.category)}
            </span>
            <span className="text-sm text-slate-400">
              {g.total} dependenc{g.total === 1 ? "y" : "ies"}
            </span>
          </header>
          <div className="space-y-3">
            {g.licenses.map(([license, items]) => (
              <div
                key={license}
                className="rounded-lg border border-slate-800 bg-slate-900/40"
              >
                <div className="flex items-center justify-between border-b border-slate-800 px-4 py-2">
                  <span className="font-mono text-sm text-slate-200">
                    {license}
                  </span>
                  <span className="text-xs text-slate-500">
                    {items.length}
                  </span>
                </div>
                <ul className="divide-y divide-slate-800/50">
                  {items.map((r) => (
                    <li
                      key={`${r.coordinate.groupId}:${r.coordinate.artifactId}:${r.coordinate.version}`}
                      onClick={() => onSelect(r)}
                      className="cursor-pointer px-4 py-2 hover:bg-slate-800/60"
                    >
                      <div className="flex items-baseline justify-between gap-3">
                        <span className="text-sm font-medium text-slate-100">
                          {r.coordinate.artifactId}
                        </span>
                        <span className="font-mono text-xs text-slate-500">
                          {r.coordinate.version}
                        </span>
                      </div>
                      <div className="text-xs text-slate-500">
                        {r.coordinate.groupId}
                      </div>
                    </li>
                  ))}
                </ul>
              </div>
            ))}
          </div>
        </section>
      ))}
    </div>
  );
}
