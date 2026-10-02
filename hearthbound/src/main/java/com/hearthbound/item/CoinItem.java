package com.hearthbound.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/** Village currency. Copper = 1, silver = 10, gold = 100. */
public class CoinItem extends Item {
    public final int value;

    public CoinItem(int value, Properties props) {
        super(props);
        this.value = value;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("hearthbound.coin.value", value).withStyle(ChatFormatting.GRAY));
        if (stack.getCount() > 1) {
            lines.add(Component.translatable("hearthbound.coin.stack", value * stack.getCount()).withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
