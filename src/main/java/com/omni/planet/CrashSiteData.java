package com.omni.planet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

/** Position du site du crash (capsule posée une seule fois par monde, partagée par tous les joueurs). */
public class CrashSiteData extends SavedData {
    public static final String NAME = "omni_crash_site";
    public static final Factory<CrashSiteData> FACTORY = new Factory<>(CrashSiteData::new, CrashSiteData::load);

    @Nullable
    private BlockPos arrival;

    @Nullable
    public BlockPos arrival() {
        return arrival;
    }

    public void setArrival(BlockPos pos) {
        this.arrival = pos.immutable();
        setDirty();
    }

    private static CrashSiteData load(CompoundTag tag, HolderLookup.Provider registries) {
        CrashSiteData data = new CrashSiteData();
        NbtUtils.readBlockPos(tag, "arrival").ifPresent(pos -> data.arrival = pos);
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        if (arrival != null) tag.put("arrival", NbtUtils.writeBlockPos(arrival));
        return tag;
    }
}
