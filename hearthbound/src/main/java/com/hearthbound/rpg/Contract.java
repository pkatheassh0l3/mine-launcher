package com.hearthbound.rpg;

import com.hearthbound.data.ContractTemplate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A rolled contract: first an offer on a village board, then an active contract of a player.
 * Everything needed to display and resolve it is stored here, so it survives datapack edits.
 */
public final class Contract {
    public UUID id = UUID.randomUUID();
    public UUID village;
    public String villageName = "";
    public ResourceLocation template;
    public ContractTemplate.Type type = ContractTemplate.Type.DELIVER;
    public String target = "";        // item / entity matcher or resource id
    public Item icon = Items.PAPER;
    public int count = 1;
    public int progress;
    public int coins;
    public int reputation;
    public int xp;
    public List<String> items = new ArrayList<>();
    public String title = "";
    /** Display data, e.g. the destination village of an envoy. */
    public String extra = "";
    public long expires;              // game time, 0 = never
    /** Personal quest of a settler (resident id), null for board contracts. */
    public UUID person;
    public String personName = "";
    public int stage;

    public boolean personal() {
        return person != null;
    }

    public boolean done() {
        return type == ContractTemplate.Type.DELIVER ? false : progress >= count;
    }

    /** Title line: custom title or generated from the type. */
    public Component title() {
        if (type == ContractTemplate.Type.BUILD) return Component.translatable("hearthbound.project.contract_title", Component.translatable(title), extra);
        if (!title.isEmpty()) return Component.translatable(title);
        return Component.translatable("hearthbound.contract.type." + type.name().toLowerCase(java.util.Locale.ROOT));
    }

    /** "Deliver 16× Wheat", "Slay 8× Zombie"... */
    public Component objective() {
        String k = "hearthbound.contract.objective." + type.name().toLowerCase(java.util.Locale.ROOT);
        if (type == ContractTemplate.Type.ENVOY) return Component.translatable(k, extra);
        if (type == ContractTemplate.Type.BUILD) return Component.translatable(k, Component.translatable(title), extra, count);
        Component what = targetName();
        return Component.translatable(k, count, what);
    }

    public Component targetName() {
        switch (type) {
            case DELIVER -> {
                if (target.startsWith("#")) return Component.translatableWithFallback("hearthbound.tag." + target.substring(1).replace(':', '.').replace('/', '.'), icon.getDescription().getString());
                return icon.getDescription();
            }
            case HUNT -> {
                if (target.equals("monster")) return Component.translatable("hearthbound.contract.monsters");
                ResourceLocation rl = ResourceLocation.tryParse(target);
                if (rl != null && BuiltInRegistries.ENTITY_TYPE.containsKey(rl)) return BuiltInRegistries.ENTITY_TYPE.get(rl).getDescription();
                return Component.literal(target);
            }
            case DONATE -> {
                return Component.translatable("hearthbound.resource." + target);
            }
            default -> {
                return Component.empty();
            }
        }
    }

    public static Contract fromTemplate(ContractTemplate t) {
        Contract c = new Contract();
        c.template = t.id;
        c.type = t.type;
        c.icon = t.icon;
        c.title = t.title;
        c.target = switch (t.type) {
            case DELIVER -> t.itemMatcher;
            case HUNT -> t.entityMatcher;
            case DONATE -> t.resource;
            default -> "";
        };
        c.items = new ArrayList<>(t.rewardItems);
        return c;
    }

    public Contract copy() {
        Contract c = load(save());
        c.id = UUID.randomUUID();
        return c;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putUUID("id", id);
        if (village != null) t.putUUID("village", village);
        t.putString("vname", villageName);
        if (template != null) t.putString("template", template.toString());
        t.putString("type", type.name());
        t.putString("target", target);
        t.putString("icon", BuiltInRegistries.ITEM.getKey(icon).toString());
        t.putInt("count", count);
        t.putInt("progress", progress);
        t.putInt("coins", coins);
        t.putInt("rep", reputation);
        t.putInt("xp", xp);
        ListTag l = new ListTag();
        for (String s : items) l.add(StringTag.valueOf(s));
        t.put("items", l);
        t.putString("title", title);
        t.putString("extra", extra);
        t.putLong("expires", expires);
        if (person != null) {
            t.putUUID("person", person);
            t.putString("pname", personName);
            t.putInt("stage", stage);
        }
        return t;
    }

    public static Contract load(CompoundTag t) {
        Contract c = new Contract();
        if (t.hasUUID("id")) c.id = t.getUUID("id");
        if (t.hasUUID("village")) c.village = t.getUUID("village");
        c.villageName = t.getString("vname");
        if (t.contains("template")) c.template = ResourceLocation.tryParse(t.getString("template"));
        try {
            c.type = ContractTemplate.Type.valueOf(t.getString("type"));
        } catch (IllegalArgumentException ignored) {
        }
        c.target = t.getString("target");
        ResourceLocation ic = ResourceLocation.tryParse(t.getString("icon"));
        c.icon = ic == null ? Items.PAPER : BuiltInRegistries.ITEM.get(ic);
        c.count = t.getInt("count");
        c.progress = t.getInt("progress");
        c.coins = t.getInt("coins");
        c.reputation = t.getInt("rep");
        c.xp = t.getInt("xp");
        for (Tag x : t.getList("items", Tag.TAG_STRING)) c.items.add(x.getAsString());
        c.title = t.getString("title");
        c.extra = t.getString("extra");
        c.expires = t.getLong("expires");
        if (t.hasUUID("person")) {
            c.person = t.getUUID("person");
            c.personName = t.getString("pname");
            c.stage = t.getInt("stage");
        }
        return c;
    }
}
