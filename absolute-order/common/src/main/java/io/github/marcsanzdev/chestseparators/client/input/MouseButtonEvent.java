package io.github.marcsanzdev.chestseparators.client.input;

/**
 * Compatibility stand-in for the E5 {@code net.minecraft.client.input.MouseButtonEvent} record (absent in
 * 1.20.1 / E4). Built from the vanilla {@code mouseClicked(double, double, int)} boundary. Component names
 * mirror the vanilla accessors used across the codebase ({@code x()}, {@code y()}, {@code button()}).
 */
public record MouseButtonEvent(double x, double y, int button) {}
