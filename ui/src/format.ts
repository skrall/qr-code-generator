/** Formats an ISO timestamp for display, keeping the raw value if it cannot be parsed. */
export function formatDate(iso: string | null): string {
  if (!iso) {
    return '—'
  }
  const date = new Date(iso)
  return Number.isNaN(date.getTime()) ? iso : date.toLocaleString()
}
