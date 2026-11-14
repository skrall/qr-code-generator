<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { getLinkStats, type DeviceClass, type LinkStats, type ShortLink } from '../api/client'

/**
 * A link's scan statistics: totals, scans per UTC day as inline SVG bars (with the same numbers
 * as a table for screen readers and anyone who wants them), devices and referrers.
 */
const props = defineProps<{ link: ShortLink }>()
const emit = defineEmits<{ close: [] }>()

const PERIODS = [7, 30, 90] as const

const days = ref<number>(30)
const stats = ref<LinkStats | null>(null)
const state = ref<'loading' | 'ready' | 'error'>('loading')
const error = ref('')
const closeButton = ref<HTMLButtonElement | null>(null)

const DEVICE_LABELS: Record<DeviceClass, string> = {
  IOS: 'iOS',
  ANDROID: 'Android',
  WINDOWS: 'Windows',
  MACOS: 'macOS',
  LINUX: 'Linux',
  OTHER: 'Other',
}

let controller: AbortController | null = null

async function load(): Promise<void> {
  controller?.abort()
  const current = new AbortController()
  controller = current
  state.value = 'loading'
  error.value = ''
  try {
    const result = await getLinkStats(props.link.code, days.value, current.signal)
    if (current.signal.aborted) {
      return
    }
    stats.value = result
    state.value = 'ready'
  } catch (cause) {
    if (current.signal.aborted) {
      return
    }
    error.value = cause instanceof Error ? cause.message : 'The statistics could not be loaded.'
    state.value = 'error'
  }
}

watch(days, () => void load())

/** The tallest day, people and bots together, so stacked bars share one scale. */
const peak = computed(() =>
  Math.max(1, ...(stats.value?.daily ?? []).map((day) => day.scans + day.bots)),
)

const BAR = 10
const GAP = 2
const HEIGHT = 100

const bars = computed(() =>
  (stats.value?.daily ?? []).map((day, index) => {
    const people = (day.scans / peak.value) * HEIGHT
    const bots = (day.bots / peak.value) * HEIGHT
    return {
      date: day.date,
      x: index * BAR + GAP / 2,
      people,
      bots,
      label: `${formatDay(day.date)}: ${count(day.scans, 'scan')}, ${count(day.bots, 'bot')}`,
    }
  }),
)

const chartWidth = computed(() => Math.max(1, (stats.value?.daily.length ?? 1) * BAR))

const busiest = computed(() => {
  const daily = stats.value?.daily ?? []
  return daily.reduce<(typeof daily)[number] | null>(
    (best, day) => (day.scans > (best?.scans ?? 0) ? day : best),
    null,
  )
})

const chartSummary = computed(() => {
  const current = stats.value
  if (!current) {
    return ''
  }
  const peakText = busiest.value
    ? ` Busiest day ${formatDay(busiest.value.date)} with ${count(busiest.value.scans, 'scan')}.`
    : ''
  return (
    `Scans per day from ${formatDay(current.from)} to ${formatDay(current.to)} (UTC): ` +
    `${count(current.scans, 'scan')} by people and ${count(current.bots, 'bot')}.${peakText}`
  )
})

const deviceTotal = computed(() =>
  (stats.value?.devices ?? []).reduce((sum, device) => sum + device.scans, 0),
)

const referrerTotal = computed(() => stats.value?.scans ?? 0)

const retentionNote = computed(() => {
  const retention = stats.value?.retentionDays ?? 0
  return retention > 0 ? `Scans are kept for ${retention} days.` : 'Scans are kept indefinitely.'
})

function count(value: number, noun: string): string {
  return `${value.toLocaleString()} ${noun}${value === 1 ? '' : 's'}`
}

/** A `YYYY-MM-DD` UTC day, shown as that calendar day whatever the browser's zone. */
function formatDay(day: string): string {
  const date = new Date(`${day}T00:00:00Z`)
  if (Number.isNaN(date.getTime())) {
    return day
  }
  return date.toLocaleDateString(undefined, { month: 'short', day: 'numeric', timeZone: 'UTC' })
}

function share(value: number, total: number): string {
  return total > 0 ? `${Math.round((value / total) * 100)}%` : '0%'
}

function close(): void {
  emit('close')
}

// Focus moves into the dialog on open and back to whatever opened it on close.
let opener: HTMLElement | null = null

onMounted(() => {
  opener = document.activeElement instanceof HTMLElement ? document.activeElement : null
  closeButton.value?.focus()
  void load()
})
onUnmounted(() => {
  controller?.abort()
  opener?.focus()
})
</script>

<template>
  <div class="edit-overlay" @click.self="close">
    <section
      class="card edit-dialog stats-dialog"
      role="dialog"
      aria-modal="true"
      aria-labelledby="link-stats-title"
      @keydown.esc="close"
    >
      <div class="stats-head">
        <h2 id="link-stats-title">Scan statistics</h2>
        <button ref="closeButton" type="button" @click="close">Close</button>
      </div>
      <p class="field-help">
        <code>{{ link.shortUrl }}</code> → <span class="stats-target">{{ link.targetUrl }}</span>
      </p>

      <div class="segmented stats-periods" role="group" aria-label="Period">
        <button
          v-for="period in PERIODS"
          :key="period"
          type="button"
          :class="{ active: days === period }"
          :aria-pressed="days === period"
          @click="days = period"
        >
          {{ period }} days
        </button>
      </div>

      <p v-if="state === 'error'" class="error" role="alert">{{ error }}</p>
      <p v-else-if="state === 'loading' && !stats" class="hint">Loading…</p>

      <template v-if="stats">
        <div class="stats-totals" :aria-busy="state === 'loading'">
          <div class="stats-total">
            <span class="stats-number">{{ stats.scans.toLocaleString() }}</span>
            <span class="hint">scans in {{ stats.days }} days</span>
          </div>
          <div class="stats-total">
            <span class="stats-number">{{ stats.bots.toLocaleString() }}</span>
            <span class="hint">bots and link previews</span>
          </div>
          <div class="stats-total">
            <span class="stats-number">{{ stats.allTime.toLocaleString() }}</span>
            <span class="hint">scans all time</span>
          </div>
        </div>

        <figure class="stats-chart">
          <svg
            :viewBox="`0 0 ${chartWidth} ${HEIGHT}`"
            preserveAspectRatio="none"
            role="img"
            :aria-label="chartSummary"
          >
            <g v-for="bar in bars" :key="bar.date">
              <title>{{ bar.label }}</title>
              <rect
                v-if="bar.people > 0"
                class="stats-bar-people"
                :x="bar.x"
                :y="HEIGHT - bar.people"
                :width="BAR - GAP"
                :height="bar.people"
              />
              <rect
                v-if="bar.bots > 0"
                class="stats-bar-bots"
                :x="bar.x"
                :y="HEIGHT - bar.people - bar.bots"
                :width="BAR - GAP"
                :height="bar.bots"
              />
            </g>
          </svg>
          <figcaption class="stats-axis hint">
            <span>{{ formatDay(stats.from) }}</span>
            <span class="stats-legend">
              <span class="stats-swatch stats-swatch-people" aria-hidden="true"></span> People
              <span class="stats-swatch stats-swatch-bots" aria-hidden="true"></span> Bots
            </span>
            <span>{{ formatDay(stats.to) }} (UTC)</span>
          </figcaption>
        </figure>

        <details class="stats-table-toggle">
          <summary>Show scans per day as a table</summary>
          <table class="links-table stats-table">
            <thead>
              <tr>
                <th>Day (UTC)</th>
                <th class="numeric">Scans</th>
                <th class="numeric">Bots</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="day in stats.daily" :key="day.date">
                <td>{{ day.date }}</td>
                <td class="numeric">{{ day.scans }}</td>
                <td class="numeric">{{ day.bots }}</td>
              </tr>
            </tbody>
          </table>
        </details>

        <div class="stats-breakdowns">
          <div>
            <h3>Devices</h3>
            <p v-if="stats.devices.length === 0" class="hint">No scans in this period.</p>
            <table v-else class="links-table stats-table" aria-label="Scans by device">
              <tbody>
                <tr v-for="device in stats.devices" :key="device.device">
                  <th scope="row">{{ DEVICE_LABELS[device.device] }}</th>
                  <td class="stats-meter-cell">
                    <span
                      class="stats-meter"
                      :style="{ width: share(device.scans, deviceTotal) }"
                      aria-hidden="true"
                    ></span>
                  </td>
                  <td class="numeric">{{ device.scans }}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <div>
            <h3>Referrers</h3>
            <p v-if="stats.referrers.length === 0" class="hint">No scans in this period.</p>
            <table v-else class="links-table stats-table" aria-label="Scans by referrer">
              <tbody>
                <tr v-for="referrer in stats.referrers" :key="referrer.host ?? ''">
                  <th scope="row">
                    <span v-if="referrer.host">{{ referrer.host }}</span>
                    <span v-else class="hint">No referrer</span>
                  </th>
                  <td class="stats-meter-cell">
                    <span
                      class="stats-meter"
                      :style="{ width: share(referrer.scans, referrerTotal) }"
                      aria-hidden="true"
                    ></span>
                  </td>
                  <td class="numeric">{{ referrer.scans }}</td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>

        <p class="field-help">
          Days are UTC. Bots (crawlers and chat link previews) are counted apart and left out of
          the device and referrer figures. No IP addresses or user agents are stored.
          {{ retentionNote }}
        </p>
      </template>
    </section>
  </div>
</template>
