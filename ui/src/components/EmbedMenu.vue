<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { copyText } from '../clipboard'
import {
  buildSnippet,
  embedFormats,
  type EmbedFormat,
  type EmbedSource,
  LARGE_SNIPPET_BYTES,
  snippetBytes,
} from '../embed'

const props = defineProps<{
  source: EmbedSource
  /** Same rule as the download buttons: no copy of a draft, while rendering or after an error. */
  disabled: boolean
}>()

const formats = computed(() => embedFormats(props.source.href))
const format = ref<EmbedFormat>('img')
const busy = ref(false)
/** Announced through the live region: "Copied", or why it did not happen. */
const status = ref('')
const failed = ref(false)
const warning = ref('')
/** Shown when the clipboard cannot be written, so the snippet can still be copied by hand. */
const fallback = ref('')
const fallbackField = ref<HTMLTextAreaElement | null>(null)

function reset(): void {
  status.value = ''
  failed.value = false
  warning.value = ''
  fallback.value = ''
}

// A new payload makes a shown snippet stale.
watch(() => props.source, reset)
watch(format, reset)
// A style change does not replace the source, but it does start a render, which disables the menu.
watch(
  () => props.disabled,
  (disabled) => {
    if (disabled) {
      reset()
    }
  },
)
// The linked format goes away when the payload stops being a URL.
watch(formats, (available) => {
  if (!available.some((option) => option.value === format.value)) {
    format.value = 'img'
  }
})

function sizeWarning(snippet: string): string {
  const bytes = snippetBytes(snippet)
  if (bytes <= LARGE_SNIPPET_BYTES) {
    return ''
  }
  const kb = Math.round(bytes / 1024)
  const advice =
    format.value === 'svg'
      ? 'Embedded images make it large; consider hosting the file and linking to it.'
      : 'A logo or background image makes the PNG large; inline SVG is often smaller, or host the file and link to it.'
  return `This snippet is ${kb} KB. ${advice}`
}

async function copy(): Promise<void> {
  if (props.disabled || busy.value) {
    return
  }
  busy.value = true
  reset()
  let snippet: string
  try {
    snippet = await buildSnippet(format.value, props.source)
  } catch (cause) {
    failed.value = true
    status.value = cause instanceof Error ? cause.message : 'The snippet could not be made.'
    busy.value = false
    return
  }
  warning.value = sizeWarning(snippet)

  const available = Boolean(navigator.clipboard?.writeText)
  if (available && (await copyText(snippet))) {
    status.value = 'Copied'
  } else {
    failed.value = true
    status.value = available
      ? 'The browser blocked clipboard access. Select the snippet below and copy it.'
      : 'Clipboard access needs HTTPS or localhost. Select the snippet below and copy it.'
    fallback.value = snippet
    // Wait for the textarea to exist, then select it so Ctrl+C is all that is left.
    setTimeout(() => {
      fallbackField.value?.focus()
      fallbackField.value?.select()
    })
  }
  busy.value = false
}
</script>

<template>
  <div class="embed-menu">
    <div class="embed-controls">
      <label class="embed-label" for="embed-format">Copy as</label>
      <select id="embed-format" v-model="format" :disabled="disabled">
        <option v-for="option in formats" :key="option.value" :value="option.value">
          {{ option.label }}
        </option>
      </select>
      <button
        type="button"
        :disabled="disabled || busy"
        aria-describedby="embed-status"
        @click="copy"
      >
        {{ busy ? 'Copying…' : 'Copy' }}
      </button>
    </div>
    <p id="embed-status" class="embed-status" :class="{ 'error-text': failed }" aria-live="polite">
      {{ status }}
    </p>
    <p v-if="warning" class="embed-warning">{{ warning }}</p>
    <div v-if="fallback" class="embed-fallback">
      <label class="embed-label" for="embed-snippet">Snippet</label>
      <textarea
        id="embed-snippet"
        ref="fallbackField"
        readonly
        rows="4"
        :value="fallback"
        @focus="($event.target as HTMLTextAreaElement).select()"
      ></textarea>
    </div>
  </div>
</template>
