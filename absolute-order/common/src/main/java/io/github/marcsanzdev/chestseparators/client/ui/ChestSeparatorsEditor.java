package io.github.marcsanzdev.chestseparators.client.ui;

import io.github.marcsanzdev.chestseparators.access.IWhitelistProvider;
import io.github.marcsanzdev.chestseparators.client.EditorState;
import io.github.marcsanzdev.chestseparators.client.ModTextures;
import io.github.marcsanzdev.chestseparators.client.ui.screens.ScreenColorPicker;
import io.github.marcsanzdev.chestseparators.client.ui.screens.ScreenDrawLines;
import io.github.marcsanzdev.chestseparators.client.ui.screens.ScreenEditFilter;
import io.github.marcsanzdev.chestseparators.client.ui.screens.ScreenViewGroups;
import io.github.marcsanzdev.chestseparators.client.ui.widgets.ToolButtonWidget;
import io.github.marcsanzdev.chestseparators.config.GlobalChestConfig;
import io.github.marcsanzdev.chestseparators.data.ChestConfigManager;
import io.github.marcsanzdev.chestseparators.data.SlotWhitelist;
import io.github.marcsanzdev.chestseparators.mixin.client.HandledScreenAccessor;
import io.github.marcsanzdev.chestseparators.util.ChestPosStorage;
import java.util.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.world.item.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

public class ChestSeparatorsEditor {

    private static ChestSeparatorsEditor activeInstance;

    public static ChestSeparatorsEditor getInstance() {
        return activeInstance;
    }

    public EditorSessionData getSession() {
        return this.session;
    }

    public final AbstractContainerScreen<?> screen;
    public final HandledScreenAccessor accessor;

    public static EditorSessionData activeSession = new EditorSessionData();
    public final EditorSessionData session = activeSession;
    public EditorGeometry geometry;
    public EditorRenderer renderer;
    public EditorInputHandler inputHandler;
    public EditorLayout layout;

    public ScreenViewGroups screenViewGroups;
    public ScreenDrawLines screenDrawLines;
    public ScreenColorPicker screenColorPicker;
    public ScreenEditFilter screenEditFilter;

    public ToolButtonWidget entryButton;
    public ToolButtonWidget whitelistButton;
    public ToolButtonWidget depositButton;
    public ToolButtonWidget fillButton;
    public ToolButtonWidget presetsButton;
    public ToolButtonWidget chestPresetsButton;
    public final io.github.marcsanzdev.chestseparators.client.ui.screens.PresetsMenu presetsMenu =
            new io.github.marcsanzdev.chestseparators.client.ui.screens.PresetsMenu(this);
    public EditBox searchBox;
    public EditBox whitelistSearchBox;

    public boolean isHoveringDeposit = false;
    public boolean isHoveringFill = false;
    public boolean depositPreviewShift = false;
    public boolean suspendDepositPreview = false;
    public long depositClickTime = 0;
    public long fillClickTime = 0;
    public final Map<Integer, Integer> previewSourceRemaining = new HashMap<>();
    public final Map<Integer, ItemStack> previewTargetIncoming = new HashMap<>();

    public static final int TOOL_ERASER_ID = -1;

    public boolean hasEditorLock = false;

    public boolean colorPickerModified = false;
    public int restoreColorIndex = -1;

    // Slots changed by the most recent undo/redo, blinked briefly to show what happened.
    public Map<Integer, ChestConfigManager.SlotChange> undoHighlights = new HashMap<>();
    public long undoHighlightStart = 0;

    public ChestSeparatorsEditor(AbstractContainerScreen<?> screen) {
        this.screen = screen;
        this.accessor = (HandledScreenAccessor) screen;
        activeInstance = this;
    }

    public void init() {

        // Determine the editor target up-front so geometry/layout (which count the editable slots) are
        // computed for the right slot set. The creative screen counts as a player-inventory context too
        // (its inventory tab shows the real player inventory).
        session.isPlayerInventory = isInventoryContextScreen(this.screen);

        this.layout = new EditorLayout();
        this.geometry = new EditorGeometry(session, accessor);
        this.layout.update(screen, accessor, getSidebarYOffset());

        this.renderer = new EditorRenderer(this, session, layout, screen, accessor);
        this.inputHandler = new EditorInputHandler(this, session, geometry, layout, screen, accessor);

        this.screenViewGroups = new ScreenViewGroups(this);
        this.screenViewGroups.init();

        this.screenDrawLines = new ScreenDrawLines(this);
        this.screenDrawLines.init();

        this.screenColorPicker = new ScreenColorPicker(this);
        this.screenColorPicker.init();

        this.screenEditFilter = new ScreenEditFilter(this);
        this.screenEditFilter.init();

        session.isInventoryScreenContext = isInventoryContextScreen(this.screen);
        session.isPlayerInventory = session.isInventoryScreenContext;

        // The survival inventory screen has no container context: clear any stale chest data left in
        // ChestPosStorage. In a chest screen we keep the chest context so the edit target can toggle.
        session.currentChestPos = session.isInventoryScreenContext ? null : ChestPosStorage.lastClickedPos;
        session.currentDimension = ChestPosStorage.lastClickedDimension;
        session.isEntityChest = !session.isInventoryScreenContext && ChestPosStorage.isEntityOpened;
        session.isMinecartChest = session.isEntityChest && ChestPosStorage.isMinecartEntity;
        session.currentEntityUUID = session.isInventoryScreenContext ? null : ChestPosStorage.lastClickedEntityUUID;
        session.isEnderChest = false;

        session.isShulkerBox = !session.isInventoryScreenContext && this.screen instanceof ShulkerBoxScreen;
        session.currentShulkerUUID = session.isInventoryScreenContext ? null : ChestPosStorage.lastOpenedShulkerUUID;

        // Consume the interactBlock -> screen-init handoff: the values now live in the session, so clear
        // the shared storage here (on read) instead of in the screen's removed(). Clearing on removed()
        // races with the NEXT container: interactBlock(B) sets the position, then the OLD screen's
        // removed() would null it before B's init reads it, leaving currentChestPos == null and silently
        // dropping B's saves. Clearing on read keeps leak-prevention without the race.
        ChestPosStorage.lastClickedPos = null;
        ChestPosStorage.lastClickedEntityUUID = null;
        ChestPosStorage.lastOpenedShulkerUUID = null;
        ChestPosStorage.isEntityOpened = false;
        ChestPosStorage.isMinecartEntity = false;

        ChestConfigManager.getInstance().loadWorldPalette();
        loadConfigForCurrentTarget();
        requestChestWhitelistsIfNeeded();

        int x = accessor.getX();
        int y = accessor.getY();
        int bgWidth = accessor.getBackgroundWidth();

        this.entryButton = new io.github.marcsanzdev.chestseparators.client.ui.widgets.ToolButtonWidget(
                x + bgWidth - 22,
                y - 22,
                io.github.marcsanzdev.chestseparators.client.ModTextures.BTN_EDIT_LINES,
                Component.translatable("tooltip.chestseparators.edit_mode").getString(),
                () -> {
                    toggleState(EditorState.DRAW_LINES);
                    playClickSound(1.0f);
                });

        this.whitelistButton = new io.github.marcsanzdev.chestseparators.client.ui.widgets.ToolButtonWidget(
                x + bgWidth - 44,
                y - 22,
                io.github.marcsanzdev.chestseparators.client.ModTextures.BTN_WHITELIST,
                "Editar zonas: filtros y colores",
                () -> {
                    boolean isFilterState = (session.currentState == EditorState.VIEW_GROUPS
                            || session.currentState == EditorState.SELECT_SLOTS
                            || session.currentState == EditorState.EDIT_FILTER);

                    if (isFilterState) {
                        toggleState(EditorState.HIDDEN);
                        playClickSound(1.0f);
                    } else {
                        if (session.currentChestPos != null && !session.isEntityChest && !session.isShulkerBox) {
                            if (dev.architectury.networking.NetworkManager.canServerReceive(
                                    io.github.marcsanzdev.chestseparators.network.EditorLockRequestPayload.TYPE)) {
                                dev.architectury.networking.NetworkManager.sendToServer(
                                        new io.github.marcsanzdev.chestseparators.network.EditorLockRequestPayload(
                                                session.currentChestPos, true));
                            } else {
                                toggleState(EditorState.VIEW_GROUPS);
                                playClickSound(1.0f);
                            }
                        } else {
                            toggleState(EditorState.VIEW_GROUPS);
                            playClickSound(1.0f);
                        }
                    }
                });

        // PUSH items into the chest. Works from every editor screen (not just the closed editor).
        this.depositButton = new ToolButtonWidget(0, 0, ModTextures.BTN_DEPOSIT, "", () -> {
            this.depositClickTime = System.currentTimeMillis();

            executeDeposit(isShiftDown());
            playClickSound(1.2f);
        });

        // PULL items from the chest. Shift also pulls unfiltered items into free inventory space.
        this.fillButton = new ToolButtonWidget(
                0,
                0,
                ModTextures.ICON_BACKPACK_FULL,
                Component.translatable("tooltip.chestseparators.pull_from_chest").getString(),
                () -> {
                    this.fillClickTime = System.currentTimeMillis();
                    requestFillFromOpenChest(isShiftDown());
                });

        // "Inventory presets" menu opener: a top icon available in any editor context.
        this.presetsButton = new ToolButtonWidget(
                0,
                0,
                ModTextures.ICON_SAVE,
                Component.translatable("tooltip.chestseparators.desc.presets").getString(),
                () -> {
                    presetsMenu.resetView();
                    boolean openingSame = session.isPresetsMenuOpen && !session.presetsMenuChestMode;
                    session.presetsMenuChestMode = false;
                    session.isPresetsMenuOpen = !openingSame;
                    // Only one of layout/filters/presets may be selected at a time: opening presets
                    // closes any open layout or filter sub-screen.
                    if (session.isPresetsMenuOpen && session.currentState != EditorState.HIDDEN) {
                        toggleState(EditorState.HIDDEN);
                    }
                    playClickSound(1.0f);
                });

        // "Chest presets" menu opener: independent from inventory presets, only when a chest is open.
        this.chestPresetsButton = new ToolButtonWidget(
                0,
                0,
                ModTextures.ICON_COPY,
                Component.translatable("tooltip.chestseparators.desc.chest_presets").getString(),
                () -> {
                    presetsMenu.resetView();
                    boolean openingSame = session.isPresetsMenuOpen && session.presetsMenuChestMode;
                    session.presetsMenuChestMode = true;
                    session.isPresetsMenuOpen = !openingSame;
                    if (session.isPresetsMenuOpen && session.currentState != EditorState.HIDDEN) {
                        toggleState(EditorState.HIDDEN);
                    }
                    playClickSound(1.0f);
                });

        // Smooth vector-exported icons (128px, linear-filtered via .mcmeta) for the toolbar. They are
        // single-color white glyphs tinted by the button state (light-gray / accent blue).
        ToolButtonWidget[] toolbarBtns = {
            this.entryButton, this.whitelistButton, this.depositButton,
            this.fillButton, this.presetsButton, this.chestPresetsButton
        };
        net.minecraft.resources.ResourceLocation[] toolbarIcons = {
            // Edit-layout reuses the combo icon (crossed pincel + brocha) — same meaning, same glyph.
            // Deposit = PUSH items into the chest = UP arrow (ICON_SM_FILL); fill = PULL items out of the
            // chest into your inventory = DOWN arrow (ICON_SM_DEPOSIT). Matches the chest-above/inventory-
            // below mental model.
            ModTextures.ICON_SM_COMBO, ModTextures.ICON_SM_FILTER, ModTextures.ICON_SM_FILL,
            ModTextures.ICON_SM_DEPOSIT, ModTextures.ICON_SM_INV_PRESETS, ModTextures.ICON_SM_CHEST_PRESETS
        };
        for (int i = 0; i < toolbarBtns.length; i++) {
            toolbarBtns[i].baseIcon = toolbarIcons[i];
            toolbarBtns[i].maskIcon = null;
            toolbarBtns[i].texSize = 128;
            toolbarBtns[i].tintByState = true;
        }

        // Toolbar positioning is now handled per-frame by EditorRenderer#renderToolbar, which docks the
        // visible icons into a vertical column on the right edge of the container GUI (adaptive to how
        // many are enabled in the config). The buttons keep their constructor positions until first render.

        session.allGameItems.clear();
        Minecraft client = Minecraft.getInstance();

        if (client.level != null) {
            // Read the survival item set from the already-built creative search group.
            //
            // We must NOT call CreativeModeTabs.updateDisplayContext here. That rebuilds the GLOBAL creative
            // display context, and because we do not also reload the vanilla creative SearchManager index,
            // it corrupts that index so the creative-inventory search returns zero results for every query.
            // The display context is shared across all screens, so calling it when opening ANY container
            // (a chest, a barrel, the inventory...) would break creative search for the rest of the session.
            // Reading getDisplayStacks() below does not rebuild anything; the context is already populated by
            // vanilla on world join / resource reload.
            CreativeModeTab searchGroup = CreativeModeTabs.searchTab();
            if (searchGroup != null) {
                for (ItemStack stack : searchGroup.getDisplayItems()) {
                    Item item = stack.getItem();
                    if (item != Items.AIR && !isNonSurvivalItem(item) && !session.allGameItems.contains(item)) {
                        session.allGameItems.add(item);
                    }
                }
            }

            // Fallback: if the creative display context has not been built yet (rare — e.g. first open before
            // vanilla populated it), derive the set straight from the item registry so the filter editor is
            // never empty.
            if (session.allGameItems.isEmpty()) {
                for (Item item : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
                    if (item != Items.AIR && !isNonSurvivalItem(item)) session.allGameItems.add(item);
                }
            }
        }

        session.filteredItems.clear();
        session.filteredItems.addAll(session.allGameItems);

        int w = 240;
        int h = 200;
        int guiX = (screen.width - w) / 2;
        int guiY = (screen.height - h) / 2;

        session.availableTabs.clear();
        for (int i = 0; i < 16; i++) {
            session.availableTabs.add(null);
        }

        EditorSessionData.CreativeTabInfo chestTab = new EditorSessionData.CreativeTabInfo(
                Component.translatable("itemGroup.chestseparators.chest_and_inventory"),
                new ItemStack(net.minecraft.world.level.block.Blocks.CHEST),
                null,
                false,
                true);
        session.availableTabs.set(12, chestTab);

        java.util.List<EditorSessionData.CreativeTabInfo> modTabs = new java.util.ArrayList<>();
        net.minecraft.core.registries.BuiltInRegistries.CREATIVE_MODE_TAB.forEach(group -> {
            net.minecraft.resources.ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.CREATIVE_MODE_TAB.getKey(group);
            if (id == null) return;
            String path = id.getPath();

            if (path.equals("spawn_eggs")
                    || path.equals("inventory")
                    || path.equals("hotbar")
                    || path.equals("op_blocks")) return;
            boolean isSearch = path.equals("search");
            EditorSessionData.CreativeTabInfo tab = new EditorSessionData.CreativeTabInfo(
                    group.getDisplayName(), group.getIconItem(), group, isSearch, false);

            if (path.equals("building_blocks")) session.availableTabs.set(0, tab);
            else if (path.equals("colored_blocks")) session.availableTabs.set(1, tab);
            else if (path.equals("natural_blocks")) session.availableTabs.set(2, tab);
            else if (path.equals("functional_blocks")) session.availableTabs.set(3, tab);
            else if (path.equals("redstone_blocks")) session.availableTabs.set(4, tab);
            else if (isSearch) session.availableTabs.set(7, tab);
            else if (path.equals("tools") || path.equals("tools_and_utilities")) session.availableTabs.set(8, tab);
            else if (path.equals("combat")) session.availableTabs.set(9, tab);
            else if (path.equals("food_and_drinks") || path.equals("food_and_drink"))
                session.availableTabs.set(10, tab);
            else if (path.equals("ingredients")) session.availableTabs.set(11, tab);
            else {
                modTabs.add(tab);
            }
        });
        session.availableTabs.addAll(modTabs);

        int firstValidTab = 0;
        for (int i = 0; i < session.availableTabs.size(); i++) {
            if (session.availableTabs.get(i) != null) {
                firstValidTab = i;
                break;
            }
        }
        switchCreativeTab(firstValidTab);

        this.searchBox = new EditBox(
                Minecraft.getInstance().font,
                guiX + 10,
                guiY + 10,
                100,
                12,
                Component.translatable("gui.chestseparators.search"));
        this.searchBox.setBordered(false);
        this.searchBox.setMaxLength(50);
        this.searchBox.setResponder(text -> {
            if (!session.availableTabs.get(session.currentCreativeTabIndex).isSearchTab) return;
            switchCreativeTab(session.currentCreativeTabIndex);
        });

        this.whitelistSearchBox = new EditBox(
                Minecraft.getInstance().font,
                guiX,
                guiY,
                100,
                12,
                Component.translatable("gui.chestseparators.search"));
        this.whitelistSearchBox.setBordered(false);
        this.whitelistSearchBox.setMaxLength(50);
        this.whitelistSearchBox.setResponder(text -> {
            session.listScrollY = 0f;
            updateWhitelistSearchCache();
        });
        // Mouse routing is done by the common GenericContainerScreenMixin, which forwards
        // clicked/released/dragged/scrolled to inputHandler; no per-screen event registration needed.
    }

    public void onClose() {
        releaseLock();
        ChestConfigManager.getInstance().clearCurrentConfig();
        ChestConfigManager.getInstance().getCurrentWhitelists().clear();

        ChestPosStorage.lastOpenedShulkerUUID = null;
        session.isColorPickerOpen = false;
        activeSession = new EditorSessionData();
        org.lwjgl.glfw.GLFW.glfwSetInputMode(
                Minecraft.getInstance().getWindow().getWindow(),
                org.lwjgl.glfw.GLFW.GLFW_CURSOR,
                org.lwjgl.glfw.GLFW.GLFW_CURSOR_NORMAL);
    }

    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        if (renderer != null) renderer.render(context, mouseX, mouseY, delta);
    }

    /**
     * Loads the working maps for the current screen: the container's config (chest variants) plus the
     * player inventory profile mirrored at offset keys, so both are edited simultaneously.
     */
    private void loadConfigForCurrentTarget() {
        ChestConfigManager manager = ChestConfigManager.getInstance();

        var customStorage = io.github.marcsanzdev.chestseparators.compat.StorageCompatibility.get(accessor.getHandler());
        if (customStorage != null) {
            session.currentChestPos = null;
            session.isEntityChest = false;
            session.isShulkerBox = false;
            manager.loadEntityConfig(customStorage.identity());
            manager.loadInventoryProfile();
            return;
        }
        // 1. Container portion (clears the working maps). The inventory screen has no container.
        if (session.isInventoryScreenContext) {
            manager.clearCurrentConfig();
            manager.getCurrentWhitelists().clear();
        } else if (session.isShulkerBox && session.currentShulkerUUID != null) {
            manager.loadShulkerConfig(session.currentShulkerUUID);
        } else if (session.isEntityChest && session.currentEntityUUID != null) {
            manager.loadEntityConfig(session.currentEntityUUID);
        } else if (session.currentChestPos != null && Minecraft.getInstance().level != null) {
            if (Minecraft.getInstance()
                            .level
                            .getBlockState(session.currentChestPos)
                            .getBlock()
                    == Blocks.ENDER_CHEST) {
                session.isEnderChest = true;
                manager.loadEnderConfig();
            } else {
                manager.loadConfig(session.currentChestPos, session.currentDimension);
            }
        }

        // 2. Player inventory portion, mirrored into the working maps at offset keys.
        manager.loadInventoryProfile();
        manager.mirrorInventoryIntoCurrent();
    }

    /** Fetches the chest's server-authoritative whitelists, when a real chest is open. */
    private void requestChestWhitelistsIfNeeded() {
        if (CommunityClient.available()) { CommunityClient.request(io.github.marcsanzdev.chestseparators.network.CommunityRequest.FETCH, io.github.marcsanzdev.chestseparators.data.CommunityData.NONE, ""); return; }
        if (io.github.marcsanzdev.chestseparators.compat.StorageCompatibility.get(accessor.getHandler()) != null) {
            if (dev.architectury.networking.NetworkManager.canServerReceive(io.github.marcsanzdev.chestseparators.network.WhitelistRequestPayload.TYPE))
                dev.architectury.networking.NetworkManager.sendToServer(new io.github.marcsanzdev.chestseparators.network.WhitelistRequestPayload(BlockPos.ZERO));
            return;
        }
        if (!session.isInventoryScreenContext
                && session.currentChestPos != null
                && !session.isEntityChest
                && !session.isEnderChest) {
            if (dev.architectury.networking.NetworkManager.canServerReceive(
                    io.github.marcsanzdev.chestseparators.network.WhitelistRequestPayload.TYPE)) {
                dev.architectury.networking.NetworkManager.sendToServer(
                        new io.github.marcsanzdev.chestseparators.network.WhitelistRequestPayload(
                                session.currentChestPos));
            }
        }
    }

    public int getSidebarYOffset() {
        return geometry.getContainerSlotCount() > 27 ? 0 : -18;
    }

    /**
     * Items that appear in the creative menu but are not obtainable in survival, so they are kept out
     * of the filter list and out of "Allow All": every spawn egg, the player head, the spawners,
     * reinforced deepslate, and the infested-stone blocks.
     */
    private static boolean isNonSurvivalItem(Item item) {
        if (item instanceof net.minecraft.world.item.SpawnEggItem) return true;
        if (item == Items.PLAYER_HEAD) return true;
        net.minecraft.world.level.block.Block block = net.minecraft.world.level.block.Block.byItem(item);
        return block == net.minecraft.world.level.block.Blocks.SPAWNER
                // TRIAL_SPAWNER is a 1.21+ block; it does not exist in 1.20.1.
                || block == net.minecraft.world.level.block.Blocks.REINFORCED_DEEPSLATE
                || block == net.minecraft.world.level.block.Blocks.INFESTED_STONE
                || block == net.minecraft.world.level.block.Blocks.INFESTED_COBBLESTONE
                || block == net.minecraft.world.level.block.Blocks.INFESTED_STONE_BRICKS
                || block == net.minecraft.world.level.block.Blocks.INFESTED_MOSSY_STONE_BRICKS
                || block == net.minecraft.world.level.block.Blocks.INFESTED_CRACKED_STONE_BRICKS
                || block == net.minecraft.world.level.block.Blocks.INFESTED_CHISELED_STONE_BRICKS
                || block == net.minecraft.world.level.block.Blocks.INFESTED_DEEPSLATE;
    }

    public boolean isEditMode() {
        return session.currentState != EditorState.HIDDEN;
    }

    /**
     * Whether a slot can be decorated/filtered by the editor. The player's own inventory slots are
     * always editable (chest screens edit them simultaneously with the chest); container slots are
     * editable only in a container screen (this also excludes the crafting slots of the inventory
     * screen). Chest and inventory edits coexist via {@link #slotKey} namespacing.
     */
    public static int storageColumns() {
        if (activeInstance == null) return 9;
        java.util.Map<Integer, java.util.Set<Integer>> rows = new java.util.HashMap<>();
        for (Slot slot : activeInstance.accessor.getHandler().slots) {
            if (isEditableSlot(slot) && slot.isActive()) rows.computeIfAbsent(slot.y, y -> new java.util.HashSet<>()).add(slot.x);
        }
        return rows.values().stream().mapToInt(java.util.Set::size).max().orElse(9);
    }

    public static boolean isEditableSlot(Slot slot) {
        if (slot.container instanceof net.minecraft.world.entity.player.Inventory) return false;
        var editor = getInstance();
        if (editor != null) {
            var custom = io.github.marcsanzdev.chestseparators.compat.StorageCompatibility.get(editor.accessor.getHandler());
            if (custom != null) return custom.contains(slot);
        }
        return !activeSession.isInventoryScreenContext;
    }

    /**
     * The real inventory index of a slot. In the creative inventory the visible slots are
     * {@code CreativeSlot} wrappers whose {@code getIndex()} is the creative handler's slot ID (armor 5-8,
     * hotbar 36-44, offhand 45), not the inventory index the server enforces by. Unwrapping to the
     * underlying slot yields the same index the survival inventory and chests report, so editing from
     * creative lands filters/layouts on the correct slot.
     */
    public static int realIndex(Slot slot) {
        if (slot instanceof io.github.marcsanzdev.chestseparators.mixin.client.CreativeSlotAccessor wrapper) {
            Slot inner = wrapper.chestseparators$getWrappedSlot();
            if (inner != null) return inner.getContainerSlot();
        }
        return slot.getContainerSlot();
    }

    /**
     * The working-map key for a slot. Player-inventory slots are offset into their own namespace so
     * chest and inventory data (which share raw slot indices) coexist while both are edited at once.
     */
    public static int slotKey(Slot slot) {
        return slot.container instanceof net.minecraft.world.entity.player.Inventory
                ? realIndex(slot) + ChestConfigManager.PLAYER_KEY_OFFSET
                : realIndex(slot);
    }

    /** True if a slot belongs to the player inventory namespace (used for the mutual-exclusion dimming). */
    public static boolean isPlayerSlot(Slot slot) {
        return slot.container instanceof net.minecraft.world.entity.player.Inventory;
    }

    /** Screens the editor treats as the player-inventory context: survival inventory and creative. */
    private static boolean isInventoryContextScreen(net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> s) {
        return s instanceof InventoryScreen
                || s instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
    }

    // While the editor is active, the creative screen's own tab switching is blocked (the tab bar must not
    // react to clicks). This flag lets the mod's OWN switch below through that block.
    public static boolean allowCreativeTabSwitch = false;

    /**
     * When a toolbar button is used on the creative screen while it is NOT on the inventory tab, jump to
     * that tab so the player's real inventory is present to interact with. No-op otherwise.
     */
    public void ensureCreativeInventoryTab() {
        if (this.screen instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen cis
                && !cis.isInventoryOpen()) {
            net.minecraft.world.item.CreativeModeTab inv = net.minecraft.core.registries.BuiltInRegistries.CREATIVE_MODE_TAB.stream()
                    .filter(g -> g.getType() == net.minecraft.world.item.CreativeModeTab.Type.INVENTORY)
                    .findFirst()
                    .orElse(null);
            if (inv != null) {
                allowCreativeTabSwitch = true;
                try {
                    ((io.github.marcsanzdev.chestseparators.mixin.client.CreativeInventoryScreenAccessor) cis)
                            .chestseparators$setSelectedTab(inv);
                } finally {
                    allowCreativeTabSwitch = false;
                }
            }
        }
    }

    /** Finds the slot for a working-map key (the inverse of {@link #slotKey}); null if none matches. */
    public Slot slotForKey(int key) {
        for (Slot s : accessor.getHandler().slots) {
            if (slotKey(s) == key) return s;
        }
        return null;
    }

    /**
     * The slots whose on-screen 16×16 box intersects the rectangle dragged from {@code start} to
     * {@code current}, restricted to the namespace (chest vs. player inventory) that {@code start} belongs
     * to. Uses real slot positions ({@code slot.x}/{@code slot.y}), NOT raw indices: the hotbar is drawn
     * BELOW the inventory rows even though its indices (0-8) come first, so {@code index / 9} row math
     * wrongly treats the hotbar as the top row and over-selects the intervening rows when a drag crosses
     * between them. Visual-box intersection selects exactly the cells the cursor swept over.
     */
    public java.util.List<Slot> slotsInDragBox(Slot start, Slot current) {
        java.util.List<Slot> out = new java.util.ArrayList<>();
        if (start == null || current == null) return out;
        boolean playerNs = isPlayerSlot(start);

        // Every slot the rectangle touches is selected, armor and offhand included: they are ordinary
        // targets for a filter, and skipping them meant a drag reaching one simply did nothing there.
        // Membership is decided purely by whether the slot's cell overlaps the swept rectangle.
        int minX = Math.min(start.x, current.x);
        int maxX = Math.max(start.x, current.x) + 16;
        int minY = Math.min(start.y, current.y);
        int maxY = Math.max(start.y, current.y) + 16;
        for (Slot s : accessor.getHandler().slots) {
            if (!isEditableSlot(s)) continue;
            if (isPlayerSlot(s) != playerNs) continue;
            if (s.x < maxX && s.x + 16 > minX && s.y < maxY && s.y + 16 > minY) out.add(s);
        }
        return out;
    }

    /**
     * Armor slots in the filter under edit that restrict which items they accept. Normal, hotbar and
     * offhand slots accept anything, so they never appear here. Recomputed each time the filter editor
     * opens; used to limit the item picker to equippable items for armor filters.
     */
    private final java.util.List<Slot> filterConstraintSlots = new java.util.ArrayList<>();

    /** True when at least one selected slot accepts anything (so the filter has no item restriction). */
    private boolean filterHasUnrestrictedSlot = true;

    /** Detects which of the filter's selected slots are restrictive (reject a non-equippable sentinel). */
    public void recomputeFilterConstraints() {
        filterConstraintSlots.clear();
        filterHasUnrestrictedSlot = false;
        ItemStack sentinel = new ItemStack(Items.STONE);
        // Measure each slot's VANILLA restriction (armor checks), not our own whitelist enforcement —
        // otherwise, when editing an existing filter, its saved whitelist blocks the sentinel and the
        // slot is wrongly flagged as restricted, hiding all game items from the picker.
        io.github.marcsanzdev.chestseparators.util.ClickTracker.BYPASS_ENFORCEMENT.set(true);
        try {
            for (int key : session.selectedSlots) {
                Slot s = slotForKey(key);
                if (s == null) continue;
                if (s.mayPlace(sentinel)) filterHasUnrestrictedSlot = true;
                else filterConstraintSlots.add(s);
            }
        } finally {
            io.github.marcsanzdev.chestseparators.util.ClickTracker.BYPASS_ENFORCEMENT.set(false);
        }
        if (filterConstraintSlots.isEmpty()) filterHasUnrestrictedSlot = true;
    }

    /**
     * Whether an item may be added to the filter currently being edited. Always true unless the filter
     * is made up only of armor slots, in which case the item must be equippable in at least one of them
     * (so selecting all four armor slots accepts any armor piece, not none).
     */
    public boolean isItemAllowedForFilter(Item item) {
        if (filterHasUnrestrictedSlot) return true;
        ItemStack stack = new ItemStack(item);
        io.github.marcsanzdev.chestseparators.util.ClickTracker.BYPASS_ENFORCEMENT.set(true);
        try {
            for (Slot s : filterConstraintSlots) {
                if (s.mayPlace(stack)) return true;
            }
        } finally {
            io.github.marcsanzdev.chestseparators.util.ClickTracker.BYPASS_ENFORCEMENT.set(false);
        }
        return false;
    }

    public void releaseLock() {
        if (hasEditorLock && session.currentChestPos != null) {
            if (dev.architectury.networking.NetworkManager.canServerReceive(
                    io.github.marcsanzdev.chestseparators.network.EditorLockRequestPayload.TYPE)) {
                dev.architectury.networking.NetworkManager.sendToServer(
                        new io.github.marcsanzdev.chestseparators.network.EditorLockRequestPayload(
                                session.currentChestPos, false));
            }
            hasEditorLock = false;
        }
    }

    public void toggleState(EditorState targetState) {
        EditorState previousState = session.currentState;

        if (session.currentState == targetState) {
            session.currentState = EditorState.HIDDEN;
        } else {
            session.currentState = targetState;
        }

        // Release the editor lock when transitioning away from any filter-editing state.
        boolean wasFilterState = (previousState == EditorState.VIEW_GROUPS
                || previousState == EditorState.SELECT_SLOTS
                || previousState == EditorState.EDIT_FILTER);
        boolean isFilterState = (session.currentState == EditorState.VIEW_GROUPS
                || session.currentState == EditorState.SELECT_SLOTS
                || session.currentState == EditorState.EDIT_FILTER);

        if (wasFilterState && !isFilterState) {
            releaseLock();
        }

        if (session.currentState == EditorState.DRAW_LINES || session.currentState == EditorState.HIDDEN) {
            this.session.selectedSlots.clear();
        }

        if (session.currentState == EditorState.HIDDEN) {
            GlobalChestConfig.saveConfig();
            session.editingLineCustomIndex = -1;
            session.editingBgCustomIndex = -1;
        }
    }

    public void startCreatingGroup(Slot initialSlot) {
        toggleState(EditorState.SELECT_SLOTS);
        session.selectedGroupId = UUID.randomUUID();
        session.selectedSlots.clear();

        if (initialSlot != null) {
            int key = slotKey(initialSlot);
            session.selectedSlots.add(key);
            session.lastClickedSlotIndex = key;
            session.lastSlotClickTime = System.currentTimeMillis();
            session.isSelecting = true;
        }
        playClickSound(1.0f);
    }

    public void prepareFilterMenu() {
        session.pendingRegionColor = null;
        session.finishingRegion = false;
        session.currentAllowedItems.clear();
        // The third rule is context-dependent: "Hopper" for a chest filter, "Pick Up" for an inventory
        // filter (they share the ruleHopper flag). Seed its default from the matching config option so an
        // inventory group defaults to the Pick Up default and a chest group to the Hopper default.
        boolean invFilter = false;
        for (int key : session.selectedSlots) {
            if (io.github.marcsanzdev.chestseparators.data.ChestConfigManager.isInventoryKey(key)) {
                invFilter = true;
                break;
            }
        }
        session.ruleManual = GlobalChestConfig.instance.defaultRuleManual;
        session.ruleShift = GlobalChestConfig.instance.defaultRuleShift;
        session.ruleHopper = invFilter
                ? GlobalChestConfig.instance.defaultRulePickup
                : GlobalChestConfig.instance.defaultRuleHopper;
        session.gridScrollY = 0f;
        session.listScrollY = 0f;

        if (session.selectedGroupId != null) {
            var whitelists = io.github.marcsanzdev.chestseparators.data.ChestConfigManager.getInstance()
                    .getCurrentWhitelists();
            boolean isExistingGroup = false;
            if (whitelists != null) {
                isExistingGroup =
                        whitelists.values().stream().anyMatch(wl -> session.selectedGroupId.equals(wl.groupId()));
            }

            if (isExistingGroup) {
                session.selectedSlots.clear();
                boolean loadedRules = false;

                for (java.util.Map.Entry<Integer, io.github.marcsanzdev.chestseparators.data.SlotWhitelist> entry :
                        whitelists.entrySet()) {
                    if (session.selectedGroupId.equals(entry.getValue().groupId())) {
                        session.selectedSlots.add(entry.getKey());

                        if (!loadedRules) {
                            session.currentAllowedItems.addAll(entry.getValue().allowedItems());
                            session.ruleManual = entry.getValue().allowManual();
                            session.ruleShift = entry.getValue().allowShift();
                            session.ruleHopper = entry.getValue().allowHopper();
                            loadedRules = true;
                        }
                    }
                }
            }
        }
        recomputeFilterConstraints();
        resetToDefaultCreativeTab();
        updateWhitelistSearchCache();

        // Rebuild the filter screen's widgets now that the container type is fully known. The initial
        // build happens during the editor's init(), BEFORE loadConfigForCurrentTarget() determines
        // isEnderChest — so without this the Ender Chest would keep its Hopper Insert button (which must be
        // dropped) and the rule buttons would not be re-centred.
        this.screenEditFilter.init();
    }

    /** The same selected zone owns its filter and appearance. Draft color commits with Save. */
    public void openRegionColor() {
        if (session.selectedSlots.isEmpty()) {
            showStatus(Component.literal("Selecciona una zona del cofre"), ChatFormatting.RED);
            return;
        }
        int color = session.currentState == EditorState.EDIT_FILTER && session.pendingRegionColor != null ? session.pendingRegionColor
                : ChestConfigManager.getInstance().getColor(session.selectedSlots.iterator().next(), ChestConfigManager.ACTION_BG);
        openColorPicker(color == 0 ? UiTheme.ACCENT : color, 3);
    }

    public void acceptRegionColor(int color) {
        if (session.currentState == EditorState.EDIT_FILTER) session.pendingRegionColor = color;
        else {
            ChestConfigManager.getInstance().saveSnapshot();
            paintSelectedRegion(color);
            saveSmart();
        }
    }

    private void paintSelectedRegion(int color) {
        var manager = ChestConfigManager.getInstance();
        for (var slot : accessor.getHandler().slots) {
            if (isEditableSlot(slot) && session.selectedSlots.contains(slotKey(slot)))
                manager.paintAction(slotKey(slot), ChestConfigManager.ACTION_BG, color | 0xFF000000);
        }
    }
    public void saveCurrentGroup() {
        if (session.pendingRegionColor != null) paintSelectedRegion(session.pendingRegionColor);
        var whitelists = ChestConfigManager.getInstance().getCurrentWhitelists();
        if (whitelists == null) whitelists = new java.util.HashMap<>();

        for (int slotIndex : session.selectedSlots) {
            io.github.marcsanzdev.chestseparators.data.SlotWhitelist wl =
                    new io.github.marcsanzdev.chestseparators.data.SlotWhitelist(
                            session.selectedGroupId,
                            new ArrayList<>(session.currentAllowedItems),
                            session.ruleManual,
                            session.ruleShift,
                            session.ruleHopper,
                            0);
            whitelists.put(slotIndex, wl);
        }
        ChestConfigManager.getInstance().setCurrentWhitelists(whitelists);

        saveSmart();
        syncClientInventoryWhitelists(whitelists);
        // sendWhitelistToServer() self-guards: it forwards block chests/shulkers and chest minecarts, and
        // skips ender chests, the player inventory, and other entity containers (all local-only).
        sendWhitelistToServer();
    }

    public void deleteCurrentGroup() {
        if (session.selectedGroupId == null) return;
        var whitelists = ChestConfigManager.getInstance().getCurrentWhitelists();
        if (whitelists != null) {
            whitelists
                    .entrySet()
                    .removeIf(entry ->
                            session.selectedGroupId.equals(entry.getValue().groupId()));
            ChestConfigManager.getInstance().setCurrentWhitelists(whitelists);

            saveSmart();
            syncClientInventoryWhitelists(whitelists);
            sendWhitelistToServer();
        }
    }

    /**
     * Pushes the container's own filters to the server. Self-guarding: it only sends for targets the
     * server can actually enforce and persist — real block chests/shulkers, and chest/hopper minecarts
     * (synced by entity UUID). Ender chests, the player inventory, and other entity containers (chest
     * boats, animals) stay local-only and are skipped here.
     */
    public void sendWhitelistToServer() {
        if (CommunityClient.available()) return;
        if (session.isEnderChest || session.isPlayerInventory) return;

        var chestOnly = io.github.marcsanzdev.chestseparators.data.ChestConfigManager.getInstance()
                .chestOnlyWhitelists();

        if (session.isMinecartChest && session.currentEntityUUID != null) {
            if (dev.architectury.networking.NetworkManager.canServerReceive(
                    io.github.marcsanzdev.chestseparators.network.EntityWhitelistPayload.TYPE)) {
                dev.architectury.networking.NetworkManager.sendToServer(
                        new io.github.marcsanzdev.chestseparators.network.EntityWhitelistPayload(
                                session.currentEntityUUID, chestOnly));
            }
            return;
        }

        // Any other entity container (chest boat, animals) is local-only.
        if (session.isEntityChest) return;

        if (dev.architectury.networking.NetworkManager.canServerReceive(
                io.github.marcsanzdev.chestseparators.network.WhitelistPayload.TYPE)) {
            BlockPos posToSend = session.currentChestPos != null ? session.currentChestPos : BlockPos.ZERO;
            dev.architectury.networking.NetworkManager.sendToServer(
                    new io.github.marcsanzdev.chestseparators.network.WhitelistPayload(
                            posToSend,
                            // Only the chest's own filters; inventory filters (offset keys) are local.
                            chestOnly));
        }
    }

    public void playClickSound(float pitch) {
        playUiSound(SoundEvents.UI_BUTTON_CLICK.value(), pitch);
    }

    /** Plays any UI sound event on the master track at the given pitch (non-positional, like the click). */
    public void playUiSound(net.minecraft.sounds.SoundEvent sound, float pitch) {
        Minecraft.getInstance()
                .getSoundManager()
                .play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        sound, pitch));
    }

    /**
     * The single "a panel was dismissed" sound. Every screen that can be closed by clicking outside it (or on
     * dead space) must play THIS, so exiting layout, filters, the item picker, presets and the palette all
     * sound identical instead of each using its own pitch.
     */
    public void playCloseSound() {
        playClickSound(1.0f);
    }

    public void saveSmart() {
        if (CommunityClient.available() && !session.isInventoryScreenContext) CommunityClient.request(io.github.marcsanzdev.chestseparators.network.CommunityRequest.SYNC, io.github.marcsanzdev.chestseparators.data.CommunityData.NONE, "");
        ChestConfigManager manager = ChestConfigManager.getInstance();

        var custom = io.github.marcsanzdev.chestseparators.compat.StorageCompatibility.get(accessor.getHandler());
        if (custom != null) {
            manager.saveEntityConfig(custom.identity());
            return;
        }
        // Container portion (chest screens only) — chest-only keys are saved to the container's store.
        if (!session.isInventoryScreenContext) {
            if (session.isShulkerBox && session.currentShulkerUUID != null) {
                manager.saveShulkerConfig(session.currentShulkerUUID);
            } else if (session.isEntityChest && session.currentEntityUUID != null) {
                manager.saveEntityConfig(session.currentEntityUUID);
            } else if (session.isEnderChest) {
                manager.saveEnderConfig();
            } else if (session.currentChestPos != null) {
                manager.saveConfig(session.currentChestPos, session.currentDimension);
            }
        }

        // Player inventory portion (always) — extracted from the offset keys and synced for Pick Up.
        // Player inventory editing is disabled; preserve the previous profile on disk.
        io.github.marcsanzdev.chestseparators.network.ModClientNetworking.sendInventoryFilters();
    }

    /** Asks the server to fill the inventory from the container currently open (the top fill icon). */
    /**
     * Pulls items from the open container into the inventory. Normally only items the inventory filters
     * want (up to each target). With {@code includeEmpty} (Shift), also pulls the rest of the container's
     * items into any free inventory space — the slots that have no filter.
     */
    public void requestFillFromOpenChest(boolean includeEmpty) {
        playClickSound(1.0f);
        net.minecraft.core.BlockPos pos = session.currentChestPos != null
                ? session.currentChestPos
                : (Minecraft.getInstance().player != null
                        ? Minecraft.getInstance().player.blockPosition()
                        : net.minecraft.core.BlockPos.ZERO);
        if (dev.architectury.networking.NetworkManager.canServerReceive(
                io.github.marcsanzdev.chestseparators.network.FillFromChestPayload.TYPE)) {
            boolean lockHotbar = io.github.marcsanzdev.chestseparators.config.GlobalChestConfig.instance == null
                    || io.github.marcsanzdev.chestseparators.config.GlobalChestConfig.instance.lockHotbarOnReorder;
            dev.architectury.networking.NetworkManager.sendToServer(
                    new io.github.marcsanzdev.chestseparators.network.FillFromChestPayload(
                            pos, includeEmpty, lockHotbar));
        }
    }

    /** Loads inventory preset {@code index} (1-based) into the live editor, or reports it is empty. */
    public void loadInventoryPresetSlot(int index) {
        ChestConfigManager manager = ChestConfigManager.getInstance();
        if (!manager.loadInventoryPreset(index)) {
            showStatus(Component.translatable("message.chestseparators.preset_empty", index), ChatFormatting.RED);
            playClickSound(0.6f);
            return;
        }
        saveSmart();
        syncClientInventoryWhitelists(manager.getCurrentWhitelists());
        showStatus(Component.translatable("message.chestseparators.preset_loaded", index), ChatFormatting.GREEN);
        playClickSound(1.1f);
    }

    /** Saves the current inventory layout + filters into preset {@code index} (1-based). */
    public void saveInventoryPresetSlot(int index) {
        ChestConfigManager.getInstance().saveInventoryPreset(index);
        showStatus(Component.translatable("message.chestseparators.preset_saved", index), ChatFormatting.GREEN);
        playClickSound(1.2f);
    }

    /** The open container's slot count (27 vs 54), used to pick the matching chest-preset set. */
    public int chestPresetSize() {
        var custom = io.github.marcsanzdev.chestseparators.compat.StorageCompatibility.get(accessor.getHandler());
        return custom != null ? custom.size() : geometry.getNamespaceSlotCount(false);
    }

    /** Loads chest preset {@code index} (1-based) of the open container's size, or reports it is empty. */
    public void loadChestPresetSlot(int index) {
        ChestConfigManager manager = ChestConfigManager.getInstance();
        if (!manager.loadChestPreset(chestPresetSize(), index)) {
            showStatus(Component.translatable("message.chestseparators.preset_empty", index), ChatFormatting.RED);
            playClickSound(0.6f);
            return;
        }
        saveSmart();
        sendWhitelistToServer();
        syncClientInventoryWhitelists(manager.getCurrentWhitelists());
        showStatus(Component.translatable("message.chestseparators.preset_loaded", index), ChatFormatting.GREEN);
        playClickSound(1.1f);
    }

    /** Saves the open chest's current layout + filters into its size's chest preset {@code index}. */
    public void saveChestPresetSlot(int index) {
        ChestConfigManager.getInstance().saveChestPreset(chestPresetSize(), index);
        showStatus(Component.translatable("message.chestseparators.preset_saved", index), ChatFormatting.GREEN);
        playClickSound(1.2f);
    }

    /** Deletes inventory preset {@code index} (1-based). */
    public void deleteInventoryPresetSlot(int index) {
        ChestConfigManager.getInstance().deleteInventoryPreset(index);
        showStatus(Component.translatable("message.chestseparators.preset_deleted", index), ChatFormatting.RED);
        playClickSound(0.8f);
    }

    /** Deletes chest preset {@code index} (1-based) of the open container's size. */
    public void deleteChestPresetSlot(int index) {
        ChestConfigManager.getInstance().deleteChestPreset(chestPresetSize(), index);
        showStatus(Component.translatable("message.chestseparators.preset_deleted", index), ChatFormatting.RED);
        playClickSound(0.8f);
    }

    public void triggerActionAnimation(int actionId) {
        session.clickedActionId = actionId;
        session.clickedActionTime = System.currentTimeMillis();
    }

    /**
     * Finalizes a unified undo/redo: persists the restored state, re-syncs filters to the server and
     * the local inventory (the shared history can change filters too), and shows feedback naming what
     * was undone/redone. {@code labelKey} comes from {@link ChestConfigManager#undo()}/redo(); a null
     * label means there was nothing to do.
     */
    public void applyUndoRedo(String labelKey, boolean isRedo) {
        if (labelKey == null) {
            playClickSound(0.5f);
            return;
        }
        saveSmart();
        sendWhitelistToServer();
        syncClientInventoryWhitelists(ChestConfigManager.getInstance().getCurrentWhitelists());

        // Blink the slots that changed, colored by the kind of change.
        undoHighlights = new HashMap<>(ChestConfigManager.getInstance().getLastUndoChanges());
        undoHighlightStart = System.currentTimeMillis();

        String feedbackKey = isRedo ? "message.chestseparators.redone" : "message.chestseparators.undone";
        showStatus(Component.translatable(feedbackKey, Component.translatable(labelKey)), ChatFormatting.GRAY);
        playClickSound(0.8f);
    }

    public void showStatus(Component message, ChatFormatting color) {
        session.statusMessage = message.copy().withStyle(color);
        session.statusMessageTime = System.currentTimeMillis();
    }

    /** Shows a status message tinted with an exact ARGB/RGB color (used for color copy/paste feedback). */
    public void showStatus(Component message, int rgbColor) {
        net.minecraft.network.chat.Style style =
                net.minecraft.network.chat.Style.EMPTY.withColor(net.minecraft.network.chat.TextColor.fromRgb(rgbColor & 0xFFFFFF));
        session.statusMessage = message.copy().setStyle(style);
        session.statusMessageTime = System.currentTimeMillis();
    }

    public boolean isHovering(int x, int y, int width, int height, double mouseX, double mouseY) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    /** Whether either Shift key is held right now (polled from GLFW at call time). */
    private static boolean isShiftDown() {
        long window = Minecraft.getInstance().getWindow().getWindow();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
    }

    public int shiftColor(int color, int amount) {
        int a = (color >> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        r = Mth.clamp(r + amount, 0, 255);
        g = Mth.clamp(g + amount, 0, 255);
        b = Mth.clamp(b + amount, 0, 255);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public void openColorPicker(int initialColor, int targetMode) {
        session.isColorPickerOpen = true;
        session.pickerTargetMode = targetMode;
        this.colorPickerModified = false;

        // Default cursors to the top-left of the picker (pure red, full saturation, full brightness).
        session.pickerHue = 0.0f;
        session.pickerSat = 1.0f;
        session.pickerVal = 1.0f;

        if (initialColor == 0) {
            // Keep RGB at 0 to show the checkerboard transparency pattern for an empty slot.
            session.pickerCurrentRGB = 0;
        } else {
            // Convert the existing color to HSB so the cursors open at the correct position.
            float[] hsb = new float[3];
            int r = (initialColor >> 16) & 0xFF;
            int g = (initialColor >> 8) & 0xFF;
            int b = initialColor & 0xFF;
            java.awt.Color.RGBtoHSB(r, g, b, hsb);
            session.pickerHue = hsb[0];
            session.pickerSat = hsb[1];
            session.pickerVal = hsb[2];
            session.pickerCurrentRGB = initialColor | 0xFF000000;
        }

        if (this.screenColorPicker != null) {
            this.screenColorPicker.onOpen();
        }
    }

    public void updateTracePath(String step) {
        if (step == null || step.isEmpty()) return;
        if (session.tracePath.isEmpty()) {
            session.tracePath.add(step);
            return;
        }
        if (session.tracePath.get(session.tracePath.size() - 1).equals(step)) {
            return;
        }

        int index = session.tracePath.indexOf(step);
        if (index != -1) {
            session.tracePath.subList(index + 1, session.tracePath.size()).clear();
        } else {
            session.tracePath.add(step);
        }
    }

    public void renderNormalModeOverlay(GuiGraphics context, int mouseX, int mouseY) {
        if (this.renderer != null) this.renderer.renderNormalModeOverlay(context, mouseX, mouseY);

        // Only render the floating status message while the main editor panel is closed.
        if (session.currentState == EditorState.HIDDEN && session.statusMessage != null) {
            long elapsed = System.currentTimeMillis() - session.statusMessageTime;

            if (elapsed < 2000) {
                int alpha = 255;
                if (elapsed > 1500) {
                    alpha = (int) (255 * (1.0f - (elapsed - 1500) / 500.0f));
                }

                if (alpha > 10) {
                    net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
                    int color = (alpha << 24) | 0xFFFFFF;
                    io.github.marcsanzdev.chestseparators.client.ui.UiTheme.centered(context, 
                            client.font, session.statusMessage, screen.width / 2, screen.height - 40, color);
                }
            } else {
                session.statusMessage = null;
            }
        }
    }

    public void renderSavedLinesLayer(GuiGraphics context) {
        if (this.renderer != null) this.renderer.renderSavedLinesLayer(context);
    }

    public boolean keyPressed(io.github.marcsanzdev.chestseparators.client.input.KeyEvent input) {
        boolean isSearchFocused = (searchBox != null && searchBox.isFocused());
        boolean isWhitelistSearchFocused = (whitelistSearchBox != null && whitelistSearchBox.isFocused());

        if (isSearchFocused) {
            if (searchBox.keyPressed(input.key(), input.scancode(), input.modifiers())) return true;
            if (input.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
                searchBox.setFocused(false);
                return true;
            }
        }
        if (session.isColorPickerOpen && screenColorPicker.keyPressed(input)) return true;
        if (this.inputHandler != null) return this.inputHandler.keyPressed(input);
        return false;
    }

    public boolean charTyped(io.github.marcsanzdev.chestseparators.client.input.CharacterEvent input) {
        if (session.isColorPickerOpen && screenColorPicker.charTyped(input)) return true;
        if (this.inputHandler != null) return this.inputHandler.charTyped(input);
        return false;
    }

    /** Runs the editor's click handling directly (used by the creative-inventory mixin to block tabs). */
    public void handleEditorClick(double mouseX, double mouseY, int button) {
        if (this.inputHandler != null) this.inputHandler.handleClick(mouseX, mouseY, button);
    }

    public List<String> extractItemsFromSelection() {
        Set<String> extracted = new LinkedHashSet<>();
        if (accessor.getHandler() != null && accessor.getHandler().slots != null) {
            // selectedSlots holds slot KEYS, not raw handler indices: player-inventory keys carry the
            // PLAYER_KEY_OFFSET, so indexing the handler directly missed every inventory slot (the offset
            // key is out of range) and the import button did nothing for inventory filters. Resolve each
            // key back to its Slot, which works for both the chest (offset 0) and the inventory.
            for (int key : session.selectedSlots) {
                Slot slot = slotForKey(key);
                if (slot == null) continue;
                ItemStack stack = slot.getItem();
                if (!stack.isEmpty()) {
                    String id = net.minecraft.core.registries.BuiltInRegistries.ITEM
                            .getKey(stack.getItem())
                            .toString();
                    if (!session.currentAllowedItems.contains(id)) extracted.add(id);
                }
            }
        }
        return new ArrayList<>(extracted);
    }

    public void buildCustomChestTab() {
        session.customChestItems.clear();
        java.util.Set<Item> uniqueItems = new java.util.LinkedHashSet<>();

        if (accessor.getHandler() != null && accessor.getHandler().slots != null) {
            for (Slot slot : accessor.getHandler().slots) {
                if (!(slot.container instanceof net.minecraft.world.entity.player.Inventory) && slot.hasItem()) {
                    uniqueItems.add(slot.getItem().getItem());
                }
            }
            for (Slot slot : accessor.getHandler().slots) {
                if (slot.container instanceof net.minecraft.world.entity.player.Inventory && slot.hasItem()) {
                    uniqueItems.add(slot.getItem().getItem());
                }
            }
        }
        session.customChestItems.addAll(uniqueItems);
    }

    /**
     * Resets the creative-item browser to its default tab and first page whenever a filter menu is
     * opened, so the last-used tab (e.g. "Chest & Inventory") and any pending search text never carry
     * over between filters. The default is the first available tab, matching the initial open.
     */
    private void resetToDefaultCreativeTab() {
        session.currentTabPage = 0;
        for (int i = 0; i < session.availableTabs.size(); i++) {
            if (session.availableTabs.get(i) != null) {
                switchCreativeTab(i);
                return;
            }
        }
    }

    public void switchCreativeTab(int tabIndex) {
        if (tabIndex < 0 || tabIndex >= session.availableTabs.size()) return;
        session.currentCreativeTabIndex = tabIndex;
        session.gridScrollY = 0f;
        session.isPreviewing = false;

        EditorSessionData.CreativeTabInfo activeTab = session.availableTabs.get(tabIndex);
        if (activeTab == null) return;

        if (!activeTab.isSearchTab && this.searchBox != null) this.searchBox.setValue("");
        session.filteredItems.clear();

        Minecraft client = Minecraft.getInstance();
        net.minecraft.world.flag.FeatureFlagSet features =
                client.level != null ? client.level.enabledFeatures() : null;
        CreativeModeTab.ItemDisplayParameters context = features != null
                ? new CreativeModeTab.ItemDisplayParameters(features, false, client.level.registryAccess())
                : null;

        if (activeTab.isCustomChestTab) {
            buildCustomChestTab();
            for (Item item : session.customChestItems) {
                if (isItemAllowedForFilter(item)) session.filteredItems.add(item);
            }
        } else if (activeTab.isSearchTab) {
            boolean isSearching =
                    this.searchBox != null && !this.searchBox.getValue().isEmpty();
            String lowerQuery = isSearching ? this.searchBox.getValue().toLowerCase() : "";

            for (EditorSessionData.CreativeTabInfo tab : session.availableTabs) {
                if (tab == null || tab.isSearchTab || tab.isCustomChestTab || tab.group == null) continue;
                java.util.Collection<ItemStack> displayStacks = tab.group.getDisplayItems();

                if ((displayStacks == null || displayStacks.isEmpty()) && context != null) {
                    tab.group.buildContents(context);
                    displayStacks = tab.group.getDisplayItems();
                }

                if (displayStacks != null) {
                    for (ItemStack stack : displayStacks) {
                        Item item = stack.getItem();
                        if (!session.filteredItems.contains(item)
                                && isItemAllowedInMenu(item)
                                && isItemAllowedForFilter(item)) {
                            if (isSearching) {
                                boolean matches = false;
                                if (lowerQuery.startsWith("#")) {
                                    String searchTag = lowerQuery.substring(1);
                                    matches = item.builtInRegistryHolder()
                                            .tags()
                                            .anyMatch(t -> t.location().getPath().contains(searchTag));
                                } else {
                                    matches = item.getDescription()
                                            .getString()
                                            .toLowerCase()
                                            .contains(lowerQuery);
                                }
                                if (matches) session.filteredItems.add(item);
                            } else {
                                session.filteredItems.add(item);
                            }
                        }
                    }
                }
            }
        } else if (activeTab.group != null) {
            java.util.Collection<ItemStack> displayStacks = activeTab.group.getDisplayItems();
            if ((displayStacks == null || displayStacks.isEmpty()) && context != null) {
                activeTab.group.buildContents(context);
                displayStacks = activeTab.group.getDisplayItems();
            }

            if (displayStacks != null && !displayStacks.isEmpty()) {
                for (ItemStack stack : displayStacks) {
                    Item item = stack.getItem();
                    if (!session.filteredItems.contains(item)
                            && isItemAllowedInMenu(item)
                            && isItemAllowedForFilter(item)) {
                        session.filteredItems.add(item);
                    }
                }
            }
        }
    }

    public boolean isItemAllowedInMenu(Item item) {
        if (item instanceof SpawnEggItem) return false;
        net.minecraft.resources.ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item);
        if (id == null) return false;

        String path = id.getPath();
        if (path.contains("infested")) return false;
        if (path.contains("command_block")) return false;

        java.util.Set<String> blacklist = java.util.Set.of(
                "air",
                "bedrock",
                "spawner",
                "reinforced_deepslate",
                "end_portal_frame",
                "knowledge_book",
                "trial_spawner",
                "vault",
                "barrier",
                "light",
                "debug_stick",
                "structure_block",
                "structure_void",
                "jigsaw",
                "test_block",
                "test_instance_block",
                "player_head",
                "farmland",
                "suspicious_sand",
                "suspicious_gravel",
                "budding_amethyst",
                "dirt_path",
                "chorus_plant",
                "frogspawn");

        return !blacklist.contains(path);
    }

    public void updateWhitelistSearchCache() {
        session.visibleLeftListItems.clear();
        String wlSearch = this.whitelistSearchBox != null
                ? this.whitelistSearchBox.getValue().toLowerCase()
                : "";

        for (String id : session.currentAllowedItems) {
            Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.tryParse(id));
            if (item != null) {
                boolean matches = false;
                if (wlSearch.isEmpty()) {
                    matches = true;
                } else if (wlSearch.startsWith("#")) {
                    String searchTag = wlSearch.substring(1);
                    matches = item.builtInRegistryHolder().tags().anyMatch(t -> t.location().getPath()
                            .contains(searchTag));
                } else {
                    matches = item.getDescription().getString().toLowerCase().contains(wlSearch);
                }

                if (matches) session.visibleLeftListItems.add(id);
            }
        }
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!isEditMode()) {
            return screenViewGroups.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        } else {
            if (session.currentState == EditorState.VIEW_GROUPS || session.currentState == EditorState.SELECT_SLOTS) {
                return screenViewGroups.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
            } else if (session.currentState == EditorState.EDIT_FILTER) {
                return screenEditFilter.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
            }
        }
        return false;
    }

    public void syncClientInventoryWhitelists(Map<Integer, SlotWhitelist> whitelists) {
        if (Minecraft.getInstance().player == null) return;
        AbstractContainerMenu handler = Minecraft.getInstance().player.containerMenu;
        if (handler == null || handler.slots.isEmpty()) return;
        var custom = io.github.marcsanzdev.chestseparators.compat.StorageCompatibility.get(handler);
        if (custom != null) { custom.filters(whitelists); return; }

        // Iterate all non-player slots and push whitelists to every unique IWhitelistProvider found.
        // This handles chest minecarts (entity IS the inventory), chest boats (same), and
        // donkeys/llamas (cargo SimpleInventory is a separate instance from the saddle slot inventory).
        java.util.Set<IWhitelistProvider> visited = new java.util.HashSet<>();
        for (Slot slot : handler.slots) {
            if (slot.container instanceof net.minecraft.world.entity.player.Inventory) continue;
            if (slot.container instanceof IWhitelistProvider provider) {
                if (visited.add(provider)) {
                    provider.setWhitelists(whitelists);
                }
            }
        }
    }

    public void executeDeposit(boolean shift) {
        if (accessor.getHandler() == null || net.minecraft.client.Minecraft.getInstance().player == null) return;
        if (!accessor.getHandler().getCarried().isEmpty()) return;

        var whitelists = io.github.marcsanzdev.chestseparators.data.ChestConfigManager.getInstance()
                .getCurrentWhitelists();
        net.minecraft.client.multiplayer.MultiPlayerGameMode interactionManager =
                net.minecraft.client.Minecraft.getInstance().gameMode;
        int syncId = accessor.getHandler().containerId;
        net.minecraft.world.entity.player.Player player = net.minecraft.client.Minecraft.getInstance().player;

        int totalMovedCount = 0;

        for (net.minecraft.world.inventory.Slot playerSlot : accessor.getHandler().slots) {
            if (playerSlot.container instanceof net.minecraft.world.entity.player.Inventory && playerSlot.hasItem()) {
                net.minecraft.world.item.ItemStack stackToMove = playerSlot.getItem().copy();
                String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM
                        .getKey(stackToMove.getItem())
                        .toString();
                int remainingOnCursor = stackToMove.getCount();

                // Build the target slot list using the 4-pass priority system.
                List<net.minecraft.world.inventory.Slot> prioritizedSlots = new ArrayList<>();

                // Pass 1: Filtered slots with a matching existing stack (stacking).
                collectTargetSlots(prioritizedSlots, whitelists, itemId, stackToMove, true, false);
                // Pass 2: Empty filtered slots (filling).
                collectTargetSlots(prioritizedSlots, whitelists, itemId, stackToMove, false, false);

                if (shift) {
                    // Pass 3: Unfiltered slots with a matching existing stack (stacking).
                    collectTargetSlots(prioritizedSlots, whitelists, itemId, stackToMove, true, true);
                    // Pass 4: Empty unfiltered slots (filling).
                    collectTargetSlots(prioritizedSlots, whitelists, itemId, stackToMove, false, true);
                }

                if (!prioritizedSlots.isEmpty()) {
                    boolean pickedUp = false;
                    for (net.minecraft.world.inventory.Slot targetSlot : prioritizedSlots) {
                        if (remainingOnCursor <= 0) break;

                        int spaceLeft = stackToMove.getMaxStackSize()
                                - (targetSlot.hasItem() ? targetSlot.getItem().getCount() : 0);
                        if (spaceLeft > 0) {
                            if (!pickedUp) {
                                interactionManager.handleInventoryMouseClick(
                                        syncId,
                                        playerSlot.index,
                                        0,
                                        net.minecraft.world.inventory.ClickType.PICKUP,
                                        player);
                                pickedUp = true;
                            }
                            interactionManager.handleInventoryMouseClick(
                                    syncId, targetSlot.index, 0, net.minecraft.world.inventory.ClickType.PICKUP, player);
                            int moved = Math.min(spaceLeft, remainingOnCursor);
                            remainingOnCursor -= moved;
                            totalMovedCount += moved;
                        }
                    }
                    if (pickedUp && remainingOnCursor > 0) {
                        interactionManager.handleInventoryMouseClick(
                                syncId, playerSlot.index, 0, net.minecraft.world.inventory.ClickType.PICKUP, player);
                    }
                }
            }
        }

        if (totalMovedCount > 0) {
            showStatus(
                    net.minecraft.network.chat.Component.translatable("message.chestseparators.deposited", totalMovedCount),
                    net.minecraft.ChatFormatting.GREEN);
            this.suspendDepositPreview = true;
            this.previewSourceRemaining.clear();
            this.previewTargetIncoming.clear();

            // Apply the server's post-deposit re-sort to the CLIENT's predicted container right now, so the
            // items show their final packed order ([64,1]) from the first frame. Without this the client
            // briefly renders its raw deposit prediction ([1,64]) and then jumps when the server's re-sort
            // syncs back — a visible flicker. Mirrors reorderFilteredGroups exactly.
            if (io.github.marcsanzdev.chestseparators.compat.StorageCompatibility.get(accessor.getHandler()) == null)
                applyClientDepositReorder(whitelists);

            // Ask the server to re-sort the container's filtered groups by priority order, so a
            // just-deposited higher-priority item ends up ahead of lower-priority ones already stored
            // (the deposit above only fills slots; it never relocates existing stacks).
            if (dev.architectury.networking.NetworkManager.canServerReceive(
                    io.github.marcsanzdev.chestseparators.network.SortOpenFiltersPayload.TYPE)) {
                dev.architectury.networking.NetworkManager.sendToServer(
                        new io.github.marcsanzdev.chestseparators.network.SortOpenFiltersPayload());
            }
        } else {
            // Be specific: "no filters at all" is a different situation from "filters exist but none has
            // room / matches", and reads far clearer to the player. Both use the same muted grey as the
            // pull/grab empty notices so every "nothing happened" message looks the same across the mod.
            boolean anyContainerFilter = false;
            for (net.minecraft.world.inventory.Slot s : accessor.getHandler().slots) {
                if (!(s.container instanceof net.minecraft.world.entity.player.Inventory)
                        && whitelists != null
                        && whitelists.containsKey(s.getContainerSlot())) {
                    anyContainerFilter = true;
                    break;
                }
            }
            showStatus(
                    net.minecraft.network.chat.Component.translatable(
                            anyContainerFilter
                                    ? "message.chestseparators.push_no_slot"
                                    : "message.chestseparators.push_no_filters"),
                    net.minecraft.ChatFormatting.GRAY);
        }
    }

    /**
     * Client-side mirror of {@code ModNetworking.reorderFilteredGroups}: packs each filtered group's stacks
     * by the filter's priority order (fuller stack first for a same-item tie) into its lowest slots, applied
     * to the open container's predicted slots so the deposit's final layout is shown with no flicker.
     */
    private void applyClientDepositReorder(
            Map<Integer, io.github.marcsanzdev.chestseparators.data.SlotWhitelist> whitelists) {
        if (whitelists == null || whitelists.isEmpty()) return;

        java.util.Map<java.util.UUID, List<net.minecraft.world.inventory.Slot>> groups =
                new java.util.LinkedHashMap<>();
        for (net.minecraft.world.inventory.Slot s : accessor.getHandler().slots) {
            if (s.container instanceof net.minecraft.world.entity.player.Inventory) continue;
            io.github.marcsanzdev.chestseparators.data.SlotWhitelist wl = whitelists.get(s.getContainerSlot());
            if (wl == null || wl.groupId() == null) continue;
            groups.computeIfAbsent(wl.groupId(), g -> new ArrayList<>()).add(s);
        }

        for (List<net.minecraft.world.inventory.Slot> gslots : groups.values()) {
            if (gslots.size() < 2) continue;
            gslots.sort(java.util.Comparator.comparingInt(net.minecraft.world.inventory.Slot::getContainerSlot));
            List<String> order = whitelists
                    .get(gslots.get(0).getContainerSlot())
                    .allowedItems();

            List<net.minecraft.world.item.ItemStack> stacks = new ArrayList<>();
            for (net.minecraft.world.inventory.Slot s : gslots) {
                if (!s.getItem().isEmpty()) stacks.add(s.getItem());
            }
            if (stacks.isEmpty()) continue;
            stacks.sort(java.util.Comparator.<net.minecraft.world.item.ItemStack>comparingInt(st -> {
                        int r = order.indexOf(net.minecraft.core.registries.BuiltInRegistries.ITEM
                                .getKey(st.getItem())
                                .toString());
                        return r < 0 ? Integer.MAX_VALUE : r;
                    })
                    .thenComparing(java.util.Comparator.comparingInt(
                                    net.minecraft.world.item.ItemStack::getCount)
                            .reversed()));

            for (int i = 0; i < gslots.size(); i++) {
                gslots.get(i)
                        .set(i < stacks.size() ? stacks.get(i) : net.minecraft.world.item.ItemStack.EMPTY);
            }
        }
    }

    private void collectTargetSlots(
            List<net.minecraft.world.inventory.Slot> list,
            Map<Integer, io.github.marcsanzdev.chestseparators.data.SlotWhitelist> whitelists,
            String itemId,
            net.minecraft.world.item.ItemStack stack,
            boolean checkExisting,
            boolean unfilteredOnly) {
        List<net.minecraft.world.inventory.Slot> collected = new ArrayList<>();
        for (net.minecraft.world.inventory.Slot chestSlot : accessor.getHandler().slots) {
            if (!isEditableSlot(chestSlot)) continue;
            if (list.contains(chestSlot) || !chestSlot.isActive() || !chestSlot.mayPlace(stack)) continue;

            boolean hasFilter = whitelists != null && whitelists.containsKey(chestSlot.getContainerSlot());
            boolean matchesFilter = hasFilter
                    && whitelists.get(chestSlot.getContainerSlot()).allowedItems().contains(itemId);

            if (unfilteredOnly) {
                if (hasFilter) continue;
            } else {
                if (!matchesFilter) continue;
            }

            if (checkExisting) {
                if (!chestSlot.hasItem()) continue;
                if (!net.minecraft.world.item.ItemStack.isSameItem(stack, chestSlot.getItem())
                        || !net.minecraft.world.item.ItemStack.isSameItemSameComponents(stack, chestSlot.getItem()))
                    continue;
                if (chestSlot.getItem().getCount() >= chestSlot.getItem().getMaxStackSize()) continue;
            } else {
                if (chestSlot.hasItem()) continue;
            }
            collected.add(chestSlot);
        }

        // Empty filtered slots are filled in the filter's own order: the item first in the list heads for
        // the group's first slot, and so on. Ties (and every other pass) keep the handler's slot order.
        if (!checkExisting && !unfilteredOnly) {
            collected.sort(java.util.Comparator.comparingInt(
                    s -> io.github.marcsanzdev.chestseparators.util.FilterPriority.slotPreference(
                            whitelists, s.getContainerSlot(), itemId)));
        }
        list.addAll(collected);
    }

    /**
     * Client-side simulation of the Pull button for its hover preview: marks each container slot whose
     * item would be pulled into the inventory (via the inventory filters' keep-limits, or — with Shift —
     * any unfiltered item), storing the amount that would remain afterwards in {@link #previewSourceRemaining}.
     * Mirrors {@code performFillFromOpenContainer} on the server.
     */
    public void updateFillPreview(boolean shift) {
        previewSourceRemaining.clear();
        previewTargetIncoming.clear();
        if (accessor.getHandler() == null || net.minecraft.client.Minecraft.getInstance().player == null) return;

        java.util.Map<Integer, io.github.marcsanzdev.chestseparators.data.SlotWhitelist> invFilters =
                ChestConfigManager.getInstance().getPlayerInventoryFilters();

        // Player inventory slots, sorted by inventory index (0-8 hotbar, then 9-35 main) — NOT the menu's
        // slot order, which lists the main inventory before the hotbar. The real pull fills via vanilla
        // Inventory#getFreeSlot, which scans items[0..35] (hotbar first), so the preview must simulate the
        // same order or the ghost lands in the wrong slot (e.g. main row 1 instead of the hotbar).
        java.util.List<net.minecraft.world.inventory.Slot> playerSlots = new java.util.ArrayList<>();
        for (net.minecraft.world.inventory.Slot s : accessor.getHandler().slots) {
            if (s.container instanceof net.minecraft.world.entity.player.Inventory) playerSlots.add(s);
        }
        playerSlots.sort(java.util.Comparator.comparingInt(net.minecraft.world.inventory.Slot::getContainerSlot));

        for (net.minecraft.world.inventory.Slot chestSlot : accessor.getHandler().slots) {
            if (chestSlot.container instanceof net.minecraft.world.entity.player.Inventory || !chestSlot.hasItem())
                continue;
            net.minecraft.world.item.ItemStack stack = chestSlot.getItem();
            String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM
                    .getKey(stack.getItem())
                    .toString();

            // Mirrors performFillFromOpenContainer: pull items the inventory filters list; with Shift, pull
            // everything into free space.
            if (!shift && !inventoryListsItemClient(invFilters, itemId)) continue;

            int placed = placeIntoPlayerPreview(playerSlots, invFilters, stack, itemId, stack.getCount(), shift);
            if (placed <= 0) continue; // inventory full — nothing actually moves from this slot
            previewSourceRemaining.put(chestSlot.index, stack.getCount() - placed);
        }

        // Mirror the server's post-pull inventory group re-sort (honouring the hotbar lock) so the preview
        // shows the same final layout the real pull will produce.
        boolean lockHotbar = io.github.marcsanzdev.chestseparators.config.GlobalChestConfig.instance == null
                || io.github.marcsanzdev.chestseparators.config.GlobalChestConfig.instance.lockHotbarOnReorder;
        int firstSortable = lockHotbar ? 9 : 0;
        java.util.List<net.minecraft.world.inventory.Slot> mainSlots = new java.util.ArrayList<>();
        for (net.minecraft.world.inventory.Slot s : playerSlots) {
            int idx = realIndex(s);
            if (idx >= firstSortable && idx < net.minecraft.world.entity.player.Inventory.INVENTORY_SIZE) {
                mainSlots.add(s);
            }
        }
        applyPreviewReorder(mainSlots, invFilters, ChestSeparatorsEditor::realIndex);
    }

    /**
     * Simulates PlayerInventory#insertStack (with the mod's inventory-filter routing) for the pull
     * preview: tops up matching partial stacks first, then places into empty slots honouring the filters —
     * an item prefers an empty slot whose active filter lists it, and never lands in a slot reserved for a
     * different item — exactly as {@code PlayerInventoryFilterMixin#getEmptySlot} does at pull time.
     * Accumulates the projected incoming amount per player slot in {@link #previewTargetIncoming}.
     * Returns how many of {@code amount} actually fit.
     */
    private int placeIntoPlayerPreview(
            java.util.List<net.minecraft.world.inventory.Slot> playerSlots,
            java.util.Map<Integer, io.github.marcsanzdev.chestseparators.data.SlotWhitelist> invFilters,
            net.minecraft.world.item.ItemStack stack,
            String itemId,
            int amount,
            boolean shift) {
        int maxC = stack.getMaxStackSize();
        int left = amount;

        // Pass 1: top up existing matching stacks (real contents or already-projected incoming). Without
        // Shift this is restricted to slots whose filter lists the item (mirrors insertRespectingFilter);
        // with Shift it tops up any matching stack (mirrors vanilla Inventory#add).
        for (net.minecraft.world.inventory.Slot ps : playerSlots) {
            if (left <= 0) break;
            if (!shift) {
                io.github.marcsanzdev.chestseparators.data.SlotWhitelist wl =
                        invFilters != null ? invFilters.get(ps.getContainerSlot()) : null;
                if (wl == null || !wl.allowedItems().contains(itemId)) continue;
            }
            net.minecraft.world.item.ItemStack real = ps.getItem();
            net.minecraft.world.item.ItemStack proj = previewTargetIncoming.get(ps.index);
            int current;
            if (!real.isEmpty()) {
                if (!net.minecraft.world.item.ItemStack.isSameItemSameComponents(stack, real)) continue;
                current = real.getCount() + (proj != null ? proj.getCount() : 0);
            } else if (proj != null) {
                if (!net.minecraft.world.item.ItemStack.isSameItemSameComponents(stack, proj)) continue;
                current = proj.getCount();
            } else {
                continue; // empty slot handled below
            }
            int canAdd = maxC - current;
            if (canAdd <= 0) continue;
            int add = Math.min(left, canAdd);
            if (proj == null) previewTargetIncoming.put(ps.index, stack.copyWithCount(add));
            else proj.setCount(proj.getCount() + add);
            left -= add;
        }

        if (!shift) {
            // Pass 2 (no Shift): drop the remainder ONLY into empty filtered slots, in filter priority order —
            // never overflow into unfiltered slots. Mirrors insertRespectingFilter phase 2 exactly.
            java.util.List<net.minecraft.world.inventory.Slot> emptyFiltered = new java.util.ArrayList<>();
            for (net.minecraft.world.inventory.Slot ps : playerSlots) {
                if (!ps.getItem().isEmpty() || previewTargetIncoming.containsKey(ps.index)) continue;
                io.github.marcsanzdev.chestseparators.data.SlotWhitelist wl =
                        invFilters != null ? invFilters.get(ps.getContainerSlot()) : null;
                if (wl == null || !wl.allowedItems().contains(itemId)) continue;
                emptyFiltered.add(ps);
            }
            emptyFiltered.sort(java.util.Comparator.comparingInt(
                            (net.minecraft.world.inventory.Slot ps) ->
                                    io.github.marcsanzdev.chestseparators.util.FilterPriority.slotPreference(
                                            invFilters, ps.getContainerSlot(), itemId))
                    .thenComparingInt(net.minecraft.world.inventory.Slot::getContainerSlot));
            for (net.minecraft.world.inventory.Slot ps : emptyFiltered) {
                if (left <= 0) break;
                int add = Math.min(left, maxC);
                previewTargetIncoming.put(ps.index, stack.copyWithCount(add));
                left -= add;
            }
        } else {
            // Pass 2 (Shift): empty slots one at a time via the filter-aware getEmptySlot equivalent, which
            // falls back to any unreserved empty slot (mirrors the vanilla spill of Inventory#add).
            while (left > 0) {
                net.minecraft.world.inventory.Slot target = findPreviewEmptySlot(playerSlots, invFilters, itemId);
                if (target == null) break;
                int add = Math.min(left, maxC);
                previewTargetIncoming.put(target.index, stack.copyWithCount(add));
                left -= add;
            }
        }
        return amount - left;
    }

    /** Mirror of PlayerInventoryFilterMixin#getEmptySlot: preferred filtered empty slot, else first unreserved. */
    private net.minecraft.world.inventory.Slot findPreviewEmptySlot(
            java.util.List<net.minecraft.world.inventory.Slot> playerSlots,
            java.util.Map<Integer, io.github.marcsanzdev.chestseparators.data.SlotWhitelist> invFilters,
            String itemId) {
        // Preferred: the empty slot whose active filter lists this item and whose place in the filter's
        // order best fits it (item first in the list -> group's first slot, and so on; ties keep the
        // lowest index). Mirrors the same slotPreference choice getEmptySlot makes at pull time.
        net.minecraft.world.inventory.Slot best = null;
        int bestPreference = Integer.MAX_VALUE;
        for (net.minecraft.world.inventory.Slot ps : playerSlots) {
            if (!ps.getItem().isEmpty() || previewTargetIncoming.containsKey(ps.index)) continue;
            io.github.marcsanzdev.chestseparators.data.SlotWhitelist wl =
                    invFilters != null ? invFilters.get(ps.getContainerSlot()) : null;
            if (wl == null || !isSlotFilterActive(wl) || !wl.allowedItems().contains(itemId)) continue;
            int preference = io.github.marcsanzdev.chestseparators.util.FilterPriority.slotPreference(
                    invFilters, ps.getContainerSlot(), itemId);
            if (preference < bestPreference) {
                bestPreference = preference;
                best = ps;
            }
        }
        if (best != null) return best;
        // Otherwise the first empty slot not reserved for a DIFFERENT item.
        for (net.minecraft.world.inventory.Slot ps : playerSlots) {
            if (!ps.getItem().isEmpty() || previewTargetIncoming.containsKey(ps.index)) continue;
            io.github.marcsanzdev.chestseparators.data.SlotWhitelist wl =
                    invFilters != null ? invFilters.get(ps.getContainerSlot()) : null;
            boolean reservedForOther =
                    wl != null && isSlotFilterActive(wl) && !wl.allowedItems().contains(itemId);
            if (!reservedForOther) return ps;
        }
        return null;
    }

    private static boolean isSlotFilterActive(io.github.marcsanzdev.chestseparators.data.SlotWhitelist wl) {
        return wl.allowHopper() || wl.allowManual() || wl.allowShift();
    }

    /** Client mirror of the server's inventoryListsItem: does any inventory filter list this item? */
    private boolean inventoryListsItemClient(
            java.util.Map<Integer, io.github.marcsanzdev.chestseparators.data.SlotWhitelist> invFilters,
            String itemId) {
        if (invFilters == null) return false;
        for (io.github.marcsanzdev.chestseparators.data.SlotWhitelist wl : invFilters.values()) {
            if (wl.allowedItems().contains(itemId)) return true;
        }
        return false;
    }

    /**
     * True when a push/pull preview is active and this slot is part of it — the vanilla item render is
     * then suppressed for that slot so the preview draws a clean ghost with no real item (and its count)
     * bleeding through underneath.
     */
    public boolean isPreviewSlot(net.minecraft.world.inventory.Slot slot) {
        if (!isHoveringDeposit && !isHoveringFill) return false;
        return previewSourceRemaining.containsKey(slot.index) || previewTargetIncoming.containsKey(slot.index);
    }

    public void updateDepositPreview(boolean shift) {
        previewSourceRemaining.clear();
        previewTargetIncoming.clear();

        if (accessor.getHandler() == null || net.minecraft.client.Minecraft.getInstance().player == null) return;
        var whitelists = io.github.marcsanzdev.chestseparators.data.ChestConfigManager.getInstance()
                .getCurrentWhitelists();

        for (net.minecraft.world.inventory.Slot playerSlot : accessor.getHandler().slots) {
            if (playerSlot.container instanceof net.minecraft.world.entity.player.Inventory && playerSlot.hasItem()) {
                net.minecraft.world.item.ItemStack pristineStack = playerSlot.getItem();
                String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM
                        .getKey(pristineStack.getItem())
                        .toString();

                int remaining = pristineStack.getCount();
                int initialCount = remaining;

                // 4-pass priority system mirrors the logic in executeDeposit.
                remaining = simulateDepositPass(whitelists, pristineStack, itemId, remaining, true, false);

                if (remaining > 0)
                    remaining = simulateDepositPass(whitelists, pristineStack, itemId, remaining, false, false);

                if (remaining > 0 && shift)
                    remaining = simulateDepositPass(whitelists, pristineStack, itemId, remaining, true, true);

                if (remaining > 0 && shift)
                    remaining = simulateDepositPass(whitelists, pristineStack, itemId, remaining, false, true);

                if (remaining < initialCount) {
                    previewSourceRemaining.put(playerSlot.index, remaining);
                }
            }
        }

        // Mirror the server's post-push per-group priority re-sort so the ghost preview lands items in the
        // exact slots the real deposit will after it re-organizes the chest.
        java.util.List<net.minecraft.world.inventory.Slot> chestSlots = new java.util.ArrayList<>();
        for (net.minecraft.world.inventory.Slot s : accessor.getHandler().slots) {
            if (!(s.container instanceof net.minecraft.world.entity.player.Inventory)) chestSlots.add(s);
        }
        applyPreviewReorder(chestSlots, whitelists, net.minecraft.world.inventory.Slot::getContainerSlot);
    }

    private int simulateDepositPass(
            Map<Integer, io.github.marcsanzdev.chestseparators.data.SlotWhitelist> whitelists,
            net.minecraft.world.item.ItemStack stack,
            String itemId,
            int count,
            boolean checkExisting,
            boolean unfilteredOnly) {
        // Empty filtered slots are visited in the filter's order, exactly as executeDeposit fills them, so
        // the ghost preview lands items in the same slots the real deposit will.
        List<net.minecraft.world.inventory.Slot> orderedSlots = new ArrayList<>();
        for (net.minecraft.world.inventory.Slot chestSlot : accessor.getHandler().slots) {
            if (!isEditableSlot(chestSlot)) continue;
            orderedSlots.add(chestSlot);
        }
        if (!checkExisting && !unfilteredOnly) {
            orderedSlots.sort(java.util.Comparator.comparingInt(
                    s -> io.github.marcsanzdev.chestseparators.util.FilterPriority.slotPreference(
                            whitelists, s.getContainerSlot(), itemId)));
        }

        for (net.minecraft.world.inventory.Slot chestSlot : orderedSlots) {
            if (count <= 0) break;

            boolean hasFilter = whitelists != null && whitelists.containsKey(chestSlot.getContainerSlot());
            boolean matchesFilter = hasFilter
                    && whitelists.get(chestSlot.getContainerSlot()).allowedItems().contains(itemId);

            if (unfilteredOnly) {
                if (hasFilter) continue;
            } else {
                if (!matchesFilter) continue;
            }

            // If a slot already has a virtual preview stack, only continue if the incoming item type matches.
            net.minecraft.world.item.ItemStack incoming = previewTargetIncoming.get(chestSlot.index);
            if (incoming != null) {
                if (!net.minecraft.world.item.ItemStack.isSameItem(stack, incoming)
                        || !net.minecraft.world.item.ItemStack.isSameItemSameComponents(stack, incoming)) {
                    continue;
                }
            }

            if (checkExisting) {
                if (!chestSlot.hasItem()) continue;
                if (!net.minecraft.world.item.ItemStack.isSameItem(stack, chestSlot.getItem())
                        || !net.minecraft.world.item.ItemStack.isSameItemSameComponents(stack, chestSlot.getItem()))
                    continue;
            } else {
                if (chestSlot.hasItem()) continue;
            }

            int currentInChest = chestSlot.hasItem() ? chestSlot.getItem().getCount() : 0;
            if (incoming != null) {
                currentInChest += incoming.getCount();
            }

            int spaceLeft = stack.getMaxStackSize() - currentInChest;
            if (spaceLeft > 0) {
                int toMove = Math.min(spaceLeft, count);
                count -= toMove;

                if (incoming == null) {
                    incoming = stack.copy();
                    incoming.setCount(toMove);
                } else {
                    incoming.grow(toMove);
                }
                previewTargetIncoming.put(chestSlot.index, incoming);
            }
        }
        return count;
    }

    /**
     * Rewrites {@link #previewTargetIncoming} so the preview shows the SAME final arrangement the real
     * push/pull produces — including the server's per-group priority re-sort. Programming rule of this mod:
     * a preview must ALWAYS render exactly what the action will do, so any change to the placement/sort
     * logic must be mirrored here. Two steps: (1) turn every projected entry from an "incoming delta" into
     * the slot's FINAL full stack, then (2) for each filtered group among {@code destSlots}, re-lay the
     * group's final stacks in priority order packed to the first slot — mirroring
     * {@code ChestSeparatorsMain#reorderFilteredGroups}. Slots that end unchanged are dropped from the map
     * (so they render normally); slots emptied by the sort are stored as an empty stack.
     *
     * @param destSlots  the slots the action re-sorts (chest slots for push; player MAIN slots, minus the
     *                   hotbar when locked, for pull)
     * @param whitelists filters keyed the way {@code indexOf} keys the slots
     * @param indexOf    maps a slot to its whitelist key (raw index for a chest, realIndex for the inventory)
     */
    private void applyPreviewReorder(
            java.util.List<net.minecraft.world.inventory.Slot> destSlots,
            java.util.Map<Integer, io.github.marcsanzdev.chestseparators.data.SlotWhitelist> whitelists,
            java.util.function.ToIntFunction<net.minecraft.world.inventory.Slot> indexOf) {
        if (whitelists == null || whitelists.isEmpty() || previewTargetIncoming.isEmpty()) return;

        // Step 1: incoming delta -> final full stack, for every currently projected slot.
        for (Integer slotId : new java.util.ArrayList<>(previewTargetIncoming.keySet())) {
            net.minecraft.world.inventory.Slot s = accessor.getHandler().getSlot(slotId);
            net.minecraft.world.item.ItemStack inc = previewTargetIncoming.get(slotId);
            net.minecraft.world.item.ItemStack fin = inc.copy();
            if (!s.getItem().isEmpty() && net.minecraft.world.item.ItemStack.isSameItemSameComponents(s.getItem(), inc)) {
                fin.setCount(s.getItem().getCount() + inc.getCount());
            }
            previewTargetIncoming.put(slotId, fin);
        }

        // Step 2: re-sort each filtered group by priority order, packed to its first slot.
        java.util.Map<java.util.UUID, java.util.List<net.minecraft.world.inventory.Slot>> groups =
                new java.util.LinkedHashMap<>();
        for (net.minecraft.world.inventory.Slot s : destSlots) {
            io.github.marcsanzdev.chestseparators.data.SlotWhitelist wl = whitelists.get(indexOf.applyAsInt(s));
            if (wl == null || wl.groupId() == null) continue;
            groups.computeIfAbsent(wl.groupId(), g -> new java.util.ArrayList<>())
                    .add(s);
        }
        for (java.util.List<net.minecraft.world.inventory.Slot> gslots : groups.values()) {
            if (gslots.size() < 2) continue;
            gslots.sort(java.util.Comparator.comparingInt(indexOf));
            java.util.List<String> order =
                    whitelists.get(indexOf.applyAsInt(gslots.get(0))).allowedItems();

            // Final content of each group slot: the projected override if present, else its real stack.
            java.util.List<net.minecraft.world.item.ItemStack> finals = new java.util.ArrayList<>();
            for (net.minecraft.world.inventory.Slot s : gslots) {
                net.minecraft.world.item.ItemStack fin =
                        previewTargetIncoming.containsKey(s.index) ? previewTargetIncoming.get(s.index) : s.getItem();
                if (!fin.isEmpty()) finals.add(fin);
            }
            finals.sort(java.util.Comparator.<net.minecraft.world.item.ItemStack>comparingInt(st -> {
                        int r = order.indexOf(net.minecraft.core.registries.BuiltInRegistries.ITEM
                                .getKey(st.getItem())
                                .toString());
                        return r < 0 ? Integer.MAX_VALUE : r;
                    })
                    // Match reorderFilteredGroups: same-item overflow packs the fuller stack first, so the
                    // preview shows exactly what the deposit + server re-sort will produce ([64,1] not [1,64]).
                    .thenComparing(java.util.Comparator.comparingInt(
                                    net.minecraft.world.item.ItemStack::getCount)
                            .reversed()));

            for (int i = 0; i < gslots.size(); i++) {
                net.minecraft.world.inventory.Slot s = gslots.get(i);
                net.minecraft.world.item.ItemStack fin =
                        i < finals.size() ? finals.get(i) : net.minecraft.world.item.ItemStack.EMPTY;
                net.minecraft.world.item.ItemStack real = s.getItem();
                boolean unchanged = net.minecraft.world.item.ItemStack.isSameItemSameComponents(fin, real)
                        && fin.getCount() == real.getCount();
                if (unchanged) previewTargetIncoming.remove(s.index);
                else previewTargetIncoming.put(s.index, fin);
            }
        }
    }
}





