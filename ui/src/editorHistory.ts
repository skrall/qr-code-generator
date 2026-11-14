import { onMounted, onUnmounted, ref, toRaw, watch, type Ref } from 'vue'
import type { QrStyle } from './api/client'
import { copyPlain, createHistory, historyShortcut } from './history'
import type { QrTypeId } from './qr/types'

/** What one undo step holds: the content tab, every tab's values and the style. */
export interface EditorSnapshot {
  typeId: QrTypeId
  values: Record<string, Record<string, string>>
  style: QrStyle
}

/**
 * Connects {@link createHistory} to the editor's reactive state and the keyboard.
 *
 * <p>Snapshots are taken from the raw objects behind the reactive proxies, so taking one reads
 * plain properties rather than going through Vue's tracking, and image data URLs are shared
 * rather than copied.
 */
export function useEditorHistory(state: {
  typeId: Ref<QrTypeId>
  values: Record<string, Record<string, string>>
  style: QrStyle
  /** Whether the keyboard shortcuts apply now. */
  active?: () => boolean
}) {
  const canUndo = ref(false)
  const canRedo = ref(false)

  const history = createHistory<EditorSnapshot>({
    capture: () => ({
      typeId: state.typeId.value,
      values: copyPlain(toRaw(state.values)),
      style: copyPlain(toRaw(state.style)),
    }),
    restore: (snapshot) => {
      state.typeId.value = snapshot.typeId
      for (const key of Object.keys(snapshot.values)) {
        state.values[key] = snapshot.values[key]
      }
      Object.assign(state.style, snapshot.style)
    },
    onChange: () => {
      canUndo.value = history.canUndo
      canRedo.value = history.canRedo
    },
  })

  watch([state.typeId, () => state.values, () => state.style], () => history.touch(), {
    deep: true,
  })

  function onKeydown(event: KeyboardEvent): void {
    const action = historyShortcut(event)
    if (action === null || state.active?.() === false) {
      return
    }
    event.preventDefault()
    if (action === 'undo') {
      history.undo()
    } else {
      history.redo()
    }
  }

  onMounted(() => window.addEventListener('keydown', onKeydown))
  onUnmounted(() => {
    window.removeEventListener('keydown', onKeydown)
    history.dispose()
  })

  return {
    canUndo,
    canRedo,
    undo: () => history.undo(),
    redo: () => history.redo(),
    /** Makes what just happened its own step, such as applying a share link. */
    commit: () => history.commit(),
  }
}
