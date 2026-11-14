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
export type GradientType = 'LINEAR' | 'RADIAL'

/**
 * A two-colour gradient in place of the flat foreground. The server rejects one whose stops do
 * not both contrast with the background on the same side of it.
 */
export interface QrGradient {
  type: GradientType
  start: string
  end: string
  /** CSS-style direction in degrees: 0 points up, 90 right. Ignored for RADIAL. */
  angle: number
  /** Whether the corner patterns take the gradient instead of `finderColor`. */
  finders: boolean
}

/** An image that fills the dark rings of a custom finder pattern. */
export interface QrFinderArtwork {
  dataUrl: string
}

/** An image drawn behind the code. */
export interface QrBackgroundImage {
  dataUrl: string
}

/**
 * A card around the code. Lengths are percentages of the code's width; the image grows around
 * the code rather than the code shrinking. A blank title or caption is left out.
 */
export interface QrFrame {
  title: string
  caption: string
  background: string
  textColor: string
  borderColor: string
  titleSizePercent: number
  captionSizePercent: number
  borderPercent: number
  radiusPercent: number
  paddingPercent: number
  marginPercent: number
  bleedPercent: number
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
  gradient: QrGradient | null
  background: string
  backgroundImage: QrBackgroundImage | null
  moduleShape: ModuleShape
  finderShape: FinderShape
  finderColor: string
  finderArtwork: QrFinderArtwork | null
  logo: QrLogo | null
  frame: QrFrame | null
  /**
   * Leaves the background unpainted, so the code can sit on existing artwork. `background` is
   * still the colour it is designed for, and what the contrast and scan checks use.
   */
  transparentBackground: boolean
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

/** Whether the scan check expects phones to read the code. */
export type ScanVerdict = 'PASS' | 'MARGINAL' | 'FAIL'

/** One attempt to read the rendered code back. */
export interface ScanTrial {
  name: 'rendered' | 'small' | 'blurred'
  description: string
  decoded: boolean
}

/** The server's verdict on a rendered code, with style changes likely to help. */
export interface ScanCheck {
  verdict: ScanVerdict
  version: number
  modules: number
  trials: ScanTrial[]
  hints: string[]
}

/**
 * The style as the API takes it. Absent images are omitted rather than sent as null, so the
 * document matches the shape the API documents. Artwork is only useful with the custom finder
 * shape, so it is left out otherwise instead of sending megabytes the server would ignore.
 * Saved designs go through the same cleanup, so a design never stores artwork it cannot show.
 */
export function apiStyle(style: QrStyle): Partial<QrStyle> {
  return {
    ...style,
    gradient: style.gradient ?? undefined,
    backgroundImage: style.backgroundImage ?? undefined,
    finderArtwork:
      style.finderShape === 'CUSTOM' ? (style.finderArtwork ?? undefined) : undefined,
    logo: style.logo ?? undefined,
    frame: style.frame ?? undefined,
  }
}

/** The request document the render and check endpoints both take. */
function requestBody(request: RenderRequest): string {
  return JSON.stringify({ data: request.data, style: apiStyle(request.style) })
}

export async function renderQrCode(
  request: RenderRequest,
  format: QrFormat,
  signal?: AbortSignal,
): Promise<Blob> {
  const response = await send(`/api/qr-codes?format=${format}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: requestBody(request),
    signal,
  })

  if (!response.ok) {
    throw new Error(await problemDetail(response))
  }
  return response.blob()
}

/** Renders the code on the server and reads it back, as plain and as a phone might see it. */
export async function checkQrCode(request: RenderRequest, signal?: AbortSignal): Promise<ScanCheck> {
  const response = await send('/api/qr-codes/check', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: requestBody(request),
    signal,
  })

  if (!response.ok) {
    throw new Error(await problemDetail(response))
  }
  return (await response.json()) as ScanCheck
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

/** Reads a background image into the data URL the API expects. */
export async function readBackgroundImageFile(file: File): Promise<QrBackgroundImage> {
  return { dataUrl: await readImageFile(file, 'background image') }
}

/** Stores a destination and returns the short link that redirects to it. */
export async function createLink(input: LinkInput, signal?: AbortSignal): Promise<ShortLink> {
  const response = await send('/api/links', {
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
  const response = await send(`/api/links/${encodeURIComponent(code)}`, {
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

  const response = await send(`/api/links?${params.toString()}`, { signal })
  if (!response.ok) {
    throw new Error(await problemDetail(response))
  }
  return (await response.json()) as LinkPage
}

/** Soft deletes a link: its code then answers 410 Gone instead of 404. */
export async function deleteLink(code: string): Promise<void> {
  const response = await send(`/api/links/${encodeURIComponent(code)}`, { method: 'DELETE' })
  if (!response.ok) {
    throw new Error(await problemDetail(response))
  }
}

/** A saved design as listed: everything but its style. */
export interface DesignSummary {
  id: number
  name: string
  /** Who saved it; null when sign-in is off. */
  owner: string | null
  shared: boolean
  mine: boolean
  /** Whether the caller may rename, overwrite, share or delete it. */
  editable: boolean
  createdAt: string
  updatedAt: string
}

/** A saved design with its style, as stored: fields the saver left at null are absent. */
export interface Design extends DesignSummary {
  style: Partial<QrStyle>
}

export interface DesignInput {
  name: string
  shared: boolean
  /** Omitted on an update that only renames or shares, so the images are not sent again. */
  style?: QrStyle
}

async function json<T>(response: Response): Promise<T> {
  if (!response.ok) {
    throw new Error(await problemDetail(response))
  }
  return (await response.json()) as T
}

/** The caller's own designs and every shared one, by name. */
export async function listDesigns(signal?: AbortSignal): Promise<DesignSummary[]> {
  return json(await send('/api/designs', { signal }))
}

export async function getDesign(id: number): Promise<Design> {
  return json(await send(`/api/designs/${id}`))
}

export async function createDesign(input: DesignInput): Promise<Design> {
  return json(
    await send('/api/designs', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        name: input.name,
        shared: input.shared,
        style: input.style ? apiStyle(input.style) : undefined,
      }),
    }),
  )
}

export async function updateDesign(id: number, input: DesignInput): Promise<Design> {
  return json(
    await send(`/api/designs/${id}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        name: input.name,
        shared: input.shared,
        style: input.style ? apiStyle(input.style) : undefined,
      }),
    }),
  )
}

export async function deleteDesign(id: number): Promise<void> {
  const response = await send(`/api/designs/${id}`, { method: 'DELETE' })
  if (!response.ok) {
    throw new Error(await problemDetail(response))
  }
}

export type BatchFormat = QrFormat | 'both'

/** How every row of a batch is rendered: with a style, or with a saved design's. */
export interface BatchOptions {
  format: BatchFormat
  style?: QrStyle
  designId?: number
}

/**
 * Uploads a CSV of payloads and returns the ZIP of codes the server renders from it.
 *
 * <p>Multipart, with the options as a JSON part. No Content-Type is set here: the browser adds
 * the multipart boundary itself.
 */
export async function renderBatch(
  csv: File,
  options: BatchOptions,
  signal?: AbortSignal,
): Promise<Blob> {
  const form = new FormData()
  form.append('csv', csv, csv.name)
  form.append(
    'options',
    new Blob(
      [
        JSON.stringify({
          format: options.format,
          style: options.style ? apiStyle(options.style) : undefined,
          designId: options.designId,
        }),
      ],
      { type: 'application/json' },
    ),
  )
  const response = await send('/api/qr-codes/batch', { method: 'POST', body: form, signal })
  if (!response.ok) {
    throw new Error(await problemDetail(response))
  }
  return response.blob()
}

/** Who is signed in, and whether they may create, edit and delete short links. */
export interface CurrentUser {
  /** Null when sign-in is turned off on the server. */
  name: string | null
  operator: boolean
}

export async function currentUser(signal?: AbortSignal): Promise<CurrentUser> {
  const response = await send('/api/me', { signal })
  if (!response.ok) {
    throw new Error(await problemDetail(response))
  }
  return (await response.json()) as CurrentUser
}

/**
 * Every API call goes through here, for the two things sign-in adds to a request.
 *
 * <p>Under OIDC the session is a cookie, so a state-changing request needs the CSRF token: the
 * server leaves it in a readable XSRF-TOKEN cookie and expects it back in X-XSRF-TOKEN. Basic
 * and header modes set no such cookie, and the request goes out unchanged.
 *
 * <p>A 401 is handled here so no caller has to. Basic and OIDC name a login-url: Basic's prompt
 * only appears on a top-level navigation, and OIDC's provider redirect cannot be followed by a
 * fetch, so the browser is sent there. Header mode names none, because the proxy owns sign-in and
 * reloading would loop; its detail is thrown for the caller to show. A 401 that is not one of
 * ours (an empty body, a proxy's HTML page) goes to /login on the same reasoning as Basic.
 */
async function send(path: string, init: RequestInit = {}): Promise<Response> {
  const method = (init.method ?? 'GET').toUpperCase()
  const response = await fetch(path, {
    ...init,
    headers: { ...csrfHeaders(method), ...(init.headers as Record<string, string> | undefined) },
  })
  if (response.status === 401) {
    return unauthorized(response)
  }
  return response
}

async function unauthorized(response: Response): Promise<never> {
  const problem = await unauthorizedProblem(response)
  const loginUrl = problem === null ? '/login' : problem.loginUrl
  if (loginUrl !== null) {
    window.location.assign(loginUrl)
    throw new Error('Sign in required.')
  }
  throw new Error(problem?.detail ?? 'Sign in required.')
}

/** The login-url and detail of a 401 the app wrote, or null for any other kind of 401. */
async function unauthorizedProblem(
  response: Response,
): Promise<{ loginUrl: string | null; detail: string | null } | null> {
  let body: string
  try {
    body = await response.text()
  } catch {
    return null
  }
  if (body.trim() === '') {
    return null
  }
  try {
    const problem: unknown = JSON.parse(body)
    if (typeof problem !== 'object' || problem === null) {
      return null
    }
    const fields = problem as { 'login-url'?: unknown; detail?: unknown }
    return {
      loginUrl:
        typeof fields['login-url'] === 'string' && fields['login-url'] !== ''
          ? fields['login-url']
          : null,
      detail: typeof fields.detail === 'string' && fields.detail !== '' ? fields.detail : null,
    }
  } catch {
    return null
  }
}

function csrfHeaders(method: string): Record<string, string> {
  if (method === 'GET' || method === 'HEAD' || method === 'OPTIONS') {
    return {}
  }
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/)
  return match === null ? {} : { 'X-XSRF-TOKEN': decodeURIComponent(match[1]) }
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
