package io.github.marcsanzdev.chestseparators.data;

import java.util.LinkedList;
import java.util.function.UnaryOperator;

/**
 * Generic bounded undo/redo history.
 *
 * <p>Stores deep copies of state snapshots (produced by the supplied copy function) so the
 * live state and the recorded history never alias. The same mechanism backs both the visual
 * separator configuration and the slot whitelists.
 *
 * @param <T> the snapshot state type
 */
public final class UndoRedoHistory<T> {

    private final LinkedList<T> undoStack = new LinkedList<>();
    private final LinkedList<T> redoStack = new LinkedList<>();
    private final UnaryOperator<T> copy;
    private final int maxSteps;

    public UndoRedoHistory(UnaryOperator<T> copy, int maxSteps) {
        this.copy = copy;
        this.maxSteps = maxSteps;
    }

    /** Records the current state as a restore point and discards any redo history. */
    public void push(T current) {
        undoStack.addLast(copy.apply(current));
        if (undoStack.size() > maxSteps) {
            undoStack.removeFirst();
        }
        redoStack.clear();
    }

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    /**
     * Steps one entry back: captures {@code current} onto the redo stack and returns the previous
     * snapshot for the caller to apply, or {@code null} when there is nothing to undo.
     */
    public T undo(T current) {
        if (!canUndo()) {
            return null;
        }
        redoStack.addLast(copy.apply(current));
        return undoStack.removeLast();
    }

    /** Steps one entry forward, mirroring {@link #undo}; returns {@code null} when nothing to redo. */
    public T redo(T current) {
        if (!canRedo()) {
            return null;
        }
        undoStack.addLast(copy.apply(current));
        return redoStack.removeLast();
    }

    public void clear() {
        undoStack.clear();
        redoStack.clear();
    }
}
