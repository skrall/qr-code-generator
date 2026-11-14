import { afterEach, describe, expect, test, vi } from 'vitest'
import { createApp, h, nextTick, reactive, ref, type App } from 'vue'
import type { QrStyle } from '../api/client'
import { defaultStyle } from '../qr/style'
import { initialValues, QR_TYPES, type QrTypeId } from '../qr/types'
import { decodeShare, encodeShare, type CurrentDesign } from '../share'
import ShareLink from './ShareLink.vue'

let app: App | null = null
let host: HTMLElement | null = null

afterEach(() => {
  app?.unmount()
  host?.remove()
  app = null
  host = null
  window.history.replaceState(null, '', '/')
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function setup(options: { design?: CurrentDesign | null; hash?: string } = {}) {
  if (options.hash) {
    window.history.replaceState(null, '', `/${options.hash}`)
  }
  const qrStyle = reactive(defaultStyle())
  const values = reactive<Record<string, Record<string, string>>>(
    Object.fromEntries(QR_TYPES.map((type) => [type.id, initialValues(type)])),
  )
  const typeId = ref<QrTypeId>('url')
  const design = ref<CurrentDesign | null>(options.design ?? null)
  const applied = vi.fn()
  const copied: string[] = []
  vi.stubGlobal('navigator', {
    clipboard: { writeText: async (text: string) => void copied.push(text) },
  })
  host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp({
    render: () =>
      h(ShareLink, {
        qrStyle,
        values,
        design: design.value,
        typeId: typeId.value,
        'onUpdate:typeId': (next: QrTypeId) => (typeId.value = next),
        onApplied: applied,
      }),
  })
  app.mount(host)
  return { container: host, qrStyle, values, typeId, design, applied, copied }
}

async function flush(): Promise<void> {
  for (let i = 0; i < 6; i++) {
    await new Promise((resolve) => setTimeout(resolve, 0))
    await nextTick()
  }
}

function button(container: HTMLElement, label: string): HTMLButtonElement {
  const found = [...container.querySelectorAll('button')].find(
    (candidate) => candidate.textContent?.trim() === label,
  )
  if (!(found instanceof HTMLButtonElement)) throw new Error(`No ${label} button`)
  return found
}

function tokenOf(url: string): string {
  const match = /#share=(.+)$/.exec(url)
  if (!match) throw new Error(`Not a share link: ${url}`)
  return match[1]
}

const logo = (): QrStyle['logo'] => ({
  dataUrl: `data:image/png;base64,${'A'.repeat(4000)}`,
  sizePercent: 20,
  paddingPercent: 8,
  background: '#ffffff',
  radiusPercent: 25,
})

describe('ShareLink copying', () => {
  test('copies a fragment link with the style and no content by default', async () => {
    const { container, qrStyle, values, copied } = setup()
    qrStyle.moduleShape = 'DOTS'
    values.url.url = 'example.com/private'
    await nextTick()

    button(container, 'Copy share link').click()
    await flush()

    expect(copied).toHaveLength(1)
    expect(copied[0].startsWith(`${window.location.origin}/#share=v1.`)).toBe(true)
    const payload = await decodeShare(tokenOf(copied[0]))
    expect(payload.style.moduleShape).toBe('DOTS')
    expect(payload.content).toBeUndefined()
    expect(container.textContent).toContain('Copied a link to this design.')
    expect((container.querySelector('.share-url') as HTMLInputElement).value).toBe(copied[0])
  })

  test('includes content when asked, but never the WiFi password', async () => {
    const { container, values, typeId, copied } = setup()
    typeId.value = 'wifi'
    Object.assign(values.wifi, { ssid: 'Cafe', password: 'secret-pass' })
    await nextTick()
    expect(container.textContent).toContain('The password is never included.')

    const checkbox = container.querySelector('input[type="checkbox"]') as HTMLInputElement
    checkbox.checked = true
    checkbox.dispatchEvent(new Event('change'))
    await nextTick()
    button(container, 'Copy share link').click()
    await flush()

    const payload = await decodeShare(tokenOf(copied[0]))
    expect(payload.content).toEqual({ type: 'wifi', values: { ssid: 'Cafe', encryption: 'WPA' } })
    expect(copied[0]).not.toContain('secret-pass')
    expect(container.textContent).toContain('Password left out.')
  })

  test('refuses a link longer than the limit', async () => {
    const { container, values, typeId, copied } = setup()
    typeId.value = 'vcard'
    let seed = 1
    const alphabet = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789'
    values.vcard.note = Array.from({ length: 12000 }, () => {
      seed = (seed * 48271) % 2147483647
      return alphabet[seed % alphabet.length]
    }).join('')
    await nextTick()
    const checkbox = container.querySelector('input[type="checkbox"]') as HTMLInputElement
    checkbox.checked = true
    checkbox.dispatchEvent(new Event('change'))
    await nextTick()

    button(container, 'Copy share link').click()
    await flush()
    expect(copied).toHaveLength(0)
    expect(container.querySelector('[role="alert"]')?.textContent).toContain('Leave the content out')
  })

  test('with images, links to the saved design instead', async () => {
    const { container, qrStyle, design, copied } = setup({
      design: { id: 7, name: 'Brand', shared: false, unchanged: true },
    })
    qrStyle.logo = logo()
    await nextTick()
    expect(container.textContent).toContain('A link cannot carry the logo')

    button(container, 'Copy design link').click()
    await flush()
    expect(copied).toEqual([`${window.location.origin}/#design=7`])
    expect(container.textContent).toContain('It is private')

    design.value = { id: 7, name: 'Brand', shared: false, unchanged: false }
    await nextTick()
    button(container, 'Copy design link').click()
    await flush()
    expect(copied).toHaveLength(1)
    expect(container.querySelector('[role="alert"]')?.textContent).toContain(
      'Save your changes to “Brand”',
    )
  })

  test('with images and no saved design, asks for one to be saved', async () => {
    const { container, qrStyle, copied } = setup()
    qrStyle.backgroundImage = { dataUrl: logo()!.dataUrl }
    await nextTick()
    button(container, 'Copy design link').click()
    await flush()
    expect(copied).toHaveLength(0)
    expect(container.querySelector('[role="alert"]')?.textContent).toContain(
      'Save this as a design',
    )
  })
})

describe('ShareLink opening', () => {
  test('applies a share fragment once and clears it from the address bar', async () => {
    const token = await encodeShare({
      style: { moduleShape: 'LIQUID', foreground: '#222222' },
      content: { type: 'wifi', values: { ssid: 'Guest' } },
    })
    const { container, qrStyle, values, typeId, applied } = setup({ hash: `#share=${token}` })
    await flush()

    expect(qrStyle.moduleShape).toBe('LIQUID')
    expect(qrStyle.foreground).toBe('#222222')
    expect(typeId.value).toBe('wifi')
    expect(values.wifi).toEqual({ encryption: 'WPA', ssid: 'Guest' })
    expect(applied).toHaveBeenCalledTimes(1)
    expect(window.location.hash).toBe('')
    expect(container.textContent).toContain('never carry the password')
  })

  test('reports a damaged fragment without touching the editor', async () => {
    const { container, qrStyle, applied } = setup({ hash: '#share=v1.z.garbage!' })
    await flush()
    expect(container.querySelector('[role="alert"]')?.textContent).toContain('damaged')
    expect(qrStyle).toEqual(defaultStyle())
    expect(applied).not.toHaveBeenCalled()
    expect(window.location.hash).toBe('')
  })

  test('reports a link from a newer version', async () => {
    const { container } = setup({ hash: '#share=v9.z.AAAA' })
    await flush()
    expect(container.querySelector('[role="alert"]')?.textContent).toContain('newer version')
  })

  test('loads a design link through the API', async () => {
    const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
      if (String(input) === '/api/designs/12') {
        return json({ id: 12, name: 'Team', style: { moduleShape: 'STAR' } })
      }
      throw new Error(`Unexpected ${String(input)}`)
    })
    vi.stubGlobal('fetch', fetchMock)
    const { container, qrStyle, applied } = setup({ hash: '#design=12' })
    await flush()
    expect(qrStyle.moduleShape).toBe('STAR')
    expect(applied).toHaveBeenCalledTimes(1)
    expect(container.textContent).toContain('Opened the design “Team”.')
  })

  test("says when a design is missing or someone else's private one", async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => json({ detail: 'No design with id 13.' }, 404)),
    )
    const { container, qrStyle, applied } = setup({ hash: '#design=13' })
    await flush()
    expect(container.querySelector('[role="alert"]')?.textContent).toBe(
      'That design was not found or is not shared with you.',
    )
    expect(qrStyle).toEqual(defaultStyle())
    expect(applied).not.toHaveBeenCalled()
  })

  test('opens a link pasted into the same tab', async () => {
    const { qrStyle } = setup()
    await flush()
    window.history.replaceState(null, '', `/#share=${await encodeShare({ style: { size: 512 } })}`)
    window.dispatchEvent(new HashChangeEvent('hashchange'))
    await flush()
    expect(qrStyle.size).toBe(512)
  })
})
