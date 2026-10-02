package com.hearthbound.item;

import com.hearthbound.village.VillageService;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;

import java.util.List;

/** Use on the ground to found your own village and become its lord. */
public class CharterItem extends Item {
    public CharterItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (ctx.getLevel().isClientSide) return InteractionResult.SUCCESS;
        if (!(ctx.getPlayer() instanceof ServerPlayer sp)) return InteractionResult.PASS;
        if (VillageService.foundByCharter(sp, ctx.getClickedPos().above())) {
            if (!sp.getAbilities().instabuild) ctx.getItemInHand().shrink(1);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.FAIL;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.hearthbound.village_charter.tip1").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("item.hearthbound.village_charter.tip2").withStyle(ChatFormatting.DARK_GRAY));
    }
}
