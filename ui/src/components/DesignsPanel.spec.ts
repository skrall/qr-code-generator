import { afterEach, describe, expect, test, vi } from 'vitest'
import { createApp, h, nextTick, reactive, type App } from 'vue'
import type { Design, DesignSummary, QrStyle } from '../api/client'
import { defaultStyle } from '../qr/style'
import DesignsPanel from './DesignsPanel.vue'

let app: App | null = null
let host: HTMLElement | null = null

function summary(overrides: Partial<DesignSummary> = {}): DesignSummary {
  return {
    id: 7,
    name: 'Brand',
    owner: 'alice',
    shared: false,
    mine: true,
    editable: true,
    createdAt: '2026-11-01T00:00:00Z',
    updatedAt: '2026-11-01T00:00:00Z',
    ...overrides,
  }
}

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

/** Answers the design API from a list and a single design, recording every call. */
function stubApi(list: DesignSummary[], design: Design): ReturnType<typeof vi.fn> {
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input)
    const method = init?.method ?? 'GET'
    if (url === '/api/designs' && method === 'GET') return json(list)
    if (url === '/api/designs' && method === 'POST') {
      const body = JSON.parse(String(init?.body)) as { name: string; shared: boolean }
      return json({ ...design, id: 99, name: body.name, shared: body.shared }, 201)
    }
    if (url === `/api/designs/${design.id}` && method === 'GET') return json(design)
    if (url === `/api/designs/${design.id}` && method === 'PUT') {
      const body = JSON.parse(String(init?.body)) as { name: string; shared: boolean }
      return json({ ...design, name: body.name, shared: body.shared })
    }
    if (url === `/api/designs/${design.id}` && method === 'DELETE') return new Response(null, { status: 204 })
    throw new Error(`Unexpected ${method} ${url}`)
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

function mount(qrStyle: QrStyle): HTMLElement {
  host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp({ render: () => h(DesignsPanel, { qrStyle }) })
  app.mount(host)
  return host
}

async function flush(): Promise<void> {
  for (let i = 0; i < 5; i++) {
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

function hasButton(container: HTMLElement, label: string): boolean {
  return [...container.querySelectorAll('button')].some((b) => b.textContent?.trim() === label)
}

async function choose(container: HTMLElement, id: number): Promise<void> {
  const select = container.querySelector('#design-select') as HTMLSelectElement
  select.value = String(id)
  select.dispatchEvent(new Event('change'))
  await nextTick()
}

function type(container: HTMLElement, value: string): void {
  const input = container.querySelector('#design-name') as HTMLInputElement
  input.value = value
  input.dispatchEvent(new Event('input'))
}

afterEach(() => {
  app?.unmount()
  host?.remove()
  app = null
  host = null
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

describe('DesignsPanel', () => {
  test('lists own and shared designs separately', async () => {
    stubApi(
      [summary(), summary({ id: 8, name: 'Team', mine: false, shared: true, owner: 'bob' })],
      { ...summary(), style: {} },
    )
    const container = mount(reactive(defaultStyle()))
    await flush()

    const groups = [...container.querySelectorAll('optgroup')].map((group) => group.label)
    expect(groups).toEqual(['Yours', 'Shared with everyone'])
    expect(container.textContent).toContain('Team — bob')
  })

  test('loads a design into the editor', async () => {
    stubApi([summary()], { ...summary(), style: { moduleShape: 'LIQUID', foreground: '#000000' } })
    const qrStyle = reactive(defaultStyle())
    const container = mount(qrStyle)
    await flush()

    await choose(container, 7)
    button(container, 'Load').click()
    await flush()

    expect(qrStyle.moduleShape).toBe('LIQUID')
    expect(qrStyle.foreground).toBe('#000000')
    expect((container.querySelector('#design-name') as HTMLInputElement).value).toBe('Brand')
    expect(container.textContent).toContain('Editing “Brand”')
    expect(button(container, 'Save changes').disabled).toBe(true)
  })

  test('saves the editor as a new design, style included', async () => {
    const fetchMock = stubApi([], { ...summary(), style: {} })
    const qrStyle = reactive(defaultStyle())
    qrStyle.moduleShape = 'STAR'
    const container = mount(qrStyle)
    await flush()

    type(container, '  Starry  ')
    await nextTick()
    button(container, 'Save as new').click()
    await flush()

    const post = fetchMock.mock.calls.find(([, init]) => init?.method === 'POST')
    const body = JSON.parse(String(post?.[1]?.body)) as { name: string; style: QrStyle }
    expect(body.name).toBe('Starry')
    expect(body.style.moduleShape).toBe('STAR')
    expect(container.textContent).toContain('Saved “Starry”.')
  })

  test('a rename alone does not resend the style', async () => {
    const fetchMock = stubApi([summary()], { ...summary(), style: { moduleShape: 'DOTS' } })
    const container = mount(reactive(defaultStyle()))
    await flush()
    await choose(container, 7)
    button(container, 'Load').click()
    await flush()

    type(container, 'Renamed')
    await nextTick()
    expect(container.textContent).toContain('unsaved changes')
    button(container, 'Save changes').click()
    await flush()

    const put = fetchMock.mock.calls.find(([, init]) => init?.method === 'PUT')
    const body = JSON.parse(String(put?.[1]?.body)) as Record<string, unknown>
    expect(body.name).toBe('Renamed')
    expect(body).not.toHaveProperty('style')
  })

  test('a changed style is sent with the update', async () => {
    const fetchMock = stubApi([summary()], { ...summary(), style: { moduleShape: 'DOTS' } })
    const qrStyle = reactive(defaultStyle())
    const container = mount(qrStyle)
    await flush()
    await choose(container, 7)
    button(container, 'Load').click()
    await flush()

    qrStyle.moduleShape = 'SQUARE'
    await nextTick()
    button(container, 'Save changes').click()
    await flush()

    const put = fetchMock.mock.calls.find(([, init]) => init?.method === 'PUT')
    const body = JSON.parse(String(put?.[1]?.body)) as { style: QrStyle }
    expect(body.style.moduleShape).toBe('SQUARE')
  })

  test("someone else's shared design can be copied but not changed", async () => {
    const theirs = summary({ mine: false, editable: false, shared: true, owner: 'bob' })
    stubApi([theirs], { ...theirs, style: {} })
    const container = mount(reactive(defaultStyle()))
    await flush()
    await choose(container, 7)
    button(container, 'Load').click()
    await flush()

    expect(hasButton(container, 'Save changes')).toBe(false)
    expect(hasButton(container, 'Delete')).toBe(false)
    expect(button(container, 'Save as new').classList.contains('primary')).toBe(true)
    expect(container.textContent).toContain('shared by bob; save a copy to keep your changes')
  })

  test('deletes only on the second click and keeps the style in the editor', async () => {
    const fetchMock = stubApi([summary()], { ...summary(), style: { moduleShape: 'DOTS' } })
    const qrStyle = reactive(defaultStyle())
    const container = mount(qrStyle)
    await flush()
    await choose(container, 7)
    button(container, 'Load').click()
    await flush()

    button(container, 'Delete').click()
    await nextTick()
    expect(fetchMock.mock.calls.some(([, init]) => init?.method === 'DELETE')).toBe(false)
    button(container, 'Confirm delete').click()
    await flush()

    expect(fetchMock.mock.calls.some(([, init]) => init?.method === 'DELETE')).toBe(true)
    expect(qrStyle.moduleShape).toBe('DOTS')
    expect(container.textContent).not.toContain('Editing')
  })

  test('imports a design file into the editor without saving it', async () => {
    const fetchMock = stubApi([], { ...summary(), style: {} })
    const qrStyle = reactive(defaultStyle())
    const container = mount(qrStyle)
    await flush()

    const input = container.querySelector('input[type="file"]') as HTMLInputElement
    const file = new File(
      [JSON.stringify({ type: 'qr-code-design', version: 1, name: 'From file', style: { moduleShape: 'STAR' } })],
      'from-file.qr-design.json',
      { type: 'application/json' },
    )
    Object.defineProperty(input, 'files', { value: [file] })
    input.dispatchEvent(new Event('change'))
    await flush()

    expect(qrStyle.moduleShape).toBe('STAR')
    expect((container.querySelector('#design-name') as HTMLInputElement).value).toBe('From file')
    expect(container.textContent).toContain('Imported. Save it to keep it here.')
    expect(fetchMock.mock.calls.filter(([, init]) => init?.method === 'POST')).toHaveLength(0)
  })

  test('shows why a bad file was refused', async () => {
    stubApi([], { ...summary(), style: {} })
    const container = mount(reactive(defaultStyle()))
    await flush()

    const input = container.querySelector('input[type="file"]') as HTMLInputElement
    Object.defineProperty(input, 'files', { value: [new File(['{"a":1}'], 'x.json')] })
    input.dispatchEvent(new Event('change'))
    await flush()

    expect(container.querySelector('.error')?.textContent).toContain(
      'not an exported QR code design',
    )
  })
})

describe('DesignsPanel reporting the design on screen', () => {
  test('names the loaded design, and whether the editor still matches it', async () => {
    const saved = summary({ shared: true })
    stubApi([saved], { ...saved, style: { moduleShape: 'DOTS' } })
    const qrStyle = reactive(defaultStyle())
    const current = vi.fn()
    host = document.createElement('div')
    document.body.appendChild(host)
    app = createApp({ render: () => h(DesignsPanel, { qrStyle, onCurrent: current }) })
    app.mount(host)
    await flush()
    expect(current).toHaveBeenLastCalledWith(null)

    await choose(host, 7)
    button(host, 'Load').click()
    await flush()
    expect(current).toHaveBeenLastCalledWith({ id: 7, name: 'Brand', shared: true, unchanged: true })

    qrStyle.moduleShape = 'SQUARE'
    await flush()
    expect(current).toHaveBeenLastCalledWith({
      id: 7,
      name: 'Brand',
      shared: true,
      unchanged: false,
    })
  })
})
