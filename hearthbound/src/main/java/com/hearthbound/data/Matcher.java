package com.hearthbound.data;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Matches registry entries: {@code minecraft:wheat}, {@code #minecraft:logs} (tag),
 * {@code @create} (every entry of a mod), {@code minecraft:*_log} (wildcard) or {@code *}.
 */
public final class Matcher<T> {
    private final String raw;
    private final ResourceLocation id;
    private final TagKey<T> tag;
    private final String namespace;
    private final Pattern glob;
    private final boolean any;

    private Matcher(String raw, ResourceKey<? extends Registry<T>> registry) {
        this.raw = raw;
        ResourceLocation id = null;
        TagKey<T> tag = null;
        String ns = null;
        Pattern glob = null;
        boolean any = false;
        if (raw.equals("*")) {
            any = true;
        } else if (raw.startsWith("#")) {
            ResourceLocation rl = ResourceLocation.tryParse(raw.substring(1));
            if (rl != null) tag = TagKey.create(registry, rl);
        } else if (raw.startsWith("@")) {
            ns = raw.substring(1);
        } else if (raw.contains("*")) {
            String s = raw.contains(":") ? raw : "minecraft:" + raw;
            glob = Pattern.compile(Pattern.quote(s).replace("*", "\\E.*\\Q"));
        } else {
            id = ResourceLocation.tryParse(raw);
        }
        this.id = id;
        this.tag = tag;
        this.namespace = ns;
        this.glob = glob;
        this.any = any;
    }

    public static <T> Matcher<T> of(String raw, ResourceKey<? extends Registry<T>> registry) {
        return new Matcher<>(raw.trim(), registry);
    }

    public boolean test(Holder<T> holder) {
        if (any) return true;
        if (tag != null) return holder.is(tag);
        ResourceLocation key = holder.unwrapKey().map(ResourceKey::location).orElse(null);
        if (key == null) return false;
        if (id != null) return id.equals(key);
        if (namespace != null) return namespace.equals(key.getNamespace());
        if (glob != null) return glob.matcher(key.toString()).matches();
        return false;
    }

    public ResourceLocation id() {
        return id;
    }

    public TagKey<T> tag() {
        return tag;
    }

    public String raw() {
        return raw;
    }

    public static <T> boolean any(List<Matcher<T>> list, Holder<T> holder) {
        for (Matcher<T> m : list) if (m.test(holder)) return true;
        return false;
    }
}
