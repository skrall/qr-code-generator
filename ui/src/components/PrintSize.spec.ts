import { afterEach, describe, expect, test } from 'vitest'
import { createApp, h, nextTick, reactive, type App } from 'vue'
import { bleedPixels, clampDpi, millimetres, printSummary } from '../print'
import PrintSize from './PrintSize.vue'
import QrPreview from './QrPreview.vue'

let app: App | null = null
let host: HTMLElement | null = null

afterEach(() => {
  app?.unmount()
  host?.remove()
  app = null
  host = null
})

function mount(component: unknown, props: Record<string, unknown>): HTMLElement {
  host = document.createElement('div')
  document.body.appendChild(host)
  const state = reactive(props)
  app = createApp({ render: () => h(component as never, state as never) })
  app.mount(host)
  return host
}

function button(container: HTMLElement, label: string): HTMLButtonElement {
  const found = [...container.querySelectorAll('button')].find((b) => b.textContent?.trim() === label)
  if (!(found instanceof HTMLButtonElement)) {
    throw new Error(`No ${label} button`)
  }
  return found
}

function field(container: HTMLElement): HTMLInputElement {
  const input = container.querySelector('#print-dpi')
  if (!(input instanceof HTMLInputElement)) {
    throw new Error('No DPI field')
  }
  return input
}

describe('print size helpers', () => {
  test('converts pixels to millimetres at a resolution', () => {
    expect(millimetres(640, 300)).toBe('54.2')
    expect(millimetres(600, 300)).toBe('50.8')
    expect(millimetres(300, 300)).toBe('25.4')
    expect(millimetres(72, 72)).toBe('25.4')
    expect(millimetres(1181, 600)).toBe('50')
  })

  test('summarises the size, and the trimmed size when there is bleed', () => {
    expect(printSummary(640, 640, 300)).toBe('Prints at 54.2 × 54.2 mm at 300 DPI')
    expect(printSummary(700, 800, 300, 30)).toBe(
      'Prints at 54.2 × 62.7 mm at 300 DPI, 59.3 × 67.7 mm with bleed',
    )
  })

  test('keeps a resolution in the range the server accepts', () => {
    expect(clampDpi(50)).toBe(72)
    expect(clampDpi(5000)).toBe(1200)
    expect(clampDpi(299.6)).toBe(300)
    expect(clampDpi(Number.NaN)).toBe(300)
  })

  test('computes the bleed as the server lays it out', () => {
    expect(bleedPixels(600, 5)).toBe(30)
    expect(bleedPixels(512, 6)).toBe(31)
    expect(bleedPixels(512, undefined)).toBe(0)
  })
})

describe('PrintSize', () => {
  test('shows the print size for the image and resolution', () => {
    const container = mount(PrintSize, { width: 640, height: 640, dpi: 300 })
    expect(container.textContent).toContain('Prints at 54.2 × 54.2 mm at 300 DPI')
  })

  test('says nothing until the image size is known', () => {
    const container = mount(PrintSize, { width: 0, height: 0, dpi: 300 })
    expect(container.querySelector('#print-size-summary')?.textContent).toBe('')
  })

  test('clamps a typed resolution and reports it', async () => {
    const updates: number[] = []
    const props = reactive({
      width: 600,
      height: 600,
      dpi: 300,
      'onUpdate:dpi': (value: number) => {
        updates.push(value)
        props.dpi = value
      },
    })
    host = document.createElement('div')
    document.body.appendChild(host)
    app = createApp({ render: () => h(PrintSize, props) })
    app.mount(host)

    const input = field(host)
    input.value = '5000'
    input.dispatchEvent(new Event('input'))
    input.dispatchEvent(new Event('change'))
    await nextTick()

    expect(updates).toEqual([1200])
    expect(input.value).toBe('1200')
    expect(host.textContent).toContain('at 1200 DPI')
  })
})

describe('QrPreview downloads', () => {
  const base = { url: 'blob:preview', state: 'ready', error: '', payload: 'https://example.com/' }

  function loaded(container: HTMLElement, width: number, height: number): void {
    const image = container.querySelector('img')
    if (!(image instanceof HTMLImageElement)) {
      throw new Error('No preview image')
    }
    Object.defineProperty(image, 'naturalWidth', { value: width })
    Object.defineProperty(image, 'naturalHeight', { value: height })
    image.dispatchEvent(new Event('load'))
  }

  test('offers a PDF next to PNG and SVG, with the same rules', () => {
    expect(button(mount(QrPreview, base), 'Download PDF').disabled).toBe(false)
  })

  test('cannot download a draft as PDF', () => {
    const container = mount(QrPreview, { ...base, draft: true, downloadable: false })
    expect(button(container, 'Download PDF').disabled).toBe(true)
    expect(button(container, 'Download PNG').disabled).toBe(true)
  })

  test('cannot download a PDF before there is an image', () => {
    const container = mount(QrPreview, { ...base, url: '', state: 'error', error: 'Bad style' })
    expect(button(container, 'Download PDF').disabled).toBe(true)
  })

  test('sends the chosen resolution with each download', async () => {
    const downloads: [string, number][] = []
    const container = mount(QrPreview, {
      ...base,
      onDownload: (format: string, dpi: number) => downloads.push([format, dpi]),
    })
    const input = field(container)
    input.value = '600'
    input.dispatchEvent(new Event('input'))
    input.dispatchEvent(new Event('change'))
    await nextTick()

    button(container, 'Download PDF').click()
    button(container, 'Download PNG').click()

    expect(downloads).toEqual([
      ['pdf', 600],
      ['png', 600],
    ])
  })

  test('measures the preview to show the print size, trimmed by the bleed', async () => {
    const container = mount(QrPreview, { ...base, bleed: 30 })
    loaded(container, 700, 800)
    await nextTick()

    expect(container.textContent).toContain('Prints at 54.2 × 62.7 mm at 300 DPI')
  })
})
