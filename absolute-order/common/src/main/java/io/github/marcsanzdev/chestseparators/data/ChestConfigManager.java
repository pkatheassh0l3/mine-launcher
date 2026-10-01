package io.github.marcsanzdev.chestseparators.data;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.Tag;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChestConfigManager {

    private static final Logger LOGGER = LoggerFactory.getLogger("chestseparators");

    private static final String MOD_ID = "chestseparators";
    private static final String FOLDER_NAME = "separators";
    private static final String ENDER_FILE_NAME = "ender_chest.dat";
    private static final String PALETTE_FILE_NAME = "world_palette.dat";
    private static final String INVENTORY_FILE_NAME = "player_inventory.dat";

    /**
     * Current on-disk format version. Increment when making breaking changes to the NBT schema.
     * Version 1 = v1.3.x (4-element visual arrays, no version field).
     * Version 2 = 5-element visual arrays with an explicit "Version" field.
     * Version 3 = 9-element visual arrays adding per-edge paint-order sequence (indices 5-8).
     * Older saves are migrated transparently on read in {@link #readRawData}.
     */
    private static final int DATA_VERSION = 3;

    public static final int ACTION_TOP = 1;
    public static final int ACTION_BOTTOM = 2;
    public static final int ACTION_LEFT = 4;
    public static final int ACTION_RIGHT = 8;
    public static final int ACTION_BG = 16;

    private static final int IDX_TOP = 0;
    private static final int IDX_BOTTOM = 1;
    private static final int IDX_LEFT = 2;
    private static final int IDX_RIGHT = 3;
    private static final int IDX_BG = 4;

    // Paint-order sequence for each of the four edges, so corners are drawn newest-on-top: the edge
    // painted later wins where two lines overlap. Indices 5-8 parallel IDX_TOP..IDX_RIGHT.
    private static final int IDX_SEQ_TOP = 5;
    private static final int IDX_SEQ_BOTTOM = 6;
    private static final int IDX_SEQ_LEFT = 7;
    private static final int IDX_SEQ_RIGHT = 8;
    private static final int SLOT_ARRAY_LEN = 9;

    /** Monotonic counter assigning a paint order to each edge as it is painted. */
    private int paintSequence = 0;

    private final Map<Integer, int[]> currentChestConfig = new HashMap<>();
    private Map<Integer, int[]> clipboardConfig = null;
    private Map<Integer, int[]> linesClipboard = null;
    private Map<Integer, int[]> bgClipboard = null;
    private int[] worldCustomLineColors = new int[8];
    private int[] worldCustomBgColors = new int[8];
    private int[] worldCustomComboColors = new int[8];

    // The player inventory's own decorations and filters, kept in memory so they can be rendered on the
    // player-inventory slots in any screen. Keyed by PlayerInventory slot index (not screen-handler
    // slot index), so the same layout shows consistently in the inventory screen and inside chests.
    private Map<Integer, int[]> playerInventoryVisual = new HashMap<>();
    private Map<Integer, SlotWhitelist> playerInventoryFilters = new HashMap<>();

    private Map<Integer, SlotWhitelist> whitelistClipboard = null;

    private static final ChestConfigManager INSTANCE = new ChestConfigManager();

    public static ChestConfigManager getInstance() {
        return INSTANCE;
    }

    public void clearCurrentConfig() {
        currentChestConfig.clear();
        paintSequence = 0;
        clearHistory();
    }

    // --- UNIFIED UNDO / REDO SYSTEM ---
    //
    // A single stack records every change to a chest — painted separators AND item filters — so
    // Ctrl+Z always undoes the most recent action regardless of which sub-screen is open. Each entry
    // snapshots both the visual layout and the whitelists, plus a label key identifying what changed
    // (used to give the user feedback such as "Undone: filter").

    private static final int MAX_UNDO_STEPS = 50;

    /** Translation key describing a separator/painting change. */
    public static final String ACTION_LAYOUT = "message.chestseparators.action.layout";
    /** Translation key describing a filter/whitelist change. */
    public static final String ACTION_FILTER = "message.chestseparators.action.filter";

    /** An immutable snapshot of the whole chest state, tagged with what changed to reach it. */
    private record ChestState(Map<Integer, int[]> visual, Map<Integer, SlotWhitelist> whitelists, String labelKey) {}

    /** Per-slot classification of an undo/redo change, used to drive the on-slot blink feedback. */
    public enum SlotChange {
        LAYOUT,
        FILTER_CREATED,
        FILTER_REMOVED,
        FILTER_MODIFIED
    }

    private final java.util.Deque<ChestState> undoStack = new java.util.ArrayDeque<>();
    private final java.util.Deque<ChestState> redoStack = new java.util.ArrayDeque<>();

    /** Slots that changed in the most recent undo/redo, with what kind of change the user just saw. */
    private Map<Integer, SlotChange> lastUndoChanges = new HashMap<>();

    public Map<Integer, SlotChange> getLastUndoChanges() {
        return lastUndoChanges;
    }

    /** Classifies, per slot, the difference between the current state and the state being restored. */
    private Map<Integer, SlotChange> computeChanges(
            Map<Integer, int[]> oldVis,
            Map<Integer, SlotWhitelist> oldWl,
            Map<Integer, int[]> newVis,
            Map<Integer, SlotWhitelist> newWl) {
        Map<Integer, SlotChange> changes = new HashMap<>();
        Set<Integer> slots = new HashSet<>();
        slots.addAll(oldVis.keySet());
        slots.addAll(newVis.keySet());
        slots.addAll(oldWl.keySet());
        slots.addAll(newWl.keySet());
        for (int slot : slots) {
            SlotWhitelist owl = oldWl.get(slot);
            SlotWhitelist nwl = newWl.get(slot);
            if (!java.util.Objects.equals(owl, nwl)) {
                if (owl == null) changes.put(slot, SlotChange.FILTER_CREATED);
                else if (nwl == null) changes.put(slot, SlotChange.FILTER_REMOVED);
                else changes.put(slot, SlotChange.FILTER_MODIFIED);
            } else if (!sameColors(oldVis.get(slot), newVis.get(slot))) {
                changes.put(slot, SlotChange.LAYOUT);
            }
        }
        return changes;
    }

    /** Compares only the five color channels (0-4), ignoring the paint-order sequence (5-8). */
    private static boolean sameColors(int[] a, int[] b) {
        for (int i = 0; i <= IDX_BG; i++) {
            int av = (a != null && i < a.length) ? a[i] : 0;
            int bv = (b != null && i < b.length) ? b[i] : 0;
            if (av != bv) return false;
        }
        return true;
    }

    private static Map<Integer, int[]> copyVisualConfig(Map<Integer, int[]> source) {
        Map<Integer, int[]> copy = new HashMap<>();
        for (Map.Entry<Integer, int[]> entry : source.entrySet()) {
            copy.put(entry.getKey(), entry.getValue().clone());
        }
        return copy;
    }

    private static Map<Integer, SlotWhitelist> copyWhitelists(Map<Integer, SlotWhitelist> source) {
        Map<Integer, SlotWhitelist> copy = new HashMap<>();
        for (Map.Entry<Integer, SlotWhitelist> entry : source.entrySet()) {
            SlotWhitelist orig = entry.getValue();
            copy.put(
                    entry.getKey(),
                    new SlotWhitelist(
                            orig.groupId(),
                            new ArrayList<>(orig.allowedItems()),
                            orig.allowManual(),
                            orig.allowShift(),
                            orig.allowHopper()));
        }
        return copy;
    }

    private ChestState snapshotCurrent(String labelKey) {
        return new ChestState(copyVisualConfig(currentChestConfig), copyWhitelists(currentWhitelists), labelKey);
    }

    /** Records the current state on the undo stack before a change of the given kind is applied. */
    private void pushHistory(String labelKey) {
        undoStack.push(snapshotCurrent(labelKey));
        while (undoStack.size() > MAX_UNDO_STEPS) undoStack.removeLast();
        redoStack.clear();
    }

    public void saveSnapshot() {
        pushHistory(ACTION_LAYOUT);
    }

    public void saveWhitelistSnapshot() {
        pushHistory(ACTION_FILTER);
    }

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    /**
     * Undoes the most recent change. Returns the label key of the undone action (for user feedback),
     * or {@code null} if there was nothing to undo.
     */
    public String undo() {
        if (undoStack.isEmpty()) return null;
        ChestState previous = undoStack.pop();
        redoStack.push(snapshotCurrent(previous.labelKey()));
        lastUndoChanges =
                computeChanges(currentChestConfig, currentWhitelists, previous.visual(), previous.whitelists());
        applyVisualConfig(previous.visual());
        applyWhitelists(previous.whitelists());
        return previous.labelKey();
    }

    /**
     * Redoes the most recently undone change. Returns the label key of the redone action, or
     * {@code null} if there was nothing to redo.
     */
    public String redo() {
        if (redoStack.isEmpty()) return null;
        ChestState next = redoStack.pop();
        undoStack.push(snapshotCurrent(next.labelKey()));
        lastUndoChanges = computeChanges(currentChestConfig, currentWhitelists, next.visual(), next.whitelists());
        applyVisualConfig(next.visual());
        applyWhitelists(next.whitelists());
        return next.labelKey();
    }

    // Filter-screen aliases kept for the View Groups buttons; the history is unified, so these simply
    // delegate to the shared undo/redo.
    public boolean canUndoWhitelist() {
        return canUndo();
    }

    public boolean canRedoWhitelist() {
        return canRedo();
    }

    public String undoWhitelist() {
        return undo();
    }

    public String redoWhitelist() {
        return redo();
    }

    private void applyVisualConfig(Map<Integer, int[]> snapshot) {
        currentChestConfig.clear();
        currentChestConfig.putAll(copyVisualConfig(snapshot));
    }

    private void applyWhitelists(Map<Integer, SlotWhitelist> snapshot) {
        currentWhitelists.clear();
        currentWhitelists.putAll(copyWhitelists(snapshot));
    }

    public void clearHistory() {
        undoStack.clear();
        redoStack.clear();
    }

    // --- PATH MANAGEMENT (NIO) ---

    private String getWorldFolderName() {
        Minecraft client = Minecraft.getInstance();
        String name = "unknown_world";

        if (client.hasSingleplayerServer() && client.getSingleplayerServer() != null) {
            // WorldSavePath.ROOT resolves to "<saveDir>/." so calling getFileName() on it yields ".", NOT
            // the world folder — which made EVERY singleplayer world collapse to the same key ("sp__") and
            // therefore share its separators AND inventory profile. Normalize away the trailing "." so we
            // recover the real, per-world save folder name.
            Path saveRoot = client.getSingleplayerServer()
                    .getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                    .toAbsolutePath()
                    .normalize();
            Path saveFolder = saveRoot.getFileName();
            String folderName = saveFolder != null ? saveFolder.toString() : "";
            if (folderName.isEmpty()) {
                // Extremely defensive fallback (e.g. root path); the level name is still per-world enough.
                folderName = client.getSingleplayerServer().getWorldData().getLevelName();
            }
            name = "sp_" + folderName;
        } else if (client.getCurrentServer() != null) {
            name = "mp_" + client.getCurrentServer().ip;
        }

        return name.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }

    private Path getWorldConfigDir() {
        Path runDir = Minecraft.getInstance().gameDirectory.toPath();
        Path baseModDir = runDir.resolve("config/" + MOD_ID);
        Path worldDir = baseModDir.resolve(getWorldFolderName());

        try {
            if (!Files.exists(worldDir)) {
                Files.createDirectories(worldDir);
            }
        } catch (IOException e) {
            LOGGER.error("chestseparators: I/O error", e);
        }
        return worldDir;
    }

    private Path getSeparatorsDir() {
        Path sepDir = getWorldConfigDir().resolve(FOLDER_NAME);
        try {
            if (!Files.exists(sepDir)) {
                Files.createDirectories(sepDir);
            }
        } catch (IOException e) {
            LOGGER.error("chestseparators: I/O error", e);
        }
        return sepDir;
    }

    private Path getFileForPos(BlockPos pos, String dimensionId) {
        if (pos == null) return null;
        Path dir = getSeparatorsDir();
        String safeDim = dimensionId.replace(":", "_");
        String fileName = String.format("%s_%d_%d_%d.dat", safeDim, pos.getX(), pos.getY(), pos.getZ());
        return dir.resolve(fileName);
    }

    private Path getEnderChestFile() {
        return getWorldConfigDir().resolve(ENDER_FILE_NAME);
    }

    private Path getFileForEntity(UUID uuid) {
        if (uuid == null) return null;
        Path dir = getSeparatorsDir();
        return dir.resolve("entity_" + uuid.toString() + ".dat");
    }

    private Path getFileForShulker(UUID uuid) {
        if (uuid == null) return null;
        Path dir = getSeparatorsDir();
        return dir.resolve("shulker_" + uuid.toString() + ".dat");
    }

    // --- CHEST LOGIC ---

    private int[] getSlotColors(int slotIndex) {
        int[] colors = currentChestConfig.get(slotIndex);
        if (colors == null) {
            colors = new int[SLOT_ARRAY_LEN];
            currentChestConfig.put(slotIndex, colors);
        } else if (colors.length < SLOT_ARRAY_LEN) {
            // Grow legacy arrays (4 or 5 elements) so paint-order indices are available.
            colors = java.util.Arrays.copyOf(colors, SLOT_ARRAY_LEN);
            currentChestConfig.put(slotIndex, colors);
        }
        return colors;
    }

    // Shared edge/bg colour lookup: null array (absent slot) reads as 0, matching both callers' guards.
    private static int colorForFlag(int[] colors, int actionFlag) {
        if (colors == null) return 0;
        if (actionFlag == ACTION_TOP) return colors[IDX_TOP];
        if (actionFlag == ACTION_BOTTOM) return colors[IDX_BOTTOM];
        if (actionFlag == ACTION_LEFT) return colors[IDX_LEFT];
        if (actionFlag == ACTION_RIGHT) return colors[IDX_RIGHT];
        if (actionFlag == ACTION_BG) return colors[IDX_BG];
        return 0;
    }

    public int getColor(int slotIndex, int actionFlag) {
        return colorForFlag(currentChestConfig.get(slotIndex), actionFlag);
    }

    /** Like {@link #getColor}, but reads the player inventory render cache (for the all-screens overlay). */
    public int getInventoryColor(int slotIndex, int actionFlag) {
        return colorForFlag(playerInventoryVisual.get(slotIndex), actionFlag);
    }

    // Shared paint-order lookup: null array reads as 0, legacy arrays without a seq slot read as 0.
    private static int seqForFlag(int[] colors, int actionFlag) {
        if (colors == null) return 0;
        int idx;
        if (actionFlag == ACTION_TOP) idx = IDX_SEQ_TOP;
        else if (actionFlag == ACTION_BOTTOM) idx = IDX_SEQ_BOTTOM;
        else if (actionFlag == ACTION_LEFT) idx = IDX_SEQ_LEFT;
        else if (actionFlag == ACTION_RIGHT) idx = IDX_SEQ_RIGHT;
        else return 0;
        return idx < colors.length ? colors[idx] : 0;
    }

    /** Like {@link #getPaintSeq}, but reads the player inventory render cache. */
    public int getInventoryPaintSeq(int slotIndex, int actionFlag) {
        return seqForFlag(playerInventoryVisual.get(slotIndex), actionFlag);
    }

    /**
     * Returns the paint-order sequence of an edge (higher = painted later, drawn on top). Returns 0
     * for backgrounds, unpainted edges, or legacy arrays without sequence data.
     */
    public int getPaintSeq(int slotIndex, int actionFlag) {
        return seqForFlag(currentChestConfig.get(slotIndex), actionFlag);
    }

    public void paintAction(int slotIndex, int actionFlags, int argbColor) {
        int[] colors = getSlotColors(slotIndex);
        if ((actionFlags & ACTION_TOP) != 0) {
            colors[IDX_TOP] = argbColor;
            colors[IDX_SEQ_TOP] = ++paintSequence;
        }
        if ((actionFlags & ACTION_BOTTOM) != 0) {
            colors[IDX_BOTTOM] = argbColor;
            colors[IDX_SEQ_BOTTOM] = ++paintSequence;
        }
        if ((actionFlags & ACTION_LEFT) != 0) {
            colors[IDX_LEFT] = argbColor;
            colors[IDX_SEQ_LEFT] = ++paintSequence;
        }
        if ((actionFlags & ACTION_RIGHT) != 0) {
            colors[IDX_RIGHT] = argbColor;
            colors[IDX_SEQ_RIGHT] = ++paintSequence;
        }
        if ((actionFlags & ACTION_BG) != 0) colors[IDX_BG] = argbColor;
    }

    /** Raises the paint counter above any sequence currently stored, e.g. after pasting another
     * chest's layout, so subsequently painted edges still win in corners. */
    private void bumpPaintSequenceToMax() {
        for (int[] colors : currentChestConfig.values()) {
            for (int si = IDX_SEQ_TOP; si < SLOT_ARRAY_LEN && si <= IDX_SEQ_RIGHT; si++) {
                if (si < colors.length && colors[si] > paintSequence) paintSequence = colors[si];
            }
        }
    }

    /** Raises paintSequence above any stored sequence in the given colour arrays (loaded profiles/presets). */
    private void bumpPaintSequence(java.util.Collection<int[]> arrays) {
        for (int[] colors : arrays) {
            for (int si = IDX_SEQ_TOP; si <= IDX_SEQ_RIGHT && si < colors.length; si++) {
                if (colors[si] > paintSequence) paintSequence = colors[si];
            }
        }
    }

    public void removeAction(int slotIndex, int actionFlags) {
        if (!currentChestConfig.containsKey(slotIndex)) return;
        int[] colors = currentChestConfig.get(slotIndex);
        if ((actionFlags & ACTION_TOP) != 0) colors[IDX_TOP] = 0;
        if ((actionFlags & ACTION_BOTTOM) != 0) colors[IDX_BOTTOM] = 0;
        if ((actionFlags & ACTION_LEFT) != 0) colors[IDX_LEFT] = 0;
        if ((actionFlags & ACTION_RIGHT) != 0) colors[IDX_RIGHT] = 0;
        if ((actionFlags & ACTION_BG) != 0) colors[IDX_BG] = 0;

        if (isBlank(colors)) {
            currentChestConfig.remove(slotIndex);
        }
    }

    /** True when a slot's first five channels (4 edges + bg) are all clear, i.e. the slot is empty. */
    private static boolean isBlank(int[] c) {
        return c[0] == 0 && c[1] == 0 && c[2] == 0 && c[3] == 0 && c[4] == 0;
    }

    public void clearAllBackgrounds() {
        for (Map.Entry<Integer, int[]> entry : currentChestConfig.entrySet()) entry.getValue()[IDX_BG] = 0;
        currentChestConfig.entrySet().removeIf(entry -> isBlank(entry.getValue()));
    }

    public void clearAllLines() {
        for (Map.Entry<Integer, int[]> entry : currentChestConfig.entrySet()) {
            entry.getValue()[IDX_TOP] = 0;
            entry.getValue()[IDX_BOTTOM] = 0;
            entry.getValue()[IDX_LEFT] = 0;
            entry.getValue()[IDX_RIGHT] = 0;
        }
        currentChestConfig.entrySet().removeIf(entry -> isBlank(entry.getValue()));
    }

    public void copyToClipboard() {
        this.clipboardConfig = copyVisualConfig(this.currentChestConfig);
    }

    public void pasteFromClipboard() {
        if (this.clipboardConfig != null && !this.clipboardConfig.isEmpty()) {
            applyVisualConfig(this.clipboardConfig);
            bumpPaintSequenceToMax();
        }
    }

    public boolean hasClipboardData() {
        return this.clipboardConfig != null && !this.clipboardConfig.isEmpty();
    }

    public void copyLinesToClipboard() {
        this.linesClipboard = new HashMap<>();
        for (Map.Entry<Integer, int[]> entry : this.currentChestConfig.entrySet()) {
            int[] c = entry.getValue();
            if (c[0] != 0 || c[1] != 0 || c[2] != 0 || c[3] != 0) {
                this.linesClipboard.put(entry.getKey(), new int[] {c[0], c[1], c[2], c[3], 0});
            }
        }
    }

    public void pasteLinesFromClipboard() {
        if (this.linesClipboard != null && !this.linesClipboard.isEmpty()) {
            for (Map.Entry<Integer, int[]> entry : this.linesClipboard.entrySet()) {
                int[] current = getSlotColors(entry.getKey());
                int[] clip = entry.getValue();
                current[0] = clip[0];
                current[1] = clip[1];
                current[2] = clip[2];
                current[3] = clip[3];
            }
        }
    }

    public boolean hasLinesClipboardData() {
        return this.linesClipboard != null && !this.linesClipboard.isEmpty();
    }

    public void copyBackgroundsToClipboard() {
        this.bgClipboard = new HashMap<>();
        for (Map.Entry<Integer, int[]> entry : this.currentChestConfig.entrySet()) {
            if (entry.getValue()[4] != 0) {
                this.bgClipboard.put(entry.getKey(), new int[] {0, 0, 0, 0, entry.getValue()[4]});
            }
        }
    }

    public void pasteBackgroundsFromClipboard() {
        if (this.bgClipboard != null && !this.bgClipboard.isEmpty()) {
            for (Map.Entry<Integer, int[]> entry : this.bgClipboard.entrySet()) {
                getSlotColors(entry.getKey())[4] = entry.getValue()[4];
            }
        }
    }

    public boolean hasBackgroundsClipboardData() {
        return this.bgClipboard != null && !this.bgClipboard.isEmpty();
    }

    public void copyAllToClipboard() {
        copyToClipboard();
        copyLinesToClipboard();
        copyBackgroundsToClipboard();
    }

    // --- PUBLIC IO OPERATIONS ---

    public void clearChest(BlockPos pos, String dimensionId) {
        Path path = getFileForPos(pos, dimensionId);
        if (path != null) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException e) {
                LOGGER.error("chestseparators: I/O error", e);
            }
        }
    }

    public void loadConfig(BlockPos pos, String dimensionId) {
        clearCurrentConfig();
        // Whitelist state is authoritative on the server; do not clear it here.

        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;

        BlockState state = client.level.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock) {
            ChestType type = state.getValue(ChestBlock.TYPE);
            if (type != ChestType.SINGLE) {
                Direction facing = state.getValue(ChestBlock.FACING);
                Direction neighborDir =
                        type == ChestType.LEFT ? facing.getClockWise() : facing.getCounterClockWise();
                BlockPos neighborPos = pos.relative(neighborDir);

                // Key the layout by ChestType, the same way the container (and thus filters, via DoubleInventoryMixin)
                // is combined: vanilla puts the RIGHT chest first (GUI slots 0-26), LEFT second (27-53). Keying by
                // min/max BlockPos instead disagreed with that order, so breaking + replacing one half (which can flip
                // the block's LEFT/RIGHT role while its position stays) moved the layout to the wrong half while the
                // filters stayed. Same key keeps layout and filters on the same half.
                BlockPos primaryPos = type == ChestType.RIGHT ? pos : neighborPos;
                BlockPos secondaryPos = type == ChestType.RIGHT ? neighborPos : pos;

                RawData firstData = readRawData(getFileForPos(primaryPos, dimensionId));
                currentChestConfig.putAll(firstData.visual);

                RawData secondData = readRawData(getFileForPos(secondaryPos, dimensionId));
                for (Map.Entry<Integer, int[]> entry : secondData.visual.entrySet()) {
                    currentChestConfig.put(entry.getKey() + 27, entry.getValue());
                }
                return;
            }
        }
        RawData rawData = readRawData(getFileForPos(pos, dimensionId));
        currentChestConfig.putAll(rawData.visual);
    }

    /**
     * Reads the locally-stored Ender Chest whitelist without disturbing the currently-open container's
     * state. Used to forward the filter to the server for radius auto-deposit (Ender filters are
     * client-side only). Returns an empty map if none is configured.
     */
    public Map<Integer, SlotWhitelist> readEnderWhitelists() {
        return new HashMap<>(readRawData(getEnderChestFile()).filters);
    }

    /**
     * Reads the locally-stored whitelist of a mobile container entity (chest minecart / boat) by its
     * UUID, without disturbing the open container's state. Entity filters are client-side only and are
     * forwarded to the server for radius auto-deposit. Returns an empty map if none is configured.
     */
    public Map<Integer, SlotWhitelist> readEntityWhitelists(UUID uuid) {
        return new HashMap<>(readRawData(getFileForEntity(uuid)).filters);
    }

    public void loadEnderConfig() {
        clearCurrentConfig();
        // Ender Chest whitelists are local-only; always load them from the local .dat file.
        currentWhitelists.clear();
        RawData data = readRawData(getEnderChestFile());
        currentChestConfig.putAll(data.visual);
        currentWhitelists.putAll(data.filters);
    }

    public void loadEntityConfig(UUID uuid) {
        clearCurrentConfig();
        currentWhitelists.clear();
        RawData data = readRawData(getFileForEntity(uuid));
        currentChestConfig.putAll(data.visual);
        currentWhitelists.putAll(data.filters);
    }

    public void loadShulkerConfig(UUID uuid) {
        clearCurrentConfig();
        // Preserve the server-authoritative whitelist; only update visual config from local storage.
        RawData data = readRawData(getFileForShulker(uuid));
        currentChestConfig.putAll(data.visual);
    }

    public void saveConfig(BlockPos pos, String dimensionId) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;

        // Only the chest's own slots; mirrored inventory entries (offset keys) are saved separately.
        Map<Integer, int[]> chestVis = chestOnlyVisual();
        Map<Integer, SlotWhitelist> chestFil = chestOnlyWhitelists();

        BlockState state = client.level.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock) {
            ChestType type = state.getValue(ChestBlock.TYPE);
            if (type != ChestType.SINGLE) {
                Direction facing = state.getValue(ChestBlock.FACING);
                Direction neighborDir =
                        type == ChestType.LEFT ? facing.getClockWise() : facing.getCounterClockWise();
                BlockPos neighborPos = pos.relative(neighborDir);

                // Key the layout by ChestType, the same way the container (and thus filters, via DoubleInventoryMixin)
                // is combined: vanilla puts the RIGHT chest first (GUI slots 0-26), LEFT second (27-53). Keying by
                // min/max BlockPos instead disagreed with that order, so breaking + replacing one half (which can flip
                // the block's LEFT/RIGHT role while its position stays) moved the layout to the wrong half while the
                // filters stayed. Same key keeps layout and filters on the same half.
                BlockPos primaryPos = type == ChestType.RIGHT ? pos : neighborPos;
                BlockPos secondaryPos = type == ChestType.RIGHT ? neighborPos : pos;

                Map<Integer, int[]> firstVis = new HashMap<>();
                Map<Integer, int[]> secondVis = new HashMap<>();
                Map<Integer, SlotWhitelist> firstFil = new HashMap<>();
                Map<Integer, SlotWhitelist> secondFil = new HashMap<>();

                for (Map.Entry<Integer, int[]> entry : chestVis.entrySet()) {
                    if (entry.getKey() < 27) firstVis.put(entry.getKey(), entry.getValue());
                    else secondVis.put(entry.getKey() - 27, entry.getValue());
                }
                for (Map.Entry<Integer, SlotWhitelist> entry : chestFil.entrySet()) {
                    if (entry.getKey() < 27) firstFil.put(entry.getKey(), entry.getValue());
                    else secondFil.put(entry.getKey() - 27, entry.getValue());
                }

                writeRawData(firstVis, firstFil, getFileForPos(primaryPos, dimensionId));
                writeRawData(secondVis, secondFil, getFileForPos(secondaryPos, dimensionId));
                return;
            }
        }
        writeRawData(chestVis, chestFil, getFileForPos(pos, dimensionId));
    }

    public void saveEnderConfig() {
        writeRawData(chestOnlyVisual(), chestOnlyWhitelists(), getEnderChestFile());
    }

    public void saveEntityConfig(UUID uuid) {
        writeRawData(chestOnlyVisual(), chestOnlyWhitelists(), getFileForEntity(uuid));
    }

    public void saveShulkerConfig(UUID uuid) {
        writeRawData(chestOnlyVisual(), chestOnlyWhitelists(), getFileForShulker(uuid));
    }

    private static class RawData {
        Map<Integer, int[]> visual = new HashMap<>();
        Map<Integer, SlotWhitelist> filters = new HashMap<>();
        // Only presets carry a display name; world configs leave this null.
        String name = null;
    }

    private RawData readRawData(Path path) {
        RawData data = new RawData();
        if (path == null || !Files.exists(path)) return data;
        try {
            CompoundTag root = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());

            if (root.contains("Name")) data.name = root.getString("Name");

            if (root.contains("Separators")) {
                CompoundTag separatorsTag = root.getCompound("Separators");
                for (String key : separatorsTag.getAllKeys()) {
                    try {
                        int slot = Integer.parseInt(key);
                        // Normalize every legacy length to the current 9-element layout:
                        // [TOP,BOTTOM,LEFT,RIGHT,BG, seqTOP,seqBOTTOM,seqLEFT,seqRIGHT].
                        int[] arr = separatorsTag.getIntArray(key);
                        int[] full;
                        if (arr.length == 4) {
                            full = new int[] {arr[0], arr[1], arr[2], arr[3], 0, 0, 0, 0, 0};
                        } else if (arr.length >= 5) {
                            full = java.util.Arrays.copyOf(arr, SLOT_ARRAY_LEN);
                        } else {
                            continue; // malformed
                        }
                        data.visual.put(slot, full);
                        // Keep the paint counter ahead of any loaded sequence so new paints
                        // stay on top in corners.
                        for (int si = IDX_SEQ_TOP; si <= IDX_SEQ_RIGHT; si++) {
                            if (full[si] > paintSequence) paintSequence = full[si];
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }

            if (root.contains("Whitelists")) {
                CompoundTag wlRoot = root.getCompound("Whitelists");
                for (String key : wlRoot.getAllKeys()) {
                    try {
                        int slot = Integer.parseInt(key);
                        CompoundTag wlTag = wlRoot.getCompound(key);

                        UUID groupId;
                        String gid = wlTag.getString("GroupId");
                        if (gid.isEmpty()) {
                            groupId = UUID.randomUUID();
                        } else {
                            try {
                                groupId = UUID.fromString(gid);
                            } catch (Exception e) {
                                groupId = UUID.randomUUID();
                            }
                        }

                        java.util.List<String> items = new ArrayList<>();
                        ListTag list = wlTag.getList("AllowedItems", Tag.TAG_STRING);
                        for (int i = 0; i < list.size(); i++) {
                            items.add(list.getString(i));
                        }

                        // Default to true when the flag is absent (files written before the flag existed).
                        boolean manual = !wlTag.contains("AllowManual") || wlTag.getBoolean("AllowManual");
                        boolean shift = !wlTag.contains("AllowShift") || wlTag.getBoolean("AllowShift");
                        boolean hopper = !wlTag.contains("AllowHopper") || wlTag.getBoolean("AllowHopper");
                        // Absent target counts load as 0 (getInt returns 0 when the key is missing).
                        int target = wlTag.getInt("TargetCount");

                        data.filters.put(
                                slot, new SlotWhitelist(groupId, items, manual, shift, hopper, target));
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.error("chestseparators: I/O error", e);
        }
        return data;
    }

    private void writeRawData(Map<Integer, int[]> visualConfig, Map<Integer, SlotWhitelist> filters, Path path) {
        writeRawData(visualConfig, filters, null, path);
    }

    private void writeRawData(
            Map<Integer, int[]> visualConfig, Map<Integer, SlotWhitelist> filters, String name, Path path) {
        if (path == null) return;
        if (visualConfig.isEmpty() && (filters == null || filters.isEmpty())) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {
            }
            return;
        }

        CompoundTag root = new CompoundTag();
        root.putInt("Version", DATA_VERSION);
        if (name != null && !name.isEmpty()) root.putString("Name", name);

        if (!visualConfig.isEmpty()) {
            CompoundTag separatorsTag = new CompoundTag();
            for (Map.Entry<Integer, int[]> entry : visualConfig.entrySet()) {
                separatorsTag.putIntArray(String.valueOf(entry.getKey()), entry.getValue());
            }
            root.put("Separators", separatorsTag);
        }

        if (filters != null && !filters.isEmpty()) {
            CompoundTag wlRoot = new CompoundTag();
            for (Map.Entry<Integer, SlotWhitelist> entry : filters.entrySet()) {
                CompoundTag wlTag = new CompoundTag();
                SlotWhitelist wl = entry.getValue();

                // Save UUID as String to avoid missing method errors
                wlTag.putString("GroupId", wl.groupId().toString());

                net.minecraft.nbt.ListTag itemsList = new net.minecraft.nbt.ListTag();
                for (String item : wl.allowedItems()) {
                    itemsList.add(net.minecraft.nbt.StringTag.valueOf(item));
                }
                wlTag.put("AllowedItems", itemsList);
                wlTag.putBoolean("AllowManual", wl.allowManual());
                wlTag.putBoolean("AllowShift", wl.allowShift());
                wlTag.putBoolean("AllowHopper", wl.allowHopper());
                wlTag.putInt("TargetCount", wl.targetCount());

                wlRoot.put(String.valueOf(entry.getKey()), wlTag);
            }
            root.put("Whitelists", wlRoot);
        }

        try {
            NbtIo.writeCompressed(root, path);
        } catch (IOException e) {
            LOGGER.error("chestseparators: I/O error", e);
        }
    }

    // --- WHITELIST DATA ---
    private Map<Integer, io.github.marcsanzdev.chestseparators.data.SlotWhitelist> currentWhitelists = new HashMap<>();

    public Map<Integer, io.github.marcsanzdev.chestseparators.data.SlotWhitelist> getCurrentWhitelists() {
        return currentWhitelists;
    }

    public void setCurrentWhitelists(
            Map<Integer, io.github.marcsanzdev.chestseparators.data.SlotWhitelist> whitelists) {
        this.currentWhitelists = new HashMap<>(whitelists);
    }

    // --- FLOOD FILL ALGORITHM (VISUAL SELECTION) ---

    /**
     * Flood-fills the contiguous region of slots reachable from {@code startSlot} without crossing a
     * separator, confined to the key window {@code [lo, hi)} (one namespace: the container starts at 0,
     * the player inventory at {@link #PLAYER_KEY_OFFSET}). {@code lo} must be a multiple of 9.
     */
    public Set<Integer> getContiguousSlots(int startSlot, int lo, int hi) {
        Set<Integer> result = new HashSet<>();
        if (startSlot < lo || startSlot >= hi) return result;

        int rowCount = (hi - lo + 8) / 9;
        java.util.Queue<Integer> queue = new java.util.LinkedList<>();
        queue.add(startSlot);
        result.add(startSlot);

        while (!queue.isEmpty()) {
            int current = queue.poll();
            int rel = current - lo;
            int row = rel / 9;
            int col = rel % 9;

            int up = current - 9;
            if (row > 0 && up >= lo && !result.contains(up)) {
                if (getColor(current, ACTION_TOP) == 0 && getColor(up, ACTION_BOTTOM) == 0) {
                    result.add(up);
                    queue.add(up);
                }
            }

            int down = current + 9;
            if (row < rowCount - 1 && down < hi && !result.contains(down)) {
                if (getColor(current, ACTION_BOTTOM) == 0 && getColor(down, ACTION_TOP) == 0) {
                    result.add(down);
                    queue.add(down);
                }
            }

            int left = current - 1;
            if (col > 0 && left >= lo && !result.contains(left)) {
                if (getColor(current, ACTION_LEFT) == 0 && getColor(left, ACTION_RIGHT) == 0) {
                    result.add(left);
                    queue.add(left);
                }
            }

            int right = current + 1;
            if (col < 8 && right < hi && !result.contains(right)) {
                if (getColor(current, ACTION_RIGHT) == 0 && getColor(right, ACTION_LEFT) == 0) {
                    result.add(right);
                    queue.add(right);
                }
            }
        }
        return result;
    }

    public int[] getCustomColors(int tabMode) {
        // In shared mode all tabs read from the single Lines palette.
        if (io.github.marcsanzdev.chestseparators.config.GlobalChestConfig.instance.sharedCustomColors)
            return worldCustomLineColors;
        if (tabMode == 1) return worldCustomBgColors;
        if (tabMode == 2) return worldCustomComboColors;
        return worldCustomLineColors;
    }

    public void setCustomColor(int index, int color, int tabMode) {
        if (index >= 0 && index < 8) {
            // In shared mode all tabs write to the single Lines palette.
            if (io.github.marcsanzdev.chestseparators.config.GlobalChestConfig.instance.sharedCustomColors)
                worldCustomLineColors[index] = color;
            else if (tabMode == 1) worldCustomBgColors[index] = color;
            else if (tabMode == 2) worldCustomComboColors[index] = color;
            else worldCustomLineColors[index] = color;
        }
    }

    public void loadWorldPalette() {
        worldCustomLineColors = new int[8];
        worldCustomBgColors = new int[8];
        worldCustomComboColors = new int[8];
        Path path = getWorldConfigDir().resolve(PALETTE_FILE_NAME);

        if (!Files.exists(path)) return;

        try {
            CompoundTag root = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());

            if (root.contains("PaletteLines")) {
                int[] loaded = root.getIntArray("PaletteLines");
                System.arraycopy(loaded, 0, worldCustomLineColors, 0, Math.min(loaded.length, 8));
            }
            if (root.contains("PaletteBg")) {
                int[] loaded = root.getIntArray("PaletteBg");
                System.arraycopy(loaded, 0, worldCustomBgColors, 0, Math.min(loaded.length, 8));
            }
            if (root.contains("PaletteCombo")) {
                int[] loaded = root.getIntArray("PaletteCombo");
                System.arraycopy(loaded, 0, worldCustomComboColors, 0, Math.min(loaded.length, 8));
            }
        } catch (IOException e) {
            LOGGER.error("chestseparators: I/O error", e);
        }
    }

    public void saveWorldPalette() {
        Path path = getWorldConfigDir().resolve(PALETTE_FILE_NAME);
        CompoundTag root = new CompoundTag();
        root.putIntArray("PaletteLines", worldCustomLineColors);
        root.putIntArray("PaletteBg", worldCustomBgColors);
        root.putIntArray("PaletteCombo", worldCustomComboColors);
        try {
            NbtIo.writeCompressed(root, path);
        } catch (IOException e) {
            LOGGER.error("chestseparators: I/O error", e);
        }
    }

    // --- PLAYER INVENTORY PROFILE (global by default, per-world when configured) ---

    /** Cross-world config dir ({@code config/chestseparators}), used for the global inventory profile. */
    private Path getGlobalConfigDir() {
        Path dir = Minecraft.getInstance().gameDirectory.toPath().resolve("config/" + MOD_ID);
        try {
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
        } catch (IOException e) {
            LOGGER.error("chestseparators: I/O error", e);
        }
        return dir;
    }

    private Path getInventoryFile() {
        // The active inventory decorators + filters are ALWAYS per-world (and, being client-side, per
        // account): they must not bleed across worlds/servers. Reusable inventory PRESETS stay global
        // (see getInventoryPresetFile) so they can be applied to any world as templates.
        return getWorldConfigDir().resolve(INVENTORY_FILE_NAME);
    }

    public Map<Integer, int[]> getPlayerInventoryVisual() {
        return playerInventoryVisual;
    }

    public Map<Integer, SlotWhitelist> getPlayerInventoryFilters() {
        return playerInventoryFilters;
    }

    // --- PRESET NAMES ---
    // Preset display names are stored inside each preset file (the "Name" tag) but cached here, keyed by
    // file path, so the menu does not decompress a file per row every frame. A cached empty string means
    // "saved but unnamed"; a missing key means "not read yet". Mutations refresh the entry.
    private final Map<String, String> presetNameCache = new HashMap<>();

    /** Reads just the "Name" tag of a preset file, or null when absent/unreadable. */
    private String readPresetName(Path path) {
        if (path == null || !Files.exists(path)) return null;
        try {
            CompoundTag root = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
            return root.contains("Name") ? root.getString("Name") : null;
        } catch (IOException e) {
            return null;
        }
    }

    /** Custom display name of the preset in this file, or null when empty/unnamed. Cached by path. */
    private String getPresetNameForFile(Path path) {
        String key = path.toString();
        String cached = presetNameCache.get(key);
        if (cached != null) return cached.isEmpty() ? null : cached;
        String name = readPresetName(path);
        presetNameCache.put(key, name == null ? "" : name);
        return name;
    }

    /** Renames a preset in place (rewrites its file keeping the layout and filters). No-op if empty. */
    private void setPresetNameForFile(Path path, String name) {
        if (!Files.exists(path)) return;
        RawData data = readRawData(path);
        String trimmed = name == null ? "" : name.trim();
        writeRawData(data.visual, data.filters, trimmed, path);
        presetNameCache.put(path.toString(), trimmed);
    }

    private void invalidatePresetNameForFile(Path path) {
        presetNameCache.remove(path.toString());
    }

    // Typed convenience wrappers. Chest presets are split by the open container's size (27 vs 54), so a
    // single chest and a double chest each have their own independent set — the menu shows only the set
    // that matches whatever is open.
    public String getInventoryPresetName(int index) {
        return getPresetNameForFile(getInventoryPresetFile(index));
    }

    public void setInventoryPresetName(int index, String name) {
        setPresetNameForFile(getInventoryPresetFile(index), name);
    }

    public String getChestPresetName(int size, int index) {
        String custom = getPresetNameForFile(getChestPresetFile(size, index));
        var entry = StoragePresetLibrary.get(index);
        return custom != null ? custom : entry != null ? entry.name() : null;
    }

    public void setChestPresetName(int size, int index, String name) {
        Path path = getChestPresetFile(size, index);
        if (!Files.exists(path) && StoragePresetLibrary.get(index) != null) {
            var generated = StoragePresetLibrary.preview(StoragePresetLibrary.get(index), size);
            writeRawData(generated.visual(), generated.filters(), name, path);
            invalidatePresetNameForFile(path);
        } else setPresetNameForFile(path, name);
    }

    // Written once the pre-made presets have been created, so they are never regenerated — a deleted or
    // overwritten default stays gone. Bump the suffix to ship a new default set to existing players.
    private static final String DEFAULTS_MARKER = ".defaults_seeded_v2";

    // The ready-made presets shipped as resources under /chestseparators_presets. Copied verbatim into
    // the config on first run, so what the player gets is byte-for-byte what was authored in-game.
    private static final String[] BUNDLED_PRESETS = {
        "chest_preset_27_1.json",
        "chest_preset_27_2.json",
        "chest_preset_27_3.json",
        "chest_preset_27_4.json",
        "chest_preset_27_5.json",
        "chest_preset_54_1.json",
        "chest_preset_54_2.json",
        "chest_preset_54_3.json",
        "chest_preset_54_4.json",
        "chest_preset_54_5.json",
        "inventory_preset_1.json",
        "inventory_preset_2.json",
        "inventory_preset_3.json",
        "inventory_preset_4.json",
        "inventory_preset_5.json",
    };

    /**
     * Copies the bundled ready-made presets into the config the first time the mod runs. Guarded by a
     * marker file so it happens exactly once; an existing slot is never overwritten, so a player who
     * already saved into that slot keeps their own preset. The files are already valid preset NBT, so
     * they are copied as raw bytes — no re-serialization.
     */
    public void seedDefaultPresetsIfNeeded() {
        Path marker = getGlobalConfigDir().resolve(DEFAULTS_MARKER);
        if (Files.exists(marker)) return;

        // Guard: if the bundled presets are not on the classpath (a broken/dev build with the resources
        // missing), do NOT write the marker — otherwise the "seeded once" flag would be set with nothing
        // copied, permanently suppressing the defaults even after the resources are added back.
        if (ChestConfigManager.class.getResource("/chestseparators_presets/" + BUNDLED_PRESETS[0]) == null) {
            return;
        }

        for (String name : BUNDLED_PRESETS) {
            Path target = getGlobalConfigDir().resolve(name);
            if (Files.exists(target)) continue;
            try (java.io.InputStream in =
                    ChestConfigManager.class.getResourceAsStream("/chestseparators_presets/" + name)) {
                if (in == null) continue;
                Files.write(target, in.readAllBytes());
                invalidatePresetNameForFile(target);
            } catch (IOException e) {
                LOGGER.error("chestseparators: failed to seed preset " + name, e);
            }
        }
        try {
            Files.createFile(marker);
        } catch (IOException ignored) {
        }
    }

    /**
     * Offset added to a player-inventory slot index to namespace it inside the editor's working maps,
     * so chest slots (raw indices) and inventory slots (offset indices) — which would otherwise share
     * the same index — coexist while editing both at once. Must exceed any container's slot count and
     * be a multiple of 9 so the 9-wide grid math stays aligned within the inventory namespace.
     */
    public static final int PLAYER_KEY_OFFSET = 117;

    public static boolean isInventoryKey(int key) {
        return key >= PLAYER_KEY_OFFSET;
    }

    /**
     * True for player-inventory keys that fall outside the 9-wide grid: armor and offhand (their
     * PlayerInventory index is >= MAIN_SIZE = 36). They are isolated cells with no grid neighbours, so
     * blob connectors and contiguous flood-fill must not bridge them.
     */
    public static boolean isNonGridInventoryKey(int key) {
        return key >= PLAYER_KEY_OFFSET + 36;
    }

    /** Loads the persistent inventory profile (separators/backgrounds + filters) from disk. */
    public void loadInventoryProfile() {
        playerInventoryVisual.clear();
        playerInventoryFilters.clear();
    }

    /**
     * Mirrors the inventory profile into the editor's working maps at offset keys, so the editor edits
     * the chest (raw keys) and the inventory (offset keys) simultaneously through the same operations.
     */
    public void mirrorInventoryIntoCurrent() {
        for (Map.Entry<Integer, int[]> e : playerInventoryVisual.entrySet()) {
            currentChestConfig.put(e.getKey() + PLAYER_KEY_OFFSET, e.getValue().clone());
        }
        for (Map.Entry<Integer, SlotWhitelist> e : playerInventoryFilters.entrySet()) {
            currentWhitelists.put(e.getKey() + PLAYER_KEY_OFFSET, e.getValue());
        }
    }

    /** Inventory-slot visuals from the working map, re-keyed back to profile (non-offset) keys, cloned. */
    private Map<Integer, int[]> extractInventoryVisual() {
        Map<Integer, int[]> vis = new HashMap<>();
        for (Map.Entry<Integer, int[]> e : currentChestConfig.entrySet()) {
            if (isInventoryKey(e.getKey()))
                vis.put(e.getKey() - PLAYER_KEY_OFFSET, e.getValue().clone());
        }
        return vis;
    }

    /** Inventory-slot filters from the working map, re-keyed back to profile (non-offset) keys. */
    private Map<Integer, SlotWhitelist> extractInventoryFilters() {
        Map<Integer, SlotWhitelist> fil = new HashMap<>();
        for (Map.Entry<Integer, SlotWhitelist> e : currentWhitelists.entrySet()) {
            if (isInventoryKey(e.getKey())) fil.put(e.getKey() - PLAYER_KEY_OFFSET, e.getValue());
        }
        return fil;
    }

    /** Extracts the inventory portion (offset keys) from the working maps back into the profile and saves it. */
    public void saveInventoryFromCurrent() {
        Map<Integer, int[]> vis = extractInventoryVisual();
        Map<Integer, SlotWhitelist> fil = extractInventoryFilters();
        playerInventoryVisual = vis;
        playerInventoryFilters = fil;
        writeRawData(vis, fil, getInventoryFile());
    }

    private static final String INVENTORY_PRESET_PREFIX = "inventory_preset_";

    /**
     * File backing inventory preset slot {@code index}. Presets are intentionally GLOBAL (shared across
     * all worlds/servers): they are reusable templates the player applies to any world. The active
     * inventory layout, by contrast, is per-world (see getInventoryFile).
     */
    private Path getInventoryPresetFile(int index) {
        return getGlobalConfigDir().resolve(INVENTORY_PRESET_PREFIX + index + ".json");
    }

    /** Whether inventory preset slot {@code index} has been saved. */
    public boolean inventoryPresetExists(int index) {
        return Files.exists(getInventoryPresetFile(index));
    }

    /** Deletes inventory preset slot {@code index} from disk. No-op if it does not exist. */
    public void deleteInventoryPreset(int index) {
        try {
            Files.deleteIfExists(getInventoryPresetFile(index));
        } catch (IOException e) {
            LOGGER.error("chestseparators: I/O error", e);
        }
        invalidatePresetNameForFile(getInventoryPresetFile(index));
    }

    /** Saves the inventory portion currently in the working maps into preset slot {@code index}. */
    public void saveInventoryPreset(int index) {
        Map<Integer, int[]> vis = extractInventoryVisual();
        Map<Integer, SlotWhitelist> fil = extractInventoryFilters();
        // Overwriting a slot keeps its existing name (see saveChestPreset).
        Path path = getInventoryPresetFile(index);
        String name = getPresetNameForFile(path);
        writeRawData(vis, fil, name, path);
        invalidatePresetNameForFile(path);
    }

    /**
     * Loads inventory preset slot {@code index} as the active inventory profile and mirrors it into the
     * working maps (so an open editor reflects it immediately). Returns false if the slot is empty.
     */
    public boolean loadInventoryPreset(int index) {
        Path f = getInventoryPresetFile(index);
        if (!Files.exists(f)) return false;
        RawData data = readRawData(f);
        playerInventoryVisual = data.visual;
        playerInventoryFilters = data.filters;
        bumpPaintSequence(playerInventoryVisual.values());
        currentChestConfig.keySet().removeIf(ChestConfigManager::isInventoryKey);
        currentWhitelists.keySet().removeIf(ChestConfigManager::isInventoryKey);
        mirrorInventoryIntoCurrent();
        writeRawData(playerInventoryVisual, playerInventoryFilters, getInventoryFile());
        return true;
    }

    private static final String CHEST_PRESET_PREFIX = "chest_preset_";

    /**
     * File backing chest preset slot {@code index} for a container of {@code size} slots (27 vs 54).
     * Presets are split by container size so a single chest and a double chest keep independent sets,
     * shown only when a container of that size is open. Global reusable template across worlds.
     */
    private Path getChestPresetFile(int size, int index) {
        var entry = StoragePresetLibrary.get(index);
        String key = entry == null ? Integer.toString(index) : "catalog_" + UUID.nameUUIDFromBytes(entry.key().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return getGlobalConfigDir().resolve(CHEST_PRESET_PREFIX + size + "_" + key + ".json");
    }

    /** Whether chest preset slot {@code index} of the {@code size}-slot set has been saved. */
    /** Hide untouched bundled examples, while retaining any user-edited version. */
    public boolean isBundledChestExample(int size, int index) {
        if (index < 1 || index > 5) return false;
        Path file = getChestPresetFile(size, index);
        if (!Files.exists(file)) return false;
        try (var resource = ChestConfigManager.class.getResourceAsStream("/chestseparators_presets/chest_preset_" + size + "_" + index + ".json")) {
            return resource != null && java.util.Arrays.equals(Files.readAllBytes(file), resource.readAllBytes());
        } catch (IOException e) { return false; }
    }

    public boolean chestPresetExists(int size, int index) {
        Path path = getChestPresetFile(size, index);
        return Files.exists(path) || (StoragePresetLibrary.get(index) != null && !Files.exists(Path.of(path + ".deleted")));
    }

    /** Deletes chest preset slot {@code index} of the {@code size}-slot set from disk. */
    public void deleteChestPreset(int size, int index) {
        Path path = getChestPresetFile(size, index);
        try {
            Files.deleteIfExists(path);
            if (StoragePresetLibrary.get(index) != null) Files.writeString(Path.of(path + ".deleted"), "deleted");
        } catch (IOException e) {
            LOGGER.error("chestseparators: I/O error", e);
        }
        invalidatePresetNameForFile(path);
    }

    /** Saves the open chest's current layout + filters into slot {@code index} of the {@code size}-slot set. */
    public void saveChestPreset(int size, int index) {
        Path path = getChestPresetFile(size, index);
        // Overwriting a slot keeps whatever name it already had, so a re-save does not wipe a rename.
        String name = getPresetNameForFile(path);
        writeRawData(chestOnlyVisual(), chestOnlyWhitelists(), name, path);
        invalidatePresetNameForFile(path);
    }

    /**
     * Loads chest preset slot {@code index} of the {@code size}-slot set onto the open chest, replacing
     * only the chest-only keys in the working maps and leaving the mirrored inventory keys untouched.
     */
    public boolean loadChestPreset(int size, int index) {
        Path f = getChestPresetFile(size, index);
        if (!chestPresetExists(size, index)) return false;
        RawData data = readRawData(f);
        if (!Files.exists(f) && StoragePresetLibrary.get(index) != null) {
            var generated = StoragePresetLibrary.preview(StoragePresetLibrary.get(index), size);
            data.visual.putAll(generated.visual());
            data.filters.putAll(generated.filters());
        }
        currentChestConfig.keySet().removeIf(k -> !isInventoryKey(k));
        currentWhitelists.keySet().removeIf(k -> !isInventoryKey(k));
        currentChestConfig.putAll(data.visual);
        currentWhitelists.putAll(data.filters);
        bumpPaintSequence(data.visual.values());
        return true;
    }

    /** Read-only snapshot of a preset's stored data, for rendering a preview without applying it. */
    public record PresetPreview(Map<Integer, int[]> visual, Map<Integer, SlotWhitelist> filters) {}

    /** The five paint colors of a slot's stored array as {top, bottom, left, right, background}. */
    public static int[] previewColors(int[] arr) {
        int[] c = new int[5];
        for (int i = 0; i <= IDX_BG; i++) {
            c[i] = (arr != null && i < arr.length) ? arr[i] : 0;
        }
        return c;
    }

    /** Reads an inventory preset's contents for preview (keys are raw PlayerInventory indices). */
    public PresetPreview readInventoryPresetPreview(int index) {
        Path f = getInventoryPresetFile(index);
        if (!Files.exists(f)) return null;
        RawData data = readRawData(f);
        return new PresetPreview(data.visual, data.filters);
    }

    /** Reads a chest preset's contents for preview (keys are raw container slot indices). */
    public PresetPreview readChestPresetPreview(int size, int index) {
        Path f = getChestPresetFile(size, index);
        if (!chestPresetExists(size, index)) return null;
        if (!Files.exists(f) && StoragePresetLibrary.get(index) != null)
            return StoragePresetLibrary.preview(StoragePresetLibrary.get(index), size);
        RawData data = readRawData(f);
        return new PresetPreview(data.visual, data.filters);
    }

    public void applySharedLayout(CommunityData.Layout layout) {
        currentChestConfig.keySet().removeIf(k -> !isInventoryKey(k));
        currentWhitelists.keySet().removeIf(k -> !isInventoryKey(k));
        layout.visual().forEach((slot, colors) -> currentChestConfig.put(slot, colors.clone()));
        currentWhitelists.putAll(layout.filters());
        bumpPaintSequence(layout.visual().values());
    }
    /** Chest-only view of the working visual config (excludes mirrored inventory entries at offset keys). */
    public Map<Integer, int[]> chestOnlyVisual() {
        Map<Integer, int[]> out = new HashMap<>();
        for (Map.Entry<Integer, int[]> e : currentChestConfig.entrySet()) {
            if (!isInventoryKey(e.getKey())) out.put(e.getKey(), e.getValue());
        }
        return out;
    }

    /** Chest-only view of the working whitelists (excludes mirrored inventory entries at offset keys). */
    public Map<Integer, SlotWhitelist> chestOnlyWhitelists() {
        Map<Integer, SlotWhitelist> out = new HashMap<>();
        for (Map.Entry<Integer, SlotWhitelist> e : currentWhitelists.entrySet()) {
            if (!isInventoryKey(e.getKey())) out.put(e.getKey(), e.getValue());
        }
        return out;
    }

    public void copyWhitelistsToClipboard() {
        if (currentWhitelists != null) {
            whitelistClipboard = new HashMap<>();
            for (Map.Entry<Integer, SlotWhitelist> entry : currentWhitelists.entrySet()) {
                whitelistClipboard.put(entry.getKey(), entry.getValue());
            }
        }
    }

    public boolean hasWhitelistClipboardData() {
        return whitelistClipboard != null && !whitelistClipboard.isEmpty();
    }

    public void pasteWhitelistsFromClipboard() {
        if (hasWhitelistClipboardData()) {
            if (currentWhitelists == null) currentWhitelists = new HashMap<>();

            Map<UUID, UUID> uuidMapping = new HashMap<>();

            for (Map.Entry<Integer, SlotWhitelist> entry : whitelistClipboard.entrySet()) {
                SlotWhitelist clipboardItem = entry.getValue();

                UUID newGroupId = uuidMapping.computeIfAbsent(clipboardItem.groupId(), k -> UUID.randomUUID());

                SlotWhitelist independentCopy = new SlotWhitelist(
                        newGroupId,
                        new ArrayList<>(clipboardItem.allowedItems()),
                        clipboardItem.allowManual(),
                        clipboardItem.allowShift(),
                        clipboardItem.allowHopper());
                currentWhitelists.put(entry.getKey(), independentCopy);
            }
        }
    }
}
