<script setup lang="ts">
import { computed } from 'vue'
import type { ErrorCorrection } from '../api/client'
import { DENSE_VERSION, EC_LEVELS, MAX_VERSION, measure } from '../qr/capacity'

/**
 * How large a code the payload needs, worked out in the browser on every keystroke.
 *
 * <p>The figures come from `qr/capacity.ts`, which follows ZXing's encoder exactly, so "too long"
 * here is the payload the server rejects. The density warning starts at the version where the
 * scan check starts hinting that modules are small.
 */
const props = withDefaults(
  defineProps<{
    /** Exactly what will be encoded; empty while there is nothing to render. */
    payload: string
    level: ErrorCorrection
    /** True while a short link's destination stands in for the short URL it will become. */
    draft?: boolean
  }>(),
  { draft: false },
)

const LEVEL_NAMES: Record<ErrorCorrection, string> = {
  LOW: 'low',
  MEDIUM: 'medium',
  QUARTILE: 'quartile',
  HIGH: 'high',
}

const number = new Intl.NumberFormat('en-US')

const size = computed(() => (props.payload ? measure(props.payload, props.level) : null))

const tone = computed(() => {
  if (!size.value) {
    return 'idle'
  }
  if (size.value.version === null) {
    return 'too-long'
  }
  return size.value.version >= DENSE_VERSION ? 'dense' : 'ok'
})

/** The version at every level, so the effect of changing the level is visible before choosing. */
const byLevel = computed(() =>
  EC_LEVELS.map((level) => ({
    level,
    letter: level[0],
    version: measure(props.payload, level).version,
  })).map((entry) => ({
    ...entry,
    short: !props.payload ? '–' : entry.version === null ? 'too long' : `v${entry.version}`,
    spoken: !props.payload ? 'none' : entry.version === null ? 'too long' : `version ${entry.version}`,
  })),
)

const fill = computed(() =>
  size.value ? Math.min(100, (size.value.usedCodewords / size.value.capacityCodewords) * 100) : 0,
)

const headline = computed(() => {
  const current = size.value
  if (!current) {
    return 'Code size appears as you type'
  }
  if (current.version === null) {
    return 'Too long for a QR code'
  }
  return `Version ${current.version} · ${current.modules} × ${current.modules} modules`
})

const usage = computed(() =>
  size.value
    ? `${number.format(size.value.usedCodewords)} / ${number.format(size.value.capacityCodewords)} bytes`
    : '',
)

/** A lower level that fits, the strongest one, since it gives up the least protection. */
const fittingLowerLevel = computed(() =>
  byLevel.value
    .slice(0, EC_LEVELS.indexOf(props.level))
    .filter((entry) => entry.version !== null)
    .at(-1),
)

const looksLikeUrl = computed(() => /^https?:\/\//i.test(props.payload.trim()))

const detail = computed(() => {
  const current = size.value
  if (!current) {
    return 'Longer content needs a larger, denser grid.'
  }
  const left = current.capacityCodewords - current.usedCodewords
  if (current.version === null) {
    const over = current.usedCodewords - current.capacityCodewords
    const remedies: string[] = []
    const lower = fittingLowerLevel.value
    if (lower) {
      remedies.push(`choose ${LEVEL_NAMES[lower.level]} error correction (version ${lower.version})`)
    }
    if (looksLikeUrl.value && !props.draft) {
      remedies.push('use a short link')
    }
    remedies.push('shorten the content')
    const advice = capitalise(
      remedies.length > 1 ? `${remedies.slice(0, -1).join(', ')} or ${remedies.at(-1)}` : remedies[0],
    )
    return `${number.format(over)} bytes over what version ${MAX_VERSION} holds at ` +
      `${LEVEL_NAMES[props.level]} error correction. ${advice}.`
  }
  const room =
    current.version === MAX_VERSION
      ? `${number.format(left)} bytes left in the largest version.`
      : `${number.format(left)} bytes left before version ${current.version + 1}.`
  return current.version >= DENSE_VERSION
    ? `Dense code: modules print small. ${room}`
    : room
})

function capitalise(text: string): string {
  return text.charAt(0).toUpperCase() + text.slice(1)
}
</script>

<template>
  <section class="payload-size" :class="`payload-size-${tone}`" aria-label="Code size">
    <div class="payload-size-row">
      <!-- Only the headline is live: it changes when the version does, not on every keystroke. -->
      <strong class="payload-size-headline" role="status" aria-live="polite">{{ headline }}</strong>
      <span class="payload-size-usage">{{ usage }}</span>
    </div>
    <div class="payload-size-meter" aria-hidden="true">
      <span :style="{ width: `${fill}%` }"></span>
    </div>
    <p class="payload-size-detail">
      {{ detail }}
      <template v-if="draft && size">
        Sized for the destination draft; Create link encodes the short URL instead.
      </template>
    </p>
    <p class="payload-size-levels">
      <span>By error correction:</span>
      <span
        v-for="entry in byLevel"
        :key="entry.level"
        class="payload-size-level"
        :class="{ current: entry.level === level }"
        :title="`${LEVEL_NAMES[entry.level]} error correction`"
      >
        <span aria-hidden="true">{{ entry.letter }} {{ entry.short }}</span>
        <span class="visually-hidden">{{ LEVEL_NAMES[entry.level] }}: {{ entry.spoken }};</span>
      </span>
    </p>
  </section>
</template>
