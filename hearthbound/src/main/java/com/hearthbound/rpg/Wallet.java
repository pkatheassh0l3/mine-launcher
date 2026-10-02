package com.hearthbound.rpg;

import com.hearthbound.item.CoinItem;
import com.hearthbound.registry.ModRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Coins in the player's inventory. Payments take any coins and give change back. */
public final class Wallet {
    private Wallet() {}

    public static int balance(Player p) {
        int total = 0;
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.getItem() instanceof CoinItem c) total += c.value * s.getCount();
        }
        return total;
    }

    public static boolean pay(ServerPlayer p, int amount) {
        if (amount <= 0) return true;
        int bal = balance(p);
        if (bal < amount) return false;
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).getItem() instanceof CoinItem) inv.setItem(i, ItemStack.EMPTY);
        }
        give(p, bal - amount);
        Rpg.data(p).coinsSpent += amount;
        return true;
    }

    public static void give(ServerPlayer p, int amount) {
        if (amount <= 0) return;
        giveCoins(p, ModRegistry.GOLD_COIN.get(), amount / 100);
        amount %= 100;
        giveCoins(p, ModRegistry.SILVER_COIN.get(), amount / 10);
        giveCoins(p, ModRegistry.COPPER_COIN.get(), amount % 10);
    }

    private static void giveCoins(ServerPlayer p, Item coin, int n) {
        while (n > 0) {
            int c = Math.min(n, 64);
            ItemStack s = new ItemStack(coin, c);
            if (!p.getInventory().add(s)) p.drop(s, false);
            n -= c;
        }
    }

    /** "1 g 2 s 5 c" style formatting (as a translation-free string). */
    public static String format(int copper) {
        int g = copper / 100, s = (copper % 100) / 10, c = copper % 10;
        StringBuilder b = new StringBuilder();
        if (g > 0) b.append(g).append("g ");
        if (s > 0 || g > 0) b.append(s).append("s ");
        b.append(c).append("c");
        return b.toString();
    }
}
