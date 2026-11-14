import { describe, expect, test } from 'vitest'
import type { QrLogo } from '../api/client'
import {
  DEFAULT_STYLE,
  applyStyle,
  defaultStyle,
  designFile,
  designFileName,
  parseDesignFile,
} from './style'

const logo: QrLogo = {
  dataUrl: 'data:image/png;base64,AAAA',
  sizePercent: 20,
  paddingPercent: 8,
  background: '#ffffff',
  radiusPercent: 25,
}

describe('applyStyle', () => {
  test('replaces every field, so nothing from the previous design survives', () => {
    const editor = defaultStyle()
    editor.logo = { ...logo }
    editor.moduleShape = 'STAR'

    applyStyle(editor, { moduleShape: 'DOTS', foreground: '#000000' })

    expect(editor.moduleShape).toBe('DOTS')
    expect(editor.foreground).toBe('#000000')
    expect(editor.logo).toBeNull()
    // Absent fields take the defaults rather than staying as they were.
    expect(editor.size).toBe(DEFAULT_STYLE.size)
  })

  test('copies values, so editing after a load does not change the saved design', () => {
    const saved = { logo: { ...logo } }
    const editor = defaultStyle()

    applyStyle(editor, saved)
    editor.logo!.sizePercent = 30

    expect(saved.logo.sizePercent).toBe(20)
  })

  test('never mutates the defaults', () => {
    const editor = defaultStyle()
    applyStyle(editor, { logo })
    editor.logo!.sizePercent = 35

    expect(DEFAULT_STYLE.logo).toBeNull()
    expect(defaultStyle().logo).toBeNull()
  })
})

describe('design files', () => {
  test('round-trip through JSON', () => {
    const file = designFile('Brand', { moduleShape: 'LIQUID', logo })

    expect(parseDesignFile(JSON.stringify(file))).toEqual(file)
  })

  test('reject what is not a design', () => {
    expect(() => parseDesignFile('not json')).toThrow('not JSON')
    expect(() => parseDesignFile('{"name":"x"}')).toThrow('not an exported QR code design')
    expect(() => parseDesignFile('{"type":"qr-code-design","version":2,"style":{}}')).toThrow(
      'version 2',
    )
    expect(() => parseDesignFile('{"type":"qr-code-design","version":1,"style":[]}')).toThrow(
      'no style',
    )
  })

  test('are named after the design', () => {
    expect(designFileName('Brand Blue (2026)')).toBe('brand-blue-2026.qr-design.json')
    expect(designFileName('  ')).toBe('qr-code-design.qr-design.json')
  })
})
