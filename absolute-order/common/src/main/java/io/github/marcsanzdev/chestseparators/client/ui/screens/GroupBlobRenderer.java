package io.github.marcsanzdev.chestseparators.client.ui.screens;

import io.github.marcsanzdev.chestseparators.client.EditorState;
import io.github.marcsanzdev.chestseparators.client.ModTextures;
import io.github.marcsanzdev.chestseparators.client.ui.ChestSeparatorsEditor;
import io.github.marcsanzdev.chestseparators.client.ui.EditorLayout;
import io.github.marcsanzdev.chestseparators.client.ui.EditorSessionData;
import io.github.marcsanzdev.chestseparators.data.ChestConfigManager;
import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.inventory.Slot;

/**
 * Renders the whitelist group "blobs" over the chest grid for the group-view screen, assigning a
 * stable color per group. Pure rendering, extracted verbatim from ScreenViewGroups.
 */
final class GroupBlobRenderer {

    static final int[] GROUP_PALETTE = {
        0x99E53935, 0x99F57C00, 0x99FBC02D, 0x997CB342,
        0x99388E3C, 0x9900897B, 0x9900ACC1, 0x991E88E5,
        0x993949AB, 0x998E24AA, 0x99D81B60, 0x99795548,
        0x99D50000, 0x99C51162, 0x99AA00FF, 0x996200EA,
        0x99304FFE, 0x992962FF, 0x9900B8D4, 0x9900BFA5,
        0x9900C853, 0x9964DD17, 0x99AEEA00, 0x99FFD600,
        0x99FFAB00, 0x99FF6D00, 0x99DD2C00, 0x995D4037,
        0x99827717, 0x99E65100, 0x99F4511E, 0x99C0CA33
    };

    /** Body opacity of a saved filter: translucent enough to read the item sitting in the slot. */
    private static final int SAVED_FILL_ALPHA = 0x70;

    // Blob geometry inside a 16px slot on an 18px pitch. The cell is deliberately one pixel smaller on
    // each side than the slot's artwork so the rounded corners breathe instead of colliding with the slot
    // border, and so two blobs in neighbouring slots never touch.
    private static final int CELL_INSET = 3; // left/top edge of the cell inside the slot
    private static final int CELL_FAR = 13; // right/bottom edge of the cell (CELL_INSET + 10)
    private static final int NEXT_CELL = 21; // where the next slot's cell starts (18 + CELL_INSET)
    // Middle of the gap on each side; the boundary at which a cell stops painting and its neighbour takes
    // over, so a slot's colour never reaches into the neighbouring slot.
    private static final int MID_FAR = 17; // (CELL_FAR + NEXT_CELL) / 2
    private static final int MID_NEAR = -1; // MID_FAR - 18

    private final ChestSeparatorsEditor editor;
    private final EditorSessionData session;
    private final EditorLayout layout;
    private final Map<UUID, Integer> stableGroupColors = new HashMap<>();

    GroupBlobRenderer(ScreenViewGroups screen) {
        this.editor = screen.editor;
        this.session = screen.session;
        this.layout = screen.layout;
    }

    void renderWhitelistGroups(GuiGraphics context, int mouseX, int mouseY) {
        int guiX = layout.guiX;
        int guiY = layout.guiY;
        var whitelists = ChestConfigManager.getInstance().getCurrentWhitelists();

        Set<Integer> activeGreenSlots = new HashSet<>(session.selectedSlots);
        Set<Integer> activeRedSlots = new HashSet<>();

        boolean isPreviewArea = session.currentState == EditorState.VIEW_GROUPS
                && session.isDraggingLine
                && session.wlToolMode == 0
                && session.dragStartSlot != null
                && session.dragCurrentSlot != null;

        if (isPreviewArea) {
            // Ask for exactly the slots the release will act on. Deriving the rectangle from slot indices
            // (index / 9) assumed the container's slots are laid out in one continuous 9-wide grid, which
            // the player inventory is not — hotbar, main rows and armor break that mapping, so the preview
            // highlighted rows the drag never touched even though the release then did the right thing.
            for (Slot slot : editor.slotsInDragBox(session.dragStartSlot, session.dragCurrentSlot)) {
                int key = ChestSeparatorsEditor.slotKey(slot);
                if (session.isSelecting) {
                    activeGreenSlots.add(key);
                } else if (activeGreenSlots.remove(key)) {
                    // Red marks what the release will actually drop, so slots that were never selected
                    // are left alone instead of flashing as if they were about to change.
                    activeRedSlots.add(key);
                }
            }
        }

        // Selected slots that ALREADY belong to a filter. Their saved group paints them green itself, so
        // the selection takes them over: they leave their old group's shape and join the selection blob,
        // so marking a slot next to another already-marked one merges both into a single green piece.
        Set<Integer> conflicting = new HashSet<>();
        if (whitelists != null) {
            for (int slotIdx : activeGreenSlots) {
                if (whitelists.containsKey(slotIdx)) conflicting.add(slotIdx);
            }
        }

        for (Slot slot : editor.accessor.getHandler().slots) {
            if (!ChestSeparatorsEditor.isEditableSlot(slot)) continue;
            int idx = ChestSeparatorsEditor.slotKey(slot);

            boolean hasWhitelist = whitelists != null && whitelists.containsKey(idx);
            boolean isActive = activeGreenSlots.contains(idx) || activeRedSlots.contains(idx);

            // Gray indicator for slots that have no filter assigned yet: same rounding language as a
            // filter blob, but one pixel larger on every side. A filter cell is kept slightly inset so
            // its rounding clears the slot border, while this one stands alone and reads too small at
            // that size.
            if (!hasWhitelist && !isActive) {
                int x = guiX + slot.x;
                int y = guiY + slot.y;
                int inset = CELL_INSET - 1;
                int size = (CELL_FAR - CELL_INSET) + 2;
                fillRounded(
                        (x0, y0, x1, y1) -> context.fill(x0, y0, x1, y1, 0x44888888),
                        x + inset,
                        y + inset,
                        size,
                        size,
                        true,
                        true,
                        true,
                        true);
            }
        }

        if (whitelists != null) {
            Map<UUID, Set<Integer>> groups = new HashMap<>();
            for (Map.Entry<Integer, SlotWhitelist> entry : whitelists.entrySet()) {
                // A slot handed over to the selection leaves its old group's silhouette, so the group
                // reshapes around the gap and the slot can fuse with the rest of the selection instead.
                if (activeGreenSlots.contains(entry.getKey())) continue;
                groups.computeIfAbsent(entry.getValue().groupId(), k -> new HashSet<>())
                        .add(entry.getKey());
            }
            groups.values().removeIf(Set::isEmpty);

            List<UUID> orderedGroupIds = new ArrayList<>(groups.keySet());
            orderedGroupIds.sort(UUID::compareTo);

            Set<Integer> usedColors = new HashSet<>();
            Map<UUID, Integer> assignedColors = new HashMap<>();

            // 1. Assign explicit user-painted colors first
            for (UUID groupId : orderedGroupIds) {
                int explicit = getExplicitGroupColor(groups.get(groupId));
                if (explicit != 0) {
                    assignedColors.put(groupId, explicit);
                    usedColors.add(explicit);
                }
            }

            // 2. Rescue stable colors from cache to prevent flickering/shifting
            for (UUID groupId : orderedGroupIds) {
                if (!assignedColors.containsKey(groupId) && stableGroupColors.containsKey(groupId)) {
                    int stableColor = stableGroupColors.get(groupId);
                    if (!usedColors.contains(stableColor)) {
                        assignedColors.put(groupId, stableColor);
                        usedColors.add(stableColor);
                    }
                }
            }

            // 3. Assign new sequential colors to brand new groups
            int paletteIdx = 0;
            for (UUID groupId : orderedGroupIds) {
                if (!assignedColors.containsKey(groupId)) {
                    while (paletteIdx < GROUP_PALETTE.length && usedColors.contains(GROUP_PALETTE[paletteIdx])) {
                        paletteIdx++;
                    }

                    int color;
                    if (paletteIdx < GROUP_PALETTE.length) {
                        color = GROUP_PALETTE[paletteIdx];
                        usedColors.add(color);
                        paletteIdx++;
                    } else {
                        color = GROUP_PALETTE[Math.abs(groupId.hashCode()) % GROUP_PALETTE.length];
                    }

                    assignedColors.put(groupId, color);
                    stableGroupColors.put(groupId, color); // Cache it for future frames
                }
            }

            // Which group the cursor is over, so it can be highlighted while the rest fade back. Makes the
            // extent of a single filter obvious when several groups sit next to each other.
            UUID hoveredGroup = null;
            for (Slot slot : editor.accessor.getHandler().slots) {
                if (!ChestSeparatorsEditor.isEditableSlot(slot)) continue;
                if (editor.isHovering(guiX + slot.x - 1, guiY + slot.y - 1, 18, 18, mouseX, mouseY)) {
                    SlotWhitelist wl = whitelists.get(ChestSeparatorsEditor.slotKey(slot));
                    if (wl != null) hoveredGroup = wl.groupId();
                    break;
                }
            }

            // Two passes on purpose: every outline underlay first, then every fill. Drawing each group
            // completely in turn would let a later group's 1px outline sit on top of an earlier group's
            // fill, nibbling its edge.
            for (Map.Entry<UUID, Set<Integer>> groupEntry : groups.entrySet()) {
                boolean isHovered = groupEntry.getKey().equals(hoveredGroup);
                boolean dimmed = hoveredGroup != null && !isHovered;
                int base = withAlpha(assignedColors.get(groupEntry.getKey()), SAVED_FILL_ALPHA);
                int fill = dimmed ? fade(base, 0.35f) : base;
                drawBlobFill(context, groupEntry.getValue(), fill, 0, null, guiX, guiY);
            }
            for (Map.Entry<UUID, Set<Integer>> groupEntry : groups.entrySet()) {
                boolean isHovered = groupEntry.getKey().equals(hoveredGroup);
                boolean dimmed = hoveredGroup != null && !isHovered;
                int base = withAlpha(assignedColors.get(groupEntry.getKey()), SAVED_FILL_ALPHA);
                drawBlobRim(context, groupEntry.getValue(), outlineFor(base, dimmed, isHovered), 0, null, guiX, guiY);
            }
        }

        // The slots being swept for a NEW filter are washed even lighter than the saved ones, so it stays
        // obvious which is a committed filter and which is the selection in progress. Slots taken from an
        // existing filter are included here, so they merge with any adjacent marked slot into one piece.
        if (!activeGreenSlots.isEmpty()) {
            final int selectionFill = 0x4433FF33;
            drawBlobFill(context, activeGreenSlots, selectionFill, 0, null, guiX, guiY);

            // Slots taken from an existing filter get their outline blinking towards white, in step with
            // the warning badge, so the conflict is legible even where the badge is not.
            List<int[]> warnSquares = new ArrayList<>();
            for (int slotIdx : conflicting) {
                Slot s = editor.slotForKey(slotIdx);
                if (s == null) continue;
                warnSquares.add(new int[] {guiX + s.x - 1, guiY + s.y - 1, guiX + s.x + 17, guiY + s.y + 17});
            }
            float warnBlink = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 200.0);
            int selectionRim = outlineFor(selectionFill, false, false);
            drawBlobRim(
                    context,
                    activeGreenSlots,
                    selectionRim,
                    lerpColor(selectionRim, 0xFFFFFFFF, warnBlink),
                    warnSquares,
                    guiX,
                    guiY);
        }
        if (!activeRedSlots.isEmpty()) drawBlobGroup(context, activeRedSlots, 0x44FF3333, guiX, guiY);

        // Mutual exclusion: once a side has a slot selected (or a drag is in progress), darken the
        // other side's slots to show a single filter can't span the chest and the inventory.
        Integer activeKey = null;
        if (session.isDraggingLine && session.dragStartSlot != null) {
            activeKey = ChestSeparatorsEditor.slotKey(session.dragStartSlot);
        } else if (!session.selectedSlots.isEmpty()) {
            activeKey = session.selectedSlots.iterator().next();
        }
        if (activeKey != null) {
            boolean activePlayer = ChestConfigManager.isInventoryKey(activeKey);
            for (Slot slot : editor.accessor.getHandler().slots) {
                if (!ChestSeparatorsEditor.isEditableSlot(slot)) continue;
                if (ChestSeparatorsEditor.isPlayerSlot(slot) != activePlayer) {
                    context.fill(
                            guiX + slot.x - 1, guiY + slot.y - 1, guiX + slot.x + 17, guiY + slot.y + 17, 0xB0101010);
                }
            }
        }

        // Conflict warning: these slots are being swept into a NEW filter but already belong to one. They
        // already read as such (their piece of the old group is tinted with the selection colour), so all
        // that is left is a blinking glyph — one per contiguous run rather than one per slot, centred over
        // the run, so a big overlapping selection is not buried under a grid of icons.
        if (!conflicting.isEmpty()) {
            {
                float blink = 0.35f + 0.65f * (0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 200.0));
                int iconAlpha = (int) (0xFF * blink) & 0xFF;
                int icon = 8; // small enough to clear the selection outline on a lone 1×1 slot

                for (Set<Integer> run : contiguousRuns(conflicting)) {
                    int count = 0;
                    float cx = 0f;
                    float cy = 0f;
                    for (int slotIdx : run) {
                        Slot slot = editor.slotForKey(slotIdx);
                        if (slot == null) continue;
                        cx += guiX + slot.x + 8f;
                        cy += guiY + slot.y + 8f;
                        count++;
                    }
                    if (count == 0) continue;
                    cx /= count;
                    cy /= count;

                    // Keep the badge at the run's centroid whenever the icon there sits fully on selected
                    // (green) cells: that centres it truly, including on the seam BETWEEN the rows of a
                    // filled block — e.g. a 2×3 selection, where the middle of both rows is green so the
                    // glyph sits between them instead of floating on the top row. Only when the centroid
                    // lands in a gap — an L corner, the hollow of a U — snap it onto the selected slot
                    // nearest the centroid, so the glyph is always as central as possible yet never left
                    // half outside the green.
                    float half = icon / 2f;
                    boolean iconOnGreen = pointOnRun(run, guiX, guiY, cx - half, cy - half)
                            && pointOnRun(run, guiX, guiY, cx + half, cy - half)
                            && pointOnRun(run, guiX, guiY, cx - half, cy + half)
                            && pointOnRun(run, guiX, guiY, cx + half, cy + half);
                    if (!iconOnGreen) {
                        Slot nearest = null;
                        float bestDistance = Float.MAX_VALUE;
                        for (int slotIdx : run) {
                            Slot slot = editor.slotForKey(slotIdx);
                            if (slot == null) continue;
                            float dx = (guiX + slot.x + 8f) - cx;
                            float dy = (guiY + slot.y + 8f) - cy;
                            float distance = dx * dx + dy * dy;
                            if (distance < bestDistance) {
                                bestDistance = distance;
                                nearest = slot;
                            }
                        }
                        if (nearest != null) {
                            cx = guiX + nearest.x + 8f;
                            cy = guiY + nearest.y + 8f;
                        }
                    }

                    int ix = Math.round(cx) - icon / 2;
                    int iy = Math.round(cy) - icon / 2;
                    io.github.marcsanzdev.chestseparators.client.ui.UiTheme.blitTex(context,
                            ModTextures.ICON_SM_CONFLICT,
                            ix,
                            iy,
                            0.0F,
                            0.0F,
                            icon,
                            icon,
                            128,
                            128,
                            128,
                            128,
                            (iconAlpha << 24) | 0xFFFFFF);
                }
            }
        }
    }

    /** True when the point lies within the 16×16 cell of any slot in the run (i.e. over green). */
    private boolean pointOnRun(Set<Integer> run, int guiX, int guiY, float px, float py) {
        for (int slotIdx : run) {
            Slot slot = editor.slotForKey(slotIdx);
            if (slot == null) continue;
            float x0 = guiX + slot.x;
            float y0 = guiY + slot.y;
            if (px >= x0 && px <= x0 + 16f && py >= y0 && py <= y0 + 16f) return true;
        }
        return false;
    }

    /**
     * Splits slots into runs connected on screen, so each run can carry a single shared badge. Adjacency
     * is positional for the same reason the blob's is: index neighbours are not necessarily neighbours in
     * the inventory, and cells set apart (armor, offhand) must stay their own run.
     */
    private List<Set<Integer>> contiguousRuns(Set<Integer> slots) {
        Map<Long, Integer> byPosition = new HashMap<>();
        for (int slotIdx : slots) {
            Slot slot = editor.slotForKey(slotIdx);
            if (slot != null) byPosition.put(posKey(slot.x, slot.y), slotIdx);
        }

        List<Set<Integer>> runs = new ArrayList<>();
        Set<Integer> remaining = new HashSet<>(slots);
        while (!remaining.isEmpty()) {
            int seed = remaining.iterator().next();
            remaining.remove(seed);

            Set<Integer> run = new HashSet<>();
            java.util.ArrayDeque<Integer> queue = new java.util.ArrayDeque<>();
            queue.add(seed);
            while (!queue.isEmpty()) {
                int current = queue.poll();
                run.add(current);
                Slot slot = editor.slotForKey(current);
                if (slot == null) continue;

                int[][] offsets = {{18, 0}, {-18, 0}, {0, 18}, {0, -18}};
                for (int[] offset : offsets) {
                    Integer neighbour = byPosition.get(posKey(slot.x + offset[0], slot.y + offset[1]));
                    if (neighbour != null && remaining.remove(neighbour)) queue.add(neighbour);
                }
            }
            runs.add(run);
        }
        return runs;
    }

    /** Same colour at a fraction of its opacity, used to push non-hovered groups into the background. */
    private static int fade(int argb, float factor) {
        int alpha = (int) (((argb >>> 24) & 0xFF) * factor);
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }

    /** Paints one rectangle of the blob. Which colour each pixel ends up with is the painter's business. */
    private interface Painter {
        void rect(int x0, int y0, int x1, int y1);
    }

    /**
     * Paints a rectangle in {@code base}, except for the parts falling inside one of {@code tintSquares},
     * which are painted in {@code tint}.
     *
     * <p>Recolouring is done per REGION, not per slot: the blob's geometry is always identical, and a
     * marked slot simply repaints whatever the group already drew inside its square. Deciding the colour
     * per slot instead left seams — a slot's own square kept pixels from its neighbours (which paint into
     * the gaps between slots) and its own rounding spilled outside.
     *
     * <p>The rectangle is cut along the edges of the squares that overlap it and each piece is painted
     * once, so nothing is ever drawn twice — important because these colours are translucent and would
     * otherwise blend into visible seams.
     *
     * <p>A fully transparent colour means "paint nothing here", which is how the outline is suppressed
     * inside a marked slot: that slot shows the plain green body, with the group's own outline continuing
     * around it, instead of gaining a second outline of its own hugging the green.
     */
    private static void fillRegion(
            GuiGraphics c, int x0, int y0, int x1, int y1, int base, int tint, List<int[]> tintSquares) {
        if (x1 <= x0 || y1 <= y0) return;
        if (tintSquares == null || tintSquares.isEmpty()) {
            c.fill(x0, y0, x1, y1, base);
            return;
        }

        java.util.TreeSet<Integer> xs = new java.util.TreeSet<>(List.of(x0, x1));
        java.util.TreeSet<Integer> ys = new java.util.TreeSet<>(List.of(y0, y1));
        boolean anyOverlap = false;
        for (int[] s : tintSquares) {
            if (s[2] <= x0 || s[0] >= x1 || s[3] <= y0 || s[1] >= y1) continue;
            anyOverlap = true;
            if (s[0] > x0 && s[0] < x1) xs.add(s[0]);
            if (s[2] > x0 && s[2] < x1) xs.add(s[2]);
            if (s[1] > y0 && s[1] < y1) ys.add(s[1]);
            if (s[3] > y0 && s[3] < y1) ys.add(s[3]);
        }
        if (!anyOverlap) {
            c.fill(x0, y0, x1, y1, base);
            return;
        }

        List<Integer> xl = new ArrayList<>(xs);
        List<Integer> yl = new ArrayList<>(ys);
        for (int i = 0; i + 1 < xl.size(); i++) {
            for (int j = 0; j + 1 < yl.size(); j++) {
                int ax = xl.get(i);
                int bx = xl.get(i + 1);
                int ay = yl.get(j);
                int by = yl.get(j + 1);
                boolean inside = false;
                for (int[] s : tintSquares) {
                    if (ax >= s[0] && bx <= s[2] && ay >= s[1] && by <= s[3]) {
                        inside = true;
                        break;
                    }
                }
                int piece = inside ? tint : base;
                if ((piece >>> 24) != 0) c.fill(ax, ay, bx, by, piece);
            }
        }
    }

    /**
     * Fills a rectangle whose corners can be individually rounded. The rounding is a two-step stair
     * (2px inset on the outermost row, 1px on the next) instead of a single clipped pixel, which reads
     * as a noticeably softer corner at Minecraft's GUI scale.
     */
    private static void fillRounded(
            Painter p, int x, int y, int w, int h, boolean tl, boolean tr, boolean bl, boolean br) {
        // Body: full width, minus the two rows at the top and bottom that carry the rounding.
        p.rect(x, y + 2, x + w, y + h - 2);
        for (int d = 0; d < 2; d++) {
            int inset = 2 - d; // outermost row insets 2px, the next one 1px
            p.rect(x + (tl ? inset : 0), y + d, x + w - (tr ? inset : 0), y + d + 1);
            p.rect(x + (bl ? inset : 0), y + h - 1 - d, x + w - (br ? inset : 0), y + h - d);
        }
    }

    /** The 1px rim that traces {@link #innerFillet}'s stair on its outer side. */
    private static void innerFilletRim(Painter p, int px, int py, int sx, int sy) {
        for (int d = 0; d < 2; d++) {
            int len = 2 - d;
            int rx0 = sx > 0 ? px + len - 1 : px - len;
            int ry = sy > 0 ? py + d + 1 : py - d - 2;
            p.rect(rx0, ry, rx0 + 1, ry + 1);
        }
    }

    /**
     * Paints one group as a single blob: a 12×12 rounded cell per slot plus 6px connectors bridging
     * same-group neighbours. {@code expand} grows every piece outwards by that many pixels — drawing the
     * blob once expanded in the outline colour and then again at normal size in the fill colour produces a
     * continuous border around the WHOLE shape, connectors included. Tracing the border edge by edge
     * instead left gaps over the connectors, since those bridges have their own outer edges.
     *
     * <p>Only genuinely convex corners are rounded, so the joints where a connector meets a cell stay
     * square and the blob remains one solid piece.
     */
    /** Replaces the alpha of a colour, keeping its RGB. */
    private static int withAlpha(int argb, int alpha) {
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }

    /** Neighbour flags for one cell of a group, so fill and rim always agree on the shape. */
    private static final class Edges {
        boolean right, left, down, up;
        // Raw diagonals (independent of the orthogonal neighbours).
        boolean rawDR, rawDL, rawUR, rawUL;
        /** The bottom-right gap is filled solid. */
        boolean dr;
        // Concave corners this cell is responsible for drawing. A gap between four slots has one concave
        // corner per "missing" slot, and each is assigned to exactly one neighbour so it is drawn once.
        boolean cTL, cTR, cBL, cBRofTopLeftGap;
    }

    /** Screen position of a slot, packed so neighbours can be looked up directly. */
    private static long posKey(int x, int y) {
        return ((long) x << 20) ^ (y & 0xFFFFF);
    }

    /** Positions occupied by a group, for adjacency tests. */
    private Set<Long> occupiedPositions(Set<Integer> groupSlots) {
        return occupiedPositions(groupSlots, editor::slotForKey);
    }

    /** As above but resolving keys with an external mapper, so the same blob draw serves callers (like the
     * preset preview) that don't have the live editor's key→slot map. */
    private static Set<Long> occupiedPositions(
            Set<Integer> groupSlots, java.util.function.IntFunction<Slot> keyToSlot) {
        Set<Long> occupied = new HashSet<>();
        for (int slotIdx : groupSlots) {
            Slot slot = keyToSlot.apply(slotIdx);
            if (slot != null && ChestSeparatorsEditor.isEditableSlot(slot)) {
                occupied.add(posKey(slot.x, slot.y));
            }
        }
        return occupied;
    }

    /**
     * Neighbour flags derived from where the slots actually SIT (18px pitch), not from their indices.
     *
     * <p>Index arithmetic (±1, ±9) only describes adjacency on a single continuous 9-wide grid. The player
     * inventory is not one: the hotbar's indices come first but it is drawn last, under the main rows, and
     * armor and offhand sit apart beside the player model. That mismatch left hotbar slots refusing to
     * merge with each other while inviting connectors between rows that are nowhere near one another.
     * Comparing positions also survives the creative screen, whose slot indices differ from survival's.
     */
    private static Edges edgesAt(Set<Long> occupied, Slot slot) {
        int x = slot.x;
        int y = slot.y;

        Edges e = new Edges();
        e.right = occupied.contains(posKey(x + 18, y));
        e.left = occupied.contains(posKey(x - 18, y));
        e.down = occupied.contains(posKey(x, y + 18));
        e.up = occupied.contains(posKey(x, y - 18));

        e.rawDR = occupied.contains(posKey(x + 18, y + 18));
        e.rawDL = occupied.contains(posKey(x - 18, y + 18));
        e.rawUR = occupied.contains(posKey(x + 18, y - 18));
        e.rawUL = occupied.contains(posKey(x - 18, y - 18));

        e.dr = e.right && e.down && e.rawDR;

        // Bottom-right gap: whichever of its four corners is concave.
        e.cTL = e.right && e.down && !e.rawDR;
        e.cTR = e.right && e.rawDR && !e.down;
        e.cBL = e.down && e.rawDR && !e.right;
        // Top-left gap: its bottom-right corner is the one this cell owns (the other three belong to the
        // cells above/left, which reach them through their own bottom-right gap).
        e.cBRofTopLeftGap = e.up && e.left && !e.rawUL;
        return e;
    }

    /**
     * The blob's body: a rounded cell per slot plus the connectors that fuse neighbours.
     *
     * <p>{@code tintedSlots} keeps the group's silhouette intact while recolouring individual slots — used
     * for slots that are about to be taken over by a new filter, so the shape they form with their current
     * group is preserved and only that piece changes colour.
     */
    private void drawBlobFill(
            GuiGraphics context,
            Set<Integer> groupSlots,
            int color,
            int tintColor,
            List<int[]> tintSquares,
            int guiX,
            int guiY) {
        Painter p = (x0, y0, x1, y1) -> fillRegion(context, x0, y0, x1, y1, color, tintColor, tintSquares);
        blobFillPass(p, groupSlots, guiX, guiY, editor::slotForKey, occupiedPositions(groupSlots));
    }

    /** The blob body loop, shared verbatim by the live editor and any static caller (the preset preview). */
    private static void blobFillPass(
            Painter p,
            Set<Integer> groupSlots,
            int guiX,
            int guiY,
            java.util.function.IntFunction<Slot> keyToSlot,
            Set<Long> occupied) {
        final int a = CELL_INSET;
        final int b = CELL_FAR;
        final int n = NEXT_CELL;
        final int far = MID_FAR;
        final int near = MID_NEAR;

        for (int slotIdx : groupSlots) {
            Slot slot = keyToSlot.apply(slotIdx);
            if (slot == null || !ChestSeparatorsEditor.isEditableSlot(slot)) continue;

            int x = guiX + slot.x;
            int y = guiY + slot.y;
            Edges e = edgesAt(occupied, slot);

            fillRounded(
                    p,
                    x + a,
                    y + a,
                    b - a,
                    b - a,
                    !e.up && !e.left,
                    !e.up && !e.right,
                    !e.down && !e.left,
                    !e.down && !e.right);

            // Connectors are painted in halves, each cell covering up to the middle of the gap it shares
            // with a neighbour, so no rectangle is ever painted twice.
            if (e.right) p.rect(x + b, y + a, x + far, y + b);
            if (e.left) p.rect(x + near, y + a, x + a, y + b);
            if (e.down) p.rect(x + a, y + b, x + b, y + far);
            if (e.up) p.rect(x + a, y + near, x + b, y + a);

            // Likewise each filled diagonal gap is covered a quarter at a time.
            if (e.dr) p.rect(x + b, y + b, x + far, y + far);
            if (e.left && e.down && e.rawDL) p.rect(x + near, y + b, x + a, y + far);
            if (e.right && e.up && e.rawUR) p.rect(x + b, y + near, x + far, y + a);
            if (e.left && e.up && e.rawUL) p.rect(x + near, y + near, x + a, y + a);

            // Concave corners: fill the sharp inner angle so an L-shaped group is rounded inside too.
            if (e.cTL) innerFillet(p, x + b, y + b, 1, 1);
            if (e.cTR) innerFillet(p, x + n, y + b, -1, 1);
            if (e.cBL) innerFillet(p, x + b, y + n, 1, -1);
            if (e.cBRofTopLeftGap) innerFillet(p, x + a, y + a, -1, -1);
        }
    }

    /**
     * A concave corner's rounding: the mirror of {@link #fillRounded}'s stair, adding material into the
     * notch. Grows from {@code (px, py)} towards {@code (sx, sy)}.
     */
    private static void innerFillet(Painter p, int px, int py, int sx, int sy) {
        for (int d = 0; d < 2; d++) {
            int len = 2 - d; // 2px against the corner, then 1px
            int x0 = sx > 0 ? px : px - len;
            int x1 = sx > 0 ? px + len : px;
            int y0 = sy > 0 ? py + d : py - d - 1;
            p.rect(x0, y0, x1, y0 + 1);
        }
    }

    /**
     * The 1px rim hugging the blob. Drawn as an outline ONLY — painting an enlarged solid copy underneath
     * would have tinted the whole interior opaque, hiding the items in the slots.
     *
     * <p>Every straight edge is shortened by the 2px rounding of the corners it runs into, and each rounded
     * corner is then traced by its own two-pixel stair, so the rim follows the body exactly. Connectors
     * contribute their own outer edges, which is what keeps the outline unbroken across them.
     */
    private void drawBlobRim(
            GuiGraphics context,
            Set<Integer> groupSlots,
            int rimColor,
            int tintRim,
            List<int[]> tintSquares,
            int guiX,
            int guiY) {
        Painter p = (x0, y0, x1, y1) -> fillRegion(context, x0, y0, x1, y1, rimColor, tintRim, tintSquares);
        blobRimPass(p, groupSlots, guiX, guiY, editor::slotForKey, occupiedPositions(groupSlots));
    }

    /** The blob outline loop, shared verbatim by the live editor and any static caller (the preset preview). */
    private static void blobRimPass(
            Painter p,
            Set<Integer> groupSlots,
            int guiX,
            int guiY,
            java.util.function.IntFunction<Slot> keyToSlot,
            Set<Long> occupied) {
        final int a = CELL_INSET;
        final int b = CELL_FAR;
        final int n = NEXT_CELL;

        for (int slotIdx : groupSlots) {
            Slot slot = keyToSlot.apply(slotIdx);
            if (slot == null || !ChestSeparatorsEditor.isEditableSlot(slot)) continue;

            int x = guiX + slot.x;
            int y = guiY + slot.y;
            Edges e = edgesAt(occupied, slot);

            boolean tl = !e.up && !e.left;
            boolean tr = !e.up && !e.right;
            boolean bl = !e.down && !e.left;
            boolean br = !e.down && !e.right;

            // Straight sides, clipped where a rounded corner takes over.
            if (!e.up) p.rect(x + a + (tl ? 2 : 0), y + a - 1, x + b - (tr ? 2 : 0), y + a);
            if (!e.down) p.rect(x + a + (bl ? 2 : 0), y + b, x + b - (br ? 2 : 0), y + b + 1);
            if (!e.left) p.rect(x + a - 1, y + a + (tl ? 2 : 0), x + a, y + b - (bl ? 2 : 0));
            if (!e.right) p.rect(x + b, y + a + (tr ? 2 : 0), x + b + 1, y + b - (br ? 2 : 0));

            // Two-pixel stair on each rounded corner.
            if (tl) {
                p.rect(x + a + 1, y + a, x + a + 2, y + a + 1);
                p.rect(x + a, y + a + 1, x + a + 1, y + a + 2);
            }
            if (tr) {
                p.rect(x + b - 2, y + a, x + b - 1, y + a + 1);
                p.rect(x + b - 1, y + a + 1, x + b, y + a + 2);
            }
            if (bl) {
                p.rect(x + a + 1, y + b - 1, x + a + 2, y + b);
                p.rect(x + a, y + b - 2, x + a + 1, y + b - 1);
            }
            if (br) {
                p.rect(x + b - 2, y + b - 1, x + b - 1, y + b);
                p.rect(x + b - 1, y + b - 2, x + b, y + b - 1);
            }

            // Outer edges of the connectors. Without these the rim would break every time two slots join.
            // Each end is pulled back by 2px at a concave corner so the stair below takes over instead of
            // the rim slicing straight across the rounding.
            if (e.right && !(e.up && e.rawUR)) { // top edge of the horizontal connector
                int x0 = (e.up && !e.rawUR) ? x + b + 2 : x + b;
                int x1 = (e.rawUR && !e.up) ? x + n - 2 : x + n;
                if (x1 > x0) p.rect(x0, y + a - 1, x1, y + a);
            }
            if (e.right && !e.dr) { // bottom edge of the horizontal connector
                int x0 = e.cTL ? x + b + 2 : x + b;
                int x1 = e.cTR ? x + n - 2 : x + n;
                if (x1 > x0) p.rect(x0, y + b, x1, y + b + 1);
            }
            if (e.down && !(e.left && e.rawDL)) { // left edge of the vertical connector
                int y0 = (e.left && !e.rawDL) ? y + b + 3 : (e.left ? y + b + 1 : y + b);
                int y1 = (e.rawDL && !e.left) ? y + n - 2 : y + n;
                if (y1 > y0) p.rect(x + a - 1, y0, x + a, y1);
            }
            if (e.down && !e.dr) { // right edge of the vertical connector
                // Starts below the horizontal connector's rim when both exist, so the shared pixel is not
                // painted twice (a translucent rim would darken there).
                int y0 = e.cTL ? y + b + 3 : (e.right ? y + b + 1 : y + b);
                int y1 = e.cBL ? y + n - 2 : y + n;
                if (y1 > y0) p.rect(x + b, y0, x + b + 1, y1);
            }

            // Stairs tracing the concave corners, so the inner angles are outlined like the outer ones.
            if (e.cTL) innerFilletRim(p, x + b, y + b, 1, 1);
            if (e.cTR) innerFilletRim(p, x + n, y + b, -1, 1);
            if (e.cBL) innerFilletRim(p, x + b, y + n, 1, -1);
            if (e.cBRofTopLeftGap) innerFilletRim(p, x + a, y + a, -1, -1);
        }
    }

    /** Blends two colours channel by channel; {@code t} of 0 gives {@code from}, 1 gives {@code to}. */
    private static int lerpColor(int from, int to, float t) {
        int a = channelMix(from >>> 24, to >>> 24, t);
        int r = channelMix((from >> 16) & 0xFF, (to >> 16) & 0xFF, t);
        int g = channelMix((from >> 8) & 0xFF, (to >> 8) & 0xFF, t);
        int b = channelMix(from & 0xFF, to & 0xFF, t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int channelMix(int from, int to, float t) {
        return Math.max(0, Math.min(255, Math.round(from + (to - from) * t)));
    }

    /** Same colour with its RGB scaled, keeping the alpha — used for the darker rim tone. */
    private static int shade(int argb, float factor) {
        int a = (argb >>> 24) & 0xFF;
        int r = (int) (((argb >> 16) & 0xFF) * factor);
        int g = (int) (((argb >> 8) & 0xFF) * factor);
        int b = (int) ((argb & 0xFF) * factor);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /**
     * Rim colour for a group. It is a deliberately darker shade of the group's own colour rather than the
     * same tone at more opacity, so that outermost pixel reads as a defined edge instead of just a denser
     * bit of the fill. The hovered group swaps it for white.
     */
    private static int outlineFor(int fill, boolean dimmed, boolean highlighted) {
        if (highlighted) return 0xFFFFFFFF;
        int darker = shade(fill, 0.5f);
        return (darker & 0x00FFFFFF) | (dimmed ? 0x66000000 : 0xFF000000);
    }

    /** Convenience for the standalone selection blobs: body then rim, in one call. */
    private void drawBlobGroup(GuiGraphics context, Set<Integer> groupSlots, int colorARGB, int guiX, int guiY) {
        drawBlobFill(context, groupSlots, colorARGB, 0, null, guiX, guiY);
        drawBlobRim(context, groupSlots, outlineFor(colorARGB, false, false), 0, null, guiX, guiY);
    }

    /**
     * Draws one group's blob (body + rim) through the exact same passes the live editor uses, but resolving
     * slot keys with an external mapper. This is the single entry point other screens (the preset Load
     * preview) must use so a filter group looks pixel-identical everywhere in the mod.
     */
    static void drawBlobGroupStatic(
            GuiGraphics context,
            Set<Integer> groupSlots,
            int colorARGB,
            int guiX,
            int guiY,
            java.util.function.IntFunction<Slot> keyToSlot) {
        Set<Long> occupied = occupiedPositions(groupSlots, keyToSlot);
        blobFillPass(
                (x0, y0, x1, y1) -> context.fill(x0, y0, x1, y1, colorARGB),
                groupSlots,
                guiX,
                guiY,
                keyToSlot,
                occupied);
        int rimColor = outlineFor(colorARGB, false, false);
        blobRimPass(
                (x0, y0, x1, y1) -> context.fill(x0, y0, x1, y1, rimColor),
                groupSlots,
                guiX,
                guiY,
                keyToSlot,
                occupied);
    }

    private int getExplicitGroupColor(Set<Integer> groupSlots) {
        ChestConfigManager manager = ChestConfigManager.getInstance();
        Set<Integer> commonColors = null;

        for (int slotIdx : groupSlots) {
            Set<Integer> slotColors = new HashSet<>();

            int bg = manager.getColor(slotIdx, ChestConfigManager.ACTION_BG) & 0x00FFFFFF;
            if (bg != 0) slotColors.add(bg);
            int top = manager.getColor(slotIdx, ChestConfigManager.ACTION_TOP) & 0x00FFFFFF;
            if (top != 0) slotColors.add(top);
            int bottom = manager.getColor(slotIdx, ChestConfigManager.ACTION_BOTTOM) & 0x00FFFFFF;
            if (bottom != 0) slotColors.add(bottom);
            int left = manager.getColor(slotIdx, ChestConfigManager.ACTION_LEFT) & 0x00FFFFFF;
            if (left != 0) slotColors.add(left);
            int right = manager.getColor(slotIdx, ChestConfigManager.ACTION_RIGHT) & 0x00FFFFFF;
            if (right != 0) slotColors.add(right);

            // If a single slot has no colors, the group cannot have a unanimous color
            if (slotColors.isEmpty()) {
                return 0;
            }

            if (commonColors == null) {
                // Initialize with the colors of the first slot
                commonColors = slotColors;
            } else {
                // Intersection: keep only colors present in all evaluated slots
                commonColors.retainAll(slotColors);
            }

            // If at any point the intersection is empty, abort
            if (commonColors.isEmpty()) {
                return 0;
            }
        }

        // If we reach here, commonColors contains at least one unanimous color
        if (commonColors != null && !commonColors.isEmpty()) {
            return commonColors.iterator().next() | 0x99000000;
        }

        return 0;
    }
}
