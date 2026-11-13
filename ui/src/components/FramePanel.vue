<script setup lang="ts">
import type { QrFrame } from '../api/client'

/** Null when there is no frame; the server then renders the bare code. */
const frame = defineModel<QrFrame | null>({ required: true })

function defaultFrame(): QrFrame {
  return {
    title: 'Scan me',
    caption: '',
    background: '#ffffff',
    textColor: '#111827',
    borderColor: '#111827',
    titleSizePercent: 9,
    captionSizePercent: 5,
    borderPercent: 2,
    radiusPercent: 4,
    paddingPercent: 6,
    marginPercent: 0,
    bleedPercent: 0,
  }
}

function setEnabled(enabled: boolean): void {
  frame.value = enabled ? defaultFrame() : null
}

const lengths = [
  { key: 'borderPercent', label: 'Border', max: 10 },
  { key: 'radiusPercent', label: 'Corner radius', max: 25 },
  { key: 'paddingPercent', label: 'Padding', max: 30 },
  { key: 'marginPercent', label: 'Margin', max: 20 },
  { key: 'bleedPercent', label: 'Bleed', max: 20 },
] as const
</script>

<template>
  <div class="frame-panel">
    <label class="checkbox">
      <input
        type="checkbox"
        :checked="frame !== null"
        aria-label="Frame"
        @change="setEnabled(($event.target as HTMLInputElement).checked)"
      />
      <span>Put the code on a card</span>
    </label>

    <template v-if="frame">
      <label class="control">
        <span class="control-label">Title</span>
        <input v-model="frame.title" type="text" maxlength="60" placeholder="Above the code" />
      </label>

      <label class="control">
        <span class="control-label">Caption</span>
        <input v-model="frame.caption" type="text" maxlength="120" placeholder="Below the code" />
      </label>

      <div class="control-grid">
        <label class="control">
          <span class="control-label">Card</span>
          <span class="color-row">
            <input v-model="frame.background" type="color" aria-label="Card colour" />
            <code>{{ frame.background }}</code>
          </span>
        </label>

        <label class="control">
          <span class="control-label">Text</span>
          <span class="color-row">
            <input v-model="frame.textColor" type="color" aria-label="Text colour" />
            <code>{{ frame.textColor }}</code>
          </span>
        </label>

        <label class="control">
          <span class="control-label">Border colour</span>
          <span class="color-row">
            <input v-model="frame.borderColor" type="color" aria-label="Border colour" />
            <code>{{ frame.borderColor }}</code>
          </span>
        </label>

        <label class="control">
          <span class="control-label">
            Title size
            <output>{{ frame.titleSizePercent }}%</output>
          </span>
          <input v-model.number="frame.titleSizePercent" type="range" min="3" max="20" step="1" />
        </label>

        <label class="control">
          <span class="control-label">
            Caption size
            <output>{{ frame.captionSizePercent }}%</output>
          </span>
          <input v-model.number="frame.captionSizePercent" type="range" min="2" max="15" step="1" />
        </label>

        <label v-for="length in lengths" :key="length.key" class="control">
          <span class="control-label">
            {{ length.label }}
            <output>{{ frame[length.key] }}%</output>
          </span>
          <input
            v-model.number="frame[length.key]"
            type="range"
            min="0"
            :max="length.max"
            step="1"
            :aria-label="length.label"
          />
        </label>
      </div>

      <p class="field-help">
        Sizes are a percentage of the code's width, and the card grows around the code. Bleed is
        extra card colour for a printer to trim off; keep the border clear of it. Text is set in
        Inter, which covers Latin, Greek and Cyrillic.
      </p>
    </template>
  </div>
</template>
