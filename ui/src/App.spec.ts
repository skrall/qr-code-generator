import { afterEach, describe, expect, test, vi } from 'vitest'
import { createApp, nextTick, type App as VueApp } from 'vue'
import type { ShortLink } from './api/client'
import QrApp from './App.vue'
import { encodeShare } from './share'

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
      if (url === '/api/me') {
        return json({ name: 'operator', operator: true })
      }
      if (url === '/api/designs') {
        return json([])
      }
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
    // Copy as… follows the downloads: a draft cannot be embedded either.
    expect(button(host, 'Copy').disabled).toBe(true)

    button(host, 'Create link').click()
    // Creation, then the 220 ms preview debounce.
    await settle(300)

    expect(fetchMock.mock.calls.map(([input]) => String(input))).toContain('/api/links')
    expect(lastRendered(fetchMock)).toBe(stored.shortUrl)
    expect(host.querySelector('.notice')).toBeNull()
    expect(button(host, 'Download PNG').disabled).toBe(false)
    expect(button(host, 'Download SVG').disabled).toBe(false)
    expect(button(host, 'Copy').disabled).toBe(false)
    // The short URL can be wrapped in a link.
    const formats = [...host.querySelectorAll('#embed-format option')].map((o) => o.getAttribute('value'))
    expect(formats).toContain('link')
  })
})

describe('App for a viewer', () => {
  test('shows who is signed in and keeps Create link disabled', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL) => {
        const url = String(input)
        if (url === '/api/me') {
          return json({ name: 'reader', operator: false })
        }
        if (url === '/api/designs') {
          return json([])
        }
        if (url === '/api/qr-codes/check') {
          return json({ verdict: 'PASS', version: 3, modules: 29, trials: [], hints: [] })
        }
        if (url.startsWith('/api/qr-codes?')) {
          return png()
        }
        throw new Error(`Unexpected request: ${url}`)
      }),
    )
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:preview')
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {})

    host = document.createElement('div')
    document.body.appendChild(host)
    app = createApp(QrApp)
    app.mount(host)
    await settle()

    expect(host.querySelector('.signed-in')?.textContent).toContain('reader')
    expect(host.querySelector('.signed-in')?.textContent).toContain('viewer')

    button(host, 'Short link').click()
    await nextTick()
    const destination = host.querySelector('#shortlink-destination')
    if (!(destination instanceof HTMLInputElement)) {
      throw new Error('No destination field')
    }
    destination.value = 'example.com/page'
    destination.dispatchEvent(new Event('input', { bubbles: true }))
    await settle(300)

    expect(button(host, 'Create link').disabled).toBe(true)
    expect(host.textContent).toContain('Creating short links needs the operator role')
  })
})

/** A server for the generator: everyone is an operator, nothing is saved. */
function stubServer(): ReturnType<typeof vi.fn> {
  const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
    const url = String(input)
    if (url === '/api/me') {
      return json({ name: null, operator: true })
    }
    if (url === '/api/designs') {
      return json([])
    }
    if (url === '/api/qr-codes/check') {
      return json({ verdict: 'PASS', version: 3, modules: 29, trials: [], hints: [] })
    }
    if (url.startsWith('/api/qr-codes?')) {
      return png()
    }
    throw new Error(`Unexpected request: ${url}`)
  })
  vi.stubGlobal('fetch', fetchMock)
  vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:preview')
  vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {})
  return fetchMock
}

/** The style of the most recent preview request. */
function lastStyle(fetchMock: ReturnType<typeof vi.fn>): Record<string, unknown> | undefined {
  const call = fetchMock.mock.calls
    .filter(([input]) => String(input).startsWith('/api/qr-codes?'))
    .at(-1)
  return call
    ? (JSON.parse(String((call[1] as RequestInit).body)).style as Record<string, unknown>)
    : undefined
}

function mountApp(): HTMLElement {
  host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp(QrApp)
  app.mount(host)
  return host
}

function typeInto(container: HTMLElement, selector: string, value: string): HTMLInputElement {
  const input = container.querySelector(selector)
  if (!(input instanceof HTMLInputElement)) {
    throw new Error(`No ${selector}`)
  }
  input.value = value
  input.dispatchEvent(new Event('input', { bubbles: true }))
  return input
}

function ctrlZ(target: EventTarget, shift = false): KeyboardEvent {
  const event = new KeyboardEvent('keydown', {
    key: shift ? 'Z' : 'z',
    ctrlKey: true,
    shiftKey: shift,
    bubbles: true,
    cancelable: true,
  })
  target.dispatchEvent(event)
  return event
}

describe('App undo and redo', () => {
  test('undoes a typed word as one step, from the buttons and the keyboard', async () => {
    stubServer()
    const container = mountApp()
    await settle()
    expect(button(container, 'Undo').disabled).toBe(true)
    expect(button(container, 'Redo').disabled).toBe(true)

    for (const text of ['e', 'ex', 'exa', 'example.com']) {
      typeInto(container, '#url-url', text)
      await settle(50)
    }
    await settle(500)
    expect(button(container, 'Undo').disabled).toBe(false)

    button(container, 'Undo').click()
    await settle()
    expect((container.querySelector('#url-url') as HTMLInputElement).value).toBe('')
    expect(button(container, 'Redo').disabled).toBe(false)

    // Outside a text field the shortcut is the editor's.
    const handled = ctrlZ(document.body, true)
    await settle()
    expect(handled.defaultPrevented).toBe(true)
    expect((container.querySelector('#url-url') as HTMLInputElement).value).toBe('example.com')
  })

  test('leaves Ctrl+Z to a focused text field', async () => {
    stubServer()
    const container = mountApp()
    await settle()
    const input = typeInto(container, '#url-url', 'example.com')
    await settle(500)

    const event = ctrlZ(input)
    await settle()
    expect(event.defaultPrevented).toBe(false)
    expect(input.value).toBe('example.com')
    expect(button(container, 'Undo').disabled).toBe(false)
  })
})

describe('App opening a share link', () => {
  afterEach(() => {
    window.history.replaceState(null, '', '/')
  })

  test('applies the fragment on startup as one undo step from the defaults', async () => {
    const fetchMock = stubServer()
    const token = await encodeShare({
      style: { moduleShape: 'STAR', foreground: '#0f172a' },
      content: { type: 'text', values: { text: 'Hello' } },
    })
    window.history.replaceState(null, '', `/#share=${token}`)

    const container = mountApp()
    await settle(300)

    expect(window.location.hash).toBe('')
    expect(lastRendered(fetchMock)).toBe('Hello')
    expect(lastStyle(fetchMock)?.moduleShape).toBe('STAR')
    expect(container.textContent).toContain('Opened a shared design and its content.')

    button(container, 'Undo').click()
    await settle(300)
    // Back on the default tab, with the default style: one step undid the whole link.
    expect(button(container, 'Website').getAttribute('aria-selected')).toBe('true')
    expect(button(container, 'Undo').disabled).toBe(true)
    expect(button(container, 'Redo').disabled).toBe(false)
    typeInto(container, '#url-url', 'example.com')
    await settle(300)
    expect(lastStyle(fetchMock)?.moduleShape).toBe('ROUNDED')
  })

  test('a corrupt fragment leaves the defaults and says why', async () => {
    const fetchMock = stubServer()
    window.history.replaceState(null, '', '/#share=v1.z.%%%')
    const container = mountApp()
    await settle(300)
    typeInto(container, '#url-url', 'example.com')
    await settle(300)

    expect(container.querySelector('.share-link [role="alert"]')?.textContent).toContain('damaged')
    expect(lastStyle(fetchMock)?.moduleShape).toBe('ROUNDED')
    expect(window.location.hash).toBe('')
  })
})
