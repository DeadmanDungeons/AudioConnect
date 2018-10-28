package com.deadmandungeons.audioconnect.compat;

import com.deadmandungeons.audioconnect.flags.FlagAdapter;
import com.sk89q.worldedit.bukkit.BukkitUtil;
import com.sk89q.worldedit.bukkit.BukkitWorld;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.SetFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagRegistry;
import com.sk89q.worldguard.protection.managers.RegionManager;
import org.apache.commons.lang.reflect.MethodUtils;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Arrays;
import java.util.List;

class WorldGuardAdapterV7 extends WorldGuardAdapter {

    private final Object regionContainer;
    private final FlagRegistry flagRegistry;

    WorldGuardAdapterV7() {
        try {
            // WorldGuard.getInstance().getPlatform().getRegionContainer()
            Class<?> worldGuardClass = getClass().getClassLoader().loadClass("com.sk89q.worldguard.WorldGuard");
            Object worldGuard = MethodUtils.invokeExactStaticMethod(worldGuardClass, "getInstance", null);
            Object worldGuardPlatform = MethodUtils.invokeExactMethod(worldGuard, "getPlatform", null);
            regionContainer = MethodUtils.invokeExactMethod(worldGuardPlatform, "getRegionContainer", null);

            // WorldGuard.getInstance().getFlagRegistry()
            flagRegistry = (FlagRegistry) MethodUtils.invokeExactMethod(worldGuard, "getFlagRegistry", null);
        } catch (Exception e) {
            throw new UnsupportedOperationException("Failed to initialize adapter for WorldGuard v7.x!", e);
        }
    }


    @Override
    public <T, F extends Flag<T> & FlagAdapter<T>> SetFlag<T> initSetFlag(String flagName, F flag) {
        return new SetFlag<>(flagName, flag);
    }

    @Override
    public void installFlags(Flag<?>... flags) {
        flagRegistry.registerAll(Arrays.asList(flags));
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<RegionManager> getRegionManagers() {
        try {
            return (List<RegionManager>) MethodUtils.invokeExactMethod(regionContainer, "getLoaded", null);
        } catch (Exception e) {
            throw new UnsupportedOperationException(e);
        }
    }

    @Override
    public RegionManager getRegionManager(World world) {
        try {
            return (RegionManager) MethodUtils.invokeExactMethod(regionContainer, "get", new BukkitWorld(world));
        } catch (Exception e) {
            throw new UnsupportedOperationException(e);
        }
    }

    @Override
    public ApplicableRegionSet getApplicableRegions(RegionManager regionManager, Location location) {
        try {
            return (ApplicableRegionSet) MethodUtils.invokeExactMethod(regionManager, "getApplicableRegions", BukkitUtil.toVector(location));
        } catch (Exception e) {
            throw new UnsupportedOperationException(e);
        }
    }

}
