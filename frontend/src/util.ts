import type { HealthStatus } from "./types";

export function statusColor(s: HealthStatus): string {
  switch (s) {
    case "HEALTHY":
      return "bg-status-healthy";
    case "OUTDATED":
      return "bg-status-outdated";
    case "WARNING":
      return "bg-status-warning";
    case "CRITICAL":
      return "bg-status-critical";
    case "UNKNOWN":
    default:
      return "bg-status-unknown";
  }
}

export function statusTextColor(s: HealthStatus): string {
  switch (s) {
    case "HEALTHY":
      return "text-emerald-400";
    case "OUTDATED":
      return "text-amber-400";
    case "WARNING":
      return "text-yellow-400";
    case "CRITICAL":
      return "text-red-400";
    case "UNKNOWN":
    default:
      return "text-slate-400";
  }
}

export function statusBorderColor(s: HealthStatus): string {
  switch (s) {
    case "HEALTHY":
      return "border-emerald-500/40";
    case "OUTDATED":
      return "border-amber-500/40";
    case "WARNING":
      return "border-yellow-500/40";
    case "CRITICAL":
      return "border-red-500/50";
    case "UNKNOWN":
    default:
      return "border-slate-600/50";
  }
}

export function formatDate(d: string | null): string {
  if (!d) return "—";
  try {
    const date = new Date(d);
    return date.toLocaleDateString(undefined, {
      year: "numeric",
      month: "short",
      day: "numeric",
    });
  } catch {
    return d;
  }
}

export function timeAgo(d: string | null): string {
  if (!d) return "—";
  try {
    const date = new Date(d);
    const diffMs = Date.now() - date.getTime();
    const days = Math.floor(diffMs / (1000 * 60 * 60 * 24));
    if (days < 0) return formatDate(d);
    if (days < 1) return "today";
    if (days < 30) return `${days}d ago`;
    const months = Math.floor(days / 30);
    if (months < 12) return `${months}mo ago`;
    const years = Math.floor(days / 365);
    return `${years}y ago`;
  } catch {
    return d;
  }
}
