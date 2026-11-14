import { afterEach, describe, expect, test } from 'vitest'
import { createApp, h, nextTick, reactive, type App } from 'vue'
import { findQrType } from '../qr/types'
import ContentForm from './ContentForm.vue'

let app: App | null = null
let host: HTMLElement | null = null

function mount(id: Parameters<typeof findQrType>[0], model: Record<string, string>): HTMLElement {
  host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp({ render: () => h(ContentForm, { type: findQrType(id), model }) })
  app.mount(host)
  return host
}

afterEach(() => {
  app?.unmount()
  host?.remove()
  app = null
  host = null
})

describe('ContentForm', () => {
  test('shows a problem under its field in place of the help', async () => {
    const model = reactive<Record<string, string>>({ name: 'Red Cross', iban: 'BE72000000001616' })
    const container = mount('epc', model)
    const iban = container.querySelector<HTMLInputElement>('#epc-iban')!

    expect(iban.getAttribute('aria-invalid')).toBeNull()
    expect(container.querySelector('[role="alert"]')).toBeNull()

    model.iban = 'BE73000000001616'
    await nextTick()

    expect(iban.getAttribute('aria-invalid')).toBe('true')
    const problem = container.querySelector(`#${iban.getAttribute('aria-describedby')}`)
    expect(problem?.textContent).toMatch(/check digits/)
  })

  test('shows a problem with the whole payload below the fields', async () => {
    const model = reactive<Record<string, string>>({
      name: 'ü'.repeat(70),
      iban: 'BE72000000001616',
      text: 'ü'.repeat(100),
    })
    const container = mount('epc', model)
    await nextTick()

    const alerts = [...container.querySelectorAll('[role="alert"]')].map((node) => node.textContent)
    expect(alerts).toHaveLength(1)
    expect(alerts[0]).toMatch(/331/)
  })

  test('a type without validation shows its help text', () => {
    const container = mount('appstore', reactive({ store: 'apple' }))

    expect(container.querySelector('#appstore-appId-help')?.textContent).toMatch(/apps\.apple\.com/)
  })
})
