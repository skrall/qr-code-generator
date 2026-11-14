import { afterEach, describe, expect, test, vi } from 'vitest'
import { createApp, h, nextTick, reactive, type App } from 'vue'
import type { DesignSummary, QrStyle } from '../api/client'
import { defaultStyle } from '../qr/style'
import BatchPanel from './BatchPanel.vue'

let app: App | null = null
let host: HTMLElement | null = null

const design: DesignSummary = {
  id: 7,
  name: 'Brand',
  owner: 'alice',
  shared: false,
  mine: true,
  editable: true,
  createdAt: '2026-11-01T00:00:00Z',
  updatedAt: '2026-11-01T00:00:00Z',
}

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

/** Answers the design list, and the batch with `batch`, recording every call. */
function stubApi(batch: () => Response): ReturnType<typeof vi.fn> {
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input)
    const method = init?.method ?? 'GET'
    if (url === '/api/designs' && method === 'GET') return json([design])
    if (url === '/api/qr-codes/batch' && method === 'POST') return batch()
    throw new Error(`Unexpected ${method} ${url}`)
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

function zip(): Response {
  return new Response(new Blob([new Uint8Array([0x50, 0x4b, 3, 4])]), {
    status: 200,
    headers: { 'Content-Type': 'application/zip' },
  })
}

function mount(qrStyle: QrStyle): HTMLElement {
  host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp({ render: () => h(BatchPanel, { qrStyle }) })
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

async function chooseFile(container: HTMLElement, text: string, name = 'codes.csv'): Promise<File> {
  const file = new File([text], name, { type: 'text/csv' })
  const input = container.querySelector('#batch-file') as HTMLInputElement
  Object.defineProperty(input, 'files', { value: [file], configurable: true })
  input.dispatchEvent(new Event('change'))
  await flush()
  return file
}

/** The multipart body of the batch request: the CSV part and the parsed options part. */
async function sentBatch(
  fetchMock: ReturnType<typeof vi.fn>,
): Promise<{ csv: File; options: Record<string, unknown> }> {
  const call = fetchMock.mock.calls.find(([input]) => String(input) === '/api/qr-codes/batch')
  if (!call) throw new Error('No batch request')
  const body = (call[1] as RequestInit).body
  if (!(body instanceof FormData)) throw new Error('The batch was not multipart')
  const options = body.get('options') as Blob
  expect(options.type).toBe('application/json')
  return {
    csv: body.get('csv') as File,
    options: JSON.parse(await options.text()) as Record<string, unknown>,
  }
}

afterEach(() => {
  app?.unmount()
  host?.remove()
  app = null
  host = null
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

describe('BatchPanel', () => {
  test('counts the rows of the chosen file and previews the first ones', async () => {
    stubApi(zip)
    const container = mount(reactive(defaultStyle()))
    await flush()

    expect(button(container, 'Download ZIP').disabled).toBe(true)
    await chooseFile(container, 'data,filename\nhttps://a,first\nhttps://b,\n"multi\nline",x\n')

    expect(container.querySelector('.batch-summary')?.textContent).toContain('3 rows found.')
    const items = [...container.querySelectorAll('.batch-summary li')].map((li) => li.textContent)
    expect(items[0]).toContain('first')
    expect(items[1]).toContain('row-2')
    expect(button(container, 'Download ZIP').disabled).toBe(false)
  })

  test('sends the file with the current style and the chosen format, then saves the ZIP', async () => {
    const fetchMock = stubApi(zip)
    const createUrl = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:zip')
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {})
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    const qrStyle = reactive(defaultStyle())
    qrStyle.moduleShape = 'DOTS'
    const container = mount(qrStyle)
    await flush()

    const file = await chooseFile(container, 'data\nhello\n')
    button(container, 'Both').click()
    await nextTick()
    button(container, 'Download ZIP').click()
    await flush()

    const sent = await sentBatch(fetchMock)
    expect(sent.csv.name).toBe(file.name)
    expect(await sent.csv.text()).toBe('data\nhello\n')
    expect(sent.options.format).toBe('both')
    expect((sent.options.style as QrStyle).moduleShape).toBe('DOTS')
    expect(sent.options).not.toHaveProperty('designId')
    // The browser sets the multipart boundary; a hand-written Content-Type would lose it.
    const init = fetchMock.mock.calls.find(([input]) => String(input) === '/api/qr-codes/batch')?.[1]
    expect((init?.headers as Record<string, string>)['Content-Type']).toBeUndefined()

    expect(createUrl).toHaveBeenCalled()
    expect(click).toHaveBeenCalled()
    expect(container.textContent).toContain('report.csv')
  })

  test('sends a saved design by id instead of the style', async () => {
    const fetchMock = stubApi(zip)
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:zip')
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {})
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    const container = mount(reactive(defaultStyle()))
    await flush()

    await chooseFile(container, 'data\nhello\n')
    button(container, 'Saved design').click()
    await nextTick()
    expect(button(container, 'Download ZIP').disabled).toBe(true)

    const select = container.querySelector('#batch-design') as HTMLSelectElement
    expect(select.textContent).toContain('Brand')
    select.value = '7'
    select.dispatchEvent(new Event('change'))
    await nextTick()
    button(container, 'Download ZIP').click()
    await flush()

    const sent = await sentBatch(fetchMock)
    expect(sent.options).toEqual({ format: 'png', designId: 7 })
  })

  test("shows the server's problem detail when the batch is refused", async () => {
    stubApi(() =>
      json(
        { title: 'Bad Request', status: 400, detail: 'Line 2 of the CSV opens a quoted field that is never closed.' },
        400,
      ),
    )
    const container = mount(reactive(defaultStyle()))
    await flush()

    await chooseFile(container, 'data\nhello\n')
    button(container, 'Download ZIP').click()
    await flush()

    expect(container.querySelector('[role="alert"]')?.textContent).toBe(
      'Line 2 of the CSV opens a quoted field that is never closed.',
    )
  })

  test('explains a file without a data column before uploading it', async () => {
    const fetchMock = stubApi(zip)
    const container = mount(reactive(defaultStyle()))
    await flush()

    await chooseFile(container, 'url\nhttps://a\n')

    expect(container.querySelector('[role="alert"]')?.textContent).toBe(
      'The header row has no data column. Found: url.',
    )
    expect(button(container, 'Download ZIP').disabled).toBe(true)
    expect(fetchMock.mock.calls.map(([input]) => String(input))).not.toContain('/api/qr-codes/batch')
  })

  test('offers a sample CSV', async () => {
    stubApi(zip)
    let saved: Blob | null = null
    vi.spyOn(URL, 'createObjectURL').mockImplementation((blob) => {
      saved = blob as Blob
      return 'blob:sample'
    })
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {})
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    const container = mount(reactive(defaultStyle()))
    await flush()

    button(container, 'Download a sample CSV').click()

    expect(saved).not.toBeNull()
    expect(await (saved as unknown as Blob).text()).toMatch(/^data,filename\r\n/)
  })
})
