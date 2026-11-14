<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { clampDpi, DPI_MAX, DPI_MIN, printSummary } from '../print'

/**
 * The print resolution for downloads, and how large the image comes out at it.
 *
 * The resolution is an output setting, not part of the design: it is not saved with a design or
 * put in a share link, and the preview does not use it.
 */
const props = withDefaults(
  defineProps<{
    /** The image's size in pixels, frame included; 0 before the preview has loaded. */
    width: number
    height: number
    /** A frame's bleed on each side, in pixels. */
    bleed?: number
  }>(),
  { bleed: 0 },
)

const dpi = defineModel<number>('dpi', { required: true })

/** The field's text, so a half-typed value is not snapped into range on every keystroke. */
const draft = ref(String(dpi.value))
watch(dpi, (value) => {
  draft.value = String(value)
})

function commit(): void {
  const value = clampDpi(Number(draft.value))
  dpi.value = value
  draft.value = String(value)
}

const summary = computed(() =>
  props.width > 0 && props.height > 0 ? printSummary(props.width, props.height, dpi.value, props.bleed) : '',
)
</script>

<template>
  <div class="print-size">
    <label for="print-dpi">Print resolution</label>
    <div class="print-size-field">
      <input
        id="print-dpi"
        v-model="draft"
        type="number"
        inputmode="numeric"
        :min="DPI_MIN"
        :max="DPI_MAX"
        step="1"
        aria-describedby="print-size-summary"
        @change="commit"
        @blur="commit"
      />
      <span>DPI</span>
    </div>
    <p id="print-size-summary" class="hint" aria-live="polite">{{ summary }}</p>
  </div>
</template>
