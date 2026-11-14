<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { ApiError, getDesign, type QrStyle } from '../api/client'
import { copyText } from '../clipboard'
import { applyStyle } from '../qr/style'
import { findQrType, initialValues, type QrTypeId } from '../qr/types'
import {
  decodeShare,
  designUrl,
  encodeShare,
  MAX_SHARE_URL_LENGTH,
  parseFragment,
  secretFields,
  shareableContent,
  shareableStyle,
  shareUrl,
  styleImages,
  type CurrentDesign,
} from '../share'

/**
 * Copies a link that reopens the design, and opens such links.
 *
 * <p>The prop is `qrStyle`, not `style`: Vue would clone a reactive prop named `style` into a
 * plain object, and opening a link would then change a copy nobody renders.
 */
const props = defineProps<{
  qrStyle: QrStyle
  values: Record<string, Record<string, string>>
  /** The saved design on screen, which a link can name when the style has images. */
  design: CurrentDesign | null
}>()

const typeId = defineModel<QrTypeId>('typeId', { required: true })

const emit = defineEmits<{
  /** The editor took a link's state; App makes that one undo step. */
  applied: []
}>()

/** Off by default: content can be personal, and a link travels further than intended. */
const includeContent = ref(false)
const link = ref('')
const notice = ref('')
const error = ref('')
const busy = ref(false)

const images = computed(() => styleImages(props.qrStyle))
const secrets = computed(() => secretFields(typeId.value))
const hasFields = computed(() => findQrType(typeId.value).fields.length > 0)

// A copied link describes the editor as it was; once that changes, it is no longer on offer.
watch([() => props.qrStyle, () => props.values, typeId, includeContent], () => {
  link.value = ''
}, { deep: true })

function base(): string {
  return `${window.location.origin}${window.location.pathname}`
}

function list(words: string[]): string {
  return words.length < 2 ? words.join('') : `${words.slice(0, -1).join(', ')} and ${words.at(-1)}`
}

async function offer(url: string, what: string): Promise<void> {
  link.value = url
  notice.value = (await copyText(url)) ? `Copied ${what}.` : `Copy ${what} from the box below.`
}

async function copyLink(): Promise<void> {
  error.value = ''
  notice.value = ''
  link.value = ''
  if (images.value.length > 0) {
    await copyDesignLink()
    return
  }
  busy.value = true
  try {
    const { content, omitted } = includeContent.value
      ? shareableContent(typeId.value, props.values[typeId.value])
      : { content: undefined, omitted: [] }
    const url = shareUrl(base(), await encodeShare({ style: shareableStyle(props.qrStyle), content }))
    if (url.length > MAX_SHARE_URL_LENGTH) {
      error.value =
        `That link would be ${url.length.toLocaleString()} characters, more than the ` +
        `${MAX_SHARE_URL_LENGTH.toLocaleString()} that travel reliably. Leave the content out, ` +
        'or save the design and share it from there.'
      return
    }
    const left = omitted.length ? ` ${list(omitted)} left out.` : ''
    await offer(url, content ? 'a link to this design and its content' : 'a link to this design')
    notice.value += left
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : 'The link could not be made.'
  } finally {
    busy.value = false
  }
}

async function copyDesignLink(): Promise<void> {
  const design = props.design
  if (!design || !design.unchanged) {
    error.value = design
      ? `A link cannot carry the ${list(images.value)}. Save your changes to “${design.name}”, ` +
        'then copy a link to it.'
      : `A link cannot carry the ${list(images.value)}. Save this as a design under Designs, ` +
        'then copy a link to it.'
    return
  }
  await offer(designUrl(base(), design.id), `a link to “${design.name}”`)
  notice.value += design.shared
    ? ' Anyone who can sign in can open it.'
    : ' It is private: share it under Designs so others can open it.'
}

// Opening a link ---------------------------------------------------------------

/**
 * Applies the link in the address bar, if there is one, then takes it out of the address bar.
 * The editor and its undo history hold the state from then on: leaving the fragment would make
 * a reload discard every later edit for the link's state, and keep content on screen in the URL.
 */
async function openFragment(): Promise<void> {
  const fragment = parseFragment(window.location.hash)
  if (fragment === null) {
    return
  }
  error.value = ''
  notice.value = ''
  link.value = ''
  try {
    if (fragment.kind === 'invalid') {
      error.value = fragment.message
    } else if (fragment.kind === 'design') {
      await openDesign(fragment.id)
    } else {
      await openShare(fragment.token)
    }
  } finally {
    const { pathname, search } = window.location
    window.history.replaceState(window.history.state, '', `${pathname}${search}`)
  }
}

async function openShare(token: string): Promise<void> {
  try {
    const payload = await decodeShare(token)
    applyStyle(props.qrStyle, payload.style)
    let opened = 'Opened a shared design.'
    if (payload.content) {
      const type = findQrType(payload.content.type)
      typeId.value = type.id
      props.values[type.id] = { ...initialValues(type), ...payload.content.values }
      const missing = secretFields(type.id)
      opened = 'Opened a shared design and its content.'
      if (missing.length) {
        opened += ` Links never carry the ${list(missing).toLowerCase()}; enter it yourself.`
      }
    }
    emit('applied')
    notice.value = opened
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : 'That share link could not be opened.'
  }
}

async function openDesign(id: number): Promise<void> {
  try {
    const design = await getDesign(id)
    applyStyle(props.qrStyle, design.style)
    emit('applied')
    notice.value = `Opened the design “${design.name}”.`
  } catch (cause) {
    error.value =
      cause instanceof ApiError && (cause.status === 404 || cause.status === 400)
        ? 'That design was not found or is not shared with you.'
        : `That design could not be opened: ${cause instanceof Error ? cause.message : 'unknown error'}`
  }
}

function onHashChange(): void {
  void openFragment()
}

onMounted(() => {
  void openFragment()
  window.addEventListener('hashchange', onHashChange)
})

onUnmounted(() => window.removeEventListener('hashchange', onHashChange))
</script>

<template>
  <div class="share-link">
    <label v-if="hasFields" class="checkbox">
      <input v-model="includeContent" type="checkbox" :disabled="images.length > 0" />
      Include content
    </label>
    <p class="field-help">
      <template v-if="images.length">
        A link cannot carry the {{ list(images) }}, so this copies a link to the saved design
        instead. Design links never carry content.
      </template>
      <template v-else>
        The design travels in the part of the link after #, which browsers never send to a server.
        <template v-if="hasFields && secrets.length">
          The {{ list(secrets).toLowerCase() }} is never included.
        </template>
        <template v-if="!hasFields">Short link content is not included.</template>
      </template>
    </p>

    <div class="button-row">
      <button type="button" :disabled="busy" @click="copyLink">
        {{ images.length ? 'Copy design link' : 'Copy share link' }}
      </button>
    </div>

    <input
      v-if="link"
      class="share-url"
      type="text"
      readonly
      :value="link"
      aria-label="Share link"
      @focus="($event.target as HTMLInputElement).select()"
    />
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <p v-else-if="notice" class="hint share-notice" aria-live="polite">{{ notice }}</p>
  </div>
</template>

<style scoped>
.share-link {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.share-link p {
  margin: 0;
}

.share-url {
  width: 100%;
  font-family: var(--mono);
  font-size: 12px;
}
</style>
