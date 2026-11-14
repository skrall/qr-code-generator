import { afterEach, describe, expect, test, vi } from 'vitest'
import { createApp, h, nextTick, type App } from 'vue'
import type { LinkStats, ShortLink } from '../api/client'
import LinkStatsDialog from './LinkStatsDialog.vue'

let app: App | null = null
let host: HTMLElement | null = null

const LINK: ShortLink = {
  code: 'abc123XYZ',
  shortUrl: 'http://localhost:8080/r/abc123XYZ',
  targetUrl: 'https://example.com/one',
  createdAt: '2026-01-01T00:00:00Z',
  expiresAt: null,
  hitCount: 12,
  lastHitAt: null,
}

function stats(days: number, overrides: Partial<LinkStats> = {}): LinkStats {
  const daily = Array.from({ length: days }, (_, index) => {
    const date = new Date(Date.UTC(2026, 2, 10 - (days - 1) + index)).toISOString().slice(0, 10)
    return { date, scans: 0, bots: 0 }
  })
  daily[days - 1] = { ...daily[days - 1], scans: 4, bots: 1 }
  daily[days - 2] = { ...daily[days - 2], scans: 2, bots: 0 }
  return {
    code: LINK.code,
    deleted: false,
    days,
    from: daily[0].date,
    to: daily[days - 1].date,
    timeZone: 'UTC',
    retentionDays: 365,
    allTime: 12,
    scans: 6,
    bots: 1,
    daily,
    devices: [
      { device: 'IOS', scans: 4 },
      { device: 'ANDROID', scans: 2 },
    ],
    referrers: [
      { host: null, scans: 5 },
      { host: 'l.instagram.com', scans: 1 },
    ],
    ...overrides,
  }
}

function response(body: unknown, status = 200): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    statusText: '',
    json: async () => body,
  } as Response
}

function mount(onClose = vi.fn()): HTMLElement {
  host = document.createElement('div')
  document.body.appendChild(host)
  app = createApp({ render: () => h(LinkStatsDialog, { link: LINK, onClose }) })
  app.mount(host)
  return host
}

function button(container: HTMLElement, label: string): HTMLButtonElement {
  const target = [...container.querySelectorAll('button')].find(
    (candidate) => candidate.textContent?.trim() === label,
  )
  if (!(target instanceof HTMLButtonElement)) {
    throw new Error(`No ${label} button`)
  }
  return target
}

async function flush(): Promise<void> {
  await new Promise((resolve) => setTimeout(resolve, 0))
  await nextTick()
}

afterEach(() => {
  app?.unmount()
  host?.remove()
  app = null
  host = null
  vi.unstubAllGlobals()
})

describe('LinkStatsDialog', () => {
  test('loads thirty days and shows the totals', async () => {
    const fetchMock = vi.fn().mockResolvedValue(response(stats(30)))
    vi.stubGlobal('fetch', fetchMock)
    const container = mount()
    await flush()

    expect(fetchMock.mock.calls[0]?.[0]).toBe('/api/links/abc123XYZ/stats?days=30')
    const totals = [...container.querySelectorAll('.stats-number')].map((n) => n.textContent)
    expect(totals).toEqual(['6', '1', '12'])
    expect(button(container, '30 days').getAttribute('aria-pressed')).toBe('true')
  })

  test('draws one bar slot per day and describes the chart for screen readers', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response(stats(30))))
    const container = mount()
    await flush()

    const svg = container.querySelector('.stats-chart svg')
    expect(svg?.getAttribute('role')).toBe('img')
    expect(svg?.getAttribute('aria-label')).toContain('6 scans by people and 1 bot')
    expect(svg?.getAttribute('aria-label')).toContain('(UTC)')
    expect(svg?.querySelectorAll('g')).toHaveLength(30)
    // Two days with people, one with a bot stacked on top.
    expect(svg?.querySelectorAll('.stats-bar-people')).toHaveLength(2)
    expect(svg?.querySelectorAll('.stats-bar-bots')).toHaveLength(1)
    // The tallest day fills the chart.
    const tallest = svg?.querySelectorAll('g')[29]
    const heights = [...(tallest?.querySelectorAll('rect') ?? [])].map((rect) =>
      Number(rect.getAttribute('height')),
    )
    expect(heights.reduce((sum, value) => sum + value, 0)).toBe(100)
  })

  test('has the same numbers as a table', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response(stats(7))))
    const container = mount()
    await flush()

    const rows = container.querySelectorAll('.stats-table-toggle tbody tr')
    expect(rows).toHaveLength(7)
    expect(rows[6]?.textContent).toContain('2026-03-10')
    expect(rows[6]?.textContent).toContain('4')
  })

  test('shows devices and referrers, naming scans without a referrer', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response(stats(30))))
    const container = mount()
    await flush()

    const devices = container.querySelector('[aria-label="Scans by device"]')
    expect(devices?.textContent).toContain('iOS')
    expect(devices?.textContent).toContain('Android')
    const referrers = container.querySelector('[aria-label="Scans by referrer"]')
    expect(referrers?.textContent).toContain('No referrer')
    expect(referrers?.textContent).toContain('l.instagram.com')
  })

  test('reloads when another period is picked', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(response(stats(30)))
      .mockResolvedValueOnce(response(stats(7)))
    vi.stubGlobal('fetch', fetchMock)
    const container = mount()
    await flush()

    button(container, '7 days').click()
    await flush()

    expect(fetchMock.mock.calls[1]?.[0]).toBe('/api/links/abc123XYZ/stats?days=7')
    expect(container.querySelectorAll('.stats-chart svg g')).toHaveLength(7)
    expect(button(container, '7 days').getAttribute('aria-pressed')).toBe('true')
  })

  test('says when there is nothing to break down', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(response(stats(7, { scans: 0, bots: 0, devices: [], referrers: [] }))),
    )
    const container = mount()
    await flush()

    expect(container.textContent).toContain('No scans in this period.')
  })

  test('reports a failed load', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(response({ detail: 'days must be between 1 and 365' }, 400)),
    )
    const container = mount()
    await flush()

    expect(container.querySelector('[role="alert"]')?.textContent).toContain('days must be')
  })

  test('closes with the button and with Escape', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response(stats(7))))
    const onClose = vi.fn()
    const container = mount(onClose)
    await flush()

    expect(document.activeElement?.textContent?.trim()).toBe('Close')
    button(container, 'Close').click()
    container
      .querySelector('[role="dialog"]')
      ?.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }))

    expect(onClose).toHaveBeenCalledTimes(2)
  })
})
