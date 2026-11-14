<script setup lang="ts">
import { computed } from 'vue'
import type { QrField, QrType } from '../qr/types'

const props = defineProps<{
  type: QrType
  model: Record<string, string>
}>()

function inputId(field: QrField): string {
  return `${props.type.id}-${field.key}`
}

/** Values that were entered but cannot be encoded; the type's builder yields nothing meanwhile. */
const problems = computed(() => props.type.validate?.(props.model) ?? {})

/** Problems about the payload as a whole rather than one field, such as its total size. */
const general = computed(() => {
  const keys = new Set(props.type.fields.map((field) => field.key))
  return Object.entries(problems.value)
    .filter(([key]) => !keys.has(key))
    .map(([, message]) => message)
})

function describedBy(field: QrField): string | undefined {
  if (problems.value[field.key]) {
    return `${inputId(field)}-problem`
  }
  return field.help ? `${inputId(field)}-help` : undefined
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
          :aria-invalid="problems[field.key] ? 'true' : undefined"
          :aria-describedby="describedBy(field)"
        />

        <span v-if="problems[field.key]" :id="`${inputId(field)}-problem`" class="field-help error-text" role="alert">
          {{ problems[field.key] }}
        </span>
        <span v-else-if="field.help" :id="`${inputId(field)}-help`" class="field-help">{{ field.help }}</span>
      </div>
    </template>

    <p v-for="message in general" :key="message" class="field-help error-text" role="alert">{{ message }}</p>
  </div>
</template>
