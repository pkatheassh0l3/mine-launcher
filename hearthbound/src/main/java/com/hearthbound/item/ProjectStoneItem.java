package com.hearthbound.item;

import com.hearthbound.village.Projects;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;

import java.util.List;

/**
 * A foundation stone handed out when a player takes a building project. It only goes down inside
 * the village it belongs to, on a free plot.
 */
public class ProjectStoneItem extends BlockItem {
    public ProjectStoneItem(Block block, Properties props) {
        super(block, props);
    }

    public static CompoundTag data(ItemStack stack) {
        CustomData d = stack.get(DataComponents.CUSTOM_DATA);
        return d == null ? new CompoundTag() : d.copyTag();
    }

    @Override
    public InteractionResult place(BlockPlaceContext ctx) {
        if (!ctx.getLevel().isClientSide && ctx.getPlayer() instanceof ServerPlayer sp) {
            Component error = Projects.canPlace(sp, ctx.getItemInHand(), ctx.getClickedPos());
            if (error != null) {
                sp.displayClientMessage(error.copy().withColor(0xE06A5A), true);
                sp.inventoryMenu.sendAllDataToRemote(); // the client may have predicted the placement
                return InteractionResult.FAIL;
            }
            CompoundTag data = data(ctx.getItemInHand());
            InteractionResult r = super.place(ctx);
            if (r.consumesAction()) Projects.placed(sp, ctx.getClickedPos(), data);
            return r;
        }
        // the client waits for the server: it decides whether the plot is valid
        return ctx.getLevel().isClientSide ? InteractionResult.SUCCESS : super.place(ctx);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        CompoundTag d = data(stack);
        if (d.contains("type")) {
            lines.add(Component.translatable("hearthbound.project.stone_for",
                    Component.translatable("hearthbound.project." + d.getString("type").replace("hearthbound:", "").replace(':', '.')),
                    d.getInt("level"), d.getString("villageName")).withColor(0xE0B25A));
            lines.add(Component.translatable("hearthbound.project.stone_tip").withColor(0x9AA0AC));
        }
        super.appendHoverText(stack, ctx, lines, flag);
    }
}
