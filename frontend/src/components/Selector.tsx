import { useEffect, useMemo, useRef, useState } from "react";
import type { VersionInfo } from "../types";
import { fetchComponents, fetchVersions } from "../api";

interface Props {
  onAnalyze: (artifactId: string, version: string) => void;
  busy: boolean;
}

export function Selector({ onAnalyze, busy }: Props) {
  const [components, setComponents] = useState<string[]>([]);
  const [filter, setFilter] = useState("");
  const [open, setOpen] = useState(false);
  const [selected, setSelected] = useState<string>("");
  const [versions, setVersions] = useState<VersionInfo[]>([]);
  const [version, setVersion] = useState<string>("");
  const [loadError, setLoadError] = useState<string | null>(null);
  const dropdownRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    fetchComponents()
      .then(setComponents)
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

  const filtered = useMemo(() => {
    const q = filter.toLowerCase().trim();
    if (!q) return components;
    return components.filter((c) => c.toLowerCase().includes(q));
  }, [components, filter]);

  const canAnalyze = !!selected && !!version && !busy;

  return (
    <div className="flex flex-wrap items-end gap-3">
      <div className="flex flex-col gap-1 grow min-w-[260px]" ref={dropdownRef}>
        <label className="text-xs uppercase tracking-wide text-slate-400">
          Camel Component
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
              {filtered.map((c) => (
                <li
                  key={c}
                  className="cursor-pointer px-3 py-1.5 text-sm hover:bg-slate-700"
                  onClick={() => {
                    setSelected(c);
                    setFilter("");
                    setOpen(false);
                  }}
                >
                  {c}
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
  );
}
