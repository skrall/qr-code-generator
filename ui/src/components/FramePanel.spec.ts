import { afterEach, describe, expect, test } from 'vitest'
import { createApp, h, nextTick, reactive, type App } from 'vue'
import type { QrFrame } from '../api/client'
import FramePanel from './FramePanel.vue'

let app: App | null = null
let host: HTMLElement | null = null

/** Mounts the panel under a parent that owns the model, as App does with `style.frame`. */
function mount(state: { frame: QrFrame | null }): HTMLElement {
  host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp({
    render: () =>
      h(FramePanel, {
        modelValue: state.frame,
        'onUpdate:modelValue': (value: QrFrame | null) => {
          state.frame = value
        },
      }),
  })
  app.mount(host)
  return host
}

function toggle(container: HTMLElement): HTMLInputElement {
  const input = container.querySelector('input[aria-label="Frame"]')
  if (!(input instanceof HTMLInputElement)) {
    throw new Error('No frame toggle')
  }
  return input
}

afterEach(() => {
  app?.unmount()
  host?.remove()
  app = null
  host = null
})

describe('FramePanel', () => {
  test('shows no controls until the frame is switched on', async () => {
    const state = reactive({ frame: null as QrFrame | null })
    const container = mount(state)

    expect(container.querySelector('input[type="text"]')).toBeNull()

    toggle(container).click()
    await nextTick()

    expect(state.frame?.title).toBe('Scan me')
    expect(state.frame?.borderPercent).toBe(2)
    expect(container.querySelectorAll('input[type="text"]')).toHaveLength(2)
  })

  test('typing a title and moving a slider change the parent frame', async () => {
    const state = reactive({ frame: null as QrFrame | null })
    const container = mount(state)
    toggle(container).click()
    await nextTick()

    const title = container.querySelector('input[type="text"]')
    const bleed = container.querySelector('input[aria-label="Bleed"]')
    if (!(title instanceof HTMLInputElement) || !(bleed instanceof HTMLInputElement)) {
      throw new Error('Missing frame inputs')
    }
    title.value = 'Order here'
    title.dispatchEvent(new Event('input'))
    bleed.value = '5'
    bleed.dispatchEvent(new Event('input'))
    await nextTick()

    expect(state.frame?.title).toBe('Order here')
    expect(state.frame?.bleedPercent).toBe(5)
  })

  test('switching the frame off clears it', async () => {
    const state = reactive({ frame: null as QrFrame | null })
    const container = mount(state)
    toggle(container).click()
    await nextTick()

    toggle(container).click()
    await nextTick()

    expect(state.frame).toBeNull()
  })
})
