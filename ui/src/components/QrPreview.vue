<script setup lang="ts">
import { ref, watch } from 'vue'
import type { QrFormat } from '../api/client'
import type { EmbedSource } from '../embed'
import { DPI_DEFAULT } from '../print'
import EmbedMenu from './EmbedMenu.vue'
import PrintSize from './PrintSize.vue'
import type { PreviewState } from '../preview'

const props = withDefaults(
  defineProps<{
    url: string
    state: PreviewState
    error: string
    payload: string
    /** Shown before there is anything to render; the short link type needs a different prompt. */
    placeholder?: string
    /** True when the code shows a draft of the destination rather than the final payload. */
    draft?: boolean
    /** False while the payload is a draft, so a destination code cannot be downloaded. */
    downloadable?: boolean
    /** Enables "Copy as…" snippets; omitted, the menu is not shown. */
    embed?: EmbedSource | null
    /** A frame's bleed on each side in pixels, for the print size; 0 for none. */
    bleed?: number
  }>(),
  {
    placeholder: 'Fill in the required fields and the code appears here.',
    draft: false,
    downloadable: true,
    embed: null,
    bleed: 0,
  },
)

defineEmits<{
  /** A download in a format, at the print resolution chosen here. */
  download: [format: QrFormat, dpi: number]
}>()

/** The print resolution: an output setting, kept here rather than in the style. */
const dpi = ref(DPI_DEFAULT)

/** The preview's pixel size, which is the download's: a frame makes it larger than `size`. */
const width = ref(0)
const height = ref(0)

function measure(event: Event): void {
  const image = event.target as HTMLImageElement
  width.value = image.naturalWidth
  height.value = image.naturalHeight
}

watch(
  () => props.url,
  (url) => {
    if (!url) {
      width.value = 0
      height.value = 0
    }
  },
)
</script>

<template>
  <div class="preview">
    <div class="preview-stage">
      <img v-if="url" :src="url" alt="QR code preview" @load="measure" />
      <p v-else-if="state === 'loading'" class="preview-placeholder">Rendering…</p>
      <p v-else class="preview-placeholder">{{ placeholder }}</p>
      <span v-if="state === 'loading' && url" class="spinner" aria-hidden="true"></span>
    </div>

    <p v-if="state === 'error'" class="error" role="alert">{{ error }}</p>

    <div v-if="draft" class="notice">
      <p>
        Draft preview: this code encodes the destination. Create the link to encode the short
        URL instead.
      </p>
    </div>

    <div class="preview-actions">
      <button
        type="button"
        class="primary"
        :disabled="!url || !downloadable"
        @click="$emit('download', 'png', dpi)"
      >
        Download PNG
      </button>
      <button type="button" :disabled="!url || !downloadable" @click="$emit('download', 'svg', dpi)">
        Download SVG
      </button>
      <button type="button" :disabled="!url || !downloadable" @click="$emit('download', 'pdf', dpi)">
        Download PDF
      </button>
    </div>

    <PrintSize v-model:dpi="dpi" :width="width" :height="height" :bleed="bleed" />

    <EmbedMenu
      v-if="embed"
      :source="embed"
      :disabled="!url || !downloadable || state !== 'ready'"
    />

    <details v-if="payload" class="payload">
      <summary>Encoded content</summary>
      <pre>{{ payload }}</pre>
    </details>
  </div>
</template>
