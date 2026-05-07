import { useMemo, useState } from "react";
import type { HealthInfo, HealthStatus } from "../types";
import { StatusPill } from "./StatusPill";
import { timeAgo } from "../util";

type SortKey =
  | "name"
  | "version"
  | "latest"
  | "lastCommit"
  | "score"
  | "status"
  | "cves";

interface Props {
  rows: HealthInfo[];
  onSelect: (h: HealthInfo) => void;
}

const STATUS_FILTERS: HealthStatus[] = [
  "HEALTHY",
  "OUTDATED",
  "WARNING",
  "CRITICAL",
  "UNKNOWN",
];

export function DependencyTable({ rows, onSelect }: Props) {
  const [sortKey, setSortKey] = useState<SortKey>("status");
  const [sortDir, setSortDir] = useState<"asc" | "desc">("desc");
  const [search, setSearch] = useState("");
  const [statusFilter, setStatusFilter] = useState<Set<HealthStatus>>(
    new Set(STATUS_FILTERS),
  );

  const filtered = useMemo(() => {
    const q = search.toLowerCase().trim();
    return rows.filter((r) => {
      if (!statusFilter.has(r.status)) return false;
      if (!q) return true;
      const c = r.coordinate;
      return (
        c.artifactId.toLowerCase().includes(q) ||
        c.groupId.toLowerCase().includes(q) ||
        c.version.toLowerCase().includes(q) ||
        (r.organization?.toLowerCase().includes(q) ?? false)
      );
    });
  }, [rows, search, statusFilter]);

  const sorted = useMemo(() => {
    const arr = [...filtered];
    arr.sort((a, b) => {
      const cmp = compare(a, b, sortKey);
      return sortDir === "asc" ? cmp : -cmp;
    });
    return arr;
  }, [filtered, sortKey, sortDir]);

  function toggleStatus(s: HealthStatus) {
    setStatusFilter((prev) => {
      const n = new Set(prev);
      if (n.has(s)) n.delete(s);
      else n.add(s);
      return n;
    });
  }

  function setSort(key: SortKey) {
    if (sortKey === key) setSortDir((d) => (d === "asc" ? "desc" : "asc"));
    else {
      setSortKey(key);
      setSortDir(key === "name" ? "asc" : "desc");
    }
  }

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap items-center gap-3">
        <input
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Filter by name / group / version…"
          className="grow min-w-[240px] rounded bg-slate-800 border border-slate-700 px-3 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-500"
        />
        <div className="flex flex-wrap gap-1">
          {STATUS_FILTERS.map((s) => (
            <button
              key={s}
              onClick={() => toggleStatus(s)}
              className={`rounded-full px-2.5 py-0.5 text-xs font-medium border transition ${
                statusFilter.has(s)
                  ? "border-slate-500 bg-slate-700 text-slate-100"
                  : "border-slate-800 bg-slate-900 text-slate-500"
              }`}
            >
              {s}
            </button>
          ))}
        </div>
        <span className="text-xs text-slate-400">
          {sorted.length} of {rows.length}
        </span>
      </div>

      <div className="overflow-auto rounded-lg border border-slate-800">
        <table className="min-w-full divide-y divide-slate-800 text-sm">
          <thead className="bg-slate-900/80 text-xs uppercase text-slate-400">
            <tr>
              <Th label="Name" k="name" sortKey={sortKey} sortDir={sortDir} onClick={setSort} />
              <Th label="Version" k="version" sortKey={sortKey} sortDir={sortDir} onClick={setSort} />
              <Th label="Latest" k="latest" sortKey={sortKey} sortDir={sortDir} onClick={setSort} />
              <Th label="Last Commit" k="lastCommit" sortKey={sortKey} sortDir={sortDir} onClick={setSort} />
              <Th label="CVEs" k="cves" sortKey={sortKey} sortDir={sortDir} onClick={setSort} />
              <Th label="Score" k="score" sortKey={sortKey} sortDir={sortDir} onClick={setSort} />
              <Th label="Status" k="status" sortKey={sortKey} sortDir={sortDir} onClick={setSort} />
            </tr>
          </thead>
          <tbody className="divide-y divide-slate-800/70">
            {sorted.map((r) => {
              const cveCount = r.vulnerabilities.length;
              return (
                <tr
                  key={`${r.coordinate.groupId}:${r.coordinate.artifactId}:${r.coordinate.version}`}
                  className="cursor-pointer hover:bg-slate-800/60"
                  onClick={() => onSelect(r)}
                >
                  <td className="px-3 py-2">
                    <div className="flex items-center gap-2">
                      <span className="font-medium text-slate-100">
                        {r.coordinate.artifactId}
                      </span>
                      {isTestOnly(r) && <TestBadge />}
                    </div>
                    <div className="text-xs text-slate-500">
                      {r.coordinate.groupId}
                    </div>
                  </td>
                  <td className="px-3 py-2 font-mono text-xs">
                    {r.coordinate.version}
                  </td>
                  <td className="px-3 py-2 font-mono text-xs">
                    {r.latestVersion ?? "—"}
                    {r.majorVersionsBehind && r.majorVersionsBehind > 0 ? (
                      <span className="ml-2 text-amber-400">
                        −{r.majorVersionsBehind} maj
                      </span>
                    ) : null}
                  </td>
                  <td className="px-3 py-2 text-xs text-slate-300">
                    {timeAgo(r.lastCommit)}
                  </td>
                  <td className="px-3 py-2">
                    {cveCount > 0 ? (
                      <span className="rounded bg-red-500/20 px-1.5 py-0.5 text-xs text-red-300">
                        {cveCount}
                      </span>
                    ) : (
                      <span className="text-xs text-slate-600">—</span>
                    )}
                  </td>
                  <td className="px-3 py-2">
                    <ScoreBar score={r.healthScore} />
                  </td>
                  <td className="px-3 py-2">
                    <StatusPill status={r.status} />
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function Th({
  label,
  k,
  sortKey,
  sortDir,
  onClick,
}: {
  label: string;
  k: SortKey;
  sortKey: SortKey;
  sortDir: "asc" | "desc";
  onClick: (k: SortKey) => void;
}) {
  const active = sortKey === k;
  return (
    <th
      onClick={() => onClick(k)}
      className={`px-3 py-2 text-left font-medium tracking-wide cursor-pointer select-none whitespace-nowrap ${
        active ? "text-emerald-400" : ""
      }`}
    >
      {label}
      {active && <span className="ml-1">{sortDir === "asc" ? "▲" : "▼"}</span>}
    </th>
  );
}

function isTestOnly(r: HealthInfo): boolean {
  return (
    Array.isArray(r.scopes) &&
    r.scopes.length > 0 &&
    r.scopes.every((s) => s === "test")
  );
}

function TestBadge() {
  return (
    <span
      className="rounded bg-sky-500/20 px-1.5 py-0.5 text-[10px] font-medium uppercase tracking-wide text-sky-300"
      title="test-scope dependency only"
    >
      test
    </span>
  );
}

function ScoreBar({ score }: { score: number }) {
  const color =
    score >= 80
      ? "bg-emerald-500"
      : score >= 60
      ? "bg-amber-500"
      : score >= 40
      ? "bg-yellow-500"
      : "bg-red-500";
  return (
    <div className="flex items-center gap-2">
      <div className="h-1.5 w-16 overflow-hidden rounded bg-slate-800">
        <div
          className={`h-full ${color}`}
          style={{ width: `${Math.max(2, score)}%` }}
        />
      </div>
      <span className="text-xs tabular-nums text-slate-400">{score}</span>
    </div>
  );
}

function compare(a: HealthInfo, b: HealthInfo, k: SortKey): number {
  switch (k) {
    case "name":
      return a.coordinate.artifactId.localeCompare(b.coordinate.artifactId);
    case "version":
      return a.coordinate.version.localeCompare(b.coordinate.version);
    case "latest":
      return (a.latestVersion ?? "").localeCompare(b.latestVersion ?? "");
    case "lastCommit":
      return cmpDate(a.lastCommit, b.lastCommit);
    case "score":
      return a.healthScore - b.healthScore;
    case "status":
      return statusRank(a.status) - statusRank(b.status);
    case "cves":
      return a.vulnerabilities.length - b.vulnerabilities.length;
  }
}

function cmpDate(a: string | null, b: string | null): number {
  if (a === b) return 0;
  if (!a) return -1;
  if (!b) return 1;
  return new Date(a).getTime() - new Date(b).getTime();
}

function statusRank(s: HealthStatus): number {
  return { CRITICAL: 4, WARNING: 3, OUTDATED: 2, UNKNOWN: 1, HEALTHY: 0 }[s];
}
