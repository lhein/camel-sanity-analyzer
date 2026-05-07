import type { HealthInfo } from "../types";
import { StatusPill } from "./StatusPill";
import { formatDate, timeAgo } from "../util";

interface Props {
  info: HealthInfo;
  onClose: () => void;
}

export function DetailDrawer({ info, onClose }: Props) {
  const c = info.coordinate;
  const mvnUrl = `https://central.sonatype.com/artifact/${c.groupId}/${c.artifactId}/${c.version}`;
  return (
    <div className="fixed inset-0 z-40 flex">
      <div
        className="grow bg-black/40 backdrop-blur-sm"
        onClick={onClose}
        aria-hidden
      />
      <div className="w-full max-w-xl overflow-y-auto bg-slate-900 border-l border-slate-800 p-6 shadow-2xl">
        <div className="mb-4 flex items-start justify-between gap-3">
          <div>
            <div className="text-xs uppercase tracking-wide text-slate-400">
              {c.groupId}
            </div>
            <h2 className="text-xl font-semibold text-slate-50">
              {c.artifactId}
            </h2>
            <div className="mt-1 font-mono text-sm text-slate-300">
              {c.version}
            </div>
          </div>
          <button
            onClick={onClose}
            className="rounded p-1 text-slate-400 hover:bg-slate-800 hover:text-slate-200"
            aria-label="Close"
          >
            ✕
          </button>
        </div>

        <div className="mb-4 flex items-center gap-3">
          <StatusPill status={info.status} />
          <span className="text-sm text-slate-400">
            Score:{" "}
            <span className="font-medium text-slate-200">
              {info.healthScore}/100
            </span>
          </span>
        </div>

        {info.reasons.length > 0 && (
          <div className="mb-5 rounded border border-slate-800 bg-slate-950/50 p-3">
            <div className="mb-1 text-xs uppercase tracking-wide text-slate-400">
              Health signals
            </div>
            <ul className="list-disc pl-5 text-sm text-slate-200 space-y-0.5">
              {info.reasons.map((r) => (
                <li key={r}>{r}</li>
              ))}
            </ul>
          </div>
        )}

        <div className="grid grid-cols-2 gap-x-6 gap-y-3 text-sm">
          <Row label="Organization" value={info.organization} />
          <Row label="License" value={info.license} />
          <Row label="Latest version" value={info.latestVersion} />
          <Row label="Released (this version)" value={formatDate(info.releaseDate)} />
          <Row label="Latest released" value={formatDate(info.latestReleaseDate)} />
          <Row
            label="Last commit"
            value={info.lastCommit ? `${formatDate(info.lastCommit)} (${timeAgo(info.lastCommit)})` : "—"}
          />
          <Row
            label="Last GH release"
            value={info.lastGithubRelease ? `${formatDate(info.lastGithubRelease)} (${timeAgo(info.lastGithubRelease)})` : "—"}
          />
          <Row label="Stars" value={info.stars} />
          <Row label="Contributors" value={info.contributors} />
          <Row label="Open issues" value={info.openIssues} />
          <Row
            label="Archived"
            value={info.archived === null ? "—" : info.archived ? "Yes" : "No"}
            danger={!!info.archived}
          />
          <Row
            label="OpenSSF Scorecard"
            value={info.scorecardScore != null ? info.scorecardScore.toFixed(1) : "—"}
          />
        </div>

        <div className="mt-6 flex flex-wrap gap-2">
          {info.repoUrl && (
            <a
              href={info.repoUrl}
              target="_blank"
              rel="noopener noreferrer"
              className="rounded border border-slate-700 bg-slate-800 px-3 py-1.5 text-xs hover:bg-slate-700"
            >
              Source repo ↗
            </a>
          )}
          {info.website && info.website !== info.repoUrl && (
            <a
              href={info.website}
              target="_blank"
              rel="noopener noreferrer"
              className="rounded border border-slate-700 bg-slate-800 px-3 py-1.5 text-xs hover:bg-slate-700"
            >
              Website ↗
            </a>
          )}
          <a
            href={mvnUrl}
            target="_blank"
            rel="noopener noreferrer"
            className="rounded border border-slate-700 bg-slate-800 px-3 py-1.5 text-xs hover:bg-slate-700"
          >
            Maven Central ↗
          </a>
          <a
            href={`https://deps.dev/maven/${encodeURIComponent(
              c.groupId + ":" + c.artifactId,
            )}/${encodeURIComponent(c.version)}`}
            target="_blank"
            rel="noopener noreferrer"
            className="rounded border border-slate-700 bg-slate-800 px-3 py-1.5 text-xs hover:bg-slate-700"
          >
            deps.dev ↗
          </a>
        </div>

        {info.vulnerabilities.length > 0 && (
          <div className="mt-6">
            <h3 className="mb-2 text-sm font-semibold text-red-300">
              Vulnerabilities ({info.vulnerabilities.length})
            </h3>
            <ul className="space-y-2">
              {info.vulnerabilities.map((v) => (
                <li
                  key={v.id}
                  className="rounded border border-red-500/30 bg-red-500/5 p-2"
                >
                  <div className="flex items-center justify-between">
                    <a
                      href={v.url}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="font-mono text-xs text-red-300 hover:underline"
                    >
                      {v.id}
                    </a>
                    {v.severity && (
                      <span className="rounded bg-red-500/30 px-1.5 py-0.5 text-[11px] uppercase tracking-wide text-red-200">
                        {v.severity}
                      </span>
                    )}
                  </div>
                  {v.summary && (
                    <p className="mt-1 text-xs text-slate-300">{v.summary}</p>
                  )}
                </li>
              ))}
            </ul>
          </div>
        )}
      </div>
    </div>
  );
}

function Row({
  label,
  value,
  danger,
}: {
  label: string;
  value: string | number | null | undefined;
  danger?: boolean;
}) {
  const v = value === null || value === undefined || value === "" ? "—" : value;
  return (
    <div>
      <div className="text-xs uppercase tracking-wide text-slate-500">
        {label}
      </div>
      <div className={`mt-0.5 ${danger ? "text-red-400" : "text-slate-200"}`}>
        {v}
      </div>
    </div>
  );
}
