/**
 * Undo and redo for the editor, with no framework in it.
 *
 * <p>A snapshot is a copy of plain objects and arrays whose strings are shared, not copied. The
 * editor's large values are images held as data URLs, which are strings, so a snapshot of a style
 * with a 2 MB logo costs the same few hundred property copies as one without: the snapshot and the
 * editor point at the same immutable string. Comparing two snapshots short-circuits on that same
 * string too. That makes it cheap to take one on every change, which is what lets an edit that
 * changes nothing (a restore echoing back through a watcher) be recognised and skipped.
 *
 * <p>Changes are coalesced: {@link EditHistory.touch} restarts a timer and the step is recorded
 * when the editor has been quiet for `delay` ms, so dragging a slider or typing a word is one
 * step. Undo records any pending change first, so nothing typed is lost by undoing straight away.
 */

/** A copy of plain objects and arrays, sharing every primitive, strings included. */
export function copyPlain<T>(value: T): T {
  if (Array.isArray(value)) {
    return value.map((item: unknown) => copyPlain(item)) as T
  }
  if (value !== null && typeof value === 'object') {
    const copy: Record<string, unknown> = {}
    for (const [key, item] of Object.entries(value)) {
      copy[key] = copyPlain(item)
    }
    return copy as T
  }
  return value
}

/** Structural equality of plain objects, arrays and primitives. */
export function samePlain(a: unknown, b: unknown): boolean {
  if (a === b) {
    return true
  }
  if (a === null || b === null || typeof a !== 'object' || typeof b !== 'object') {
    return false
  }
  if (Array.isArray(a) !== Array.isArray(b)) {
    return false
  }
  const keysA = Object.keys(a)
  const keysB = Object.keys(b)
  if (keysA.length !== keysB.length) {
    return false
  }
  const recordA = a as Record<string, unknown>
  const recordB = b as Record<string, unknown>
  return keysA.every((key) => Object.hasOwn(recordB, key) && samePlain(recordA[key], recordB[key]))
}

export interface HistoryOptions<T> {
  /** The editor's state now, as a snapshot the editor will not mutate afterwards. */
  capture: () => T
  /** Puts a snapshot back. The history passes a copy, so the editor may mutate what it gets. */
  restore: (snapshot: T) => void
  equals?: (a: T, b: T) => boolean
  /** Quiet time in ms after which a run of changes becomes one step. */
  delay?: number
  /** The most steps kept, the starting state included; the oldest go first. */
  limit?: number
  /** Called whenever canUndo or canRedo may have changed. */
  onChange?: () => void
}

export interface EditHistory {
  /** Reports that the editor changed; the step is recorded once it has been quiet for `delay`. */
  touch: () => void
  /** Records the current state now, as its own step, if it differs from the last one. */
  commit: () => void
  undo: () => boolean
  redo: () => boolean
  readonly canUndo: boolean
  readonly canRedo: boolean
  /** Steps held, the current one included. */
  readonly size: number
  dispose: () => void
}

export const HISTORY_DELAY = 450
export const HISTORY_LIMIT = 100

export function createHistory<T>(options: HistoryOptions<T>): EditHistory {
  const equals = options.equals ?? samePlain
  const delay = options.delay ?? HISTORY_DELAY
  const limit = Math.max(2, options.limit ?? HISTORY_LIMIT)
  const entries: T[] = [options.capture()]
  let index = 0
  /** A change that differs from entries[index] and has not been recorded yet. */
  let pending = false
  let timer: ReturnType<typeof setTimeout> | undefined

  const changed = (): void => options.onChange?.()

  function cancelTimer(): void {
    clearTimeout(timer)
    timer = undefined
  }

  function commit(): void {
    cancelTimer()
    const wasPending = pending
    pending = false
    const current = options.capture()
    if (!equals(current, entries[index])) {
      // A new edit after an undo discards the steps that were undone.
      entries.splice(index + 1)
      entries.push(current)
      if (entries.length > limit) {
        entries.splice(0, entries.length - limit)
      }
      index = entries.length - 1
      changed()
    } else if (wasPending) {
      changed()
    }
  }

  function touch(): void {
    cancelTimer()
    const wasPending = pending
    // Restoring a step changes the editor too; that change matches the step and is no edit.
    pending = !equals(options.capture(), entries[index])
    if (pending) {
      timer = setTimeout(commit, delay)
    }
    if (pending !== wasPending) {
      changed()
    }
  }

  function moveTo(next: number): void {
    index = next
    options.restore(copyPlain(entries[index]))
    changed()
  }

  return {
    touch,
    commit,
    undo() {
      if (pending) {
        commit()
      }
      if (index === 0) {
        return false
      }
      moveTo(index - 1)
      return true
    },
    redo() {
      if (pending || index >= entries.length - 1) {
        return false
      }
      moveTo(index + 1)
      return true
    },
    get canUndo() {
      return index > 0 || pending
    },
    get canRedo() {
      return !pending && index < entries.length - 1
    },
    get size() {
      return entries.length
    },
    dispose: cancelTimer,
  }
}

export type HistoryAction = 'undo' | 'redo'

/** Input types that edit text, and so have their own native undo. */
const TEXT_INPUT_TYPES = new Set([
  'text',
  'search',
  'url',
  'tel',
  'email',
  'password',
  'number',
  'date',
  'datetime-local',
  'month',
  'time',
  'week',
])

/**
 * Whether the element has an undo of its own that a page shortcut would steal. Selects are
 * included so a keyboard user changing one is never surprised; sliders, checkboxes and buttons
 * have no native undo, so the editor's undo applies there.
 */
export function isEditable(target: EventTarget | null): boolean {
  if (!(target instanceof Element)) {
    return false
  }
  if (target instanceof HTMLTextAreaElement || target instanceof HTMLSelectElement) {
    return true
  }
  if (target instanceof HTMLInputElement) {
    return TEXT_INPUT_TYPES.has(target.type)
  }
  return (
    target instanceof HTMLElement &&
    (target.isContentEditable ||
      target.closest('[contenteditable]:not([contenteditable="false"])') !== null)
  )
}

/**
 * The editor action a key press asks for: Ctrl/Cmd+Z undoes, Ctrl/Cmd+Shift+Z and Ctrl+Y redo.
 * Null inside a text field, so the field's own undo keeps working.
 */
export function historyShortcut(event: KeyboardEvent): HistoryAction | null {
  if (event.defaultPrevented || event.altKey || event.isComposing) {
    return null
  }
  const key = event.key.toLowerCase()
  let action: HistoryAction | null = null
  if ((event.ctrlKey || event.metaKey) && key === 'z') {
    action = event.shiftKey ? 'redo' : 'undo'
  } else if (event.ctrlKey && !event.metaKey && !event.shiftKey && key === 'y') {
    action = 'redo'
  }
  if (action === null || isEditable(event.target)) {
    return null
  }
  return action
}
