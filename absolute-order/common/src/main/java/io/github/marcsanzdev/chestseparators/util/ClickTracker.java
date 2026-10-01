package io.github.marcsanzdev.chestseparators.util;

// Tracks the current slot interaction type using ThreadLocal to prevent
// Client and Server threads from overwriting each other's state in singleplayer.
public class ClickTracker {
    public static final ThreadLocal<Boolean> IS_SHIFT_CLICK = ThreadLocal.withInitial(() -> false);

    // When set, the mayPlace mixins skip the mod's whitelist enforcement so the editor can probe a
    // slot's *vanilla* restriction (real armor checks) without the saved filter blocking the sentinel.
    public static final ThreadLocal<Boolean> BYPASS_ENFORCEMENT = ThreadLocal.withInitial(() -> false);

    // The [start, end) slot range of the shift-click insertion currently running, or null outside one.
    // The filter-priority rule needs it so it only ever defers to a dedicated slot that the current
    // insertion can actually reach (deferring to a slot outside the range would strand the item).
    public static final ThreadLocal<int[]> INSERT_RANGE = new ThreadLocal<>();
}
