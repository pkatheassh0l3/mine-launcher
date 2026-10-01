package io.github.marcsanzdev.chestseparators.mixin.client;

import io.github.marcsanzdev.chestseparators.config.GlobalChestConfig;
import io.github.marcsanzdev.chestseparators.data.ChestConfigManager;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws separator backgrounds and 2px border lines on the HUD hotbar slots.
 *
 * <p>MC 1.21.6+ uses a deferred GUI renderer (Matrix3x2fStack, no z) whose item
 * vs colored-quad layering is not a clean global submission order: drawing all
 * backgrounds up-front (before the item loop) leaves some slots' fills on top of
 * their item and others underneath. To get uniform "background under item"
 * behaviour, each slot's background is drawn at the HEAD of {@code renderSlot}
 * — immediately before that very slot's item is rendered — so the bg→item order is
 * local and adjacent for every slot.
 *
 * <p>Lines inject at RETURN, on top of everything. The vanilla selection highlight
 * is a 24×23 frame extending 4px around the 16×16 item area; a 16×16 background
 * fills only its transparent center, so the selected slot's background draws
 * normally, while the selected slot's own border lines (and the selection-facing
 * edges of its neighbours) are suppressed to keep the highlight frame pristine.
 */
@Mixin(Gui.class)
public class InGameHudHotbarLinesMixin {

    @Inject(method = "renderSlot", at = @At("HEAD"))
    private void chestseparators$renderSlotBackground(
            GuiGraphics context,
            int x,
            int y,
            DeltaTracker counter,
            Player player,
            ItemStack stack,
            int seq,
            CallbackInfo ci) {
        ChestConfigManager m = ChestConfigManager.getInstance();
        if (m.getPlayerInventoryVisual().isEmpty()) return;

        // The x passed in is the slot's item-area left edge (baseX + slot*20).
        // Derive the hotbar slot; bail on the offhand call (not an aligned slot).
        int rel = x - hotbarBase()[0];
        if (rel < 0 || rel % 20 != 0) return;
        int slot = rel / 20;
        if (slot < 0 || slot > 8) return;

        int bgColor = m.getInventoryColor(slot, ChestConfigManager.ACTION_BG);
        if (bgColor == 0) return;

        int bgAlpha = (GlobalChestConfig.instance.bgTransparency * 255 / 100) << 24;
        context.fill(x, y, x + 16, y + 16, (bgColor & 0xFFFFFF) | bgAlpha);
    }

    @Inject(method = "renderItemHotbar", at = @At("RETURN"))
    private void chestseparators$renderHotbarLines(GuiGraphics context, DeltaTracker counter, CallbackInfo ci) {
        ChestConfigManager m = ChestConfigManager.getInstance();
        if (m.getPlayerInventoryVisual().isEmpty()) return;

        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;
        int selected = client.player.getInventory().selected;

        int[] pos = hotbarBase();
        int baseX = pos[0], baseY = pos[1];
        int lineAlpha = (GlobalChestConfig.instance.lineTransparency * 255 / 100) << 24;

        for (int i = 0; i < 9; i++) {
            if (i == selected) continue; // selection highlight owns this slot's border

            int x = baseX + i * 20;
            int y = baseY;

            // Suppress the edge (and its corners) facing the selected slot: the 24×23
            // highlight frame extends 4px into the neighbour, where the 2px line would land.
            boolean skipRight = (i == selected - 1);
            boolean skipLeft = (i == selected + 1);

            int rTop = applyAlpha(m.getInventoryColor(i, ChestConfigManager.ACTION_TOP), lineAlpha);
            int rBot = applyAlpha(m.getInventoryColor(i, ChestConfigManager.ACTION_BOTTOM), lineAlpha);
            int rLeft = skipLeft ? 0 : applyAlpha(m.getInventoryColor(i, ChestConfigManager.ACTION_LEFT), lineAlpha);
            int rRight = skipRight ? 0 : applyAlpha(m.getInventoryColor(i, ChestConfigManager.ACTION_RIGHT), lineAlpha);

            if (rTop == 0 && rBot == 0 && rLeft == 0 && rRight == 0) continue;

            int sTop = m.getInventoryPaintSeq(i, ChestConfigManager.ACTION_TOP);
            int sBot = m.getInventoryPaintSeq(i, ChestConfigManager.ACTION_BOTTOM);
            int sLeft = m.getInventoryPaintSeq(i, ChestConfigManager.ACTION_LEFT);
            int sRight = m.getInventoryPaintSeq(i, ChestConfigManager.ACTION_RIGHT);

            // 2px lines fully cover the grey slot dividers of the hotbar texture.
            if (rTop != 0) context.fill(x, y - 2, x + 16, y, rTop);
            if (rBot != 0) context.fill(x, y + 16, x + 16, y + 18, rBot);
            if (rLeft != 0) context.fill(x - 2, y, x, y + 16, rLeft);
            if (rRight != 0) context.fill(x + 16, y, x + 18, y + 16, rRight);

            // Corners on the selection-facing side are guarded by boolean (not just a zero
            // color) because drawCorner falls back to the other edge's color when one is 0.
            if (!skipLeft) drawCorner(context, x - 2, y - 2, rTop, sTop, rLeft, sLeft);
            if (!skipRight) drawCorner(context, x + 16, y - 2, rTop, sTop, rRight, sRight);
            if (!skipLeft) drawCorner(context, x - 2, y + 16, rBot, sBot, rLeft, sLeft);
            if (!skipRight) drawCorner(context, x + 16, y + 16, rBot, sBot, rRight, sRight);
        }
    }

    private static int[] hotbarBase() {
        Minecraft client = Minecraft.getInstance();
        int w = client.getWindow().getGuiScaledWidth();
        int h = client.getWindow().getGuiScaledHeight();
        return new int[] {(w - 182) / 2 + 3, h - 19};
    }

    private static int applyAlpha(int color, int lineAlpha) {
        return color == 0 ? 0 : (color & 0x00FFFFFF) | lineAlpha;
    }

    /** Paints a 2×2 corner using the edge with the higher paint sequence. */
    private static void drawCorner(GuiGraphics context, int cx, int cy, int colorA, int seqA, int colorB, int seqB) {
        if (colorA == 0 && colorB == 0) return;
        int color;
        if (colorA == 0) color = colorB;
        else if (colorB == 0) color = colorA;
        else color = (seqA >= seqB) ? colorA : colorB;
        context.fill(cx, cy, cx + 2, cy + 2, color);
    }
}
