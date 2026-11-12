<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { deleteLink, listLinks, type ShortLink } from '../api/client'
import { copyText } from '../clipboard'
import { formatDate } from '../format'

/** The stored links, with a debounced search and a two-step delete. */
const PAGE_SIZE = 25

const query = ref('')
const page = ref(0)
const links = ref<ShortLink[]>([])
const total = ref(0)
const state = ref<'idle' | 'loading' | 'ready' | 'error'>('idle')
const error = ref('')
const pendingDelete = ref<string | null>(null)
const deleting = ref<string | null>(null)
const copiedCode = ref<string | null>(null)

let controller: AbortController | null = null
let timer: ReturnType<typeof setTimeout> | undefined

const pageCount = computed(() => Math.max(1, Math.ceil(total.value / PAGE_SIZE)))

async function load(): Promise<void> {
  controller?.abort()
  const current = new AbortController()
  controller = current
  state.value = 'loading'
  error.value = ''

  try {
    const result = await listLinks(query.value, page.value, PAGE_SIZE, current.signal)
    if (current.signal.aborted) {
      return
    }
    links.value = result.items
    total.value = result.total
    state.value = 'ready'
  } catch (cause) {
    if (current.signal.aborted) {
      return
    }
    error.value = cause instanceof Error ? cause.message : 'The links could not be loaded.'
    state.value = 'error'
  }
}

function schedule(): void {
  clearTimeout(timer)
  timer = setTimeout(() => void load(), 250)
}

watch(query, () => {
  page.value = 0
  schedule()
})

function go(delta: number): void {
  const next = page.value + delta
  if (next < 0 || next >= pageCount.value) {
    return
  }
  page.value = next
  void load()
}

async function remove(code: string): Promise<void> {
  // Two clicks rather than a browser confirm: the second click is the confirmation, and the
  // button label says so.
  if (pendingDelete.value !== code) {
    pendingDelete.value = code
    return
  }

  deleting.value = code
  error.value = ''
  try {
    await deleteLink(code)
    pendingDelete.value = null
    await load()
  } catch (cause) {
    error.value = cause instanceof Error ? cause.message : 'The link could not be deleted.'
  } finally {
    deleting.value = null
  }
}

async function copy(link: ShortLink): Promise<void> {
  if (await copyText(link.shortUrl)) {
    copiedCode.value = link.code
  }
}

onMounted(() => void load())
onUnmounted(() => {
  clearTimeout(timer)
  controller?.abort()
})
</script>

<template>
  <div class="card links-card">
    <div class="links-head">
      <div>
        <h2>Short links</h2>
        <p class="hint">
          Destinations stored by the generator. A deleted code answers 410 Gone, not 404.
        </p>
      </div>
      <label class="search-field" for="links-search">
        <span class="visually-hidden">Search destinations</span>
        <input
          id="links-search"
          v-model="query"
          type="search"
          placeholder="Search destination or code"
          autocomplete="off"
        />
      </label>
    </div>

    <p v-if="state === 'error'" class="error" role="alert">{{ error }}</p>

    <p v-if="state === 'loading' && links.length === 0" class="hint">Loading…</p>
    <p v-else-if="state === 'ready' && links.length === 0" class="hint">
      {{ query ? 'No links match that search.' : 'No links yet. Create one from the Short link type.' }}
    </p>

    <div v-else-if="links.length" class="links-table-wrap">
      <table class="links-table">
        <thead>
          <tr>
            <th>Short link</th>
            <th>Destination</th>
            <th>Created</th>
            <th>Expires</th>
            <th class="numeric">Hits</th>
            <th>Last hit</th>
            <th><span class="visually-hidden">Actions</span></th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="link in links" :key="link.code">
            <td class="cell-short">
              <code>{{ link.shortUrl }}</code>
              <button type="button" class="link-button" @click="copy(link)">
                {{ copiedCode === link.code ? 'Copied' : 'Copy' }}
              </button>
            </td>
            <td class="cell-target" :title="link.targetUrl">
              <span>{{ link.targetUrl }}</span>
            </td>
            <td>{{ formatDate(link.createdAt) }}</td>
            <td>{{ link.expiresAt ? formatDate(link.expiresAt) : 'Never' }}</td>
            <td class="numeric">{{ link.hitCount }}</td>
            <td>{{ formatDate(link.lastHitAt) }}</td>
            <td class="cell-actions">
              <button
                type="button"
                class="danger"
                :disabled="deleting === link.code"
                @click="remove(link.code)"
              >
                {{ pendingDelete === link.code ? 'Confirm' : 'Delete' }}
              </button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <div class="pager">
      <button type="button" :disabled="page === 0" @click="go(-1)">Previous</button>
      <span>Page {{ page + 1 }} of {{ pageCount }} · {{ total }} {{ total === 1 ? 'link' : 'links' }}</span>
      <button type="button" :disabled="page + 1 >= pageCount" @click="go(1)">Next</button>
    </div>
  </div>
</template>
