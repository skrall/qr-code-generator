import { afterEach, describe, expect, test, vi } from 'vitest'
import { createApp, h, nextTick, type App } from 'vue'
import type { LinkPage, ShortLink } from '../api/client'
import LinksView from './LinksView.vue'

let app: App | null = null
let host: HTMLElement | null = null

function link(overrides: Partial<ShortLink> = {}): ShortLink {
  return {
    code: 'abc123XYZ',
    shortUrl: 'http://localhost:8080/r/abc123XYZ',
    targetUrl: 'https://example.com/one',
    createdAt: '2026-01-01T00:00:00Z',
    expiresAt: null,
    hitCount: 0,
    lastHitAt: null,
    ...overrides,
  }
}

function page(items: ShortLink[], total = items.length, index = 0): LinkPage {
  return { items, total, page: index, size: 25 }
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

function mount(operator = true): HTMLElement {
  host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp({ render: () => h(LinksView, { operator }) })
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

async function flush(): Promise<void> {
  await new Promise((resolve) => setTimeout(resolve, 0))
  await nextTick()
}

/** Sets a text field or textarea the way typing would. */
async function fill(container: HTMLElement, selector: string, value: string): Promise<void> {
  const field = container.querySelector(selector)
  if (!(field instanceof HTMLInputElement || field instanceof HTMLTextAreaElement)) {
    throw new Error(`No ${selector} field`)
  }
  field.value = value
  field.dispatchEvent(new Event('input', { bubbles: true }))
  await nextTick()
}

/** Long enough for the 250 ms search debounce to fire. */
async function settle(): Promise<void> {
  await new Promise((resolve) => setTimeout(resolve, 300))
  await nextTick()
}

afterEach(() => {
  app?.unmount()
  host?.remove()
  app = null
  host = null
  vi.unstubAllGlobals()
})

describe('LinksView', () => {
  test('lists the first page newest first', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(
        response(
          page([
            link({ code: 'newer', targetUrl: 'https://example.com/two', hitCount: 3 }),
            link({ code: 'older', targetUrl: 'https://example.com/one' }),
          ]),
        ),
      )
    vi.stubGlobal('fetch', fetchMock)
    const container = mount()
    await flush()

    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/links?page=0&size=25')
    expect(container.querySelectorAll('.links-table tbody tr')).toHaveLength(2)
    expect(container.textContent).toContain('https://example.com/two')
    expect(container.textContent).toContain('Page 1 of 1 · 2 links')
  })

  test('says so when there is nothing stored', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response(page([]))))
    const container = mount()
    await flush()

    expect(container.textContent).toContain('No links yet')
  })

  test('searches after the typing pause and resets to the first page', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(response(page([link()])))
      .mockResolvedValueOnce(response(page([link({ code: 'match' })])))
    vi.stubGlobal('fetch', fetchMock)
    const container = mount()
    await flush()

    const search = container.querySelector('#links-search')
    if (!(search instanceof HTMLInputElement)) {
      throw new Error('No search field')
    }
    search.value = 'match'
    search.dispatchEvent(new Event('input', { bubbles: true }))
    await settle()

    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(fetchMock.mock.calls[1]?.[0]).toBe('/api/links?page=0&size=25&q=match')
    expect(container.textContent).toContain('/r/abc123XYZ')
  })

  test('edits a destination without changing the code and reloads the list', async () => {
    const updated = link({ targetUrl: 'https://example.com/two' })
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(response(page([link()])))
      .mockResolvedValueOnce(response(updated))
      .mockResolvedValueOnce(response(page([updated])))
    vi.stubGlobal('fetch', fetchMock)
    const container = mount()
    await flush()

    button(container, 'Edit').click()
    await nextTick()

    const target = container.querySelector('#edit-link-target')
    if (!(target instanceof HTMLInputElement)) {
      throw new Error('No edit destination field')
    }
    expect(target.value).toBe('https://example.com/one')
    target.value = 'example.com/two'
    target.dispatchEvent(new Event('input', { bubbles: true }))
    await nextTick()

    button(container, 'Save changes').click()
    await flush()

    const [url, init] = fetchMock.mock.calls[1] as [string, RequestInit]
    expect(url).toBe('/api/links/abc123XYZ')
    expect(init.method).toBe('PUT')
    expect(JSON.parse(String(init.body))).toEqual({ url: 'https://example.com/two' })
    expect(container.querySelector('#edit-link-target')).toBeNull()
    expect(container.textContent).toContain('https://example.com/two')
  })

  test('refuses an expiry that is already past', async () => {
    const fetchMock = vi.fn().mockResolvedValueOnce(response(page([link()])))
    vi.stubGlobal('fetch', fetchMock)
    const container = mount()
    await flush()

    button(container, 'Edit').click()
    await nextTick()

    const expiry = container.querySelector('#edit-link-expiry')
    if (!(expiry instanceof HTMLInputElement)) {
      throw new Error('No edit expiry field')
    }
    expiry.value = '2000-01-01T00:00'
    expiry.dispatchEvent(new Event('input', { bubbles: true }))
    await nextTick()

    expect(button(container, 'Save changes').disabled).toBe(true)
    expect(container.querySelector('.edit-dialog .error-text')?.textContent).toContain('already past')
    expect(fetchMock).toHaveBeenCalledOnce()
  })

  test('deletes only on the second click and reloads the list', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(response(page([link()])))
      .mockResolvedValueOnce(response(null, 204))
      .mockResolvedValueOnce(response(page([])))
    vi.stubGlobal('fetch', fetchMock)
    const container = mount()
    await flush()

    const remove = button(container, 'Delete')
    remove.click()
    await nextTick()

    expect(remove.textContent?.trim()).toBe('Confirm')
    expect(fetchMock).toHaveBeenCalledOnce()

    remove.click()
    await flush()

    expect(fetchMock.mock.calls[1]).toEqual([
      '/api/links/abc123XYZ',
      expect.objectContaining({ method: 'DELETE' }),
    ])
    expect(container.textContent).toContain('No links yet')
  })

  test('lists links for a viewer without the edit and delete actions', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response(page([link()]))))

    const container = mount(false)
    await flush()

    expect(container.querySelector('.links-table')).not.toBeNull()
    expect(container.textContent).toContain('abc123XYZ')
    const labels = [...container.querySelectorAll('button')].map((b) => b.textContent?.trim())
    expect(labels).not.toContain('Edit')
    expect(labels).not.toContain('Delete')
  })

  test('pages through a longer list', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(response(page([link()], 30, 0)))
      .mockResolvedValueOnce(response(page([link({ code: 'second' })], 30, 1)))
    vi.stubGlobal('fetch', fetchMock)
    const container = mount()
    await flush()

    expect(container.textContent).toContain('Page 1 of 2 · 30 links')

    button(container, 'Next').click()
    await flush()

    expect(fetchMock.mock.calls[1]?.[0]).toBe('/api/links?page=1&size=25')
    expect(container.textContent).toContain('Page 2 of 2')
    expect(button(container, 'Next').disabled).toBe(true)
  })

  test('edits the gone options, opened when the link already has one', async () => {
    const stored = link({ goneUrl: null, goneMessage: 'Old message' })
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(response(page([stored])))
      .mockResolvedValueOnce(response(stored))
      .mockResolvedValueOnce(response(page([stored])))
    vi.stubGlobal('fetch', fetchMock)
    const container = mount()
    await flush()

    button(container, 'Edit').click()
    await nextTick()

    const details = container.querySelector('.edit-dialog details.gone-options')
    expect(details?.hasAttribute('open')).toBe(true)
    const message = container.querySelector('#edit-link-gone-message')
    expect(message instanceof HTMLTextAreaElement && message.value).toBe('Old message')

    await fill(container, '#edit-link-gone-url', 'example.com/after')
    await fill(container, '#edit-link-gone-message', '')
    button(container, 'Save changes').click()
    await flush()

    const init = fetchMock.mock.calls[1]?.[1] as RequestInit
    expect(JSON.parse(String(init.body))).toEqual({
      url: 'https://example.com/one',
      goneUrl: 'https://example.com/after',
    })
  })

  test('keeps the gone options folded for a link without any', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(response(page([link()]))))
    const container = mount()
    await flush()

    button(container, 'Edit').click()
    await nextTick()

    expect(container.querySelector('.edit-dialog details.gone-options')?.hasAttribute('open')).toBe(
      false,
    )
  })
})
