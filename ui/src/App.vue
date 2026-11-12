<script setup lang="ts">
import { computed, onUnmounted, reactive, ref, watch } from 'vue'
import { renderQrCode, type QrFormat, type QrStyle, type ShortLink } from './api/client'
import ContentForm from './components/ContentForm.vue'
import LinksView from './components/LinksView.vue'
import LogoPanel from './components/LogoPanel.vue'
import QrPreview from './components/QrPreview.vue'
import ShortLinkForm from './components/ShortLinkForm.vue'
import StylePanel from './components/StylePanel.vue'
import type { PreviewState } from './preview'
import { findQrType, initialValues, QR_TYPES, type QrTypeId } from './qr/types'

const view = ref<'generator' | 'links'>('generator')

const typeId = ref<QrTypeId>('url')
const activeType = computed(() => findQrType(typeId.value))

/** Values are kept per type, so switching back and forth does not discard what was typed. */
const values = reactive<Record<string, Record<string, string>>>(
  Object.fromEntries(QR_TYPES.map((type) => [type.id, initialValues(type)])),
)
const model = computed(() => values[typeId.value])

const style = reactive<QrStyle>({
  size: 640,
  margin: 4,
  errorCorrection: 'QUARTILE',
  foreground: '#111827',
  background: '#ffffff',
  moduleShape: 'ROUNDED',
  finderShape: 'ROUNDED',
  finderColor: '#3158c4',
  logo: null,
})

/**
 * The short link's payload comes from the server, not from a builder, so it is held here and
 * cleared whenever the type changes or the form is edited.
 */
const shortUrl = ref('')

const payload = computed(() => {
  if (typeId.value === 'shortlink') {
    return shortUrl.value
  }
  return activeType.value.build(model.value)
})
const missing = computed(() =>
  activeType.value.fields.filter((field) => field.required && !(model.value[field.key] ?? '').trim()),
)
const canRender = computed(() => {
  if (typeId.value === 'shortlink') {
    return shortUrl.value.length > 0
  }
  return missing.value.length === 0 && payload.value.trim().length > 0
})

watch(typeId, () => {
  shortUrl.value = ''
})

function linkCreated(link: ShortLink): void {
  shortUrl.value = link.shortUrl
}

function linkCleared(): void {
  shortUrl.value = ''
}

const previewUrl = ref('')
const state = ref<PreviewState>('idle')
const error = ref('')

let controller: AbortController | null = null
let timer: ReturnType<typeof setTimeout> | undefined
let objectUrl = ''

function messageOf(cause: unknown): string {
  return cause instanceof Error ? cause.message : 'Something went wrong.'
}

function showUrl(next: string): void {
  if (objectUrl) {
    URL.revokeObjectURL(objectUrl)
  }
  objectUrl = next
  previewUrl.value = next
}

async function refresh(): Promise<void> {
  controller?.abort()

  if (!canRender.value) {
    showUrl('')
    state.value = 'idle'
    error.value = ''
    return
  }

  const current = new AbortController()
  controller = current
  state.value = 'loading'
  error.value = ''

  try {
    const blob = await renderQrCode({ data: payload.value, style }, 'png', current.signal)
    if (current.signal.aborted) {
      return
    }
    showUrl(URL.createObjectURL(blob))
    state.value = 'ready'
  } catch (cause) {
    if (current.signal.aborted) {
      return
    }
    showUrl('')
    error.value = messageOf(cause)
    state.value = 'error'
  }
}

function schedule(): void {
  clearTimeout(timer)
  timer = setTimeout(() => void refresh(), 220)
}

watch([payload, style], schedule, { deep: true, immediate: true })

const downloading = ref<QrFormat | null>(null)

async function download(format: QrFormat): Promise<void> {
  if (!canRender.value || downloading.value) {
    return
  }
  downloading.value = format
  error.value = ''
  try {
    const blob = await renderQrCode({ data: payload.value, style }, format)
    const url = URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = `${typeId.value}-qr-code.${format}`
    anchor.click()
    setTimeout(() => URL.revokeObjectURL(url), 1000)
  } catch (cause) {
    error.value = messageOf(cause)
    state.value = 'error'
  } finally {
    downloading.value = null
  }
}

const logoNeedsHigh = computed(() => style.logo !== null && style.errorCorrection !== 'HIGH')

function useHighErrorCorrection(): void {
  style.errorCorrection = 'HIGH'
}

onUnmounted(() => {
  clearTimeout(timer)
  controller?.abort()
  if (objectUrl) {
    URL.revokeObjectURL(objectUrl)
  }
})
</script>

<template>
  <div class="app">
    <header class="app-header">
      <div>
        <h1>QR Code Generator</h1>
        <p>Styled codes with rounded modules, shaped corner patterns and your own logo.</p>
      </div>
      <div class="header-actions">
        <nav class="view-nav" aria-label="View">
          <button
            type="button"
            :class="{ active: view === 'generator' }"
            :aria-current="view === 'generator' ? 'page' : undefined"
            @click="view = 'generator'"
          >
            Generator
          </button>
          <button
            type="button"
            :class="{ active: view === 'links' }"
            :aria-current="view === 'links' ? 'page' : undefined"
            @click="view = 'links'"
          >
            Links
          </button>
        </nav>
        <a class="api-link" href="/v3/api-docs" target="_blank" rel="noreferrer">OpenAPI</a>
      </div>
    </header>

    <!-- v-show rather than v-if: the generator keeps its short link form state when the links
         view is opened, so a created link and the preview do not disagree on return. -->
    <main v-show="view === 'generator'" class="workspace">
      <section class="column">
        <div class="card">
          <h2>Content</h2>
          <div class="type-grid" role="tablist" aria-label="QR code type">
            <button
              v-for="type in QR_TYPES"
              :key="type.id"
              type="button"
              role="tab"
              :aria-selected="type.id === typeId"
              :class="{ active: type.id === typeId }"
              @click="typeId = type.id"
            >
              {{ type.label }}
            </button>
          </div>
          <p class="hint">{{ activeType.hint }}</p>
          <!-- The short link type has no fields of its own: its payload is the URL the server
               returns, so the create step lives in its own component. -->
          <ShortLinkForm
            v-if="typeId === 'shortlink'"
            @created="linkCreated"
            @cleared="linkCleared"
          />
          <ContentForm v-else :type="activeType" :model="model" />
        </div>

        <div class="card">
          <h2>Style</h2>
          <!-- Not `:style`: Vue clones a reactive prop named `style` into a plain object. -->
          <StylePanel :qr-style="style" />

          <hr />

          <h2>Logo</h2>
          <LogoPanel v-model="style.logo" />
          <p class="field-help">
            A logo sits on a plate in the middle and hides part of the code; high error correction
            keeps the rest recoverable.
          </p>
        </div>
      </section>

      <aside class="column preview-column">
        <div class="card preview-card">
          <h2>Preview</h2>
          <QrPreview
            :url="previewUrl"
            :state="state"
            :error="error"
            :payload="payload"
            :placeholder="
              typeId === 'shortlink'
                ? 'Create a link and the code appears here.'
                : 'Fill in the required fields and the code appears here.'
            "
            @download="download"
          />

          <div v-if="logoNeedsHigh" class="notice">
            <p>A logo covers part of the code. High error correction is recommended.</p>
            <button type="button" @click="useHighErrorCorrection">Use high</button>
          </div>

          <p v-if="missing.length" class="hint" aria-live="polite">
            Still needed: {{ missing.map((field) => field.label).join(', ') }}
          </p>
        </div>
      </aside>
    </main>

    <main v-if="view === 'links'" class="workspace single">
      <LinksView />
    </main>
  </div>
</template>
