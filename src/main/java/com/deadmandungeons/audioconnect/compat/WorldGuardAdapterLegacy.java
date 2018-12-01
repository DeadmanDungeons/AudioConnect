package com.deadmandungeons.audioconnect.compat;

import com.deadmandungeons.audioconnect.flags.FlagAdapter;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.DefaultFlag;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.SetFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;

class WorldGuardAdapterLegacy extends WorldGuardAdapter {

    private final WorldGuardPlugin worldGuardPlugin;

    WorldGuardAdapterLegacy() {
        try {
            worldGuardPlugin = (WorldGuardPlugin) Bukkit.getPluginManager().getPlugin("WorldGuard");
        } catch (Exception e) {
            throw new UnsupportedOperationException(e);
        }
    }

    @Override
    public void initRegionAdapter() {
        // nothing to do
    }

    @Override
    public <T, F extends Flag<T> & FlagAdapter<T>> SetFlag<T> initSetFlag(String flagName, F flag) {
        return new SetFlag<>(flagName, flag.toLegacy());
    }

    @Override
    public void installFlags(Flag<?>... flags) {
        try {
            Flag<?>[] flagsList = DefaultFlag.flagsList;

            Field flagsListField = DefaultFlag.class.getField("flagsList");
            Field modifiersField = Field.class.getDeclaredField("modifiers");
            modifiersField.setAccessible(true);
            modifiersField.setInt(flagsListField, flagsListField.getModifiers() & ~Modifier.FINAL);

            Flag<?>[] newFlagsList = new Flag<?>[flagsList.length + flags.length];
            System.arraycopy(flagsList, 0, newFlagsList, 0, flagsList.length);
            System.arraycopy(flags, 0, newFlagsList, flagsList.length, flags.length);

            flagsListField.set(null, newFlagsList);
        } catch (Exception e) {
            throw new UnsupportedOperationException(e);
        }
    }

    @Override
    public List<RegionManager> getRegionManagers() {
        return worldGuardPlugin.getGlobalRegionManager().getLoaded();
    }

    @Override
    public RegionManager getRegionManager(World world) {
        return worldGuardPlugin.getRegionManager(world);
    }

    @Override
    public ApplicableRegionSet getApplicableRegions(RegionManager regionManager, Location location) {
        return regionManager.getApplicableRegions(location);
    }

}
