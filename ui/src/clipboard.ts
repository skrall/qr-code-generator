/**
 * Copies text to the clipboard, reporting whether it worked.
 *
 * <p>The Clipboard API is unavailable on an insecure origin, which is exactly how this tool is
 * usually reached on a LAN. Callers show "Copy" only as an offer, so a false result is a failed
 * copy rather than an error worth interrupting anyone over.
 */
export async function copyText(text: string): Promise<boolean> {
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(text)
      return true
    }
  } catch {
    // Denied permission or an insecure context; the caller falls back to selecting the text.
  }
  return false
}
