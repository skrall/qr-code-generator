<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import {
  apiStyle,
  createDesign,
  deleteDesign,
  getDesign,
  listDesigns,
  updateDesign,
  type DesignSummary,
  type QrStyle,
} from '../api/client'
import { applyStyle, designFile, designFileName, parseDesignFile } from '../qr/style'

/**
 * Saves the current style as a named design, loads saved ones, and moves designs in and out as
 * JSON files.
 *
 * <p>The prop is `qrStyle`, not `style`: Vue would clone a reactive prop named `style` into a
 * plain object, and loading a design would then change a copy nobody renders.
 */
const props = defineProps<{ qrStyle: QrStyle }>()

const designs = ref<DesignSummary[]>([])
const selectedId = ref<number | ''>('')
/** The design the editor holds, if it came from the list; null after an import or a delete. */
const loaded = ref<DesignSummary | null>(null)
/** The loaded style as saved, to tell whether the editor has changed it since. */
const snapshot = ref('')
const name = ref('')
const shared = ref(false)
const busy = ref(false)
const error = ref('')
const notice = ref('')
const confirmingDelete = ref(false)
const fileInput = ref<HTMLInputElement | null>(null)

const mine = computed(() => designs.value.filter((design) => design.mine))
const others = computed(() => designs.value.filter((design) => !design.mine))
const trimmedName = computed(() => name.value.trim())
const canSave = computed(() => trimmedName.value.length > 0 && !busy.value)

const currentStyle = (): string => JSON.stringify(apiStyle(props.qrStyle))

const dirty = computed(
  () =>
    loaded.value !== null &&
    (snapshot.value !== currentStyle() ||
      trimmedName.value !== loaded.value.name ||
      shared.value !== loaded.value.shared),
)

// A pending delete is for the design on screen; changing anything else cancels it.
watch([selectedId, loaded], () => {
  confirmingDelete.value = false
})

function messageOf(cause: unknown): string {
  return cause instanceof Error ? cause.message : 'Something went wrong.'
}

async function run(action: () => Promise<void>): Promise<void> {
  busy.value = true
  error.value = ''
  notice.value = ''
  try {
    await action()
  } catch (cause) {
    error.value = messageOf(cause)
  } finally {
    busy.value = false
  }
}

async function refresh(): Promise<void> {
  designs.value = await listDesigns()
}

function remember(design: DesignSummary): void {
  loaded.value = design
  selectedId.value = design.id
  name.value = design.name
  shared.value = design.shared
  snapshot.value = currentStyle()
}

async function load(): Promise<void> {
  const id = selectedId.value
  if (id === '') {
    return
  }
  await run(async () => {
    const design = await getDesign(id)
    applyStyle(props.qrStyle, design.style)
    remember(design)
    notice.value = `Loaded “${design.name}”.`
  })
}

async function saveNew(): Promise<void> {
  await run(async () => {
    const design = await createDesign({
      name: trimmedName.value,
      shared: shared.value,
      style: props.qrStyle,
    })
    await refresh()
    remember(design)
    notice.value = `Saved “${design.name}”.`
  })
}

async function saveChanges(): Promise<void> {
  const current = loaded.value
  if (!current) {
    return
  }
  await run(async () => {
    // Only send the style when it changed: a rename or share should not resend the images.
    const styleChanged = snapshot.value !== currentStyle()
    const design = await updateDesign(current.id, {
      name: trimmedName.value,
      shared: shared.value,
      style: styleChanged ? props.qrStyle : undefined,
    })
    await refresh()
    remember(design)
    notice.value = `Saved changes to “${design.name}”.`
  })
}

async function remove(): Promise<void> {
  const current = loaded.value
  if (!current) {
    return
  }
  if (!confirmingDelete.value) {
    confirmingDelete.value = true
    return
  }
  await run(async () => {
    await deleteDesign(current.id)
    await refresh()
    loaded.value = null
    selectedId.value = ''
    notice.value = `Deleted “${current.name}”. The editor still holds its style.`
  })
}

function exportJson(): void {
  const file = designFile(trimmedName.value || loaded.value?.name || '', apiStyle(props.qrStyle))
  const url = URL.createObjectURL(
    new Blob([JSON.stringify(file, null, 2)], { type: 'application/json' }),
  )
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = designFileName(file.name)
  anchor.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

function chooseFile(): void {
  fileInput.value?.click()
}

async function importJson(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) {
    return
  }
  await run(async () => {
    const design = parseDesignFile(await file.text())
    applyStyle(props.qrStyle, design.style)
    loaded.value = null
    selectedId.value = ''
    name.value = design.name
    shared.value = false
    notice.value = 'Imported. Save it to keep it here.'
  })
}

onMounted(() => {
  void run(refresh)
})
</script>

<template>
  <div class="designs-panel">
    <div class="design-picker">
      <label class="visually-hidden" for="design-select">Saved designs</label>
      <select id="design-select" v-model="selectedId" :disabled="busy">
        <option value="">
          {{ designs.length ? 'Choose a saved design…' : 'No saved designs yet' }}
        </option>
        <optgroup v-if="mine.length" label="Yours">
          <option v-for="design in mine" :key="design.id" :value="design.id">
            {{ design.name }}{{ design.shared ? ' (shared)' : '' }}
          </option>
        </optgroup>
        <optgroup v-if="others.length" label="Shared with everyone">
          <option v-for="design in others" :key="design.id" :value="design.id">
            {{ design.name }}{{ design.owner ? ` — ${design.owner}` : '' }}
          </option>
        </optgroup>
      </select>
      <button type="button" :disabled="selectedId === '' || busy" @click="load">Load</button>
    </div>

    <p v-if="loaded" class="field-help design-status">
      Editing “{{ loaded.name }}”<template v-if="dirty"> · unsaved changes</template>
      <template v-if="!loaded.editable">
        · shared by {{ loaded.owner ?? 'someone else' }}; save a copy to keep your changes
      </template>
    </p>

    <div class="field">
      <label class="field-label" for="design-name">Name</label>
      <input
        id="design-name"
        v-model="name"
        type="text"
        maxlength="100"
        placeholder="e.g. Brand blue"
        autocomplete="off"
      />
    </div>

    <label class="checkbox">
      <input v-model="shared" type="checkbox" />
      Share with everyone who can sign in
    </label>

    <div class="button-row">
      <button
        v-if="loaded?.editable"
        type="button"
        class="primary"
        :disabled="!canSave || !dirty"
        @click="saveChanges"
      >
        Save changes
      </button>
      <button
        type="button"
        :class="{ primary: !loaded?.editable }"
        :disabled="!canSave"
        @click="saveNew"
      >
        Save as new
      </button>
      <button
        v-if="loaded?.editable"
        type="button"
        class="danger"
        :disabled="busy"
        @click="remove"
      >
        {{ confirmingDelete ? 'Confirm delete' : 'Delete' }}
      </button>
    </div>

    <div class="button-row">
      <button type="button" :disabled="busy" @click="exportJson">Export JSON</button>
      <button type="button" :disabled="busy" @click="chooseFile">Import JSON</button>
      <input
        ref="fileInput"
        class="visually-hidden"
        type="file"
        accept="application/json,.json"
        aria-label="Import a design file"
        @change="importJson"
      />
    </div>

    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <p v-else-if="notice" class="hint design-notice" aria-live="polite">{{ notice }}</p>
  </div>
</template>
