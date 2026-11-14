/**
 * Builds the "Copy as…" snippets from a rendered code.
 *
 * <p>Everything here is pure string work on the bytes the server returned, so it is tested apart
 * from the component that fetches those bytes and writes them to the clipboard.
 */

import type { QrTypeId } from './qr/types'

export type EmbedFormat = 'img' | 'svg' | 'dataurl' | 'link'

/** What the preview hands the "Copy as…" menu: how to fetch the exact image, and how to name it. */
export interface EmbedSource {
  /** The same render call a download makes, for the current payload and style. */
  render: (format: 'png' | 'svg') => Promise<Blob>
  /** The alt text, or SVG title, for the code. */
  alt: string
  /** Where a click on the code should go, when the payload is a web address. */
  href: string | null
}

/** Above this a data URL bloats the page and some editors and mail clients truncate it. */
export const LARGE_SNIPPET_BYTES = 100 * 1024

/** The formats offered for a source; the linked image only when there is somewhere to link to. */
export function embedFormats(href: string | null): { value: EmbedFormat; label: string }[] {
  const formats: { value: EmbedFormat; label: string }[] = [
    { value: 'img', label: 'HTML <img> (PNG data URL)' },
    { value: 'svg', label: 'Inline SVG' },
    { value: 'dataurl', label: 'Data URL (PNG)' },
  ]
  if (href) {
    formats.push({ value: 'link', label: 'Linked <img> (<a> wrapper)' })
  }
  return formats
}

/** Escapes text for a double-quoted HTML attribute or element content. */
export function escapeHtml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/"/g, '&quot;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
}

/** Base64 for arbitrary bytes, in chunks so a large PNG does not overflow the argument list. */
export function toBase64(bytes: Uint8Array): string {
  let binary = ''
  const chunk = 0x8000
  for (let i = 0; i < bytes.length; i += chunk) {
    binary += String.fromCharCode(...bytes.subarray(i, i + chunk))
  }
  return btoa(binary)
}

export function pngDataUrl(bytes: Uint8Array): string {
  return `data:image/png;base64,${toBase64(bytes)}`
}

/**
 * The pixel size from a PNG's IHDR chunk, or null if the bytes are not a PNG.
 *
 * <p>A frame makes the image larger than `style.size`, so reading the header is the only way to
 * be sure the attributes match the file.
 */
export function pngSize(bytes: Uint8Array): { width: number; height: number } | null {
  const signature = [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]
  if (bytes.length < 24 || signature.some((byte, i) => bytes[i] !== byte)) {
    return null
  }
  const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength)
  return { width: view.getUint32(16), height: view.getUint32(20) }
}

export function imgTag(dataUrl: string, alt: string, size: { width: number; height: number } | null): string {
  const dimensions = size ? ` width="${size.width}" height="${size.height}"` : ''
  return `<img src="${dataUrl}" alt="${escapeHtml(alt)}"${dimensions}>`
}

export function linkedImg(href: string, img: string): string {
  return `<a href="${escapeHtml(href)}">${img}</a>`
}

/** A web address the code can link to on screen, or null. Only http and https are linked. */
export function linkTarget(payload: string): string | null {
  const value = payload.trim()
  if (!/^https?:\/\/\S+$/i.test(value)) {
    return null
  }
  try {
    new URL(value)
    return value
  } catch {
    return null
  }
}

function hostOf(url: string): string {
  try {
    return new URL(url).host || url
  } catch {
    return url
  }
}

/** Collapses whitespace and shortens a value for use inside alt text. */
function clip(value: string | undefined, max = 60): string {
  const text = (value ?? '').replace(/\s+/g, ' ').trim()
  return text.length > max ? `${text.slice(0, max - 1)}…` : text
}

/**
 * A short, non-secret description of what the code encodes.
 *
 * <p>Alt text ends up in page source, crawlers and screen readers, so it names what the code is
 * for and leaves out anything private: never a WiFi password, a message body, an IBAN or an
 * amount, and only the host of a URL, since a query string can carry tokens.
 */
export function altText(type: QrTypeId, values: Readonly<Record<string, string>>, payload: string): string {
  const v = (key: string): string => clip(values[key])
  switch (type) {
    case 'url':
    case 'appstore':
    case 'shortlink': {
      const target = linkTarget(payload)
      return target ? `QR code linking to ${hostOf(target)}` : 'QR code with a link'
    }
    case 'email':
      return v('to') ? `QR code to email ${v('to')}` : 'QR code to send an email'
    case 'phone':
      return v('number') ? `QR code to call ${v('number')}` : 'QR code to make a call'
    case 'sms':
      return v('number') ? `QR code to text ${v('number')}` : 'QR code to send a text message'
    case 'wifi':
      return v('ssid') ? `QR code to join the WiFi network ${v('ssid')}` : 'QR code to join a WiFi network'
    case 'vcard':
    case 'mecard': {
      const name = clip([values.firstName, values.lastName].filter((part) => part?.trim()).join(' '))
      return name ? `QR code with contact details for ${name}` : 'QR code with contact details'
    }
    case 'event':
      return v('summary') ? `QR code for the event ${v('summary')}` : 'QR code for a calendar event'
    case 'geo':
      return 'QR code for a map location'
    case 'bitcoin':
      return 'QR code for a Bitcoin payment'
    case 'epc':
      return v('name') ? `QR code for a SEPA payment to ${v('name')}` : 'QR code for a SEPA payment'
    case 'text': {
      // Plain text may be anything, including something private; only a URL is described.
      const target = linkTarget(payload)
      return target ? `QR code linking to ${hostOf(target)}` : 'QR code with text'
    }
  }
}

/** A prefix unique enough that two codes pasted into one page do not share an id. */
export function idPrefix(random: () => number = Math.random): string {
  return `qr${Math.floor(random() * 36 ** 6).toString(36).padStart(6, '0')}-`
}

/**
 * Prefixes every id in an SVG document and every local reference to one.
 *
 * <p>The renderer uses fixed ids (`qr-gradient`, `finder-outer`, `finder-gap-0`…), which is right
 * for a standalone file. Inline in HTML all ids share the page's namespace, so a second code's
 * `url(#qr-gradient)` would paint with the first code's gradient. References are rewritten only
 * for ids the document defines, and only in `url(#…)` and `href="#…"` form; embedded images are
 * `data:` URLs and never start with `#`.
 */
export function prefixSvgIds(svg: string, prefix: string): string {
  const ids = new Set([...svg.matchAll(/\sid="([^"]+)"/g)].map((match) => match[1]))
  if (ids.size === 0) {
    return svg
  }
  return svg
    .replace(/(\sid=")([^"]+)"/g, (_, head: string, id: string) => `${head}${prefix}${id}"`)
    .replace(/url\((['"]?)#([^)'"\s]+)\1\)/g, (match, quote: string, id: string) =>
      ids.has(id) ? `url(${quote}#${prefix}${id}${quote})` : match,
    )
    .replace(/(\s(?:xlink:)?href=")#([^"]+)"/g, (match, head: string, id: string) =>
      ids.has(id) ? `${head}#${prefix}${id}"` : match,
    )
}

/**
 * The SVG as markup to paste into HTML: no XML declaration, unique ids, and the alt text as its
 * accessible name.
 */
export function inlineSvg(svg: string, alt: string, prefix: string): string {
  const body = svg.replace(/^﻿?\s*<\?xml[^>]*\?>\s*/, '').trim()
  const titled = /<title>[\s\S]*?<\/title>/.test(body)
    ? body.replace(/<title>[\s\S]*?<\/title>/, `<title>${escapeHtml(alt)}</title>`)
    : body
  return prefixSvgIds(titled, prefix)
}

/** The snippet's size in bytes as it would be pasted (UTF-8). */
export function snippetBytes(snippet: string): number {
  return new TextEncoder().encode(snippet).length
}

/** Builds one snippet from the source, fetching the format the snippet needs. */
export async function buildSnippet(
  format: EmbedFormat,
  source: EmbedSource,
  prefix: string = idPrefix(),
): Promise<string> {
  if (format === 'svg') {
    const blob = await source.render('svg')
    return inlineSvg(await blob.text(), source.alt, prefix)
  }
  const bytes = new Uint8Array(await (await source.render('png')).arrayBuffer())
  const dataUrl = pngDataUrl(bytes)
  if (format === 'dataurl') {
    return dataUrl
  }
  const img = imgTag(dataUrl, source.alt, pngSize(bytes))
  if (format === 'link') {
    if (!source.href) {
      throw new Error('This code does not encode a web address to link to.')
    }
    return linkedImg(source.href, img)
  }
  return img
}
