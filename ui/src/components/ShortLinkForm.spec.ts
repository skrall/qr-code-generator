import { afterEach, describe, expect, test, vi } from 'vitest'
import { createApp, h, nextTick, type App } from 'vue'
import type { ShortLink } from '../api/client'
import ShortLinkForm from './ShortLinkForm.vue'

let app: App | null = null
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

/** A response shaped like the ones the API client reads, without depending on a fetch polyfill. */
function response(body: unknown, status = 200): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    statusText: '',
    json: async () => body,
  } as Response
}

function mount(
  handlers: {
    created?: (link: ShortLink) => void
    cleared?: () => void
    draft?: (url: string) => void
  } = {},
): HTMLElement {
  host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp({
    render: () =>
      h(ShortLinkForm, {
        onCreated: handlers.created,
        onCleared: handlers.cleared,
        onDraft: handlers.draft,
      }),
  })
  app.mount(host)
  return host
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

async function type(container: HTMLElement, value: string): Promise<void> {
  const field = container.querySelector('#shortlink-destination')
  if (!(field instanceof HTMLInputElement)) {
    throw new Error('No destination field')
  }
  field.value = value
  field.dispatchEvent(new Event('input', { bubbles: true }))
  await nextTick()
}

/** Lets the async click handler finish: one macrotask covers its promise chain. */
async function flush(): Promise<void> {
  await new Promise((resolve) => setTimeout(resolve, 0))
  await nextTick()
}

afterEach(() => {
  app?.unmount()
  host?.remove()
  app = null
  host = null
  vi.unstubAllGlobals()
})

describe('ShortLinkForm', () => {
  test('creates a link and shows the short URL', async () => {
    const created: ShortLink[] = []
    const fetchMock = vi.fn().mockResolvedValue(response(stored, 201))
    vi.stubGlobal('fetch', fetchMock)
    const container = mount({ created: (link) => created.push(link) })

    await type(container, 'https://example.com/page')
    button(container, 'Create link').click()
    await flush()

    expect(fetchMock).toHaveBeenCalledOnce()
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('/api/links')
    expect(init.method).toBe('POST')
    expect(JSON.parse(String(init.body))).toEqual({ url: 'https://example.com/page' })
    expect(created).toEqual([stored])
    expect(container.querySelector('.short-link-result code')?.textContent).toBe(stored.shortUrl)
  })

  test('sends a preset expiry as an ISO timestamp', async () => {
    const fetchMock = vi.fn().mockResolvedValue(response(stored, 201))
    vi.stubGlobal('fetch', fetchMock)
    const container = mount()

    await type(container, 'https://example.com')
    const select = container.querySelector('#shortlink-expiry')
    if (!(select instanceof HTMLSelectElement)) {
      throw new Error('No expiry select')
    }
    select.value = '7d'
    select.dispatchEvent(new Event('change', { bubbles: true }))
    await nextTick()

    button(container, 'Create link').click()
    await flush()

    const init = fetchMock.mock.calls[0]?.[1] as RequestInit
    const expiresAt = JSON.parse(String(init.body)).expiresAt as string
    const days = (new Date(expiresAt).getTime() - Date.now()) / (24 * 60 * 60 * 1000)
    expect(days).toBeGreaterThan(6.9)
    expect(days).toBeLessThan(7.1)
  })

  test('shows the server message when the destination is rejected', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        response({ detail: 'url must be an absolute http or https URL' }, 400),
      ),
    )
    const container = mount()

    await type(container, 'javascript:alert(1)')
    button(container, 'Create link').click()
    await flush()

    expect(container.querySelector('.error')?.textContent).toContain('http or https')
    expect(container.querySelector('.short-link-result')).toBeNull()
  })

  test('refuses a custom expiry that is already past', async () => {
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
    const container = mount()

    await type(container, 'https://example.com')

    const select = container.querySelector('#shortlink-expiry')
    if (!(select instanceof HTMLSelectElement)) {
      throw new Error('No expiry select')
    }
    select.value = 'custom'
    select.dispatchEvent(new Event('change', { bubbles: true }))
    await nextTick()

    const custom = container.querySelector('#shortlink-custom-expiry')
    if (!(custom instanceof HTMLInputElement)) {
      throw new Error('No custom expiry field')
    }
    custom.value = '2000-01-01T00:00'
    custom.dispatchEvent(new Event('input', { bubbles: true }))
    await nextTick()

    expect(button(container, 'Create link').disabled).toBe(true)
    expect(container.querySelector('.error-text')?.textContent).toContain('already past')
    expect(fetchMock).not.toHaveBeenCalled()
  })

  test('creates the link when the form is submitted', async () => {
    const fetchMock = vi.fn().mockResolvedValue(response(stored, 201))
    vi.stubGlobal('fetch', fetchMock)
    const container = mount()

    await type(container, 'https://example.com/page')

    const form = container.querySelector('form')
    if (!(form instanceof HTMLFormElement)) {
      throw new Error('No form')
    }
    form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }))
    await flush()

    expect(fetchMock).toHaveBeenCalledOnce()
  })

  test('emits the destination as a draft while typing', async () => {
    const drafts: string[] = []
    const container = mount({ draft: (url) => drafts.push(url) })

    await type(container, 'example.com/page')

    expect(drafts).toEqual(['https://example.com/page'])
  })

  test('normalizes a bare host before creating the link', async () => {
    const fetchMock = vi.fn().mockResolvedValue(response(stored, 201))
    vi.stubGlobal('fetch', fetchMock)
    const container = mount()

    await type(container, 'example.com/page')
    button(container, 'Create link').click()
    await flush()

    const init = fetchMock.mock.calls[0]?.[1] as RequestInit
    expect(JSON.parse(String(init.body))).toEqual({ url: 'https://example.com/page' })
  })

  test('drops the shown link when the destination changes', async () => {
    let cleared = 0
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response(stored, 201)))
    const container = mount({ cleared: () => (cleared += 1) })

    await type(container, 'https://example.com/page')
    button(container, 'Create link').click()
    await flush()
    expect(container.querySelector('.short-link-result')).not.toBeNull()

    await type(container, 'https://example.com/other')

    expect(container.querySelector('.short-link-result')).toBeNull()
    expect(cleared).toBe(1)
  })
})
