<script setup lang="ts">
import type { QrFormat } from '../api/client'
import type { PreviewState } from '../preview'

withDefaults(
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
  }>(),
  {
    placeholder: 'Fill in the required fields and the code appears here.',
    draft: false,
    downloadable: true,
  },
)

defineEmits<{
  download: [format: QrFormat]
}>()
</script>

<template>
  <div class="preview">
    <div class="preview-stage">
      <img v-if="url" :src="url" alt="QR code preview" />
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
        @click="$emit('download', 'png')"
      >
        Download PNG
      </button>
      <button type="button" :disabled="!url || !downloadable" @click="$emit('download', 'svg')">
        Download SVG
      </button>
    </div>

    <details v-if="payload" class="payload">
      <summary>Encoded content</summary>
      <pre>{{ payload }}</pre>
    </details>
  </div>
</template>
