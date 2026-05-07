import type { HealthStatus } from "../types";
import { statusColor } from "../util";

export function StatusPill({ status }: { status: HealthStatus }) {
  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-full px-2 py-0.5 text-xs font-medium text-slate-950 ${statusColor(
        status,
      )}`}
    >
      {status}
    </span>
  );
}

export function StatusDot({ status }: { status: HealthStatus }) {
  return (
    <span
      className={`inline-block h-2 w-2 rounded-full ${statusColor(status)}`}
    />
  );
}
