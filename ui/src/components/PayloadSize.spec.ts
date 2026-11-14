import { afterEach, describe, expect, test } from 'vitest'
import { createApp, h, nextTick, reactive, type App } from 'vue'
import type { ErrorCorrection } from '../api/client'
import PayloadSize from './PayloadSize.vue'

let app: App | null = null
let host: HTMLElement | null = null

interface Props {
  payload: string
  level: ErrorCorrection
  draft?: boolean
}

/** Mounts with reactive props, so a test can type by changing `payload`. */
function mount(initial: Props): { props: Props; root: () => HTMLElement } {
  const props = reactive({ draft: false, ...initial })
  host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp({ render: () => h(PayloadSize, { ...props }) })
  app.mount(host)
  return {
    props,
    root: () => {
      const section = host?.querySelector('.payload-size')
      if (!(section instanceof HTMLElement)) {
        throw new Error('No readout')
      }
      return section
    },
  }
}

function text(element: Element | null | undefined): string {
  return (element?.textContent ?? '').replace(/\s+/g, ' ').trim()
}

/** The visible version per level, L to H. */
function levels(root: HTMLElement): string[] {
  return [...root.querySelectorAll('.payload-size-level [aria-hidden]')].map((entry) => text(entry))
}

afterEach(() => {
  app?.unmount()
  host?.remove()
  app = null
  host = null
})

describe('PayloadSize', () => {
  test('keeps its rows while empty, so the layout does not jump on the first keystroke', () => {
    const { root } = mount({ payload: '', level: 'MEDIUM' })
    expect(root().classList).toContain('payload-size-idle')
    expect(root().querySelector('.payload-size-meter')).not.toBeNull()
    expect(root().querySelector('.payload-size-detail')).not.toBeNull()
    expect(text(root().querySelector('[aria-live="polite"]'))).toBe('Code size appears as you type')
  })

  test('updates as the payload grows', async () => {
    const { props, root } = mount({ payload: 'https://example.com', level: 'MEDIUM' })
    expect(text(root().querySelector('.payload-size-headline'))).toBe('Version 2 · 25 × 25 modules')
    expect(text(root().querySelector('.payload-size-usage'))).toBe('22 / 28 bytes')
    expect(text(root().querySelector('.payload-size-detail'))).toBe('6 bytes left before version 3.')
    expect(root().classList).toContain('payload-size-ok')

    props.payload = 'https://example.com/a-longer-path'
    await nextTick()
    expect(text(root().querySelector('.payload-size-headline'))).toBe('Version 3 · 29 × 29 modules')
  })

  test('follows the error-correction level and lists the version at each level', async () => {
    const { props, root } = mount({ payload: 'https://example.com', level: 'MEDIUM' })
    expect(levels(root())).toEqual(['L v2', 'M v2', 'Q v2', 'H v3'])
    expect(text(root().querySelector('.current [aria-hidden]'))).toBe('M v2')
    expect(text(root().querySelector('.current .visually-hidden'))).toBe('medium: version 2;')

    props.level = 'HIGH'
    await nextTick()
    expect(text(root().querySelector('.payload-size-headline'))).toBe('Version 3 · 29 × 29 modules')
    expect(text(root().querySelector('.current [aria-hidden]'))).toBe('H v3')
  })

  test('warns from version 10, where the scan check calls the code dense', async () => {
    // 12 + 4 + 8 bits of header and 179 bytes fill version 9-M's 182 codewords exactly.
    const { props, root } = mount({ payload: 'a'.repeat(179), level: 'MEDIUM' })
    expect(text(root().querySelector('.payload-size-headline'))).toContain('Version 9')
    expect(root().classList).toContain('payload-size-ok')

    props.payload = 'a'.repeat(180)
    await nextTick()
    expect(text(root().querySelector('.payload-size-headline'))).toBe('Version 10 · 57 × 57 modules')
    expect(root().classList).toContain('payload-size-dense')
    expect(text(root().querySelector('.payload-size-detail'))).toContain('Dense code')
  })

  test('says when the payload is too long and which lower level would fit', () => {
    // 1600 bytes and 4 header bytes: over high's 1276 codewords, within quartile's 1666.
    const { root } = mount({ payload: `https://example.com/${'a'.repeat(1580)}`, level: 'HIGH' })
    expect(root().classList).toContain('payload-size-too-long')
    expect(text(root().querySelector('[aria-live="polite"]'))).toBe('Too long for a QR code')
    expect(text(root().querySelector('.payload-size-usage'))).toBe('1,604 / 1,276 bytes')
    expect(text(root().querySelector('.payload-size-detail'))).toBe(
      '328 bytes over what version 40 holds at high error correction. Choose quartile error ' +
        'correction (version 40), use a short link or shorten the content.',
    )
    expect(levels(root())).toEqual(['L v29', 'M v33', 'Q v40', 'H too long'])
  })

  test('only suggests shortening when no level fits and the payload is not a URL', () => {
    const { root } = mount({ payload: 'x'.repeat(3000), level: 'LOW' })
    expect(text(root().querySelector('.payload-size-detail'))).toBe(
      '48 bytes over what version 40 holds at low error correction. Shorten the content.',
    )
  })

  test('sizes a short link draft and says the short URL replaces it', () => {
    const { root } = mount({ payload: 'https://example.com/page', level: 'MEDIUM', draft: true })
    expect(text(root().querySelector('.payload-size-headline'))).toBe('Version 2 · 25 × 25 modules')
    expect(text(root().querySelector('.payload-size-detail'))).toContain(
      'Sized for the destination draft; Create link encodes the short URL instead.',
    )
  })

  test('does not suggest a short link for a draft that is too long', () => {
    const { root } = mount({ payload: `https://example.com/${'a'.repeat(3000)}`, level: 'LOW', draft: true })
    expect(text(root().querySelector('.payload-size-detail'))).not.toContain('short link')
  })
})
