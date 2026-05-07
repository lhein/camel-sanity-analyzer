import { useEffect, useMemo, useState } from "react";
import type { AnalysisResult, HealthInfo, Progress } from "./types";
import { analyze, fetchHealthInfo } from "./api";
import { Selector } from "./components/Selector";
import { Dashboard } from "./components/Dashboard";
import { DependencyTable } from "./components/DependencyTable";
import { DetailDrawer } from "./components/DetailDrawer";
import { RisksList } from "./components/RisksList";
import { TreeGraph } from "./components/TreeGraph";
import { ProgressBar } from "./components/ProgressBar";

type Tab = "table" | "tree" | "risks";

export default function App() {
  const [result, setResult] = useState<AnalysisResult | null>(null);
  const [progress, setProgress] = useState<Progress | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [selected, setSelected] = useState<HealthInfo | null>(null);
  const [tab, setTab] = useState<Tab>("table");
  const [tokenWarning, setTokenWarning] = useState(false);

  useEffect(() => {
    fetchHealthInfo()
      .then((h) => setTokenWarning(!h.githubTokenConfigured))
      .catch(() => setTokenWarning(true));
  }, []);

  const rows = useMemo(
    () => (result ? Object.values(result.healthByGav) : []),
    [result],
  );

  function startAnalyze(artifactId: string, version: string, includeTransitiveTest: boolean) {
    setBusy(true);
    setError(null);
    setResult(null);
    setProgress({
      phase: "RESOLVING",
      completed: 0,
      total: 0,
      message: "Starting…",
    });
    analyze(
      { groupId: "org.apache.camel", artifactId, version },
      { includeTransitiveTest },
      {
        onProgress: setProgress,
        onResult: (r) => {
          setResult(r);
          setBusy(false);
        },
        onError: (msg) => {
          setError(msg);
          setBusy(false);
        },
      },
    );
  }

  return (
    <div className="min-h-full">
      <header className="border-b border-slate-800 bg-slate-950/80 backdrop-blur sticky top-0 z-30">
        <div className="mx-auto max-w-screen-2xl px-6 py-4">
          <div className="mb-3 flex items-baseline justify-between gap-4">
            <div>
              <h1 className="text-lg font-semibold text-slate-100">
                Camel Sanity Analyzer
              </h1>
              <p className="text-xs text-slate-500">
                Health & freshness check for Apache Camel components
              </p>
            </div>
            {tokenWarning && (
              <span className="rounded border border-amber-500/40 bg-amber-500/10 px-2 py-1 text-xs text-amber-200">
                ⚠ No GitHub token configured — rate-limited to 60 calls/h
              </span>
            )}
          </div>
          <Selector onAnalyze={startAnalyze} busy={busy} />
        </div>
      </header>

      <main className="mx-auto max-w-screen-2xl space-y-6 px-6 py-6">
        {error && (
          <div className="rounded border border-red-500/40 bg-red-500/10 p-4 text-sm text-red-200">
            ⚠ {error}
          </div>
        )}

        {busy && progress && <ProgressBar progress={progress} />}

        {result && (
          <>
            <Dashboard summary={result.summary} />

            <div className="flex items-center gap-1 border-b border-slate-800">
              <TabButton id="table" current={tab} onClick={setTab}>
                Dependencies
              </TabButton>
              <TabButton id="tree" current={tab} onClick={setTab}>
                Tree
              </TabButton>
              <TabButton id="risks" current={tab} onClick={setTab}>
                Risks
              </TabButton>
              <span className="ml-auto text-xs text-slate-500">
                Analyzed {new Date(result.analyzedAt).toLocaleString()}
              </span>
            </div>

            {tab === "table" && (
              <DependencyTable rows={rows} onSelect={setSelected} />
            )}
            {tab === "tree" && (
              <TreeGraph
                tree={result.tree}
                healthByGav={result.healthByGav}
                onNodeClick={setSelected}
              />
            )}
            {tab === "risks" && (
              <RisksList rows={rows} onSelect={setSelected} />
            )}
          </>
        )}

        {!result && !busy && !error && (
          <div className="rounded-lg border border-dashed border-slate-800 bg-slate-900/40 p-12 text-center text-slate-500">
            Select a Camel component and a version, then click <em>Analyze</em>.
          </div>
        )}
      </main>

      {selected && (
        <DetailDrawer info={selected} onClose={() => setSelected(null)} />
      )}
    </div>
  );
}

function TabButton({
  id,
  current,
  onClick,
  children,
}: {
  id: Tab;
  current: Tab;
  onClick: (t: Tab) => void;
  children: React.ReactNode;
}) {
  const active = id === current;
  return (
    <button
      onClick={() => onClick(id)}
      className={`-mb-px rounded-t border-b-2 px-4 py-2 text-sm font-medium transition ${
        active
          ? "border-emerald-500 text-emerald-300"
          : "border-transparent text-slate-400 hover:text-slate-200"
      }`}
    >
      {children}
    </button>
  );
}
