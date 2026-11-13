/**
 * Typed access to the QR rendering API.
 *
 * <p>The server returns the image itself, so the preview is an object URL made from a blob and a
 * download is the same request with a save dialog attached.
 */

export type ErrorCorrection = 'LOW' | 'MEDIUM' | 'QUARTILE' | 'HIGH'
export type ModuleShape = 'SQUARE' | 'ROUNDED' | 'DOTS' | 'STAR' | 'LIQUID'
export type FinderShape = 'SQUARE' | 'ROUNDED' | 'CIRCLE' | 'LEAF' | 'DIAMOND' | 'CUSTOM'
export type QrFormat = 'png' | 'svg'

/** An image that fills the dark rings of a custom finder pattern. */
export interface QrFinderArtwork {
  dataUrl: string
}

export interface QrLogo {
  dataUrl: string
  sizePercent: number
  paddingPercent: number
  background: string
  radiusPercent: number
}

export interface QrStyle {
  size: number
  margin: number
  errorCorrection: ErrorCorrection
  foreground: string
  background: string
  moduleShape: ModuleShape
  finderShape: FinderShape
  finderColor: string
  finderArtwork: QrFinderArtwork | null
  logo: QrLogo | null
}

export interface RenderRequest {
  data: string
  style: QrStyle
}

/** A stored destination and the code that redirects to it. */
export interface ShortLink {
  code: string
  shortUrl: string
  targetUrl: string
  createdAt: string
  expiresAt: string | null
  hitCount: number
  lastHitAt: string | null
}

export interface LinkPage {
  items: ShortLink[]
  total: number
  page: number
  size: number
}

export interface LinkInput {
  url: string
  /** ISO timestamp, or null for a link that never expires. */
  expiresAt: string | null
}

/** The formats the server can decode, which is narrower than what a browser can display. */
const SUPPORTED_IMAGE_TYPES = new Set(['image/png', 'image/jpeg', 'image/gif'])

/** Matches the server's decoded-size limit. */
const MAX_IMAGE_BYTES = 2_000_000

export async function renderQrCode(
  request: RenderRequest,
  format: QrFormat,
  signal?: AbortSignal,
): Promise<Blob> {
  const body = {
    data: request.data,
    // Absent images are omitted rather than sent as null, so the document matches the shape
    // the API documents. Artwork is only useful with the custom finder shape, so it is left
    // out otherwise instead of sending megabytes the server would ignore.
    style: {
      ...request.style,
      finderArtwork:
        request.style.finderShape === 'CUSTOM'
          ? (request.style.finderArtwork ?? undefined)
          : undefined,
      logo: request.style.logo ?? undefined,
    },
  }

  const response = await fetch(`/api/qr-codes?format=${format}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
    signal,
  })

  if (!response.ok) {
    throw new Error(await problemDetail(response))
  }
  return response.blob()
}

/**
 * Reads an image file into a data URL.
 *
 * <p>The checks here are the same ones the server makes, but failing in the browser is faster and
 * lets the message appear next to the file picker rather than in the preview.
 */
async function readImageFile(file: File, noun: string): Promise<string> {
  if (!SUPPORTED_IMAGE_TYPES.has(file.type)) {
    throw new Error('Choose a PNG, JPEG or GIF image.')
  }
  if (file.size > MAX_IMAGE_BYTES) {
    throw new Error(`The ${noun} must be at most 2 MB.`)
  }

  return await new Promise<string>((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result))
    reader.onerror = () => reject(new Error(`The ${noun} could not be read.`))
    reader.readAsDataURL(file)
  })
}

/** Reads a logo into the data URL and defaults the API expects. */
export async function readLogoFile(file: File): Promise<QrLogo> {
  return {
    dataUrl: await readImageFile(file, 'logo'),
    sizePercent: 20,
    paddingPercent: 8,
    background: '#ffffff',
    radiusPercent: 25,
  }
}

/** Reads finder artwork into the data URL the API expects. */
export async function readFinderArtworkFile(file: File): Promise<QrFinderArtwork> {
  return { dataUrl: await readImageFile(file, 'finder artwork') }
}

/** Stores a destination and returns the short link that redirects to it. */
export async function createLink(input: LinkInput, signal?: AbortSignal): Promise<ShortLink> {
  const response = await fetch('/api/links', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ url: input.url, expiresAt: input.expiresAt ?? undefined }),
    signal,
  })

  if (!response.ok) {
    throw new Error(await problemDetail(response))
  }
  return (await response.json()) as ShortLink
}

/**
 * Repoints a link at a new destination without changing its code.
 *
 * <p>The short URL is what an already-printed QR code carries, so editing keeps it and only
 * changes where it redirects.
 */
export async function updateLink(
  code: string,
  input: LinkInput,
  signal?: AbortSignal,
): Promise<ShortLink> {
  const response = await fetch(`/api/links/${encodeURIComponent(code)}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ url: input.url, expiresAt: input.expiresAt ?? undefined }),
    signal,
  })

  if (!response.ok) {
    throw new Error(await problemDetail(response))
  }
  return (await response.json()) as ShortLink
}

/** One page of stored links, newest first. An empty query lists everything. */
export async function listLinks(
  query: string,
  page: number,
  size: number,
  signal?: AbortSignal,
): Promise<LinkPage> {
  const params = new URLSearchParams({ page: String(page), size: String(size) })
  const trimmed = query.trim()
  if (trimmed) {
    params.set('q', trimmed)
  }

  const response = await fetch(`/api/links?${params.toString()}`, { signal })
  if (!response.ok) {
    throw new Error(await problemDetail(response))
  }
  return (await response.json()) as LinkPage
}

/** Soft deletes a link: its code then answers 410 Gone instead of 404. */
export async function deleteLink(code: string): Promise<void> {
  const response = await fetch(`/api/links/${encodeURIComponent(code)}`, { method: 'DELETE' })
  if (!response.ok) {
    throw new Error(await problemDetail(response))
  }
}

/** Reads the RFC 9457 detail the server sends, falling back to the status. */
async function problemDetail(response: Response): Promise<string> {
  try {
    const problem: unknown = await response.json()
    if (problem && typeof problem === 'object' && 'detail' in problem) {
      const detail = (problem as { detail?: unknown }).detail
      if (typeof detail === 'string' && detail.length > 0) {
        return detail
      }
    }
  } catch {
    // Not a problem document; the status is all there is.
  }
  return `The server answered ${response.status} ${response.statusText}.`
}
