/**
 * Share links: the current design, and optionally its content, in a URL fragment.
 *
 * <p>The state goes in the fragment (`#share=…`), never the query, because a browser does not send
 * the fragment to the server: neither its logs nor a proxy's ever see what the link carries.
 *
 * <p>Format: `#share=v1.<codec>.<data>`, where data is the base64url of a JSON {@link SharePayload}
 * and codec is `z` for raw deflate (CompressionStream) or `j` for plain JSON where the browser has
 * no CompressionStream. `#design=<id>` instead names a saved design, which is how a design with
 * images is shared: a data URL is far too big for a link. The version lets the format change
 * without old links being misread.
 */
import { apiStyle, type QrStyle } from './api/client'
import { DEFAULT_STYLE } from './qr/style'
import { QR_TYPES, type QrTypeId } from './qr/types'

export const SHARE_VERSION = 'v1'

/**
 * The longest link made or accepted. Browsers take far longer URLs, but chat apps, mail clients
 * and QR codes of the link itself do not, and a link that is cut short is worse than none.
 */
export const MAX_SHARE_URL_LENGTH = 8000

/** The most JSON a link may inflate to, so a crafted link cannot exhaust memory. */
const MAX_JSON_BYTES = 64 * 1024

/** A field's value is at most this long in a link; the longest field takes 1200. */
const MAX_VALUE_LENGTH = 2000

export class ShareError extends Error {}

export interface ShareContent {
  type: QrTypeId
  values: Record<string, string>
}

export interface SharePayload {
  style: Partial<QrStyle>
  content?: ShareContent
}

/** The style keys that hold images, with the words the UI uses for them. */
const IMAGES = [
  ['logo', 'logo'],
  ['backgroundImage', 'background image'],
  ['finderArtwork', 'corner artwork'],
] as const satisfies readonly (readonly [keyof QrStyle, string])[]

const IMAGE_KEYS = new Set<string>(IMAGES.map(([key]) => key))

/** The images the style would send, by name. Artwork only counts with the custom corner shape. */
export function styleImages(style: QrStyle): string[] {
  const sent = apiStyle(style)
  return IMAGES.filter(([key]) => sent[key] !== undefined).map(([, label]) => label)
}

/** The style as a link carries it: as the API takes it, without images. */
export function shareableStyle(style: QrStyle): Partial<QrStyle> {
  const sent: Record<string, unknown> = { ...apiStyle(style) }
  for (const key of IMAGE_KEYS) {
    delete sent[key]
  }
  return sent as Partial<QrStyle>
}

/**
 * The content a link may carry for a type: its non-empty values, secrets left out. `omitted`
 * names the secret fields that had a value, for the UI to say so.
 */
export function shareableContent(
  typeId: QrTypeId,
  values: Readonly<Record<string, string>>,
): { content: ShareContent | undefined; omitted: string[] } {
  const type = QR_TYPES.find((candidate) => candidate.id === typeId)
  const kept: Record<string, string> = {}
  const omitted: string[] = []
  for (const field of type?.fields ?? []) {
    const value = values[field.key] ?? ''
    if (value === '') {
      continue
    }
    if (field.secret) {
      omitted.push(field.label)
    } else {
      kept[field.key] = value
    }
  }
  return {
    content: Object.keys(kept).length > 0 ? { type: typeId, values: kept } : undefined,
    omitted,
  }
}

/** The labels of a type's secret fields, which a link never fills. */
export function secretFields(typeId: QrTypeId): string[] {
  const type = QR_TYPES.find((candidate) => candidate.id === typeId)
  return (type?.fields ?? []).filter((field) => field.secret).map((field) => field.label)
}

/** The saved design on screen, as DesignsPanel reports it; a design link names it. */
export interface CurrentDesign {
  id: number
  name: string
  shared: boolean
  /** Whether the editor's style still matches what was saved, so the link shows what is seen. */
  unchanged: boolean
}

// base64url ------------------------------------------------------------------

function toBase64Url(bytes: Uint8Array): string {
  let binary = ''
  for (let start = 0; start < bytes.length; start += 0x8000) {
    binary += String.fromCharCode(...bytes.subarray(start, start + 0x8000))
  }
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

function fromBase64Url(text: string): Uint8Array<ArrayBuffer> {
  if (!/^[A-Za-z0-9_-]*$/.test(text)) {
    throw new ShareError('That share link is damaged.')
  }
  const base64 = text.replace(/-/g, '+').replace(/_/g, '/')
  let binary: string
  try {
    binary = atob(base64 + '='.repeat((4 - (base64.length % 4)) % 4))
  } catch {
    throw new ShareError('That share link is damaged.')
  }
  const bytes = new Uint8Array(binary.length)
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i)
  }
  return bytes
}

// deflate --------------------------------------------------------------------

function canCompress(): boolean {
  return typeof CompressionStream === 'function' && typeof DecompressionStream === 'function'
}

/** Runs bytes through a transform, giving up once the output passes `limit` bytes. */
async function transform(
  bytes: Uint8Array<ArrayBuffer>,
  stream: CompressionStream | DecompressionStream,
  limit = Infinity,
): Promise<Uint8Array<ArrayBuffer>> {
  const source = new ReadableStream<BufferSource>({
    start(controller) {
      controller.enqueue(bytes)
      controller.close()
    },
  })
  const reader = source.pipeThrough(stream).getReader()
  const chunks: Uint8Array[] = []
  let total = 0
  for (;;) {
    const { done, value } = await reader.read()
    if (done) {
      break
    }
    total += value.length
    if (total > limit) {
      await reader.cancel().catch(() => {})
      throw new ShareError('That share link holds more than a design.')
    }
    chunks.push(value)
  }
  const out = new Uint8Array(total)
  let offset = 0
  for (const chunk of chunks) {
    out.set(chunk, offset)
    offset += chunk.length
  }
  return out
}

// encode and decode ----------------------------------------------------------

/**
 * The fragment value for a payload, `v1.z.…` or, without CompressionStream, `v1.j.…`.
 * `compress` is there for tests; leave it to detect the browser.
 */
export async function encodeShare(payload: SharePayload, compress = canCompress()): Promise<string> {
  const json = new TextEncoder().encode(JSON.stringify(payload))
  if (compress) {
    const deflated = await transform(json, new CompressionStream('deflate-raw'))
    return `${SHARE_VERSION}.z.${toBase64Url(deflated)}`
  }
  return `${SHARE_VERSION}.j.${toBase64Url(json)}`
}

/** Reads a fragment value back, checking everything: a link is input from anyone. */
export async function decodeShare(token: string): Promise<SharePayload> {
  const match = /^(v\d+)\.([a-z])\.(.*)$/.exec(token)
  if (!match) {
    throw new ShareError('That share link is damaged.')
  }
  const [, version, codec, data] = match
  if (version !== SHARE_VERSION) {
    throw new ShareError('That share link was made by a newer version of this app.')
  }
  let bytes = fromBase64Url(data)
  if (codec === 'z') {
    if (!canCompress()) {
      throw new ShareError('This browser cannot open compressed share links.')
    }
    try {
      bytes = await transform(bytes, new DecompressionStream('deflate-raw'), MAX_JSON_BYTES)
    } catch (cause) {
      throw cause instanceof ShareError ? cause : new ShareError('That share link is damaged.')
    }
  } else if (codec !== 'j') {
    throw new ShareError('That share link is damaged.')
  }
  if (bytes.length > MAX_JSON_BYTES) {
    throw new ShareError('That share link holds more than a design.')
  }
  let parsed: unknown
  try {
    parsed = JSON.parse(new TextDecoder('utf-8', { fatal: true }).decode(bytes))
  } catch {
    throw new ShareError('That share link is damaged.')
  }
  return checkPayload(parsed)
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
}

/** A nested style object (gradient, frame) holds only primitives. */
function isFlatRecord(value: unknown): boolean {
  return (
    isRecord(value) &&
    Object.values(value).every((item) => ['string', 'number', 'boolean'].includes(typeof item))
  )
}

function checkPayload(parsed: unknown): SharePayload {
  const invalid = new ShareError('That share link does not hold a valid design.')
  if (!isRecord(parsed) || !isRecord(parsed.style)) {
    throw invalid
  }
  const style: Record<string, unknown> = {}
  for (const [key, fallback] of Object.entries(DEFAULT_STYLE)) {
    const value = parsed.style[key]
    // Images never travel in a link; one that does is ignored rather than trusted.
    if (value === undefined || value === null || IMAGE_KEYS.has(key)) {
      continue
    }
    const fits = fallback === null ? isFlatRecord(value) : typeof value === typeof fallback
    if (!fits) {
      throw invalid
    }
    style[key] = value
  }

  const payload: SharePayload = { style: style as Partial<QrStyle> }
  if (parsed.content !== undefined) {
    payload.content = checkContent(parsed.content, invalid)
  }
  return payload
}

function checkContent(content: unknown, invalid: ShareError): ShareContent {
  if (!isRecord(content) || !isRecord(content.values)) {
    throw invalid
  }
  const type = QR_TYPES.find((candidate) => candidate.id === content.type)
  if (!type || type.fields.length === 0) {
    throw invalid
  }
  const values: Record<string, string> = {}
  for (const field of type.fields) {
    const value = content.values[field.key]
    if (value === undefined || field.secret) {
      continue
    }
    if (typeof value !== 'string' || value.length > MAX_VALUE_LENGTH) {
      throw invalid
    }
    values[field.key] = value
  }
  return { type: type.id, values }
}

// fragments ------------------------------------------------------------------

export type Fragment =
  | { kind: 'share'; token: string }
  | { kind: 'design'; id: number }
  | { kind: 'invalid'; message: string }

/** What a location hash asks for, or null when it is not one of ours (or empty). */
export function parseFragment(hash: string): Fragment | null {
  const fragment = hash.startsWith('#') ? hash.slice(1) : hash
  if (fragment.startsWith('share=')) {
    if (hash.length > MAX_SHARE_URL_LENGTH) {
      return { kind: 'invalid', message: 'That share link is too long to open.' }
    }
    return { kind: 'share', token: fragment.slice('share='.length) }
  }
  if (fragment.startsWith('design=')) {
    const id = fragment.slice('design='.length)
    if (!/^[1-9]\d{0,15}$/.test(id)) {
      return { kind: 'invalid', message: 'That design link is damaged.' }
    }
    return { kind: 'design', id: Number(id) }
  }
  return null
}

export function shareUrl(base: string, token: string): string {
  return `${base}#share=${token}`
}

export function designUrl(base: string, id: number): string {
  return `${base}#design=${id}`
}
