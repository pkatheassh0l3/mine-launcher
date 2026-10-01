package io.github.marcsanzdev.chestseparators.neoforge.mixin;

import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Pseudo
@Mixin(targets = "net.p3pp3rf1y.sophisticatedcore.settings.SettingsHandler", remap = false)
public interface SophisticatedSettingsAccessor {
    @Invoker("saveCategoryNbt") void absoluteOrder$saveCategory(CompoundTag settings, String key, CompoundTag value);
    @Accessor("markContentsDirty") Runnable absoluteOrder$markDirty();
}
