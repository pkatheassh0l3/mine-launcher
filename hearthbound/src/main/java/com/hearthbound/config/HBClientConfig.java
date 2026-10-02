package com.hearthbound.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client configuration ({@code config/hearthbound-client.toml}), also editable from Mods → Config. */
public final class HBClientConfig {
    public enum Corner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue VILLAGE_BANNER;
    public static final ModConfigSpec.IntValue BANNER_SECONDS;
    public static final ModConfigSpec.BooleanValue TRACKER;
    public static final ModConfigSpec.EnumValue<Corner> TRACKER_CORNER;
    public static final ModConfigSpec.DoubleValue TRACKER_SCALE;
    public static final ModConfigSpec.DoubleValue TRACKER_OPACITY;
    public static final ModConfigSpec.BooleanValue COMPASS;
    public static final ModConfigSpec.BooleanValue NOTIFICATIONS;
    public static final ModConfigSpec.BooleanValue SOUNDS;
    public static final ModConfigSpec.BooleanValue ANIMATIONS;
    public static final ModConfigSpec.BooleanValue CULTURE_ACCENT;
    public static final ModConfigSpec.ConfigValue<String> ACCENT_COLOR;
    public static final ModConfigSpec.DoubleValue PANEL_OPACITY;
    public static final ModConfigSpec.BooleanValue SHOW_ROLE_IN_NAME;
    public static final ModConfigSpec.BooleanValue CUSTOM_SKINS;
    public static final ModConfigSpec.EnumValue<TrackerMode> TRACKER_MODE;
    public static final ModConfigSpec.IntValue TRACKER_SECONDS;

    public enum TrackerMode { ON_KEY, ALWAYS }
    public static final ModConfigSpec.DoubleValue CUSTOM_SKIN_SHARE;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("hud");
        VILLAGE_BANNER = b.comment("Show a banner when entering a village.").define("villageBanner", true);
        BANNER_SECONDS = b.defineInRange("bannerSeconds", 4, 1, 30);
        TRACKER = b.comment("Show the tracked contract on screen.").define("tracker", true);
        TRACKER_MODE = b.comment("ON_KEY: the tracker only appears for a few seconds when you press the character key (J); press J again while it is shown to open the character sheet. ALWAYS: always on screen.")
                .defineEnum("trackerMode", TrackerMode.ON_KEY);
        TRACKER_SECONDS = b.comment("Seconds the tracker stays on screen in ON_KEY mode.").defineInRange("trackerSeconds", 8, 1, 120);
        TRACKER_CORNER = b.defineEnum("trackerCorner", Corner.TOP_RIGHT);
        TRACKER_SCALE = b.defineInRange("trackerScale", 1.0, 0.5, 2.0);
        TRACKER_OPACITY = b.defineInRange("trackerOpacity", 0.75, 0.0, 1.0);
        COMPASS = b.comment("Show a compass arrow to the nearest known village (range from the Scouting skill).").define("villageCompass", true);
        NOTIFICATIONS = b.comment("Small popups for reputation, XP and coins.").define("notifications", true);
        b.pop();
        b.push("interface");
        SOUNDS = b.define("sounds", true);
        ANIMATIONS = b.define("animations", true);
        CULTURE_ACCENT = b.comment("Tint the village interface with the culture's color.").define("cultureAccent", true);
        ACCENT_COLOR = b.comment("Accent color when cultureAccent is off (hex).").define("accentColor", "#E0B25A");
        PANEL_OPACITY = b.defineInRange("panelOpacity", 0.92, 0.3, 1.0);
        SHOW_ROLE_IN_NAME = b.comment("Show the settler's job next to its name.").define("showRoleInName", true);
        b.pop();
        b.comment("Your own settler skins: put PNG skins (64x64 or 64x32) in",
                "config/hearthbound/skins/<culture>/<role>/ or config/hearthbound/skins/<culture>/any/ and press F3+T.").push("skins");
        CUSTOM_SKINS = b.define("customSkins", true);
        CUSTOM_SKIN_SHARE = b.comment("Share of settlers that use your skins when you have some for their culture/role (1 = all).")
                .defineInRange("customSkinShare", 1.0, 0.0, 1.0);
        b.pop();
        SPEC = b.build();
    }

    private HBClientConfig() {}

    public static int accent() {
        try {
            String s = ACCENT_COLOR.get().replace("#", "");
            return 0xFF000000 | Integer.parseInt(s, 16);
        } catch (Exception e) {
            return 0xFFE0B25A;
        }
    }
}
