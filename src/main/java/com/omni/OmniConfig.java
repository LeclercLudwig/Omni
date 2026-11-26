package com.omni;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Configuration commune (config/omni-common.toml). */
public final class OmniConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue START_ON_KHESSAR;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        START_ON_KHESSAR = builder
                .comment("Les nouveaux joueurs commencent sur Khessar, à côté de leur capsule de survie écrasée.")
                .define("startOnKhessar", true);
        SPEC = builder.build();
    }

    private OmniConfig() {}
}
