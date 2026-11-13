<script setup lang="ts">
import { ref } from 'vue'
import { readBackgroundImageFile, type QrBackgroundImage } from '../api/client'

const backgroundImage = defineModel<QrBackgroundImage | null>({ required: true })

const dragging = ref(false)
const error = ref('')
const fileInput = ref<HTMLInputElement | null>(null)

function messageOf(cause: unknown): string {
  return cause instanceof Error ? cause.message : 'The background image could not be read.'
}

async function accept(file: File | undefined): Promise<void> {
  if (!file) {
    return
  }
  error.value = ''
  try {
    backgroundImage.value = await readBackgroundImageFile(file)
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
  backgroundImage.value = null
  error.value = ''
}
</script>

<template>
  <div class="logo-panel background-panel">
    <input
      ref="fileInput"
      class="visually-hidden"
      type="file"
      accept="image/png,image/jpeg,image/gif"
      @change="onFileChange"
    />

    <template v-if="backgroundImage">
      <div class="logo-preview">
        <img :src="backgroundImage.dataUrl" alt="The selected background" />
        <div class="logo-actions">
          <p>Background added. It sits behind the code and is washed so the modules stay readable.</p>
          <div class="button-row">
            <button type="button" @click="chooseFile">Replace</button>
            <button type="button" class="danger" @click="remove">Remove</button>
          </div>
        </div>
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
      <p class="field-help">PNG, JPEG or GIF, up to 2 MB. Busy images are softened for contrast.</p>
    </div>

    <p v-if="error" class="error" role="alert">{{ error }}</p>
  </div>
</template>
