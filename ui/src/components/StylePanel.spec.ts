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
