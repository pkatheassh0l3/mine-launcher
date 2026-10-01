package io.github.marcsanzdev.chestseparators.client.ui.widgets;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * A tiny, shared "button was just pressed" registry keyed by on-screen position.
 *
 * <p>Many action buttons (preset Save/Load/Delete, Copy/Paste/Undo/Redo, …) are re-created every frame, so
 * per-instance press state does not survive. Keying by the button's (x, y) — which stays constant across
 * those re-creations while a button keeps its place — lets a stateless button flash "pressed" (accent +
 * shrunk) for a moment after a click, then return to normal. Two distinct buttons never occupy the same
 * (x, y) in the same frame, so collisions are momentary at worst.
 *
 * <p>For buttons whose action would close their own screen (so the flash would never be seen), {@link
 * #pressAndDefer} plays the flash first and runs the action a moment later; {@link #tick()} — pumped once
 * per frame by the editor — fires those deferred actions when their flash has elapsed.
 */
public final class PressAnim {

    private static final long DURATION_MS = 140L;
    private static final long DEFER_MS = 110L;
    private static final Map<Long, Long> PRESSES = new HashMap<>();

    private static final class Deferred {
        final long dueAt;
        final Runnable action;

        Deferred(long dueAt, Runnable action) {
            this.dueAt = dueAt;
            this.action = action;
        }
    }

    private static final List<Deferred> PENDING = new ArrayList<>();

    private PressAnim() {}

    private static long key(int x, int y) {
        return ((long) x << 20) ^ (y & 0xFFFFF);
    }

    /** Marks the button at (x, y) as just pressed, starting its flash. */
    public static void press(int x, int y) {
        PRESSES.put(key(x, y), System.currentTimeMillis());
    }

    /** True while the button at (x, y) is still within its post-click flash window. */
    public static boolean active(int x, int y) {
        Long t = PRESSES.get(key(x, y));
        if (t == null) return false;
        if (System.currentTimeMillis() - t > DURATION_MS) {
            PRESSES.remove(key(x, y));
            return false;
        }
        return true;
    }

    /** Flash the button at (x, y), then run {@code action} once the flash has played (see {@link #tick()}). */
    public static void pressAndDefer(int x, int y, Runnable action) {
        press(x, y);
        if (action != null) PENDING.add(new Deferred(System.currentTimeMillis() + DEFER_MS, action));
    }

    /** Runs any deferred actions whose flash has elapsed. Pump once per frame from the editor render. */
    public static void tick() {
        if (PENDING.isEmpty()) return;
        long now = System.currentTimeMillis();
        for (Iterator<Deferred> it = PENDING.iterator(); it.hasNext(); ) {
            Deferred d = it.next();
            if (now >= d.dueAt) {
                it.remove();
                d.action.run();
            }
        }
    }
}
