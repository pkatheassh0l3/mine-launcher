package com.hearthbound.village;

import com.hearthbound.data.Culture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Map;

/**
 * Village banners. Each village gets a banner generated from its culture (base colour from the
 * culture colour, an emblem of its people and a border or stripe), flown on a pole next to the
 * plaza. The lord can replace it with any banner designed on a loom.
 */
public final class Flags {
    private Flags() {}

    private static final Map<String, String[]> EMBLEMS = Map.of(
            "valdoran", new String[]{"minecraft:straight_cross", "minecraft:rhombus", "minecraft:cross"},
            "sylvaran", new String[]{"minecraft:flower", "minecraft:circle", "minecraft:curly_border"},
            "durnhal", new String[]{"minecraft:bricks", "minecraft:triangle_top", "minecraft:half_horizontal_bottom"},
            "solarys", new String[]{"minecraft:circle", "minecraft:globe", "minecraft:rhombus"},
            "brumaverde", new String[]{"minecraft:skull", "minecraft:flower", "minecraft:gradient"},
            "hrimfell", new String[]{"minecraft:triangles_bottom", "minecraft:straight_cross", "minecraft:triangle_bottom"});
    private static final String[] GENERIC = {"minecraft:circle", "minecraft:rhombus", "minecraft:cross", "minecraft:flower"};
    private static final String[] TRIMS = {"minecraft:border", "minecraft:stripe_top", "minecraft:stripe_bottom", "minecraft:stripe_center",
            "minecraft:stripe_middle", "minecraft:half_horizontal", "minecraft:gradient", "minecraft:curly_border", "minecraft:triangles_top"};

    // ================================================================== data

    /** Generates the default banner if the village has none yet. */
    public static void ensure(Village v) {
        if (!v.flag.contains("base")) generate(v);
    }

    public static void generate(Village v) {
        RandomSource r = RandomSource.create(v.id.getMostSignificantBits() ^ 0x5F1A6L);
        Culture c = v.culture();
        DyeColor base = nearest(c == null ? 0xE0B25A : c.color);
        DyeColor accent = contrast(base, r);
        DyeColor second = r.nextBoolean() ? accent : contrast(base, r);
        String path = v.culture == null ? "" : v.culture.getPath();
        String[] emblems = EMBLEMS.getOrDefault(path, GENERIC);
        CompoundTag t = new CompoundTag();
        t.putString("base", base.getName());
        ListTag layers = new ListTag();
        layers.add(layer(TRIMS[r.nextInt(TRIMS.length)], second));
        layers.add(layer(emblems[r.nextInt(emblems.length)], accent));
        if (r.nextInt(3) == 0) layers.add(layer("minecraft:border", accent));
        t.put("layers", layers);
        v.flag = t;
    }

    private static CompoundTag layer(String pattern, DyeColor color) {
        CompoundTag e = new CompoundTag();
        e.putString("p", pattern);
        e.putString("c", color.getName());
        return e;
    }

    private static DyeColor nearest(int rgb) {
        DyeColor best = DyeColor.WHITE;
        double bestD = Double.MAX_VALUE;
        for (DyeColor d : DyeColor.values()) {
            int c = d.getTextureDiffuseColor();
            double dr = ((c >> 16) & 0xFF) - ((rgb >> 16) & 0xFF), dg = ((c >> 8) & 0xFF) - ((rgb >> 8) & 0xFF), db = (c & 0xFF) - (rgb & 0xFF);
            double dist = dr * dr * 0.3 + dg * dg * 0.59 + db * db * 0.11;
            if (dist < bestD) {
                bestD = dist;
                best = d;
            }
        }
        return best;
    }

    private static DyeColor contrast(DyeColor base, RandomSource r) {
        DyeColor[] good = {DyeColor.WHITE, DyeColor.YELLOW, DyeColor.BLACK, DyeColor.RED, DyeColor.LIGHT_BLUE, DyeColor.ORANGE, DyeColor.LIME, DyeColor.PURPLE};
        for (int i = 0; i < 12; i++) {
            DyeColor d = good[r.nextInt(good.length)];
            if (d != base && Math.abs(lum(d) - lum(base)) > 60) return d;
        }
        return lum(base) > 128 ? DyeColor.BLACK : DyeColor.WHITE;
    }

    private static int lum(DyeColor d) {
        int c = d.getTextureDiffuseColor();
        return (int) (((c >> 16) & 0xFF) * 0.3 + ((c >> 8) & 0xFF) * 0.59 + (c & 0xFF) * 0.11);
    }

    public static DyeColor base(Village v) {
        ensure(v);
        DyeColor d = DyeColor.byName(v.flag.getString("base"), DyeColor.WHITE);
        return d == null ? DyeColor.WHITE : d;
    }

    public static BannerPatternLayers layers(Village v, RegistryAccess access) {
        ensure(v);
        var reg = access.lookupOrThrow(Registries.BANNER_PATTERN);
        BannerPatternLayers.Builder b = new BannerPatternLayers.Builder();
        for (Tag x : v.flag.getList("layers", Tag.TAG_COMPOUND)) {
            CompoundTag e = (CompoundTag) x;
            ResourceLocation id = ResourceLocation.tryParse(e.getString("p"));
            if (id == null) continue;
            DyeColor color = DyeColor.byName(e.getString("c"), DyeColor.WHITE);
            reg.get(ResourceKey.create(Registries.BANNER_PATTERN, id)).ifPresent(h -> b.add(h, color));
        }
        return b.build();
    }

    /** The village banner as an item. */
    public static ItemStack item(Village v, RegistryAccess access) {
        ItemStack s = new ItemStack(BannerBlock.byColor(base(v)).asItem());
        s.set(DataComponents.BANNER_PATTERNS, layers(v, access));
        s.set(DataComponents.CUSTOM_NAME, Component.translatable("hearthbound.flag.name", v.name).withStyle(Style.EMPTY.withItalic(false)));
        return s;
    }

    /** The lord adopts the banner they hold. */
    public static boolean setFromItem(Village v, ItemStack held) {
        if (!(held.getItem() instanceof BannerItem bi)) return false;
        CompoundTag t = new CompoundTag();
        t.putString("base", bi.getColor().getName());
        ListTag layers = new ListTag();
        BannerPatternLayers pl = held.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY);
        for (BannerPatternLayers.Layer l : pl.layers()) {
            Holder<BannerPattern> h = l.pattern();
            h.unwrapKey().ifPresent(k -> layers.add(layer(k.location().toString(), l.color())));
        }
        t.put("layers", layers);
        t.putBoolean("custom", true);
        v.flag = t;
        return true;
    }

    // ================================================================== world

    /** Raises the flagpole next to the plaza the first time, or repaints the banner on it. */
    public static void placeInWorld(ServerLevel level, Village v) {
        ensure(v);
        if (v.flagPos == null) {
            BlockPos spot = findSpot(level, v);
            if (spot == null) return;
            for (int i = 0; i < 3; i++) level.setBlock(spot.above(i), Blocks.SPRUCE_FENCE.defaultBlockState(), 3);
            v.flagPos = spot.above(3);
            VillageData.get(level.getServer()).setDirty();
        }
        if (!level.isLoaded(v.flagPos)) return;
        BlockState cur = level.getBlockState(v.flagPos);
        boolean pole = level.getBlockState(v.flagPos.below()).is(Blocks.SPRUCE_FENCE);
        if (!(cur.getBlock() instanceof BannerBlock) && !(cur.isAir() && pole)) return; // removed by someone: respect it
        int rot = cur.getBlock() instanceof BannerBlock ? cur.getValue(BannerBlock.ROTATION) : level.random.nextInt(16);
        Block banner = BannerBlock.byColor(base(v));
        level.setBlock(v.flagPos, banner.defaultBlockState().setValue(BannerBlock.ROTATION, rot), 3);
        if (level.getBlockEntity(v.flagPos) instanceof BannerBlockEntity be) {
            be.fromItem(item(v, level.registryAccess()), base(v));
            be.setChanged();
            level.sendBlockUpdated(v.flagPos, level.getBlockState(v.flagPos), level.getBlockState(v.flagPos), 3);
        }
    }

    private static BlockPos findSpot(ServerLevel level, Village v) {
        int[][] offs = {{4, 4}, {-4, 4}, {4, -4}, {-4, -4}, {5, 0}, {0, 5}, {-5, 0}, {0, -5}, {6, 3}, {-6, 3}, {3, 6}, {3, -6}};
        List<net.minecraft.world.level.levelgen.structure.BoundingBox> areas = Projects.protectedAreas(v);
        for (int[] o : offs) {
            int x = v.center.getX() + o[0], z = v.center.getZ() + o[1];
            if (!level.hasChunk(x >> 4, z >> 4)) continue;
            int gy = Terraform.groundTop(level, x, z);
            BlockPos ground = new BlockPos(x, gy, z);
            if (Math.abs(gy - v.center.getY()) > 4) continue;
            if (!level.getBlockState(ground).isSolid() || com.hearthbound.world.PlayerBuilds.isPlayerBlock(level, ground)) continue;
            boolean clear = true;
            for (int i = 1; i <= 5; i++) if (!level.getBlockState(ground.above(i)).isAir()) clear = false;
            if (!clear) continue;
            boolean taken = false;
            for (PlacedBuilding b : v.buildings) if (b.bounds.inflatedBy(1).isInside(ground.above())) taken = true;
            for (var a : areas) if (a.isInside(ground.above())) taken = true;
            if (!taken) return ground.above();
        }
        return null;
    }
}
