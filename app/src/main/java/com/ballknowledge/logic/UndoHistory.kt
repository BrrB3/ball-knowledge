package com.ballknowledge.logic

/**
 * Undo/redo for one open page. Typing into the same box within [groupMillis]
 * of the last keystroke is one step, so Undo removes a phrase, not a letter.
 */
class UndoHistory<T>(private val limit: Int = 200, private val groupMillis: Long = 1_500L) {
    private val undoStack = ArrayDeque<T>()
    private val redoStack = ArrayDeque<T>()
    private var lastKey: String? = null
    private var lastTime: Long = 0L

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    /**
     * Call before applying a change, with the state as it was. [key] identifies what's
     * being edited (e.g. a field name); null means "always a separate step".
     */
    fun record(before: T, key: String?, now: Long) {
        val sameBurst = key != null && key == lastKey && now - lastTime <= groupMillis
        if (!sameBurst) {
            undoStack.addLast(before)
            if (undoStack.size > limit) undoStack.removeFirst()
        }
        redoStack.clear()
        lastKey = key
        lastTime = now
    }

    fun undo(current: T): T? {
        val previous = undoStack.removeLastOrNull() ?: return null
        redoStack.addLast(current)
        lastKey = null
        return previous
    }

    fun redo(current: T): T? {
        val next = redoStack.removeLastOrNull() ?: return null
        undoStack.addLast(current)
        lastKey = null
        return next
    }
}
