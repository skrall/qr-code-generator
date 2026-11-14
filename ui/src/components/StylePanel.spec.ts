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

function gradientToggle(container: HTMLElement): HTMLInputElement {
  const toggle = container.querySelector('input[aria-label="Gradient"]')
  if (!(toggle instanceof HTMLInputElement)) {
    throw new Error('No gradient toggle')
  }
  return toggle
}

function freshStyle(): QrStyle {
  return {
    size: 640,
    margin: 4,
    errorCorrection: 'QUARTILE',
    foreground: '#111827',
    gradient: null,
    background: '#ffffff',
    backgroundImage: null,
    moduleShape: 'ROUNDED',
    finderShape: 'ROUNDED',
    finderColor: '#3158c4',
    finderArtwork: null,
    logo: null,
    frame: null,
    transparentBackground: false,
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

    expect(container.querySelector('.finder-artwork-panel input[type="file"]')).toBeNull()

    buttonIn(container, 'Corner pattern shape', 'Custom').click()
    await nextTick()

    expect(style.finderShape).toBe('CUSTOM')
    expect(container.querySelector('.finder-artwork-panel input[type="file"]')).not.toBeNull()
    expect(container.textContent).toContain('Corner artwork')
  })

  test('offers a background image uploader', async () => {
    const style = reactive(freshStyle())
    const container = mount(style)

    expect(container.querySelector('.background-panel input[type="file"]')).not.toBeNull()
    expect(container.textContent).toContain('Background image')
  })

  test('turning the gradient on seeds it from the current colours', async () => {
    const style = reactive(freshStyle())
    const container = mount(style)

    expect(container.querySelector('[aria-label="Gradient type"]')).toBeNull()
    expect(container.querySelector('[aria-label="Foreground colour"]')).not.toBeNull()

    gradientToggle(container).click()
    await nextTick()

    expect(style.gradient).toEqual({
      type: 'LINEAR',
      start: '#111827',
      end: '#3158c4',
      angle: 135,
      finders: false,
    })
    // The gradient replaces the foreground, so its picker would only mislead.
    expect(container.querySelector('[aria-label="Foreground colour"]')).toBeNull()
    expect(container.textContent).toContain('Angle')
  })

  test('a radial gradient has no angle', async () => {
    const style = reactive(freshStyle())
    const container = mount(style)

    gradientToggle(container).click()
    await nextTick()
    buttonIn(container, 'Gradient type', 'Radial').click()
    await nextTick()

    expect(style.gradient?.type).toBe('RADIAL')
    expect(container.textContent).not.toContain('Angle')
    expect(container.textContent).toContain('Centre')
  })

  test('turning the gradient off clears it', async () => {
    const style = reactive(freshStyle())
    style.gradient = { type: 'LINEAR', start: '#111827', end: '#3158c4', angle: 90, finders: true }
    const container = mount(style)

    gradientToggle(container).click()
    await nextTick()

    expect(style.gradient).toBeNull()
  })

  test('a preset clears the gradient', async () => {
    const style = reactive(freshStyle())
    style.gradient = { type: 'LINEAR', start: '#111827', end: '#3158c4', angle: 90, finders: true }
    const container = mount(style)

    buttonIn(container, 'Style presets', 'Classic').click()
    await nextTick()

    expect(style.gradient).toBeNull()
    expect(style.foreground).toBe('#111827')
  })

  test('removing a background image clears it from the style', async () => {
    const style = reactive(freshStyle())
    style.backgroundImage = { dataUrl: 'data:image/png;base64,AAAA' }
    const container = mount(style)

    const panel = container.querySelector('.background-panel')
    if (!(panel instanceof HTMLElement)) {
      throw new Error('No background panel')
    }
    const remove = [...panel.querySelectorAll('button')].find(
      (button) => button.textContent?.trim() === 'Remove',
    )
    if (!(remove instanceof HTMLButtonElement)) {
      throw new Error('No Remove button')
    }
    remove.click()
    await nextTick()

    expect(style.backgroundImage).toBeNull()
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

  test('the transparent background checkbox toggles the flag', async () => {
    const style = reactive(freshStyle())
    const container = mount(style)

    const toggle = container.querySelector('input[aria-label="Transparent background"]')
    if (!(toggle instanceof HTMLInputElement)) {
      throw new Error('No transparent background toggle')
    }
    expect(toggle.checked).toBe(false)

    toggle.click()
    await nextTick()
    expect(style.transparentBackground).toBe(true)
    expect(container.textContent).toContain('Only the code is drawn')

    toggle.click()
    await nextTick()
    expect(style.transparentBackground).toBe(false)
  })

  test('a background image disables the transparent background', () => {
    const style = reactive({
      ...freshStyle(),
      backgroundImage: { dataUrl: 'data:image/png;base64,AA==' },
    })
    const container = mount(style)

    const toggle = container.querySelector('input[aria-label="Transparent background"]')
    if (!(toggle instanceof HTMLInputElement)) {
      throw new Error('No transparent background toggle')
    }
    expect(toggle.disabled).toBe(true)
    expect(container.textContent).toContain('Remove the background image')
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

describe('StylePanel invert colours', () => {
  function invertButton(container: HTMLElement): HTMLButtonElement {
    const button = [...container.querySelectorAll('button')].find(
      (candidate) => candidate.textContent?.trim() === 'Invert colours',
    )
    if (!(button instanceof HTMLButtonElement)) {
      throw new Error('No Invert colours button')
    }
    return button
  }

  test('swaps the code colours and explains the scan check', async () => {
    const style = reactive(freshStyle())
    const container = mount(style)

    expect(container.querySelector('.invert-caveat')).toBeNull()

    invertButton(container).click()
    await nextTick()

    expect(style.foreground).toBe('#ffffff')
    expect(style.background).toBe('#111827')
    expect(style.finderColor).toBe('#adbeeb')
    expect(container.querySelector('.invert-caveat')?.textContent).toContain(
      'May not scan on every phone',
    )
  })

  test('leaves the logo, frame and images alone', async () => {
    const style = reactive(freshStyle())
    style.logo = {
      dataUrl: 'data:image/png;base64,AAAA',
      sizePercent: 20,
      paddingPercent: 8,
      radiusPercent: 25,
      background: '#ffffff',
    }
    style.backgroundImage = { dataUrl: 'data:image/png;base64,BBBB' }
    const before = JSON.stringify({ logo: style.logo, frame: style.frame, image: style.backgroundImage })
    const container = mount(style)

    invertButton(container).click()
    await nextTick()

    expect(JSON.stringify({ logo: style.logo, frame: style.frame, image: style.backgroundImage })).toBe(
      before,
    )
  })

  test('a corner colour matching the foreground follows it', async () => {
    const style = reactive(freshStyle())
    style.finderColor = '#111827'
    const container = mount(style)

    invertButton(container).click()
    await nextTick()

    expect(style.finderColor).toBe('#ffffff')
  })

  test('inverts the gradient stops so they stay lighter than the new background', async () => {
    const style = reactive(freshStyle())
    style.gradient = { type: 'LINEAR', start: '#111827', end: '#3158c4', angle: 90, finders: true }
    const container = mount(style)

    invertButton(container).click()
    await nextTick()

    expect(style.gradient).toEqual({
      type: 'LINEAR',
      start: '#ffffff',
      end: '#adbeeb',
      angle: 90,
      finders: true,
    })
  })

  test('says so when a gradient has to be turned off', async () => {
    const style = reactive(freshStyle())
    style.foreground = '#ffffff'
    style.gradient = { type: 'LINEAR', start: '#000000', end: '#202020', angle: 90, finders: false }
    const container = mount(style)

    invertButton(container).click()
    await nextTick()

    expect(style.gradient).toBeNull()
    expect(container.textContent).toContain('so it was turned off')
  })

  test('a second click restores the exact colours, fallbacks included', async () => {
    const style = reactive(freshStyle())
    // #dddddd cannot stay a distinct accent on the dark side, so the first click replaces it.
    style.finderColor = '#dddddd'
    style.gradient = { type: 'RADIAL', start: '#7c3aed', end: '#0f766e', angle: 135, finders: false }
    const original = JSON.parse(JSON.stringify(style)) as QrStyle
    const container = mount(style)

    invertButton(container).click()
    await nextTick()
    expect(style.finderColor).toBe('#ffffff')

    invertButton(container).click()
    await nextTick()
    expect(JSON.parse(JSON.stringify(style))).toEqual(original)
    expect(container.querySelector('.invert-caveat')).toBeNull()
  })
})
