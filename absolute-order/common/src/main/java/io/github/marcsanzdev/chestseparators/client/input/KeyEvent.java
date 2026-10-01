package io.github.marcsanzdev.chestseparators.client.input;

/**
 * Compatibility stand-in for the E5 {@code net.minecraft.client.input.KeyEvent} record, which does not
 * exist in 1.20.1 (E4). The editor's internal input pipeline is written against this record; the vanilla
 * {@code keyPressed(int, int, int)} boundaries (screen mixins) build one from the primitive GLFW arguments.
 * Component names mirror the vanilla accessors used across the codebase (notably {@code key()}).
 */
public record KeyEvent(int key, int scancode, int modifiers) {}
