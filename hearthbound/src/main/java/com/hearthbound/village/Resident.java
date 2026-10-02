package com.hearthbound.village;

import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/** Bookkeeping for one settler of a village, alive or waiting to be replaced. */
public final class Resident {
    /** Stable identity of the person (survives respawns and moving to another village). */
    public UUID id = UUID.randomUUID();
    /** Life story id (see {@link com.hearthbound.data.Persona}), empty until assigned. */
    public String persona = "";
    /** Index of the trade origin used by generated stories, -1 if none. */
    public int origin = -1;
    public UUID entity;
    public String name;
    public Role role;
    public boolean female;
    public int variant;
    public int home = -1;      // index into buildings
    public int work = -1;      // index into buildings
    /** Head block of the bed this settler sleeps in, null until the village gives it one. */
    public net.minecraft.core.BlockPos bed;
    public long deadSince = -1; // game time, -1 when alive

    public boolean alive() {
        return deadSince < 0;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putUUID("id", id);
        t.putString("persona", persona);
        t.putInt("origin", origin);
        if (entity != null) t.putUUID("entity", entity);
        t.putString("name", name);
        t.putString("role", role.id());
        t.putBoolean("female", female);
        t.putInt("variant", variant);
        t.putInt("home", home);
        t.putInt("work", work);
        t.putLong("dead", deadSince);
        if (bed != null) t.put("bed", net.minecraft.nbt.NbtUtils.writeBlockPos(bed));
        return t;
    }

    public static Resident load(CompoundTag t) {
        Resident r = new Resident();
        if (t.hasUUID("id")) r.id = t.getUUID("id");
        r.persona = t.getString("persona");
        r.origin = t.contains("origin") ? t.getInt("origin") : -1;
        if (t.hasUUID("entity")) r.entity = t.getUUID("entity");
        r.name = t.getString("name");
        r.role = Role.byId(t.getString("role"));
        r.female = t.getBoolean("female");
        r.variant = t.getInt("variant");
        r.home = t.getInt("home");
        r.work = t.getInt("work");
        r.deadSince = t.getLong("dead");
        r.bed = net.minecraft.nbt.NbtUtils.readBlockPos(t, "bed").orElse(null);
        return r;
    }
}
