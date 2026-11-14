import { afterEach, describe, expect, test, vi } from 'vitest'
import { createApp, h, nextTick, reactive, type App } from 'vue'
import type { EmbedSource } from '../embed'
import EmbedMenu from './EmbedMenu.vue'
import QrPreview from './QrPreview.vue'

let app: App | null = null
let host: HTMLElement | null = null

/** A PNG header declaring 64×64, padded to `padding` extra bytes. */
function png(padding = 0): Blob {
  const bytes = new Uint8Array(33 + padding)
  bytes.set([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0, 0, 0, 13, 0x49, 0x48, 0x44, 0x52])
  const view = new DataView(bytes.buffer)
  view.setUint32(16, 64)
  view.setUint32(20, 64)
  return new Blob([bytes as BlobPart], { type: 'image/png' })
}

const SVG =
  '<?xml version="1.0" encoding="UTF-8"?>\n<svg xmlns="http://www.w3.org/2000/svg" role="img"><title>QR code</title><linearGradient id="qr-gradient"/><rect fill="url(#qr-gradient)"/></svg>\n'

function source(overrides: Partial<EmbedSource> = {}): EmbedSource {
  return {
    render: vi.fn(async (format: 'png' | 'svg') =>
      format === 'svg' ? new Blob([SVG], { type: 'image/svg+xml' }) : png(),
    ),
    alt: 'QR code linking to example.com',
    href: 'https://example.com/',
    ...overrides,
  }
}

function stubClipboard(writeText?: (text: string) => Promise<void>): void {
  Object.defineProperty(navigator, 'clipboard', {
    configurable: true,
    value: writeText ? { writeText } : undefined,
  })
}

function mount(props: { source: EmbedSource; disabled: boolean }): HTMLElement {
  host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp({ render: () => h(EmbedMenu, props) })
  app.mount(host)
  return host
}

function copyButton(container: HTMLElement): HTMLButtonElement {
  const target = [...container.querySelectorAll('button')].find(
    (candidate) => candidate.textContent?.trim() === 'Copy',
  )
  if (!(target instanceof HTMLButtonElement)) {
    throw new Error('No Copy button')
  }
  return target
}

async function choose(container: HTMLElement, value: string): Promise<void> {
  const select = container.querySelector('select')
  if (!(select instanceof HTMLSelectElement)) {
    throw new Error('No format select')
  }
  select.value = value
  select.dispatchEvent(new Event('change'))
  await nextTick()
}

async function settle(): Promise<void> {
  for (let i = 0; i < 5; i++) {
    await new Promise((resolve) => setTimeout(resolve, 0))
    await nextTick()
  }
}

afterEach(() => {
  app?.unmount()
  host?.remove()
  app = null
  host = null
  stubClipboard()
  vi.restoreAllMocks()
})

describe('EmbedMenu', () => {
  test('is a labelled select with a live status region', () => {
    const container = mount({ source: source(), disabled: false })
    const select = container.querySelector('select')
    expect(container.querySelector(`label[for="${select?.id}"]`)?.textContent).toBe('Copy as')
    expect(container.querySelector('[aria-live="polite"]')).not.toBeNull()
    expect([...(select?.options ?? [])].map((option) => option.value)).toEqual([
      'img',
      'svg',
      'dataurl',
      'link',
    ])
  })

  test('copies an img tag and announces it', async () => {
    const writeText = vi.fn(async (_text: string) => {})
    stubClipboard(writeText)
    const embed = source()
    const container = mount({ source: embed, disabled: false })

    copyButton(container).click()
    await settle()

    expect(embed.render).toHaveBeenCalledWith('png')
    expect(writeText).toHaveBeenCalledOnce()
    expect(writeText.mock.calls[0][0]).toMatch(
      /^<img src="data:image\/png;base64,[^"]+" alt="QR code linking to example\.com" width="64" height="64">$/,
    )
    expect(container.querySelector('[aria-live="polite"]')?.textContent?.trim()).toBe('Copied')
    expect(container.querySelector('textarea')).toBeNull()
  })

  test('copies inline SVG fetched as SVG', async () => {
    const writeText = vi.fn(async (_text: string) => {})
    stubClipboard(writeText)
    const embed = source()
    const container = mount({ source: embed, disabled: false })

    await choose(container, 'svg')
    copyButton(container).click()
    await settle()

    expect(embed.render).toHaveBeenCalledWith('svg')
    const text = writeText.mock.calls[0][0]
    expect(text.startsWith('<svg ')).toBe(true)
    expect(text).toMatch(/id="qr[0-9a-z]{6}-qr-gradient"/)
  })

  test('copies the linked img for a URL', async () => {
    const writeText = vi.fn(async (_text: string) => {})
    stubClipboard(writeText)
    const container = mount({ source: source(), disabled: false })

    await choose(container, 'link')
    copyButton(container).click()
    await settle()

    expect(writeText.mock.calls[0][0]).toMatch(/^<a href="https:\/\/example\.com\/"><img [^>]+><\/a>$/)
  })

  test('has no linked option without a URL', () => {
    const container = mount({ source: source({ href: null }), disabled: false })
    const values = [...container.querySelectorAll('option')].map((option) => option.value)
    expect(values).not.toContain('link')
  })

  test('is disabled when told to be', () => {
    const embed = source()
    const container = mount({ source: embed, disabled: true })
    expect(copyButton(container).disabled).toBe(true)
    expect(container.querySelector('select')?.disabled).toBe(true)
  })

  test('warns about a large data URL without blocking the copy', async () => {
    const writeText = vi.fn(async (_text: string) => {})
    stubClipboard(writeText)
    const container = mount({
      source: source({ render: async () => png(120 * 1024) }),
      disabled: false,
    })

    copyButton(container).click()
    await settle()

    expect(writeText).toHaveBeenCalledOnce()
    expect(container.querySelector('[aria-live="polite"]')?.textContent?.trim()).toBe('Copied')
    const warning = container.querySelector('.embed-warning')?.textContent ?? ''
    expect(warning).toMatch(/This snippet is \d+ KB/)
    expect(warning).toContain('inline SVG')
  })

  test('falls back to a selectable textarea when the clipboard refuses', async () => {
    stubClipboard(async () => {
      throw new DOMException('Denied', 'NotAllowedError')
    })
    const container = mount({ source: source(), disabled: false })

    copyButton(container).click()
    await settle()

    const status = container.querySelector('[aria-live="polite"]')?.textContent ?? ''
    expect(status).toContain('blocked clipboard access')
    const textarea = container.querySelector('textarea')
    expect(textarea?.readOnly).toBe(true)
    expect(textarea?.value).toMatch(/^<img src="data:image\/png;base64,/)
    expect(container.querySelector(`label[for="${textarea?.id}"]`)).not.toBeNull()
  })

  test('falls back to a textarea when there is no clipboard API', async () => {
    stubClipboard()
    const container = mount({ source: source(), disabled: false })

    copyButton(container).click()
    await settle()

    expect(container.querySelector('[aria-live="polite"]')?.textContent).toContain('needs HTTPS')
    expect(container.querySelector('textarea')?.value).toMatch(/^<img /)
  })

  test('reports a render failure', async () => {
    stubClipboard(async () => {})
    const container = mount({
      source: source({
        render: async () => {
          throw new Error('The style is not valid.')
        },
      }),
      disabled: false,
    })

    copyButton(container).click()
    await settle()

    expect(container.querySelector('[aria-live="polite"]')?.textContent?.trim()).toBe(
      'The style is not valid.',
    )
  })
})

describe('QrPreview with embed snippets', () => {
  function mountPreview(props: Record<string, unknown>): HTMLElement {
    host = document.createElement('div')
    document.body.appendChild(host)
    const state = reactive(props)
    app = createApp({ render: () => h(QrPreview, state as never) })
    app.mount(host)
    return host
  }

  const base = { url: 'blob:preview', state: 'ready', error: '', payload: 'https://example.com/', embed: source() }

  test('enables Copy when the download would be enabled', () => {
    expect(copyButton(mountPreview(base)).disabled).toBe(false)
  })

  test('disables Copy for a short link draft', () => {
    expect(copyButton(mountPreview({ ...base, draft: true, downloadable: false })).disabled).toBe(true)
  })

  test('disables Copy after an error', () => {
    expect(copyButton(mountPreview({ ...base, url: '', state: 'error', error: 'Bad style' })).disabled).toBe(
      true,
    )
  })

  test('disables Copy while rendering', () => {
    expect(copyButton(mountPreview({ ...base, state: 'loading' })).disabled).toBe(true)
  })

  test('shows no menu without an embed source', () => {
    const container = mountPreview({ ...base, embed: null })
    expect(container.querySelector('.embed-menu')).toBeNull()
  })
})
