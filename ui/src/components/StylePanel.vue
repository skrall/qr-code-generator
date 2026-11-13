<script setup lang="ts">
import type {
  ErrorCorrection,
  FinderShape,
  GradientType,
  ModuleShape,
  QrStyle,
} from '../api/client'
import BackgroundImagePanel from './BackgroundImagePanel.vue'
import FinderArtworkPanel from './FinderArtworkPanel.vue'

/**
 * The prop is deliberately not called `style`.
 *
 * <p>Vue treats a vnode prop named `style` as a style binding: `createBaseVNode` clones a
 * reactive object under that key into a plain one. The child would then mutate a detached copy,
 * so nothing would re-render and no error would be raised. `qrStyle` passes the object through
 * untouched.
 */
const props = defineProps<{ qrStyle: QrStyle }>()

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
  {
    id: 'stars',
    label: 'Stars',
    moduleShape: 'STAR',
    finderShape: 'DIAMOND',
    foreground: '#1f2937',
    background: '#ffffff',
    finderColor: '#7c3aed',
  },
  {
    id: 'liquid',
    label: 'Liquid',
    moduleShape: 'LIQUID',
    finderShape: 'CIRCLE',
    foreground: '#0f172a',
    background: '#ffffff',
    finderColor: '#0f766e',
  },
]

const moduleShapes: readonly { value: ModuleShape; label: string }[] = [
  { value: 'SQUARE', label: 'Squares' },
  { value: 'ROUNDED', label: 'Rounded' },
  { value: 'DOTS', label: 'Dots' },
  { value: 'STAR', label: 'Stars' },
  { value: 'LIQUID', label: 'Liquid' },
]

const finderShapes: readonly { value: FinderShape; label: string }[] = [
  { value: 'SQUARE', label: 'Square' },
  { value: 'ROUNDED', label: 'Rounded' },
  { value: 'CIRCLE', label: 'Circle' },
  { value: 'LEAF', label: 'Leaf' },
  { value: 'DIAMOND', label: 'Diamond' },
  { value: 'CUSTOM', label: 'Custom' },
]

const gradientTypes: readonly { value: GradientType; label: string }[] = [
  { value: 'LINEAR', label: 'Linear' },
  { value: 'RADIAL', label: 'Radial' },
]

const errorCorrections: readonly { value: ErrorCorrection; label: string }[] = [
  { value: 'LOW', label: 'Low — 7% recoverable, smallest' },
  { value: 'MEDIUM', label: 'Medium — 15% recoverable' },
  { value: 'QUARTILE', label: 'Quartile — 25% recoverable' },
  { value: 'HIGH', label: 'High — 30% recoverable, best with a logo' },
]

function apply(preset: Preset): void {
  props.qrStyle.moduleShape = preset.moduleShape
  props.qrStyle.finderShape = preset.finderShape
  props.qrStyle.foreground = preset.foreground
  props.qrStyle.background = preset.background
  props.qrStyle.finderColor = preset.finderColor
  // A preset is a whole look; a gradient left on would hide its foreground colour.
  props.qrStyle.gradient = null
}

/** Starts from the current colours, so switching it on changes as little as possible. */
function setGradient(enabled: boolean): void {
  props.qrStyle.gradient = enabled
    ? {
        type: 'LINEAR',
        start: props.qrStyle.foreground,
        end: props.qrStyle.finderColor,
        angle: 135,
        finders: false,
      }
    : null
}

function isActive(preset: Preset): boolean {
  return (
    props.qrStyle.moduleShape === preset.moduleShape &&
    props.qrStyle.finderShape === preset.finderShape &&
    props.qrStyle.foreground === preset.foreground &&
    props.qrStyle.background === preset.background &&
    props.qrStyle.finderColor === preset.finderColor &&
    props.qrStyle.gradient === null
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
          :class="{ active: qrStyle.moduleShape === shape.value }"
          :aria-pressed="qrStyle.moduleShape === shape.value"
          @click="qrStyle.moduleShape = shape.value"
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
          :class="{ active: qrStyle.finderShape === shape.value }"
          :aria-pressed="qrStyle.finderShape === shape.value"
          @click="qrStyle.finderShape = shape.value"
        >
          {{ shape.label }}
        </button>
      </div>
    </div>

    <div v-if="qrStyle.finderShape === 'CUSTOM'" class="control">
      <span class="control-label">Corner artwork</span>
      <FinderArtworkPanel v-model="qrStyle.finderArtwork" />
      <p class="field-help">
        The artwork fills the corner rings; the light gap stays clear so the code remains
        scannable.
      </p>
    </div>

    <div class="control-grid">
      <label v-if="!qrStyle.gradient" class="control">
        <span class="control-label">Foreground</span>
        <span class="color-row">
          <input v-model="qrStyle.foreground" type="color" aria-label="Foreground colour" />
          <code>{{ qrStyle.foreground }}</code>
        </span>
      </label>

      <label class="control">
        <span class="control-label">Background</span>
        <span class="color-row">
          <input v-model="qrStyle.background" type="color" aria-label="Background colour" />
          <code>{{ qrStyle.background }}</code>
        </span>
      </label>

      <label class="control">
        <span class="control-label">Corners</span>
        <span class="color-row">
          <input v-model="qrStyle.finderColor" type="color" aria-label="Corner pattern colour" />
          <code>{{ qrStyle.finderColor }}</code>
        </span>
      </label>

      <label class="control">
        <span class="control-label">Error correction</span>
        <select v-model="qrStyle.errorCorrection">
          <option v-for="option in errorCorrections" :key="option.value" :value="option.value">
            {{ option.label }}
          </option>
        </select>
      </label>
    </div>

    <div class="control gradient-panel">
      <label class="checkbox">
        <input
          type="checkbox"
          :checked="qrStyle.gradient !== null"
          aria-label="Gradient"
          @change="setGradient(($event.target as HTMLInputElement).checked)"
        />
        <span>Gradient</span>
      </label>

      <template v-if="qrStyle.gradient">
        <div class="segmented" role="group" aria-label="Gradient type">
          <button
            v-for="type in gradientTypes"
            :key="type.value"
            type="button"
            :class="{ active: qrStyle.gradient.type === type.value }"
            :aria-pressed="qrStyle.gradient.type === type.value"
            @click="qrStyle.gradient.type = type.value"
          >
            {{ type.label }}
          </button>
        </div>

        <div class="control-grid">
          <label class="control">
            <span class="control-label">{{ qrStyle.gradient.type === 'RADIAL' ? 'Centre' : 'Start' }}</span>
            <span class="color-row">
              <input v-model="qrStyle.gradient.start" type="color" aria-label="Gradient start colour" />
              <code>{{ qrStyle.gradient.start }}</code>
            </span>
          </label>

          <label class="control">
            <span class="control-label">{{ qrStyle.gradient.type === 'RADIAL' ? 'Edge' : 'End' }}</span>
            <span class="color-row">
              <input v-model="qrStyle.gradient.end" type="color" aria-label="Gradient end colour" />
              <code>{{ qrStyle.gradient.end }}</code>
            </span>
          </label>
        </div>

        <label v-if="qrStyle.gradient.type === 'LINEAR'" class="control">
          <span class="control-label">
            Angle
            <output>{{ qrStyle.gradient.angle }}°</output>
          </span>
          <input v-model.number="qrStyle.gradient.angle" type="range" min="0" max="360" step="15" />
        </label>

        <label class="checkbox">
          <input v-model="qrStyle.gradient.finders" type="checkbox" />
          <span>Corner patterns follow the gradient</span>
        </label>

        <p class="field-help">
          Both colours must stand out from the background, and on the same side of it: a gradient
          that fades into the background would erase part of the code.
        </p>
      </template>
    </div>

    <div class="control">
      <span class="control-label">Background image</span>
      <BackgroundImagePanel v-model="qrStyle.backgroundImage" />
      <p class="field-help">
        The image sits behind the code; it is washed so the modules stay readable.
      </p>
    </div>

    <div class="control-grid">
      <label class="control">
        <span class="control-label">
          Size
          <output>{{ qrStyle.size }} px</output>
        </span>
        <input v-model.number="qrStyle.size" type="range" min="128" max="1024" step="16" />
      </label>

      <label class="control">
        <span class="control-label">
          Quiet zone
          <output>{{ qrStyle.margin }} modules</output>
        </span>
        <input v-model.number="qrStyle.margin" type="range" min="0" max="12" step="1" />
      </label>
    </div>
  </div>
</template>
