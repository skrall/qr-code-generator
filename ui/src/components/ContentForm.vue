<script setup lang="ts">
import type { QrField, QrType } from '../qr/types'

const props = defineProps<{
  type: QrType
  model: Record<string, string>
}>()

function inputId(field: QrField): string {
  return `${props.type.id}-${field.key}`
}
</script>

<template>
  <div class="fields" :class="{ dense: type.fields.length > 4 }">
    <template v-for="field in type.fields" :key="field.key">
      <div v-if="field.kind === 'checkbox'" class="field kind-checkbox">
        <label class="checkbox" :for="inputId(field)">
          <input
            :id="inputId(field)"
            v-model="model[field.key]"
            type="checkbox"
            true-value="true"
            false-value=""
          />
          <span>{{ field.label }}</span>
        </label>
      </div>

      <div v-else class="field" :class="`kind-${field.kind}`">
        <label class="field-label" :for="inputId(field)">
          {{ field.label }}
          <span v-if="field.required" class="required" aria-hidden="true">*</span>
        </label>

        <textarea
          v-if="field.kind === 'textarea'"
          :id="inputId(field)"
          v-model="model[field.key]"
          :placeholder="field.placeholder"
          :maxlength="field.maxLength"
          rows="3"
        ></textarea>

        <select v-else-if="field.kind === 'select'" :id="inputId(field)" v-model="model[field.key]">
          <option v-for="option in field.options ?? []" :key="option.value" :value="option.value">
            {{ option.label }}
          </option>
        </select>

        <input
          v-else
          :id="inputId(field)"
          v-model="model[field.key]"
          :type="field.kind"
          :placeholder="field.placeholder"
          :maxlength="field.maxLength"
          :autocomplete="field.autocomplete ?? 'off'"
          :step="field.kind === 'number' ? 'any' : undefined"
        />

        <span v-if="field.help" class="field-help">{{ field.help }}</span>
      </div>
    </template>
  </div>
</template>
