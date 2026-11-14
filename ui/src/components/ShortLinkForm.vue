<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { createLink, type ShortLink } from '../api/client'
import { copyText } from '../clipboard'
import { formatDate } from '../format'
import { normalizeUrl } from '../qr/types'
import GoneOptionsFields from './GoneOptionsFields.vue'

/**
 * Creates a stored short link and hands the resulting URL to the parent.
 *
 * <p>The preview gets a draft of the destination while the user types, so it is not blank until
 * the link exists. Creating is still an explicit action: only the draft is shown before then,
 * and the parent disables downloads until the real short URL is available.
 */
const props = withDefaults(
  defineProps<{
    /** False for a VIEWER: the server refuses creation, so the form says so up front. */
    operator?: boolean
  }>(),
  { operator: true },
)

const emit = defineEmits<{
  created: [link: ShortLink]
  cleared: []
  draft: [url: string]
}>()

type Expiry = 'never' | '1d' | '7d' | '30d' | 'custom'

const destination = ref('')
const expiry = ref<Expiry>('never')
const customExpiry = ref('')
const goneUrl = ref('')
const goneMessage = ref('')
const busy = ref(false)
const error = ref('')
const created = ref<ShortLink | null>(null)
const copied = ref(false)

const customIsFuture = computed(() => {
  if (expiry.value !== 'custom') {
    return true
  }
  return customExpiry.value.length > 0 && new Date(customExpiry.value).getTime() > Date.now()
})

const canCreate = computed(
  () =>
    props.operator && destination.value.trim().length > 0 && customIsFuture.value && !busy.value,
)

const DAY = 24 * 60 * 60 * 1000

function expiresAt(): string | null {
  switch (expiry.value) {
    case '1d':
      return new Date(Date.now() + DAY).toISOString()
    case '7d':
      return new Date(Date.now() + 7 * DAY).toISOString()
    case '30d':
      return new Date(Date.now() + 30 * DAY).toISOString()
    case 'custom':
      return customExpiry.value ? new Date(customExpiry.value).toISOString() : null
    default:
      return null
  }
}

async function create(): Promise<void> {
  if (!canCreate.value) {
    return
  }
  busy.value = true
  error.value = ''
  copied.value = false

  try {
    const link = await createLink({
      url: normalizeUrl(destination.value),
      expiresAt: expiresAt(),
      goneUrl: normalizeUrl(goneUrl.value),
      goneMessage: goneMessage.value,
    })
    created.value = link
    emit('created', link)
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : 'The link could not be created.'
  } finally {
    busy.value = false
  }
}

async function copy(): Promise<void> {
  if (created.value) {
    copied.value = await copyText(created.value.shortUrl)
  }
}

// The destination is the draft the parent renders before a link exists. A change after creation
// makes the shown link stale, so drop it rather than let the preview describe a destination the
// form no longer names.
watch(destination, () => {
  if (created.value) {
    created.value = null
    copied.value = false
    emit('cleared')
  }
  emit('draft', normalizeUrl(destination.value))
})

// Expiry and the gone options do not change the draft payload, but they do invalidate a link
// that was created with the previous settings.
watch([expiry, customExpiry, goneUrl, goneMessage], () => {
  if (!created.value) {
    return
  }
  created.value = null
  copied.value = false
  emit('cleared')
})
</script>

<template>
  <form class="short-link-form" @submit.prevent="create">
    <div class="field">
      <label class="field-label" for="shortlink-destination">
        Destination
        <span class="required" aria-hidden="true">*</span>
      </label>
      <input
        id="shortlink-destination"
        v-model="destination"
        type="text"
        inputmode="url"
        spellcheck="false"
        placeholder="example.com/page"
        autocomplete="url"
      />
      <span class="field-help">Stored on this server. Only http and https are accepted.</span>
    </div>

    <div class="field">
      <label class="field-label" for="shortlink-expiry">Expires</label>
      <select id="shortlink-expiry" v-model="expiry">
        <option value="never">Never</option>
        <option value="1d">In 24 hours</option>
        <option value="7d">In 7 days</option>
        <option value="30d">In 30 days</option>
        <option value="custom">Pick a date…</option>
      </select>
    </div>

    <div v-if="expiry === 'custom'" class="field">
      <label class="field-label" for="shortlink-custom-expiry">Expires at</label>
      <input id="shortlink-custom-expiry" v-model="customExpiry" type="datetime-local" />
      <span v-if="customExpiry && !customIsFuture" class="field-help error-text">
        That time is already past.
      </span>
    </div>

    <GoneOptionsFields
      v-model:gone-url="goneUrl"
      v-model:gone-message="goneMessage"
      id-prefix="shortlink"
    />

    <div class="button-row">
      <button type="submit" class="primary" :disabled="!canCreate">
        {{ busy ? 'Creating…' : 'Create link' }}
      </button>
    </div>

    <p v-if="!operator" class="field-help">
      Creating short links needs the operator role. The preview still shows a draft of the
      destination.
    </p>
    <p v-else-if="!created" class="field-help">
      The preview shows the destination as a draft. Create the link to encode the short URL;
      pressing Enter in the destination field does the same thing.
    </p>

    <p v-if="error" class="error" role="alert">{{ error }}</p>

    <div v-if="created" class="short-link-result">
      <p class="result-label">Short link</p>
      <div class="copy-row">
        <code>{{ created.shortUrl }}</code>
        <button type="button" @click="copy">{{ copied ? 'Copied' : 'Copy' }}</button>
      </div>
      <dl class="result-meta">
        <div>
          <dt>Points to</dt>
          <dd>{{ created.targetUrl }}</dd>
        </div>
        <div>
          <dt>Expires</dt>
          <dd>{{ created.expiresAt ? formatDate(created.expiresAt) : 'Never' }}</dd>
        </div>
      </dl>
      <p class="field-help">The code on the right encodes this short link.</p>
    </div>
  </form>
</template>
