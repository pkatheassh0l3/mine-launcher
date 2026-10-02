package com.hearthbound.data;

import com.google.gson.JsonObject;
import com.hearthbound.village.Resource;
import com.hearthbound.village.Role;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A building type. The shape is generated procedurally from {@link Kind} and the culture's
 * palette, so a single definition looks different in every culture.
 * Loaded from {@code data/<ns>/hearthbound/buildings/<id>.json}.
 */
public final class BuildingDef {
    public enum Kind { HALL, HOUSE, FARM, FORGE, MARKET, TAVERN, TOWER, SHRINE, MAGE_TOWER, STOREHOUSE, WALL_GATE }

    public final ResourceLocation id;
    public final Kind kind;
    public final String name;
    public final String description;
    public final Item icon;
    public final int width;
    public final int depth;
    public final int floors;
    public final Map<Resource, Integer> cost = new EnumMap<>(Resource.class);
    public final Map<Resource, Integer> produces = new EnumMap<>(Resource.class);
    public final int residents;
    public final List<Role> roles = new ArrayList<>();
    public final int minTier;
    public final String age;

    public BuildingDef(ResourceLocation id, JsonObject j) {
        this.id = id;
        Kind k;
        try {
            k = Kind.valueOf(Json.str(j, "kind", "house").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            k = Kind.HOUSE;
        }
        this.kind = k;
        this.name = Json.str(j, "name", "hearthbound.building." + id.getPath());
        this.description = Json.str(j, "description", "hearthbound.building." + id.getPath() + ".desc");
        this.icon = Json.item(j, "icon", Items.OAK_DOOR);
        this.width = Math.max(5, Math.min(31, Json.integer(j, "width", 7)));
        this.depth = Math.max(5, Math.min(31, Json.integer(j, "depth", 7)));
        this.floors = Math.max(1, Math.min(6, Json.integer(j, "floors", 1)));
        for (var e : Json.intMap(j, "cost").entrySet()) {
            Resource r = Resource.byId(e.getKey());
            if (r != null) cost.put(r, e.getValue());
        }
        for (var e : Json.intMap(j, "produces").entrySet()) {
            Resource r = Resource.byId(e.getKey());
            if (r != null) produces.put(r, e.getValue());
        }
        int res = Json.integer(j, "residents", k == Kind.HOUSE ? -1 : 0);
        this.residents = res < 0 ? -1 : res;
        for (String s : Json.strings(j, "roles")) roles.add(Role.byId(s));
        this.minTier = Json.integer(j, "min_tier", 0);
        this.age = Json.str(j, "age", "");
    }

    public Component title() {
        return Component.translatable(name);
    }

    public Component desc() {
        return Component.translatableWithFallback(description, "");
    }
}
