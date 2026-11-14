import type { QrGradient, QrStyle } from '../api/client'

/**
 * Inverting a code's colours: dark modules on a light background become light on dark, and back.
 *
 * <p>Only the code's own colours change: `foreground`, `background`, `finderColor` and the
 * gradient. The logo plate, the frame and any uploaded image are left as they are; the server
 * washes a background image and clamps corner artwork against whichever background it is given.
 *
 * <p>The foreground and background swap. Every other ink is reflected in brightness across the
 * midpoint of the two, keeping its hue and saturation: an ink's luminance `L` becomes
 * `Lfg + Lbg - L`. Its distance from the background is unchanged, and it lands on the same side as
 * the new modules, so a style the server accepted stays acceptable, and inverting twice gives the
 * original colours back (to within rounding). The panel also remembers its last inversion, so an
 * immediate second click restores the exact colours.
 *
 * <p>Three cases fall back:
 * - a corner colour that equals the foreground follows it, becoming the new foreground;
 * - a corner colour whose reflection would not stand out from the new background (too close, or
 *   on the wrong side, which only happens when it was already hard to read or the reflection is
 *   clipped at black or white) becomes the new foreground;
 * - a gradient whose reflected stops would fail the server's check (see `Gradient.java`) is
 *   turned off, and the caller is told so.
 */

/** The minimum luminance distance the server asks of an ink, on a 0–255 scale. */
export const MIN_CONTRAST = 96

/** The colours an inversion touches. */
export interface CodeColours {
  foreground: string
  background: string
  finderColor: string
  gradient: QrGradient | null
}

export interface Inversion {
  colours: CodeColours
  /** Set when part of the style could not be inverted and was changed in another way. */
  notice: string | null
}

type Rgb = [number, number, number]

function rgb(hex: string): Rgb {
  const value = Number.parseInt(hex.slice(1), 16)
  return [(value >> 16) & 0xff, (value >> 8) & 0xff, value & 0xff]
}

function hex([r, g, b]: Rgb): string {
  return '#' + [r, g, b].map((c) => Math.round(c).toString(16).padStart(2, '0')).join('')
}

/** The server's luminance (`Images.luminance`), on a 0–255 scale. */
export function luminance(colour: string): number {
  const [r, g, b] = rgb(colour)
  return lum([r, g, b])
}

function lum([r, g, b]: Rgb): number {
  return 0.299 * r + 0.587 * g + 0.114 * b
}

/** True when the modules are lighter than the background, as the scan check decides it. */
export function isInverted(style: Pick<QrStyle, 'foreground' | 'background' | 'gradient'>): boolean {
  const ink = style.gradient ? style.gradient.start : style.foreground
  return luminance(ink) > luminance(style.background)
}

function toHsl([r, g, b]: Rgb): [number, number, number] {
  const red = r / 255
  const green = g / 255
  const blue = b / 255
  const max = Math.max(red, green, blue)
  const min = Math.min(red, green, blue)
  const l = (max + min) / 2
  if (max === min) {
    return [0, 0, l]
  }
  const d = max - min
  const s = l > 0.5 ? d / (2 - max - min) : d / (max + min)
  let h: number
  if (max === red) {
    h = (green - blue) / d + (green < blue ? 6 : 0)
  } else if (max === green) {
    h = (blue - red) / d + 2
  } else {
    h = (red - green) / d + 4
  }
  return [h / 6, s, l]
}

function fromHsl(h: number, s: number, l: number): Rgb {
  if (s === 0) {
    return [l * 255, l * 255, l * 255]
  }
  const q = l < 0.5 ? l * (1 + s) : l + s - l * s
  const p = 2 * l - q
  const channel = (t: number): number => {
    const u = t < 0 ? t + 1 : t > 1 ? t - 1 : t
    if (u < 1 / 6) return p + (q - p) * 6 * u
    if (u < 1 / 2) return q
    if (u < 2 / 3) return p + (q - p) * (2 / 3 - u) * 6
    return p
  }
  return [channel(h + 1 / 3) * 255, channel(h) * 255, channel(h - 1 / 3) * 255]
}

/**
 * The colour with the same hue and saturation whose luminance is as close to `target` as 8-bit
 * channels allow. Luminance rises with HSL lightness at a fixed hue and saturation, so a bisection
 * finds it.
 */
function withLuminance(colour: string, target: number): string {
  const [h, s] = toHsl(rgb(colour))
  const goal = Math.max(0, Math.min(255, target))
  let low = 0
  let high = 1
  for (let i = 0; i < 40; i++) {
    const mid = (low + high) / 2
    if (lum(fromHsl(h, s, mid)) < goal) {
      low = mid
    } else {
      high = mid
    }
  }
  return hex(fromHsl(h, s, (low + high) / 2))
}

function same(a: string, b: string): boolean {
  return a.toLowerCase() === b.toLowerCase()
}

/** True when `ink` is far enough from `background`, on the same side as `modules`. */
function readable(ink: string, background: string, modules: string): boolean {
  const bg = luminance(background)
  const distance = luminance(ink) - bg
  const side = luminance(modules) - bg
  return Math.abs(distance) >= MIN_CONTRAST && Math.sign(distance) === Math.sign(side)
}

/** The server's gradient rule: both stops far from the background and on the same side of it. */
function validGradient(gradient: QrGradient, background: string): boolean {
  const bg = luminance(background)
  const start = luminance(gradient.start) - bg
  const end = luminance(gradient.end) - bg
  return (
    Math.abs(start) >= MIN_CONTRAST && Math.abs(end) >= MIN_CONTRAST && start < 0 === end < 0
  )
}

/** Inverts the code's colours. Pure: the input is not modified. */
export function invertColours(colours: CodeColours): Inversion {
  const { foreground, background, finderColor, gradient } = colours
  const pivot = luminance(foreground) + luminance(background)
  const reflect = (ink: string): string => {
    if (same(ink, foreground)) return background
    if (same(ink, background)) return foreground
    return withLuminance(ink, pivot - luminance(ink))
  }

  const newForeground = background
  const newBackground = foreground

  let newFinder = reflect(finderColor)
  if (!same(newFinder, newForeground) && !readable(newFinder, newBackground, newForeground)) {
    newFinder = newForeground
  }

  let newGradient: QrGradient | null = null
  let notice: string | null = null
  if (gradient) {
    const reflected = { ...gradient, start: reflect(gradient.start), end: reflect(gradient.end) }
    if (validGradient(reflected, newBackground)) {
      newGradient = reflected
    } else {
      notice =
        'The gradient could not be inverted against this background, so it was turned off.'
    }
  }

  return {
    colours: {
      foreground: newForeground,
      background: newBackground,
      finderColor: newFinder,
      gradient: newGradient,
    },
    notice,
  }
}

function snapshot(style: CodeColours): CodeColours {
  return {
    foreground: style.foreground,
    background: style.background,
    finderColor: style.finderColor,
    gradient: style.gradient ? { ...style.gradient } : null,
  }
}

function equal(a: CodeColours, b: CodeColours): boolean {
  return JSON.stringify(snapshot(a)) === JSON.stringify(snapshot(b))
}

/**
 * Inverts a style in place, remembering the last inversion: inverting a style that is still
 * exactly what the last call produced restores the colours it started from, so the action is a
 * true toggle even when a fallback changed a colour.
 */
export function createInverter(): (style: QrStyle) => string | null {
  let last: { before: CodeColours; after: CodeColours } | null = null
  return (style) => {
    const before = snapshot(style)
    let result: Inversion
    if (last && equal(before, last.after)) {
      result = { colours: snapshot(last.before), notice: null }
      last = null
    } else {
      result = invertColours(before)
      last = { before, after: snapshot(result.colours) }
    }
    Object.assign(style, result.colours)
    return result.notice
  }
}
