package io.github.marcsanzdev.chestseparators.client;

// Represents the current state of the Chest Separators GUI.
public enum EditorState {
    HIDDEN, // The editor is closed (normal vanilla behavior)
    DRAW_LINES, // The classic mode: drawing visual separators (Pencil button)
    VIEW_GROUPS, // Viewing existing whitelist groups (light/dark gray overlay)
    SELECT_SLOTS, // Creating/modifying a group's slot selection (green overlay)
    EDIT_FILTER // In the creative-style menu configuring allowed items
}
