export type HealthStatus =
  | "HEALTHY"
  | "OUTDATED"
  | "WARNING"
  | "CRITICAL"
  | "UNKNOWN";

export interface Coordinate {
  groupId: string;
  artifactId: string;
  version: string;
}

export interface Vulnerability {
  id: string;
  summary: string | null;
  severity: string | null;
  url: string;
}

export interface HealthInfo {
  coordinate: Coordinate;
  latestVersion: string | null;
  releaseDate: string | null;
  latestReleaseDate: string | null;
  majorVersionsBehind: number | null;
  organization: string | null;
  website: string | null;
  repoUrl: string | null;
  lastCommit: string | null;
  stars: number | null;
  contributors: number | null;
  openIssues: number | null;
  archived: boolean | null;
  lastGithubRelease: string | null;
  license: string | null;
  dependents: number | null;
  vulnerabilities: Vulnerability[];
  scorecardScore: number | null;
  healthScore: number;
  status: HealthStatus;
  reasons: string[];
}

export interface DependencyNode {
  coordinate: Coordinate;
  scope: string;
  optional: boolean;
  children: DependencyNode[];
}

export interface Summary {
  total: number;
  healthy: number;
  outdated: number;
  warning: number;
  critical: number;
  unknown: number;
  withVulnerabilities: number;
  totalVulnerabilities: number;
  archivedRepos: number;
}

export interface AnalysisResult {
  root: Coordinate;
  analyzedAt: string;
  tree: DependencyNode;
  healthByGav: Record<string, HealthInfo>;
  summary: Summary;
}

export interface Progress {
  phase: "RESOLVING" | "ENRICHING" | "DONE";
  completed: number;
  total: number;
  message: string;
}

export interface VersionInfo {
  version: string;
  released: string | null;
}

export type Kind = "component" | "dataformat" | "language";

export interface ArtifactEntry {
  artifactId: string;
  kinds: Kind[];
}

export interface ArtifactsResponse {
  components: string[];
  dataformats: string[];
  languages: string[];
  all: ArtifactEntry[];
}
