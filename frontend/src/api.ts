import type {
  AnalysisResult,
  ArtifactsResponse,
  Coordinate,
  Progress,
  VersionInfo,
} from "./types";

export async function fetchArtifacts(camelVersion?: string): Promise<ArtifactsResponse> {
  const url = camelVersion
    ? `/api/artifacts?camelVersion=${encodeURIComponent(camelVersion)}`
    : "/api/artifacts";
  const r = await fetch(url);
  if (!r.ok) throw new Error("Failed to load artifacts");
  return r.json();
}

export async function fetchCamelVersions(): Promise<string[]> {
  const r = await fetch("/api/camel-versions");
  if (!r.ok) throw new Error("Failed to load Camel versions");
  return r.json();
}

export async function fetchVersions(artifactId: string): Promise<VersionInfo[]> {
  const r = await fetch(`/api/components/${artifactId}/versions`);
  if (!r.ok) throw new Error("Failed to load versions");
  return r.json();
}

export async function fetchHealthInfo(): Promise<{
  status: string;
  githubTokenConfigured: boolean;
}> {
  const r = await fetch("/api/health");
  return r.json();
}

export interface AnalyzeCallbacks {
  onProgress: (p: Progress) => void;
  onResult: (r: AnalysisResult) => void;
  onError: (msg: string) => void;
}

export function analyze(
  coord: Coordinate,
  cb: AnalyzeCallbacks,
): { close: () => void } {
  const url = `/api/analyze?artifactId=${encodeURIComponent(
    coord.artifactId,
  )}&version=${encodeURIComponent(coord.version)}&groupId=${encodeURIComponent(
    coord.groupId,
  )}`;
  const es = new EventSource(url);
  es.addEventListener("progress", (ev) => {
    try {
      cb.onProgress(JSON.parse((ev as MessageEvent).data));
    } catch {
      /* ignore */
    }
  });
  es.addEventListener("result", (ev) => {
    try {
      cb.onResult(JSON.parse((ev as MessageEvent).data));
    } catch (e) {
      cb.onError(String(e));
    } finally {
      es.close();
    }
  });
  es.addEventListener("error", (ev) => {
    try {
      const d = (ev as MessageEvent).data;
      if (d) {
        const parsed = JSON.parse(d);
        cb.onError(parsed.message ?? "Unknown error");
      }
    } catch {
      /* ignore */
    }
  });
  es.onerror = () => {
    // Browser fires this on any disconnection. If we already got a result,
    // it's expected. Otherwise surface as error.
    es.close();
  };
  return { close: () => es.close() };
}
