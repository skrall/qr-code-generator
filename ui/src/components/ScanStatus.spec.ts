import { afterEach, describe, expect, test } from 'vitest'
import { createApp, h, type App } from 'vue'
import type { ScanCheck } from '../api/client'
import ScanStatus from './ScanStatus.vue'

let app: App | null = null
let host: HTMLElement | null = null

function mount(check: ScanCheck | null, pending = false): HTMLElement {
  host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp({ render: () => h(ScanStatus, { check, pending }) })
  app.mount(host)
  return host
}

function result(overrides: Partial<ScanCheck>): ScanCheck {
  return {
    verdict: 'PASS',
    version: 4,
    modules: 33,
    trials: [
      { name: 'rendered', description: 'As rendered', decoded: true },
      { name: 'small', description: 'Small in the frame', decoded: true },
      { name: 'blurred', description: 'Slightly out of focus', decoded: true },
    ],
    hints: [],
    ...overrides,
  }
}

afterEach(() => {
  app?.unmount()
  host?.remove()
  app = null
  host = null
})

describe('ScanStatus', () => {
  test('renders nothing before the first check', () => {
    expect(mount(null).querySelector('.scan-status')).toBeNull()
  })

  test('says it is checking while the first check runs', () => {
    const status = mount(null, true).querySelector('.scan-status')
    expect(status?.textContent).toContain('Checking scannability')
    expect(status?.getAttribute('aria-busy')).toBe('true')
  })

  test('shows a pass with the grid size and no hints', () => {
    const container = mount(result({}))
    expect(container.querySelector('.scan-pass')).not.toBeNull()
    expect(container.textContent).toContain('Scans reliably')
    expect(container.textContent).toContain('33×33 modules')
    expect(container.querySelector('.scan-hints')).toBeNull()
  })

  test('marks the failed attempts and lists the hints of a marginal code', () => {
    const container = mount(
      result({
        verdict: 'MARGINAL',
        trials: [
          { name: 'rendered', description: 'As rendered', decoded: true },
          { name: 'small', description: 'Small in the frame', decoded: false },
          { name: 'blurred', description: 'Slightly out of focus', decoded: false },
        ],
        hints: ['Try square or rounded corner patterns.'],
      }),
    )
    expect(container.querySelector('.scan-marginal')).not.toBeNull()
    expect(container.textContent).toContain('May not scan on every phone')
    expect(container.querySelectorAll('.scan-trials .failed')).toHaveLength(2)
    expect(container.querySelector('.scan-hints')?.textContent).toContain('corner patterns')
  })

  test('dims an old result while a newer check runs', () => {
    const container = mount(result({ verdict: 'FAIL' }), true)
    expect(container.querySelector('.scan-fail.scan-stale')).not.toBeNull()
    expect(container.textContent).toContain('Does not scan')
  })
})
