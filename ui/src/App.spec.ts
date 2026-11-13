import { afterEach, describe, expect, test, vi } from 'vitest'
import { createApp, nextTick, type App as VueApp } from 'vue'
import type { ShortLink } from './api/client'
import QrApp from './App.vue'

let app: VueApp | null = null
let host: HTMLElement | null = null

const stored: ShortLink = {
  code: 'abc123XYZ',
  shortUrl: 'http://localhost:8080/r/abc123XYZ',
  targetUrl: 'https://example.com/page',
  createdAt: '2026-01-01T00:00:00Z',
  expiresAt: null,
  hitCount: 0,
  lastHitAt: null,
}

function json(body: unknown, status = 200): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    statusText: '',
    json: async () => body,
  } as Response
}

function png(): Response {
  return {
    ok: true,
    status: 200,
    statusText: '',
    blob: async () => new Blob(['png'], { type: 'image/png' }),
  } as Response
}

function button(container: HTMLElement, label: string): HTMLButtonElement {
  const target = [...container.querySelectorAll('button')].find(
    (candidate) => candidate.textContent?.trim() === label,
  )
  if (!(target instanceof HTMLButtonElement)) {
    throw new Error(`No ${label} button`)
  }
  return target
}

async function settle(ms = 0): Promise<void> {
  await new Promise((resolve) => setTimeout(resolve, ms))
  await nextTick()
}

afterEach(() => {
  app?.unmount()
  host?.remove()
  app = null
  host = null
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

/** The payload of the most recent preview request, as the server would encode it. */
function lastRendered(fetchMock: ReturnType<typeof vi.fn>): string | undefined {
  const call = fetchMock.mock.calls
    .filter(([input]) => String(input).startsWith('/api/qr-codes?'))
    .at(-1)
  return call ? (JSON.parse(String((call[1] as RequestInit).body)).data as string) : undefined
}

describe('App with a short link', () => {
  test('previews the destination while typing, then the short link after creation', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      const url = String(input)
      if (url === '/api/links') {
        return json(stored, 201)
      }
      if (url === '/api/qr-codes/check') {
        return json({
          verdict: 'MARGINAL',
          version: 3,
          modules: 29,
          trials: [],
          hints: ['Widen the margin.'],
        })
      }
      if (url.startsWith('/api/qr-codes?')) {
        return png()
      }
      throw new Error(`Unexpected request: ${url}`)
    })
    vi.stubGlobal('fetch', fetchMock)
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:preview')
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {})

    host = document.createElement('div')
    document.body.appendChild(host)
    app = createApp(QrApp)
    app.mount(host)

    button(host, 'Short link').click()
    await nextTick()

    const destination = host.querySelector('#shortlink-destination')
    if (!(destination instanceof HTMLInputElement)) {
      throw new Error('No destination field')
    }
    destination.value = 'example.com/page'
    destination.dispatchEvent(new Event('input', { bubbles: true }))
    await settle(300)

    // Typing alone renders a draft of the normalized destination.
    expect(lastRendered(fetchMock)).toBe('https://example.com/page')
    expect(host.querySelector('img[alt="QR code preview"]')?.getAttribute('src')).toBe(
      'blob:preview',
    )
    expect(host.querySelector('.notice')?.textContent).toContain('Draft preview')
    // The check follows the render and reports on the same payload.
    expect(host.querySelector('.scan-status')?.textContent).toContain('May not scan on every phone')
    expect(host.querySelector('.scan-hints')?.textContent).toContain('Widen the margin.')
    expect(button(host, 'Download PNG').disabled).toBe(true)
    expect(button(host, 'Download SVG').disabled).toBe(true)

    button(host, 'Create link').click()
    // Creation, then the 220 ms preview debounce.
    await settle(300)

    expect(fetchMock.mock.calls.map(([input]) => String(input))).toContain('/api/links')
    expect(lastRendered(fetchMock)).toBe(stored.shortUrl)
    expect(host.querySelector('.notice')).toBeNull()
    expect(button(host, 'Download PNG').disabled).toBe(false)
    expect(button(host, 'Download SVG').disabled).toBe(false)
  })
})
