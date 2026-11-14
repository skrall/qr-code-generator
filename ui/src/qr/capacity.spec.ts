import { describe, expect, test } from 'vitest'
import type { ErrorCorrection } from '../api/client'
import {
  characterCountBits,
  chooseMode,
  DATA_CODEWORDS,
  EC_LEVELS,
  measure,
  segmentDataBits,
  utf8Length,
  type QrMode,
} from './capacity'
// Raw rather than parsed by Vite: its JSON loader rejects the lone surrogates some cases contain,
// which JSON.parse (like Java) accepts.
import fixtureText from './capacity.fixture.json?raw'

interface FixtureCase {
  name: string
  prefix: string
  repeat: string
  times: number
  mode: QrMode
  bytes: number
  versions: Record<ErrorCorrection, number | null>
}

const fixture = JSON.parse(fixtureText) as {
  dataCodewords: Record<ErrorCorrection, number[]>
  cases: FixtureCase[]
}

describe('parity with ZXing (capacity.fixture.json, written by CapacityParityTest)', () => {
  test('uses the same data codeword table', () => {
    expect(DATA_CODEWORDS).toEqual(fixture.dataCodewords)
  })

  test('covers every mode, the version edges and too-long payloads', () => {
    const modes = new Set(fixture.cases.map((item) => item.mode))
    expect([...modes].sort()).toEqual(['ALPHANUMERIC', 'BYTE', 'NUMERIC'])
    const versions = fixture.cases.flatMap((item) => Object.values(item.versions))
    expect(versions).toContain(null)
    for (const edge of [9, 10, 26, 27, 40]) {
      expect(versions).toContain(edge)
    }
  })

  test.each(fixture.cases.map((item) => [item.name, item] as const))('%s', (_, item) => {
    const payload = item.prefix + item.repeat.repeat(item.times)
    expect(chooseMode(payload)).toBe(item.mode)
    expect(utf8Length(payload)).toBe(item.bytes)
    for (const level of EC_LEVELS) {
      expect({ level, version: measure(payload, level).version }).toEqual({
        level,
        version: item.versions[level],
      })
    }
  })
})

describe('chooseMode', () => {
  test('is numeric only when every character is a digit', () => {
    expect(chooseMode('0123')).toBe('NUMERIC')
    expect(chooseMode('01 23')).toBe('ALPHANUMERIC')
  })

  test('is alphanumeric for upper case and the nine symbols', () => {
    expect(chooseMode('HTTPS://EXAMPLE.COM/A-B')).toBe('ALPHANUMERIC')
    expect(chooseMode(' $%*+-./:')).toBe('ALPHANUMERIC')
  })

  test('falls back to byte mode for anything else', () => {
    expect(chooseMode('https://example.com')).toBe('BYTE')
    expect(chooseMode('ABC?')).toBe('BYTE')
    expect(chooseMode('É')).toBe('BYTE')
    expect(chooseMode('')).toBe('BYTE')
  })
})

describe('utf8Length', () => {
  test('counts UTF-8 bytes, not characters', () => {
    expect(utf8Length('abc')).toBe(3)
    expect(utf8Length('é')).toBe(2)
    expect(utf8Length('中')).toBe(3)
    expect(utf8Length('😀')).toBe(4)
    expect(utf8Length('😀')).toBe(new TextEncoder().encode('😀').length)
  })

  test("counts a lone surrogate as Java's one-byte replacement, unlike TextEncoder", () => {
    expect(utf8Length('\uD800')).toBe(1)
    expect(utf8Length('a\uDC00b')).toBe(3)
    expect(new TextEncoder().encode('\uD800').length).toBe(3)
  })
})

describe('bit counting', () => {
  test('packs digits in threes and letters in pairs', () => {
    expect(segmentDataBits('123', 'NUMERIC')).toBe(10)
    expect(segmentDataBits('1234', 'NUMERIC')).toBe(14)
    expect(segmentDataBits('12345', 'NUMERIC')).toBe(17)
    expect(segmentDataBits('AB', 'ALPHANUMERIC')).toBe(11)
    expect(segmentDataBits('ABC', 'ALPHANUMERIC')).toBe(17)
    expect(segmentDataBits('é', 'BYTE')).toBe(16)
  })

  test('widens the character count at versions 10 and 27', () => {
    expect([1, 9, 10, 26, 27, 40].map((v) => characterCountBits('BYTE', v))).toEqual([
      8, 8, 16, 16, 16, 16,
    ])
    expect([9, 10, 27].map((v) => characterCountBits('NUMERIC', v))).toEqual([10, 12, 14])
    expect([9, 10, 27].map((v) => characterCountBits('ALPHANUMERIC', v))).toEqual([9, 11, 13])
  })
})

describe('measure', () => {
  test('reports the grid and how full the version is', () => {
    // 12 (ECI) + 4 (mode) + 8 (count) + 19 × 8 bits = 22 codewords: version 2-M holds 28.
    expect(measure('https://example.com', 'MEDIUM')).toEqual({
      mode: 'BYTE',
      bytes: 19,
      version: 2,
      modules: 25,
      usedCodewords: 22,
      capacityCodewords: 28,
    })
  })

  test('spends a byte on the UTF-8 ECI header, so byte payloads hold one byte less', () => {
    // The standard's 2331 bytes at 40-M assume no ECI; ZXing writes one, leaving 2330.
    expect(measure('a'.repeat(2330), 'MEDIUM').version).toBe(40)
    expect(measure('a'.repeat(2331), 'MEDIUM').version).toBeNull()
    // Numeric and alphanumeric carry no ECI and reach the standard's figures.
    expect(measure('1'.repeat(7089), 'LOW').version).toBe(40)
    expect(measure('A'.repeat(4296), 'LOW').version).toBe(40)
  })

  test('steps up a version at the first byte over capacity', () => {
    // Version 1-L: 19 codewords, 3 of them header, so 16 payload bytes.
    expect(measure('a'.repeat(16), 'LOW').version).toBe(1)
    expect(measure('a'.repeat(17), 'LOW').version).toBe(2)
  })

  test('reports how far over version 40 a too-long payload is', () => {
    const size = measure('中'.repeat(500), 'HIGH')
    expect(size.version).toBeNull()
    expect(size.modules).toBeNull()
    expect(size.capacityCodewords).toBe(1276)
    expect(size.usedCodewords).toBe(1504)
  })
})
