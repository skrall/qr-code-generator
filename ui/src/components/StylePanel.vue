<script setup lang="ts">
import type { ErrorCorrection, FinderShape, ModuleShape, QrStyle } from '../api/client'

const props = defineProps<{ style: QrStyle }>()

interface Preset {
  id: string
  label: string
  moduleShape: ModuleShape
  finderShape: FinderShape
  foreground: string
  background: string
  finderColor: string
}

const presets: readonly Preset[] = [
  {
    id: 'classic',
    label: 'Classic',
    moduleShape: 'SQUARE',
    finderShape: 'SQUARE',
    foreground: '#111827',
    background: '#ffffff',
    finderColor: '#111827',
  },
  {
    id: 'rounded',
    label: 'Rounded',
    moduleShape: 'ROUNDED',
    finderShape: 'ROUNDED',
    foreground: '#16203a',
    background: '#ffffff',
    finderColor: '#3158c4',
  },
  {
    id: 'dots',
    label: 'Dots',
    moduleShape: 'DOTS',
    finderShape: 'CIRCLE',
    foreground: '#111827',
    background: '#ffffff',
    finderColor: '#111827',
  },
  {
    id: 'midnight',
    label: 'Midnight',
    moduleShape: 'DOTS',
    finderShape: 'CIRCLE',
    foreground: '#e8eefc',
    background: '#0f172a',
    finderColor: '#8ab4ff',
  },
]

const moduleShapes: readonly { value: ModuleShape; label: string }[] = [
  { value: 'SQUARE', label: 'Squares' },
  { value: 'ROUNDED', label: 'Rounded' },
  { value: 'DOTS', label: 'Dots' },
]

const finderShapes: readonly { value: FinderShape; label: string }[] = [
  { value: 'SQUARE', label: 'Square' },
  { value: 'ROUNDED', label: 'Rounded' },
  { value: 'CIRCLE', label: 'Circle' },
]

const errorCorrections: readonly { value: ErrorCorrection; label: string }[] = [
  { value: 'LOW', label: 'Low — 7% recoverable, smallest' },
  { value: 'MEDIUM', label: 'Medium — 15% recoverable' },
  { value: 'QUARTILE', label: 'Quartile — 25% recoverable' },
  { value: 'HIGH', label: 'High — 30% recoverable, best with a logo' },
]

function apply(preset: Preset): void {
  props.style.moduleShape = preset.moduleShape
  props.style.finderShape = preset.finderShape
  props.style.foreground = preset.foreground
  props.style.background = preset.background
  props.style.finderColor = preset.finderColor
}

function isActive(preset: Preset): boolean {
  return (
    props.style.moduleShape === preset.moduleShape &&
    props.style.finderShape === preset.finderShape &&
    props.style.foreground === preset.foreground &&
    props.style.background === preset.background &&
    props.style.finderColor === preset.finderColor
  )
}
</script>

<template>
  <div class="style-panel">
    <div class="preset-row" role="group" aria-label="Style presets">
      <button
        v-for="preset in presets"
        :key="preset.id"
        type="button"
        class="preset"
        :class="{ active: isActive(preset) }"
        :aria-pressed="isActive(preset)"
        @click="apply(preset)"
      >
        <span class="preset-swatch" :style="{ background: preset.foreground, borderColor: preset.background }"></span>
        {{ preset.label }}
      </button>
    </div>

    <div class="control">
      <span class="control-label">Modules</span>
      <div class="segmented" role="group" aria-label="Module shape">
        <button
          v-for="shape in moduleShapes"
          :key="shape.value"
          type="button"
          :class="{ active: style.moduleShape === shape.value }"
          :aria-pressed="style.moduleShape === shape.value"
          @click="style.moduleShape = shape.value"
        >
          {{ shape.label }}
        </button>
      </div>
    </div>

    <div class="control">
      <span class="control-label">Corner patterns</span>
      <div class="segmented" role="group" aria-label="Corner pattern shape">
        <button
          v-for="shape in finderShapes"
          :key="shape.value"
          type="button"
          :class="{ active: style.finderShape === shape.value }"
          :aria-pressed="style.finderShape === shape.value"
          @click="style.finderShape = shape.value"
        >
          {{ shape.label }}
        </button>
      </div>
    </div>

    <div class="control-grid">
      <label class="control">
        <span class="control-label">Foreground</span>
        <span class="color-row">
          <input v-model="style.foreground" type="color" aria-label="Foreground colour" />
          <code>{{ style.foreground }}</code>
        </span>
      </label>

      <label class="control">
        <span class="control-label">Background</span>
        <span class="color-row">
          <input v-model="style.background" type="color" aria-label="Background colour" />
          <code>{{ style.background }}</code>
        </span>
      </label>

      <label class="control">
        <span class="control-label">Corners</span>
        <span class="color-row">
          <input v-model="style.finderColor" type="color" aria-label="Corner pattern colour" />
          <code>{{ style.finderColor }}</code>
        </span>
      </label>

      <label class="control">
        <span class="control-label">Error correction</span>
        <select v-model="style.errorCorrection">
          <option v-for="option in errorCorrections" :key="option.value" :value="option.value">
            {{ option.label }}
          </option>
        </select>
      </label>
    </div>

    <div class="control-grid">
      <label class="control">
        <span class="control-label">
          Size
          <output>{{ style.size }} px</output>
        </span>
        <input v-model.number="style.size" type="range" min="128" max="1024" step="16" />
      </label>

      <label class="control">
        <span class="control-label">
          Quiet zone
          <output>{{ style.margin }} modules</output>
        </span>
        <input v-model.number="style.margin" type="range" min="0" max="12" step="1" />
      </label>
    </div>
  </div>
</template>
