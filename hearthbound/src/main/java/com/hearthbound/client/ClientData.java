package com.hearthbound.client;

import com.hearthbound.rpg.PlayerData;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** The client's copy of the player's character and known villages (from the "sync" message). */
public final class ClientData {
    public static final PlayerData DATA = new PlayerData();
    public static int cap = 50;
    public static int next = 100;
    public static int maxContracts = 3;
    public static int maxCompanions = 1;
    public static int maxRank = 5;
    public static int radar = 400;
    public static String nextAge;
    public static Component nextAgeName;
    public static boolean skillsLocked;
    public static Component skillsAge;
    public static boolean classChange;
    public static boolean synced;
    public static final List<KnownVillage> VILLAGES = new ArrayList<>();

    public record KnownVillage(UUID id, String name, String dim, int x, int y, int z, int tier, int color, String culture, boolean lord) {}

    private ClientData() {}

    public static void load(CompoundTag t) {
        DATA.load(t);
        cap = t.getInt("cap");
        next = Math.max(1, t.getInt("next"));
        maxContracts = t.getInt("maxContracts");
        maxCompanions = t.getInt("maxCompanions");
        maxRank = Math.max(1, t.getInt("maxRank"));
        radar = t.getInt("radar");
        var access = Minecraft.getInstance().level == null ? null : Minecraft.getInstance().level.registryAccess();
        nextAge = t.contains("nextAge") ? t.getString("nextAge") : null;
        nextAgeName = t.contains("nextAgeName") && access != null ? Component.Serializer.fromJson(t.getString("nextAgeName"), access) : null;
        skillsLocked = t.getBoolean("skillsLocked");
        skillsAge = t.contains("skillsAge") && access != null ? Component.Serializer.fromJson(t.getString("skillsAge"), access) : null;
        classChange = t.getBoolean("classChange");
        VILLAGES.clear();
        for (Tag x : t.getList("villages", Tag.TAG_COMPOUND)) {
            CompoundTag e = (CompoundTag) x;
            VILLAGES.add(new KnownVillage(e.getUUID("id"), e.getString("name"), e.getString("dim"), e.getInt("x"), e.getInt("y"), e.getInt("z"),
                    e.getInt("tier"), e.getInt("color"), e.getString("culture"), e.getBoolean("lord")));
        }
        synced = true;
    }

    public static Component json(String s) {
        var mc = Minecraft.getInstance();
        if (s == null || s.isEmpty() || mc.level == null) return Component.empty();
        try {
            Component c = Component.Serializer.fromJson(s, mc.level.registryAccess());
            return c == null ? Component.empty() : c;
        } catch (Exception e) {
            return Component.literal(s);
        }
    }

    public static void reset() {
        DATA.load(new PlayerData().save());
        VILLAGES.clear();
        synced = false;
    }
}
