package io.github.marcsanzdev.chestseparators.client.ui;

/**
 * Named ARGB color constants for the editor UI.
 *
 * <p>Only the dominant, semantically stable theme colors live here. Context-specific shades
 * (hover/sunken/disabled variants) remain inline at their call sites.
 */
public final class UiColors {

    private UiColors() {}

    /** Panel/surface background in dark mode. */
    public static final int SURFACE_DARK = 0xFF212121;

    /** Panel/surface background in light mode (vanilla inventory gray). */
    public static final int SURFACE_LIGHT = 0xFFC6C6C6;
}
