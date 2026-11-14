/**
 * Print size: how large the downloaded image comes out on paper at a resolution.
 *
 * The server takes the same `dpi` (72–1200): it sizes a PDF's page and is written into a PNG's
 * pHYs chunk. The pixels never change, only how many of them make an inch.
 */

export const DPI_MIN = 72
export const DPI_MAX = 1200
export const DPI_DEFAULT = 300

const MM_PER_INCH = 25.4

/** A typed resolution brought into range; anything unreadable falls back to the default. */
export function clampDpi(value: number): number {
  if (!Number.isFinite(value)) {
    return DPI_DEFAULT
  }
  return Math.min(DPI_MAX, Math.max(DPI_MIN, Math.round(value)))
}

/** Pixels to millimetres at a resolution, to one decimal, without a trailing ".0". */
export function millimetres(pixels: number, dpi: number): string {
  const mm = Math.round(((pixels / dpi) * MM_PER_INCH) * 10) / 10
  return Number.isInteger(mm) ? String(mm) : mm.toFixed(1)
}

/**
 * The sentence under the resolution control, e.g. "Prints at 54 × 54 mm at 300 DPI". With a
 * frame's bleed, the PDF is trimmed to a smaller page, which is the size that matters on paper.
 *
 * @param width  the image's width in pixels, frame and bleed included
 * @param height the image's height in pixels
 * @param bleed  the bleed on each side in pixels, 0 for none
 */
export function printSummary(width: number, height: number, dpi: number, bleed = 0): string {
  const size = `${millimetres(width, dpi)} × ${millimetres(height, dpi)} mm`
  if (bleed <= 0) {
    return `Prints at ${size} at ${dpi} DPI`
  }
  const trimmed = `${millimetres(width - 2 * bleed, dpi)} × ${millimetres(height - 2 * bleed, dpi)} mm`
  return `Prints at ${trimmed} at ${dpi} DPI, ${size} with bleed`
}

/** The bleed in pixels the server lays out for a frame: a percentage of the code's side. */
export function bleedPixels(size: number, bleedPercent: number | undefined | null): number {
  return Math.round((size * (bleedPercent ?? 0)) / 100)
}
