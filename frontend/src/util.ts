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

export function scopeColor(scope: string): string {
  switch (scope) {
    case "compile":
      return "bg-slate-700/60 text-slate-300";
    case "runtime":
      return "bg-violet-500/20 text-violet-300";
    case "test":
      return "bg-sky-500/20 text-sky-300";
    case "provided":
      return "bg-amber-500/20 text-amber-300";
    case "system":
      return "bg-rose-500/20 text-rose-300";
    default:
      return "bg-slate-700/60 text-slate-300";
  }
}

export function updateLevelColor(level: string): string {
  switch (level) {
    case "PATCH":
      return "text-emerald-400";
    case "MINOR":
      return "text-amber-400";
    case "MAJOR":
      return "text-red-400";
    default:
      return "text-slate-500";
  }
}

export function licenseCategoryColor(cat: string): string {
  switch (cat) {
    case "PERMISSIVE":
      return "bg-emerald-500/15 text-emerald-300 border-emerald-500/40";
    case "WEAK_COPYLEFT":
      return "bg-amber-500/15 text-amber-300 border-amber-500/40";
    case "COPYLEFT":
      return "bg-red-500/15 text-red-300 border-red-500/40";
    case "PUBLIC_DOMAIN":
      return "bg-sky-500/15 text-sky-300 border-sky-500/40";
    case "PROPRIETARY":
      return "bg-violet-500/15 text-violet-300 border-violet-500/40";
    default:
      return "bg-slate-700/40 text-slate-400 border-slate-600/40";
  }
}

export function licenseCategoryLabel(cat: string): string {
  switch (cat) {
    case "PERMISSIVE":
      return "Permissive";
    case "WEAK_COPYLEFT":
      return "Weak copyleft";
    case "COPYLEFT":
      return "Copyleft";
    case "PUBLIC_DOMAIN":
      return "Public domain";
    case "PROPRIETARY":
      return "Proprietary";
    default:
      return "Unknown";
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
