<script setup lang="ts">
import type { QrFormat } from '../api/client'
import type { PreviewState } from '../preview'

defineProps<{
  url: string
  state: PreviewState
  error: string
  payload: string
}>()

defineEmits<{
  download: [format: QrFormat]
}>()
</script>

<template>
  <div class="preview">
    <div class="preview-stage">
      <img v-if="url" :src="url" alt="QR code preview" />
      <p v-else-if="state === 'loading'" class="preview-placeholder">Rendering…</p>
      <p v-else class="preview-placeholder">
        Fill in the required fields and the code appears here.
      </p>
      <span v-if="state === 'loading' && url" class="spinner" aria-hidden="true"></span>
    </div>

    <p v-if="state === 'error'" class="error" role="alert">{{ error }}</p>

    <div class="preview-actions">
      <button type="button" class="primary" :disabled="!url" @click="$emit('download', 'png')">
        Download PNG
      </button>
      <button type="button" :disabled="!url" @click="$emit('download', 'svg')">
        Download SVG
      </button>
    </div>

    <details v-if="payload" class="payload">
      <summary>Encoded content</summary>
      <pre>{{ payload }}</pre>
    </details>
  </div>
</template>
