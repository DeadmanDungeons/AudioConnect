package com.deadmandungeons.audioconnect.compat;

import com.deadmandungeons.audioconnect.flags.FlagAdapter;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.SetFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Arrays;
import java.util.List;

// Project is compiled against WorldGuard 6.2, so no need for reflection
// The FlagRegistry was introduced in WorldGuard 6.1.3
class WorldGuardAdapterV6_1_3 extends WorldGuardAdapter {

    @Override
    public <T, F extends Flag<T> & FlagAdapter<T>> SetFlag<T> initSetFlag(String flagName, F flag) {
        return new SetFlag<>(flagName, flag);
    }

    @Override
    public void installFlags(Flag<?>... flags) {
        WorldGuardPlugin.inst().getFlagRegistry().registerAll(Arrays.asList(flags));
    }

    @Override
    public List<RegionManager> getRegionManagers() {
        return WorldGuardPlugin.inst().getRegionContainer().getLoaded();
    }

    @Override
    public RegionManager getRegionManager(World world) {
        return WorldGuardPlugin.inst().getRegionManager(world);
    }

    @Override
    public ApplicableRegionSet getApplicableRegions(RegionManager regionManager, Location location) {
        return regionManager.getApplicableRegions(location);
    }
}
