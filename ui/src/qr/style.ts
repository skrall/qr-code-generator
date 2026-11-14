import type { QrStyle } from '../api/client'

/** The style the editor starts with, and what a saved design falls back to for absent fields. */
export const DEFAULT_STYLE: Readonly<QrStyle> = Object.freeze({
  size: 640,
  margin: 4,
  errorCorrection: 'QUARTILE',
  foreground: '#111827',
  gradient: null,
  background: '#ffffff',
  backgroundImage: null,
  moduleShape: 'ROUNDED',
  finderShape: 'ROUNDED',
  finderColor: '#3158c4',
  finderArtwork: null,
  logo: null,
  frame: null,
})

/** A fresh, mutable copy of the default style. */
export function defaultStyle(): QrStyle {
  return structuredClone({ ...DEFAULT_STYLE })
}

/**
 * Replaces every field of the editor's style with a saved one.
 *
 * <p>Every field is assigned, so nothing from the previous design survives: a saved design
 * without a logo removes the logo that was on screen. A field the saved style lacks (it was null
 * when saved, or the design predates the field) takes its default. Values are copied, so editing
 * after a load never mutates the loaded design.
 */
export function applyStyle(target: QrStyle, saved: Partial<QrStyle>): void {
  const merged = structuredClone({ ...DEFAULT_STYLE }) as QrStyle
  for (const key of Object.keys(DEFAULT_STYLE) as (keyof QrStyle)[]) {
    const value = saved[key]
    if (value !== undefined && value !== null) {
      Object.assign(merged, { [key]: structuredClone(value) })
    }
  }
  Object.assign(target, merged)
}

/** What an exported design file holds. */
export interface DesignFile {
  type: 'qr-code-design'
  version: 1
  name: string
  style: Partial<QrStyle>
}

export function designFile(name: string, style: Partial<QrStyle>): DesignFile {
  return { type: 'qr-code-design', version: 1, name, style }
}

/**
 * Reads an exported design, rejecting anything that is not one. The style itself is checked by
 * the server when the design is rendered or saved, as any other style is.
 */
export function parseDesignFile(text: string): DesignFile {
  let parsed: unknown
  try {
    parsed = JSON.parse(text)
  } catch {
    throw new Error('That file is not JSON.')
  }
  const file = parsed as Partial<DesignFile> | null
  if (!file || typeof file !== 'object' || file.type !== 'qr-code-design') {
    throw new Error('That file is not an exported QR code design.')
  }
  if (file.version !== 1) {
    throw new Error(`Design files of version ${String(file.version)} are not supported.`)
  }
  if (!file.style || typeof file.style !== 'object' || Array.isArray(file.style)) {
    throw new Error('The design file has no style.')
  }
  return {
    type: 'qr-code-design',
    version: 1,
    name: typeof file.name === 'string' ? file.name.slice(0, 100) : '',
    style: file.style,
  }
}

/** A file name for a design: its name in lower-case words, or a generic one. */
export function designFileName(name: string): string {
  const slug = name
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
  return `${slug || 'qr-code-design'}.qr-design.json`
}
