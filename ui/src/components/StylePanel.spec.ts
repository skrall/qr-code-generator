import { afterEach, describe, expect, test } from 'vitest'
import { createApp, h, nextTick, reactive, type App } from 'vue'
import type { QrStyle } from '../api/client'
import StylePanel from './StylePanel.vue'

let app: App | null = null
let host: HTMLElement | null = null

function mount(style: QrStyle): HTMLElement {
  host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp({ render: () => h(StylePanel, { qrStyle: style }) })
  app.mount(host)
  return host
}

function buttonIn(container: HTMLElement, group: string, label: string): HTMLButtonElement {
  const target = [...container.querySelectorAll(`[aria-label="${group}"] button`)].find(
    (button) => button.textContent?.trim() === label,
  )
  if (!(target instanceof HTMLButtonElement)) {
    throw new Error(`No ${label} button in ${group}`)
  }
  return target
}

function freshStyle(): QrStyle {
  return {
    size: 640,
    margin: 4,
    errorCorrection: 'QUARTILE',
    foreground: '#111827',
    background: '#ffffff',
    moduleShape: 'ROUNDED',
    finderShape: 'ROUNDED',
    finderColor: '#3158c4',
    finderArtwork: null,
    logo: null,
  }
}

afterEach(() => {
  app?.unmount()
  host?.remove()
  app = null
  host = null
})

describe('StylePanel', () => {
  // A prop named `style` would be cloned by Vue into a plain object, so the panel would mutate
  // a detached copy and these assertions would fail without any error being raised.
  test('mutates the reactive style it was given', async () => {
    const style = reactive(freshStyle())
    const container = mount(style)

    buttonIn(container, 'Module shape', 'Dots').click()
    buttonIn(container, 'Corner pattern shape', 'Circle').click()
    await nextTick()

    expect(style.moduleShape).toBe('DOTS')
    expect(style.finderShape).toBe('CIRCLE')
    expect(buttonIn(container, 'Module shape', 'Dots').classList.contains('active')).toBe(true)
  })

  test('offers the star module and leaf and diamond corner patterns', async () => {
    const style = reactive(freshStyle())
    const container = mount(style)

    buttonIn(container, 'Module shape', 'Stars').click()
    buttonIn(container, 'Corner pattern shape', 'Leaf').click()
    await nextTick()
    expect(style.moduleShape).toBe('STAR')
    expect(style.finderShape).toBe('LEAF')

    buttonIn(container, 'Corner pattern shape', 'Diamond').click()
    await nextTick()
    expect(style.finderShape).toBe('DIAMOND')
  })

  test('offers the liquid module shape and preset', async () => {
    const style = reactive(freshStyle())
    const container = mount(style)

    buttonIn(container, 'Module shape', 'Liquid').click()
    await nextTick()
    expect(style.moduleShape).toBe('LIQUID')

    const liquid = [...container.querySelectorAll('.preset')].find(
      (button) => button.textContent?.trim() === 'Liquid',
    )
    if (!(liquid instanceof HTMLButtonElement)) {
      throw new Error('No Liquid preset')
    }
    liquid.click()
    await nextTick()

    expect(style.moduleShape).toBe('LIQUID')
    expect(style.finderShape).toBe('CIRCLE')
    expect(style.finderColor).toBe('#0f766e')
    expect(liquid.classList.contains('active')).toBe(true)
  })

  test('shows the artwork uploader only for the custom corner shape', async () => {
    const style = reactive(freshStyle())
    const container = mount(style)

    expect(container.querySelector('input[type="file"]')).toBeNull()

    buttonIn(container, 'Corner pattern shape', 'Custom').click()
    await nextTick()

    expect(style.finderShape).toBe('CUSTOM')
    expect(container.querySelector('input[type="file"]')).not.toBeNull()
    expect(container.textContent).toContain('Corner artwork')
  })

  test('removing corner artwork clears it from the style', async () => {
    const style = reactive(freshStyle())
    style.finderShape = 'CUSTOM'
    style.finderArtwork = { dataUrl: 'data:image/png;base64,AAAA' }
    const container = mount(style)

    const remove = [...container.querySelectorAll('button')].find(
      (button) => button.textContent?.trim() === 'Remove',
    )
    if (!(remove instanceof HTMLButtonElement)) {
      throw new Error('No Remove button')
    }
    remove.click()
    await nextTick()

    expect(style.finderArtwork).toBeNull()
  })

  test('a preset updates every colour and shape it owns', async () => {
    const style = reactive(freshStyle())
    const container = mount(style)

    const midnight = [...container.querySelectorAll('.preset')].find(
      (button) => button.textContent?.trim() === 'Midnight',
    )
    if (!(midnight instanceof HTMLButtonElement)) {
      throw new Error('No Midnight preset')
    }
    midnight.click()
    await nextTick()

    expect(style.moduleShape).toBe('DOTS')
    expect(style.finderShape).toBe('CIRCLE')
    expect(style.foreground).toBe('#e8eefc')
    expect(style.background).toBe('#0f172a')
    expect(style.finderColor).toBe('#8ab4ff')
    expect(midnight.classList.contains('active')).toBe(true)
  })

  test('a range input writes back a number', async () => {
    const style = reactive(freshStyle())
    const container = mount(style)

    const size = container.querySelector('input[type="range"]')
    if (!(size instanceof HTMLInputElement)) {
      throw new Error('No range input')
    }
    size.value = '900'
    size.dispatchEvent(new Event('input', { bubbles: true }))
    await nextTick()

    expect(style.size).toBe(900)
  })
})
