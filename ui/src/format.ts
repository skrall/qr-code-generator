/** Formats an ISO timestamp for display, keeping the raw value if it cannot be parsed. */
export function formatDate(iso: string | null): string {
  if (!iso) {
    return '—'
  }
  const date = new Date(iso)
  return Number.isNaN(date.getTime()) ? iso : date.toLocaleString()
}

/**
 * Converts an ISO timestamp to the local `YYYY-MM-DDTHH:mm` a datetime-local input expects.
 *
 * <p>The input has no timezone, so this uses the browser's local time. Sending the value back
 * through `new Date(...)` reverses the conversion.
 */
export function toDateTimeInput(iso: string | null): string {
  if (!iso) {
    return ''
  }
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) {
    return ''
  }
  const pad = (value: number): string => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}
