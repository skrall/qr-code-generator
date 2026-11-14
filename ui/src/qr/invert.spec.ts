import { describe, expect, test } from 'vitest'
import type { QrGradient } from '../api/client'
import { invertColours, isInverted, luminance, MIN_CONTRAST, type CodeColours } from './invert'

function colours(overrides: Partial<CodeColours> = {}): CodeColours {
  return {
    foreground: '#111827',
    background: '#ffffff',
    finderColor: '#3158c4',
    gradient: null,
    ...overrides,
  }
}

/** Largest per-channel difference between two `#rrggbb` colours. */
function distance(a: string, b: string): number {
  const channels = (hex: string) => [1, 3, 5].map((i) => Number.parseInt(hex.slice(i, i + 2), 16))
  const [x, y] = [channels(a), channels(b)]
  return Math.max(...x.map((c, i) => Math.abs(c - (y[i] ?? 0))))
}

function contrast(ink: string, background: string): number {
  return Math.abs(luminance(ink) - luminance(background))
}

describe('invertColours', () => {
  test('swaps the foreground and background', () => {
    const { colours: inverted } = invertColours(colours())

    expect(inverted.foreground).toBe('#ffffff')
    expect(inverted.background).toBe('#111827')
    expect(isInverted(inverted)).toBe(true)
  })

  test('a corner colour that matches the foreground follows it', () => {
    const { colours: inverted } = invertColours(colours({ finderColor: '#111827' }))

    expect(inverted.finderColor).toBe('#ffffff')
  })

  test('an accent corner colour keeps its hue and becomes light on the dark background', () => {
    const { colours: inverted } = invertColours(colours())

    // #3158c4 is a mid blue; its reflection is a pale blue.
    expect(inverted.finderColor).toBe('#adbeeb')
    expect(luminance(inverted.finderColor)).toBeGreaterThan(luminance(inverted.background))
    // Reflection keeps the distance from the background it had before.
    expect(contrast(inverted.finderColor, inverted.background)).toBeCloseTo(
      contrast('#3158c4', '#ffffff'),
      0,
    )
  })

  test('a corner colour that would not stand out becomes the new foreground', () => {
    // #dddddd is barely darker than white: its reflection would sit next to the new background.
    const { colours: inverted } = invertColours(colours({ finderColor: '#dddddd' }))

    expect(inverted.finderColor).toBe('#ffffff')
  })

  test('maps both gradient stops so the server still accepts them', () => {
    const gradient: QrGradient = {
      type: 'LINEAR',
      start: '#7c3aed',
      end: '#0f766e',
      angle: 135,
      finders: true,
    }
    const { colours: inverted, notice } = invertColours(
      colours({ foreground: '#101820', finderColor: '#101820', gradient }),
    )

    expect(notice).toBeNull()
    expect(inverted.gradient).not.toBeNull()
    const { start, end, type, angle, finders } = inverted.gradient as QrGradient
    expect({ type, angle, finders }).toEqual({ type: 'LINEAR', angle: 135, finders: true })
    for (const stop of [start, end]) {
      expect(luminance(stop)).toBeGreaterThan(luminance(inverted.background))
      expect(contrast(stop, inverted.background)).toBeGreaterThanOrEqual(MIN_CONTRAST)
    }
    expect(isInverted(inverted)).toBe(true)
  })

  test('a gradient seeded from the foreground swaps that stop exactly', () => {
    const gradient: QrGradient = {
      type: 'RADIAL',
      start: '#111827',
      end: '#3158c4',
      angle: 135,
      finders: false,
    }
    const { colours: inverted } = invertColours(colours({ gradient }))

    expect(inverted.gradient?.start).toBe('#ffffff')
    expect(inverted.gradient?.end).toBe(inverted.finderColor)
  })

  test('turns off a gradient it cannot invert, and says so', () => {
    // A hidden foreground the same as the background leaves nothing to invert against.
    const gradient: QrGradient = {
      type: 'LINEAR',
      start: '#000000',
      end: '#202020',
      angle: 90,
      finders: false,
    }
    const { colours: inverted, notice } = invertColours(
      colours({ foreground: '#ffffff', gradient }),
    )

    expect(inverted.gradient).toBeNull()
    expect(notice).toContain('turned off')
  })

  test('inverting twice gives the original colours back', () => {
    const cases: CodeColours[] = [
      colours(),
      colours({ finderColor: '#111827' }),
      colours({ foreground: '#e8eefc', background: '#0f172a', finderColor: '#8ab4ff' }),
      colours({ foreground: '#0f172a', finderColor: '#0f766e' }),
      colours({
        foreground: '#101820',
        finderColor: '#101820',
        gradient: { type: 'LINEAR', start: '#7c3aed', end: '#0f766e', angle: 135, finders: true },
      }),
    ]
    for (const original of cases) {
      const twice = invertColours(invertColours(original).colours).colours

      expect(twice.foreground).toBe(original.foreground)
      expect(twice.background).toBe(original.background)
      // Accents pass through 8-bit rounding twice, so allow a step either way.
      expect(distance(twice.finderColor, original.finderColor)).toBeLessThanOrEqual(1)
      if (original.gradient) {
        expect(distance(twice.gradient?.start ?? '', original.gradient.start)).toBeLessThanOrEqual(1)
        expect(distance(twice.gradient?.end ?? '', original.gradient.end)).toBeLessThanOrEqual(1)
      }
    }
  })

  test('does not modify its input', () => {
    const original = colours()
    invertColours(original)

    expect(original).toEqual(colours())
  })
})
