import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'
import { copyPlain, createHistory, historyShortcut, samePlain } from './history'

interface State {
  text: string
  nested: { size: number; image: string | null }
}

function editor(initial: State = { text: '', nested: { size: 1, image: null } }) {
  const state: State = copyPlain(initial)
  const restored: State[] = []
  const history = createHistory<State>({
    capture: () => copyPlain(state),
    restore: (snapshot) => {
      restored.push(snapshot)
      Object.assign(state, snapshot)
    },
    delay: 400,
    limit: 5,
  })
  /** An edit as the app makes one: change the state, then report it. */
  const edit = (change: (s: State) => void): void => {
    change(state)
    history.touch()
  }
  return { state, history, edit, restored }
}

beforeEach(() => {
  vi.useFakeTimers()
})

afterEach(() => {
  vi.useRealTimers()
})

describe('createHistory', () => {
  test('a run of changes inside the delay is one step', () => {
    const { state, history, edit } = editor()
    for (const text of ['h', 'he', 'hel', 'hell', 'hello']) {
      edit((s) => (s.text = text))
      vi.advanceTimersByTime(300)
    }
    expect(history.size).toBe(1)
    expect(history.canUndo).toBe(true)
    vi.advanceTimersByTime(400)
    expect(history.size).toBe(2)

    expect(history.undo()).toBe(true)
    expect(state.text).toBe('')
    expect(history.redo()).toBe(true)
    expect(state.text).toBe('hello')
  })

  test('changes separated by a pause are separate steps', () => {
    const { state, history, edit } = editor()
    edit((s) => (s.text = 'one'))
    vi.advanceTimersByTime(500)
    edit((s) => (s.nested.size = 2))
    vi.advanceTimersByTime(500)

    history.undo()
    expect(state).toEqual({ text: 'one', nested: { size: 1, image: null } })
    history.undo()
    expect(state).toEqual({ text: '', nested: { size: 1, image: null } })
    expect(history.canUndo).toBe(false)
    expect(history.undo()).toBe(false)
  })

  test('undo records a pending change first, so nothing typed is lost', () => {
    const { state, history, edit } = editor()
    edit((s) => (s.text = 'quick'))
    expect(history.canRedo).toBe(false)
    history.undo()
    expect(state.text).toBe('')
    history.redo()
    expect(state.text).toBe('quick')
  })

  test('a new edit after undo discards the undone steps', () => {
    const { state, history, edit } = editor()
    edit((s) => (s.text = 'a'))
    history.commit()
    edit((s) => (s.text = 'b'))
    history.commit()
    history.undo()
    expect(history.canRedo).toBe(true)

    edit((s) => (s.text = 'c'))
    // The pending edit already hides redo; committing it removes the branch.
    expect(history.canRedo).toBe(false)
    vi.advanceTimersByTime(400)
    expect(history.redo()).toBe(false)
    history.undo()
    expect(state.text).toBe('a')
  })

  test('restoring creates no entries, even when the change echoes back through touch', () => {
    const { history, edit } = editor()
    edit((s) => (s.text = 'a'))
    history.commit()
    edit((s) => (s.text = 'b'))
    history.commit()
    expect(history.size).toBe(3)

    history.undo()
    // A watcher sees the restore as a change and reports it.
    history.touch()
    expect(history.canRedo).toBe(true)
    vi.advanceTimersByTime(1000)
    expect(history.size).toBe(3)
    expect(history.canRedo).toBe(true)
  })

  test('a change that ends where it started records nothing', () => {
    const { history, edit } = editor()
    edit((s) => (s.text = 'x'))
    edit((s) => (s.text = ''))
    expect(history.canUndo).toBe(false)
    vi.advanceTimersByTime(1000)
    expect(history.size).toBe(1)
  })

  test('keeps at most limit steps, dropping the oldest', () => {
    const { state, history, edit } = editor()
    for (let i = 1; i <= 8; i++) {
      edit((s) => (s.text = String(i)))
      history.commit()
    }
    expect(history.size).toBe(5)
    while (history.undo()) {
      // back to the oldest kept
    }
    expect(state.text).toBe('4')
  })

  test('commit makes a discrete action its own step', () => {
    const { state, history, edit } = editor()
    edit((s) => (s.text = 'typed'))
    history.commit()
    edit((s) => (s.nested = { size: 9, image: 'data:image/png;base64,AAAA' }))
    history.commit()
    history.undo()
    expect(state.nested).toEqual({ size: 1, image: null })
    expect(state.text).toBe('typed')
  })

  test('restores a copy, so later edits do not change the stored step', () => {
    const { state, history, edit } = editor()
    edit((s) => (s.nested.size = 5))
    history.commit()
    history.undo()
    state.nested.size = 77
    history.redo()
    history.undo()
    expect(state.nested.size).toBe(1)
  })

  test('reports when canUndo and canRedo may have changed', () => {
    const state = { n: 0 }
    const onChange = vi.fn()
    const history = createHistory({ capture: () => ({ ...state }), restore: (s) => Object.assign(state, s), onChange })
    state.n = 1
    history.touch()
    expect(onChange).toHaveBeenCalledTimes(1)
    vi.runAllTimers()
    history.undo()
    expect(onChange.mock.calls.length).toBeGreaterThanOrEqual(2)
  })
})

describe('snapshots', () => {
  test('copy plain objects and compare by structure', () => {
    const value = { a: [1, { b: 'x' }], c: null }
    const copy = copyPlain(value)
    expect(copy).toEqual(value)
    expect(copy.a).not.toBe(value.a)
    expect(samePlain(copy, value)).toBe(true)
    expect(samePlain(copy, { ...value, c: 0 })).toBe(false)
    expect(samePlain({ a: 1 }, { a: 1, b: undefined })).toBe(false)
    expect(samePlain([1], { 0: 1 })).toBe(false)
  })

  test('stay cheap with a large image: the data URL is shared, not copied', () => {
    const image = `data:image/png;base64,${'A'.repeat(3_000_000)}`
    const state = {
      values: Object.fromEntries(Array.from({ length: 14 }, (_, i) => [`t${i}`, { a: 'x', b: 'y' }])),
      style: { size: 640, logo: { dataUrl: image, sizePercent: 20 }, frame: { title: 'Hi' } },
    }
    vi.useRealTimers()
    const start = performance.now()
    let previous = copyPlain(state)
    for (let i = 0; i < 1000; i++) {
      const next = copyPlain(state)
      samePlain(next, previous)
      previous = next
    }
    const elapsed = performance.now() - start
    // JSON or structuredClone of a 3 MB string, a thousand times, takes seconds.
    expect(elapsed).toBeLessThan(250)
  })
})

describe('historyShortcut', () => {
  function press(init: KeyboardEventInit, target: EventTarget = document.body): KeyboardEvent {
    const event = new KeyboardEvent('keydown', { bubbles: true, cancelable: true, ...init })
    Object.defineProperty(event, 'target', { value: target })
    return event
  }

  test('maps the usual keys', () => {
    expect(historyShortcut(press({ key: 'z', ctrlKey: true }))).toBe('undo')
    expect(historyShortcut(press({ key: 'z', metaKey: true }))).toBe('undo')
    expect(historyShortcut(press({ key: 'Z', ctrlKey: true, shiftKey: true }))).toBe('redo')
    expect(historyShortcut(press({ key: 'Z', metaKey: true, shiftKey: true }))).toBe('redo')
    expect(historyShortcut(press({ key: 'y', ctrlKey: true }))).toBe('redo')
  })

  test('ignores other keys and modifiers', () => {
    expect(historyShortcut(press({ key: 'z' }))).toBeNull()
    expect(historyShortcut(press({ key: 'z', ctrlKey: true, altKey: true }))).toBeNull()
    expect(historyShortcut(press({ key: 'y', metaKey: true }))).toBeNull()
    expect(historyShortcut(press({ key: 'x', ctrlKey: true }))).toBeNull()
  })

  test('leaves text fields their native undo', () => {
    const input = document.createElement('input')
    const textarea = document.createElement('textarea')
    const select = document.createElement('select')
    const editable = document.createElement('div')
    editable.setAttribute('contenteditable', 'true')
    const inside = document.createElement('span')
    editable.appendChild(inside)
    for (const target of [input, textarea, select, inside]) {
      expect(historyShortcut(press({ key: 'z', ctrlKey: true }, target))).toBeNull()
    }
  })

  test('applies on controls without a native undo', () => {
    for (const type of ['range', 'checkbox', 'color']) {
      const input = document.createElement('input')
      input.type = type
      expect(historyShortcut(press({ key: 'z', ctrlKey: true }, input))).toBe('undo')
    }
    expect(historyShortcut(press({ key: 'z', ctrlKey: true }, document.createElement('button')))).toBe(
      'undo',
    )
  })
})
