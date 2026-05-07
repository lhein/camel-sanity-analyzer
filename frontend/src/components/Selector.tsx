import { useEffect, useMemo, useRef, useState } from "react";
import type { ArtifactEntry, ArtifactsResponse, Kind, VersionInfo } from "../types";
import { fetchArtifacts, fetchVersions } from "../api";

interface Props {
  onAnalyze: (artifactId: string, version: string) => void;
  busy: boolean;
}

type KindFilter = "all" | Kind;

const FILTERS: { id: KindFilter; label: string }[] = [
  { id: "all", label: "All" },
  { id: "component", label: "Components" },
  { id: "dataformat", label: "Dataformats" },
  { id: "language", label: "Languages" },
];

export function Selector({ onAnalyze, busy }: Props) {
  const [artifacts, setArtifacts] = useState<ArtifactsResponse | null>(null);
  const [kind, setKind] = useState<KindFilter>("all");
  const [filter, setFilter] = useState("");
  const [open, setOpen] = useState(false);
  const [selected, setSelected] = useState<string>("");
  const [versions, setVersions] = useState<VersionInfo[]>([]);
  const [version, setVersion] = useState<string>("");
  const [loadError, setLoadError] = useState<string | null>(null);
  const dropdownRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    fetchArtifacts()
      .then(setArtifacts)
      .catch((e) => setLoadError(String(e)));
  }, []);

  useEffect(() => {
    function clickOutside(ev: MouseEvent) {
      if (dropdownRef.current && !dropdownRef.current.contains(ev.target as Node)) {
        setOpen(false);
      }
    }
    document.addEventListener("mousedown", clickOutside);
    return () => document.removeEventListener("mousedown", clickOutside);
  }, []);

  useEffect(() => {
    if (!selected) return;
    setVersion("");
    fetchVersions(selected)
      .then((vs) => {
        setVersions(vs);
        if (vs.length) setVersion(vs[0].version);
      })
      .catch((e) => setLoadError(String(e)));
  }, [selected]);

  const entries = useMemo<ArtifactEntry[]>(() => {
    if (!artifacts) return [];
    if (kind === "all") return artifacts.all;
    const list =
      kind === "component"
        ? artifacts.components
        : kind === "dataformat"
        ? artifacts.dataformats
        : artifacts.languages;
    return list.map((a) => ({
      artifactId: a,
      kinds:
        artifacts.all.find((e) => e.artifactId === a)?.kinds ?? [kind],
    }));
  }, [artifacts, kind]);

  const filtered = useMemo(() => {
    const q = filter.toLowerCase().trim();
    if (!q) return entries;
    return entries.filter((e) => e.artifactId.toLowerCase().includes(q));
  }, [entries, filter]);

  const canAnalyze = !!selected && !!version && !busy;

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap items-center gap-1">
        {FILTERS.map((f) => {
          const active = kind === f.id;
          const count =
            !artifacts
              ? null
              : f.id === "all"
              ? artifacts.all.length
              : f.id === "component"
              ? artifacts.components.length
              : f.id === "dataformat"
              ? artifacts.dataformats.length
              : artifacts.languages.length;
          return (
            <button
              key={f.id}
              onClick={() => {
                setKind(f.id);
                // Reset selection if it doesn't fit the new kind
                if (selected && f.id !== "all") {
                  const targetKind: Kind = f.id;
                  const stillIn = entries.find(
                    (e) =>
                      e.artifactId === selected && e.kinds.includes(targetKind),
                  );
                  if (!stillIn) setSelected("");
                }
              }}
              className={`rounded-full border px-3 py-1 text-xs font-medium transition ${
                active
                  ? "border-emerald-500/60 bg-emerald-500/15 text-emerald-200"
                  : "border-slate-700 bg-slate-900 text-slate-400 hover:bg-slate-800"
              }`}
            >
              {f.label}
              {count != null && (
                <span className="ml-1.5 text-[10px] opacity-60">{count}</span>
              )}
            </button>
          );
        })}
      </div>

      <div className="flex flex-wrap items-end gap-3">
        <div className="flex flex-col gap-1 grow min-w-[260px]" ref={dropdownRef}>
          <label className="text-xs uppercase tracking-wide text-slate-400">
            Camel Artifact
          </label>
          <div className="relative">
            <input
              type="text"
              value={open ? filter : selected || filter}
              placeholder="camel-…"
              onFocus={() => setOpen(true)}
              onChange={(e) => {
                setFilter(e.target.value);
                setOpen(true);
              }}
              className="w-full rounded bg-slate-800 border border-slate-700 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-500"
            />
            {open && filtered.length > 0 && (
              <ul className="absolute z-50 mt-1 max-h-72 w-full overflow-auto rounded border border-slate-700 bg-slate-900 shadow-lg">
                {filtered.map((e) => (
                  <li
                    key={e.artifactId}
                    className="flex cursor-pointer items-center justify-between gap-2 px-3 py-1.5 text-sm hover:bg-slate-700"
                    onClick={() => {
                      setSelected(e.artifactId);
                      setFilter("");
                      setOpen(false);
                    }}
                  >
                    <span>{e.artifactId}</span>
                    <span className="flex gap-1">
                      {e.kinds.map((k) => (
                        <KindBadge key={k} kind={k} />
                      ))}
                    </span>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </div>

        <div className="flex flex-col gap-1 min-w-[180px]">
          <label className="text-xs uppercase tracking-wide text-slate-400">
            Version
          </label>
          <select
            value={version}
            disabled={!versions.length}
            onChange={(e) => setVersion(e.target.value)}
            className="rounded bg-slate-800 border border-slate-700 px-3 py-2 text-sm disabled:opacity-50 focus:outline-none focus:ring-2 focus:ring-emerald-500"
          >
            {!versions.length && <option>—</option>}
            {versions.map((v) => (
              <option key={v.version} value={v.version}>
                {v.version}
                {v.released ? ` (${new Date(v.released).getFullYear()})` : ""}
              </option>
            ))}
          </select>
        </div>

        <button
          disabled={!canAnalyze}
          onClick={() => onAnalyze(selected, version)}
          className="rounded bg-emerald-600 px-4 py-2 text-sm font-medium text-white hover:bg-emerald-500 disabled:cursor-not-allowed disabled:opacity-50"
        >
          {busy ? "Analyzing…" : "Analyze"}
        </button>

        {loadError && (
          <span className="text-xs text-red-400">⚠ {loadError}</span>
        )}
      </div>
    </div>
  );
}

function KindBadge({ kind }: { kind: Kind }) {
  const cls =
    kind === "component"
      ? "bg-blue-500/20 text-blue-300"
      : kind === "dataformat"
      ? "bg-purple-500/20 text-purple-300"
      : "bg-amber-500/20 text-amber-300";
  const label =
    kind === "component" ? "comp" : kind === "dataformat" ? "df" : "lang";
  return (
    <span className={`rounded px-1.5 py-0.5 text-[10px] font-medium ${cls}`}>
      {label}
    </span>
  );
}
