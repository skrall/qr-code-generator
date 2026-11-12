<script setup lang="ts">
import { ref } from 'vue'
import { readLogoFile, type QrLogo } from '../api/client'

const logo = defineModel<QrLogo | null>({ required: true })

const dragging = ref(false)
const error = ref('')
const fileInput = ref<HTMLInputElement | null>(null)

function messageOf(cause: unknown): string {
  return cause instanceof Error ? cause.message : 'The logo could not be read.'
}

async function accept(file: File | undefined): Promise<void> {
  if (!file) {
    return
  }
  error.value = ''
  try {
    logo.value = await readLogoFile(file)
  } catch (cause) {
    error.value = messageOf(cause)
  }
}

function chooseFile(): void {
  fileInput.value?.click()
}

function onFileChange(event: Event): void {
  const target = event.target as HTMLInputElement
  void accept(target.files?.[0])
  // Allow picking the same file again after removing it.
  target.value = ''
}

function onDrop(event: DragEvent): void {
  dragging.value = false
  void accept(event.dataTransfer?.files?.[0])
}

function remove(): void {
  logo.value = null
  error.value = ''
}
</script>

<template>
  <div class="logo-panel">
    <input
      ref="fileInput"
      class="visually-hidden"
      type="file"
      accept="image/png,image/jpeg,image/gif"
      @change="onFileChange"
    />

    <template v-if="logo">
      <div class="logo-preview">
        <img :src="logo.dataUrl" alt="The selected logo" />
        <div class="logo-actions">
          <p>Logo added. It sits on a light plate in the middle of the code.</p>
          <div class="button-row">
            <button type="button" @click="chooseFile">Replace</button>
            <button type="button" class="danger" @click="remove">Remove</button>
          </div>
        </div>
      </div>

      <div class="control-grid">
        <label class="control">
          <span class="control-label">
            Logo size
            <output>{{ logo.sizePercent }}%</output>
          </span>
          <input v-model.number="logo.sizePercent" type="range" min="5" max="35" step="1" />
        </label>

        <label class="control">
          <span class="control-label">
            Plate padding
            <output>{{ logo.paddingPercent }}%</output>
          </span>
          <input v-model.number="logo.paddingPercent" type="range" min="0" max="30" step="1" />
        </label>

        <label class="control">
          <span class="control-label">
            Plate roundness
            <output>{{ logo.radiusPercent }}%</output>
          </span>
          <input v-model.number="logo.radiusPercent" type="range" min="0" max="50" step="1" />
        </label>

        <label class="control">
          <span class="control-label">Plate colour</span>
          <span class="color-row">
            <input v-model="logo.background" type="color" aria-label="Logo plate colour" />
            <code>{{ logo.background }}</code>
          </span>
        </label>
      </div>
    </template>

    <div
      v-else
      class="dropzone"
      :class="{ dragging }"
      @dragover.prevent="dragging = true"
      @dragleave.prevent="dragging = false"
      @drop.prevent="onDrop"
    >
      <p>Drop an image here, or</p>
      <button type="button" @click="chooseFile">Choose a file</button>
      <p class="field-help">PNG, JPEG or GIF, up to 2 MB.</p>
    </div>

    <p v-if="error" class="error" role="alert">{{ error }}</p>
  </div>
</template>
