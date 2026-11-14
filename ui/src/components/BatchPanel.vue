<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  listDesigns,
  renderBatch,
  type BatchFormat,
  type DesignSummary,
  type QrStyle,
} from '../api/client'
import {
  MAX_BATCH_BYTES,
  MAX_BATCH_ROWS,
  SAMPLE_CSV,
  summarizeCsv,
  type CsvSummary,
} from '../batch'

/**
 * Renders a CSV of payloads with one style and downloads the codes as a ZIP.
 *
 * <p>The prop is `qrStyle`, not `style`: Vue would clone a reactive prop named `style` into a
 * plain object. The panel only reads it, at the moment the batch is sent.
 */
const props = defineProps<{ qrStyle: QrStyle }>()

const formats: { value: BatchFormat; label: string }[] = [
  { value: 'png', label: 'PNG' },
  { value: 'svg', label: 'SVG' },
  { value: 'both', label: 'Both' },
]

const file = ref<File | null>(null)
const summary = ref<CsvSummary | null>(null)
const format = ref<BatchFormat>('png')
const source = ref<'current' | 'design'>('current')
const designs = ref<DesignSummary[]>([])
const designId = ref<number | ''>('')
const busy = ref(false)
const error = ref('')
const notice = ref('')

/** Problems the browser can see before uploading; the server checks the same and more. */
const problem = computed(() => {
  if (!file.value || !summary.value) {
    return ''
  }
  if (file.value.size > MAX_BATCH_BYTES) {
    return 'The CSV must be at most 1 MB.'
  }
  if (!summary.value.hasData) {
    return summary.value.columns.length
      ? `The header row has no data column. Found: ${summary.value.columns.join(', ')}.`
      : 'The file is empty. Its first row must name the columns.'
  }
  if (summary.value.rows === 0) {
    return 'The CSV has a header but no rows.'
  }
  if (summary.value.rows > MAX_BATCH_ROWS) {
    return `The CSV has ${summary.value.rows} rows; a batch takes at most ${MAX_BATCH_ROWS}.`
  }
  return ''
})

const canRun = computed(
  () =>
    file.value !== null &&
    problem.value === '' &&
    !busy.value &&
    (source.value === 'current' || designId.value !== ''),
)

function messageOf(cause: unknown): string {
  return cause instanceof Error ? cause.message : 'Something went wrong.'
}

async function choose(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const chosen = input.files?.[0] ?? null
  error.value = ''
  notice.value = ''
  file.value = chosen
  summary.value = null
  if (!chosen) {
    return
  }
  try {
    summary.value = summarizeCsv(chosen.size > MAX_BATCH_BYTES ? '' : await chosen.text())
  } catch (cause) {
    error.value = messageOf(cause)
  }
}

function save(blob: Blob, name: string): void {
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = name
  anchor.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

function downloadSample(): void {
  save(new Blob([SAMPLE_CSV], { type: 'text/csv' }), 'qr-batch-sample.csv')
}

async function run(): Promise<void> {
  const csv = file.value
  if (!csv || !canRun.value) {
    return
  }
  busy.value = true
  error.value = ''
  notice.value = ''
  try {
    const zip = await renderBatch(
      csv,
      source.value === 'design' && designId.value !== ''
        ? { format: format.value, designId: designId.value }
        : { format: format.value, style: props.qrStyle },
    )
    save(zip, 'qr-codes.zip')
    notice.value = 'Downloaded. Rows that could not be rendered are listed in report.csv.'
  } catch (cause) {
    error.value = messageOf(cause)
  } finally {
    busy.value = false
  }
}

onMounted(async () => {
  try {
    designs.value = await listDesigns()
  } catch {
    // Without the list only the current style is offered; the batch itself still works.
  }
})
</script>

<template>
  <div class="batch-panel">
    <p class="field-help">
      Upload a UTF-8 CSV whose header row has a <code>data</code> column (the payload of each
      code) and optionally a <code>filename</code> column. Up to {{ MAX_BATCH_ROWS }} rows and
      1 MB. Rows that cannot be rendered are skipped and listed in the ZIP's
      <code>report.csv</code>.
      <button type="button" class="link-button" @click="downloadSample">Download a sample CSV</button>
    </p>

    <div class="field">
      <label class="field-label" for="batch-file">CSV file</label>
      <input id="batch-file" type="file" accept=".csv,text/csv" @change="choose" />
    </div>

    <div v-if="summary && !problem" class="batch-summary" aria-live="polite">
      <p class="hint">{{ summary.rows }} {{ summary.rows === 1 ? 'row' : 'rows' }} found.</p>
      <ul>
        <li v-for="(row, index) in summary.sample" :key="index">
          <code>{{ row.filename || `row-${index + 1}` }}</code>
          <span>{{ row.data || '(empty, will be skipped)' }}</span>
        </li>
      </ul>
    </div>

    <div class="control">
      <span class="control-label">Format</span>
      <div class="segmented" role="group" aria-label="Batch format">
        <button
          v-for="option in formats"
          :key="option.value"
          type="button"
          :class="{ active: format === option.value }"
          :aria-pressed="format === option.value"
          @click="format = option.value"
        >
          {{ option.label }}
        </button>
      </div>
    </div>

    <div class="control">
      <span class="control-label">Style</span>
      <div class="segmented" role="group" aria-label="Batch style">
        <button
          type="button"
          :class="{ active: source === 'current' }"
          :aria-pressed="source === 'current'"
          @click="source = 'current'"
        >
          Current style
        </button>
        <button
          type="button"
          :class="{ active: source === 'design' }"
          :aria-pressed="source === 'design'"
          :disabled="designs.length === 0"
          @click="source = 'design'"
        >
          Saved design
        </button>
      </div>
      <template v-if="source === 'design'">
        <label class="visually-hidden" for="batch-design">Design for the batch</label>
        <select id="batch-design" v-model="designId">
          <option value="">Choose a saved design…</option>
          <option v-for="design in designs" :key="design.id" :value="design.id">
            {{ design.name }}{{ !design.mine && design.owner ? ` — ${design.owner}` : '' }}
          </option>
        </select>
      </template>
    </div>

    <div class="button-row">
      <button type="button" class="primary" :disabled="!canRun" @click="run">
        {{ busy ? 'Rendering…' : 'Download ZIP' }}
      </button>
    </div>

    <p v-if="problem" class="error" role="alert">{{ problem }}</p>
    <p v-else-if="error" class="error" role="alert">{{ error }}</p>
    <p v-else-if="notice" class="hint" aria-live="polite">{{ notice }}</p>
  </div>
</template>
