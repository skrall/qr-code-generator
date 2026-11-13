<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import {
  checkQrCode,
  currentUser,
  type CurrentUser,
  renderQrCode,
  type QrFormat,
  type QrStyle,
  type ScanCheck,
  type ShortLink,
} from './api/client'
import ContentForm from './components/ContentForm.vue'
import LinksView from './components/LinksView.vue'
import FramePanel from './components/FramePanel.vue'
import LogoPanel from './components/LogoPanel.vue'
import QrPreview from './components/QrPreview.vue'
import ScanStatus from './components/ScanStatus.vue'
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
  gradient: null,
  background: '#ffffff',
  backgroundImage: null,
  moduleShape: 'ROUNDED',
  finderShape: 'ROUNDED',
  finderColor: '#3158c4',
  finderArtwork: null,
  logo: null,
  frame: null,
})

/**
 * The short link's payload comes from the server, not from a builder, so it is held here. Until
 * the link is created the form supplies a draft of the destination, which keeps the preview
 * responsive while the user types.
 */
const shortUrl = ref('')
const draftUrl = ref('')

const payload = computed(() => {
  if (typeId.value === 'shortlink') {
    return shortUrl.value || draftUrl.value
  }
  return activeType.value.build(model.value)
})
const missing = computed(() =>
  activeType.value.fields.filter((field) => field.required && !(model.value[field.key] ?? '').trim()),
)
const canRender = computed(() => {
  if (typeId.value === 'shortlink') {
    return shortUrl.value.length > 0 || draftUrl.value.length > 0
  }
  return missing.value.length === 0 && payload.value.trim().length > 0
})

/** True while the preview shows the destination rather than the stored short URL. */
const isDraft = computed(
  () => typeId.value === 'shortlink' && shortUrl.value.length === 0 && draftUrl.value.length > 0,
)

watch(typeId, () => {
  shortUrl.value = ''
  draftUrl.value = ''
})

function linkCreated(link: ShortLink): void {
  shortUrl.value = link.shortUrl
}

function linkCleared(): void {
  shortUrl.value = ''
}

function linkDraft(url: string): void {
  draftUrl.value = url
}

/**
 * Who is signed in. Until it is known the link actions stay hidden: showing them to a viewer for
 * a moment, then taking them away, is worse than showing them a moment late.
 */
const user = ref<CurrentUser | null>(null)
const operator = computed(() => user.value?.operator ?? false)

onMounted(async () => {
  try {
    user.value = await currentUser()
  } catch {
    // A 401 has already sent the browser to sign in; anything else leaves the actions hidden.
  }
})

const previewUrl = ref('')
const state = ref<PreviewState>('idle')
const error = ref('')
const scan = ref<ScanCheck | null>(null)
const scanPending = ref(false)

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
    scan.value = null
    scanPending.value = false
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
    scan.value = null
    scanPending.value = false
    return
  }

  await refreshScan(current)
}

/**
 * Checks the code the preview now shows. It runs after the render rather than beside it, so a
 * rejected style is reported once, by the preview, and the image is never held up by the check.
 */
async function refreshScan(current: AbortController): Promise<void> {
  scanPending.value = true
  try {
    const result = await checkQrCode({ data: payload.value, style }, current.signal)
    if (current.signal.aborted) {
      return
    }
    scan.value = result
  } catch {
    if (current.signal.aborted) {
      return
    }
    // The check is advice. If it cannot run, saying nothing beats a verdict about an old image.
    scan.value = null
  }
  scanPending.value = false
}

function schedule(): void {
  clearTimeout(timer)
  timer = setTimeout(() => void refresh(), 220)
}

watch([payload, style], schedule, { deep: true, immediate: true })

const downloading = ref<QrFormat | null>(null)

async function download(format: QrFormat): Promise<void> {
  if (!canRender.value || isDraft.value || downloading.value) {
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
        <span v-if="user?.name" class="signed-in" :title="operator ? 'Operator' : 'Viewer'">
          {{ user.name }}<span class="role">{{ operator ? 'operator' : 'viewer' }}</span>
        </span>
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
            :operator="operator"
            @created="linkCreated"
            @cleared="linkCleared"
            @draft="linkDraft"
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

          <hr />

          <h2>Frame</h2>
          <FramePanel v-model="style.frame" />
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
            :draft="isDraft"
            :downloadable="!isDraft"
            :placeholder="
              typeId === 'shortlink'
                ? 'Enter a destination to see a draft, then Create link to encode the short URL.'
                : 'Fill in the required fields and the code appears here.'
            "
            @download="download"
          />

          <ScanStatus v-if="state !== 'error'" :check="scan" :pending="scanPending" />

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
      <LinksView :operator="operator" />
    </main>
  </div>
</template>
