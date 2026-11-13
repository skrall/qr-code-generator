<script setup lang="ts">
import { computed } from 'vue'
import type { ScanCheck, ScanVerdict } from '../api/client'

const props = defineProps<{
  /** The latest result, kept while a newer check runs so the panel does not flicker. */
  check: ScanCheck | null
  /** True while a check for the current preview is in flight. */
  pending: boolean
}>()

const LABELS: Record<ScanVerdict, string> = {
  PASS: 'Scans reliably',
  MARGINAL: 'May not scan on every phone',
  FAIL: 'Does not scan',
}

const label = computed(() => (props.check ? LABELS[props.check.verdict] : 'Checking scannability…'))
const tone = computed(() => props.check?.verdict.toLowerCase() ?? 'pending')
</script>

<template>
  <section
    v-if="check || pending"
    class="scan-status"
    :class="[`scan-${tone}`, { 'scan-stale': pending && check }]"
    role="status"
    aria-live="polite"
    :aria-busy="pending"
  >
    <p class="scan-verdict">
      <span class="scan-dot" aria-hidden="true"></span>
      <strong>{{ label }}</strong>
      <span v-if="check" class="scan-meta">
        Version {{ check.version }} · {{ check.modules }}×{{ check.modules }} modules
      </span>
    </p>

    <ul v-if="check" class="scan-trials" aria-label="Read attempts">
      <li v-for="trial in check.trials" :key="trial.name" :class="{ failed: !trial.decoded }">
        <span aria-hidden="true">{{ trial.decoded ? '✓' : '✕' }}</span>
        {{ trial.description }}
        <span class="visually-hidden">{{ trial.decoded ? 'read' : 'not read' }}</span>
      </li>
    </ul>

    <ul v-if="check && check.hints.length" class="scan-hints">
      <li v-for="hint in check.hints" :key="hint">{{ hint }}</li>
    </ul>
  </section>
</template>
