/**
 * Typed access to the QR rendering API.
 *
 * <p>The server returns the image itself, so the preview is an object URL made from a blob and a
 * download is the same request with a save dialog attached.
 */

export type ErrorCorrection = 'LOW' | 'MEDIUM' | 'QUARTILE' | 'HIGH'
export type ModuleShape = 'SQUARE' | 'ROUNDED' | 'DOTS'
export type FinderShape = 'SQUARE' | 'ROUNDED' | 'CIRCLE'
export type QrFormat = 'png' | 'svg'

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
  logo: QrLogo | null
}

export interface RenderRequest {
  data: string
  style: QrStyle
}

/** The formats the server can decode, which is narrower than what a browser can display. */
const SUPPORTED_LOGO_TYPES = new Set(['image/png', 'image/jpeg', 'image/gif'])

/** Matches the server's decoded-size limit. */
const MAX_LOGO_BYTES = 2_000_000

export async function renderQrCode(
  request: RenderRequest,
  format: QrFormat,
  signal?: AbortSignal,
): Promise<Blob> {
  const body = {
    data: request.data,
    // An absent logo is omitted rather than sent as null, so the document matches the shape
    // the API documents.
    style: { ...request.style, logo: request.style.logo ?? undefined },
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
 * Reads a logo into the data URL the API expects.
 *
 * <p>The checks here are the same ones the server makes, but failing in the browser is faster and
 * lets the message appear next to the file picker rather than in the preview.
 */
export async function readLogoFile(file: File): Promise<QrLogo> {
  if (!SUPPORTED_LOGO_TYPES.has(file.type)) {
    throw new Error('Choose a PNG, JPEG or GIF image.')
  }
  if (file.size > MAX_LOGO_BYTES) {
    throw new Error('The logo must be at most 2 MB.')
  }

  const dataUrl = await new Promise<string>((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result))
    reader.onerror = () => reject(new Error('The logo could not be read.'))
    reader.readAsDataURL(file)
  })

  return {
    dataUrl,
    sizePercent: 20,
    paddingPercent: 8,
    background: '#ffffff',
    radiusPercent: 25,
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
