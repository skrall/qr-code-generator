<script setup lang="ts">
import { computed } from 'vue'

/**
 * What a scan gets once a short link has expired or been deleted: a fallback URL to redirect to,
 * or a message for the "no longer active" page. Shared by the create form and the edit dialog.
 *
 * <p>Folded away by default, since most links never need it, and opened when either option is
 * already set so an edit does not hide what is stored.
 */
const props = defineProps<{
  /** Prefix for the field ids, so the form and the dialog can both be on the page. */
  idPrefix: string
}>()

const goneUrl = defineModel<string>('goneUrl', { required: true })
const goneMessage = defineModel<string>('goneMessage', { required: true })

/** Matches the server's limit; the textarea stops there and the counter says so. */
const MAX_MESSAGE = 280

const startOpen = goneUrl.value.trim() !== '' || goneMessage.value.trim() !== ''
const remaining = computed(() => MAX_MESSAGE - goneMessage.value.length)
const urlId = computed(() => `${props.idPrefix}-gone-url`)
const messageId = computed(() => `${props.idPrefix}-gone-message`)
</script>

<template>
  <details class="gone-options" :open="startOpen">
    <summary>If the link expires or is removed</summary>
    <p class="field-help">
      Applies to expired and deleted links alike. Without either option a scan shows a short
      "no longer active" page.
    </p>

    <div class="field">
      <label class="field-label" :for="urlId">Send scans to</label>
      <input
        :id="urlId"
        v-model="goneUrl"
        type="text"
        inputmode="url"
        spellcheck="false"
        placeholder="example.com/whats-new"
        autocomplete="url"
      />
      <span class="field-help">Optional. Redirects instead of showing the page; http and https only.</span>
    </div>

    <div class="field">
      <label class="field-label" :for="messageId">Message on the page</label>
      <textarea
        :id="messageId"
        v-model="goneMessage"
        rows="3"
        :maxlength="MAX_MESSAGE"
        placeholder="This offer ended on 31 March. Thanks for scanning!"
      ></textarea>
      <span v-if="goneUrl.trim() !== ''" class="field-help">
        Not shown while scans are sent to the address above.
      </span>
      <span v-else class="field-help">
        Optional, plain text. {{ remaining }} {{ remaining === 1 ? 'character' : 'characters' }} left.
      </span>
    </div>
  </details>
</template>
