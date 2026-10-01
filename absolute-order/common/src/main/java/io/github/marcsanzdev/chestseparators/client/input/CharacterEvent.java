package io.github.marcsanzdev.chestseparators.client.input;

/**
 * Compatibility stand-in for the E5 {@code net.minecraft.client.input.CharacterEvent} record (absent in
 * 1.20.1 / E4). Built from the vanilla {@code charTyped(char, int)} boundary. {@link #codepoint()} carries
 * the typed character promoted to an int, matching the E5 accessor name.
 */
public record CharacterEvent(int codepoint, int modifiers) {}
