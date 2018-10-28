package com.deadmandungeons.audioconnect.compat;

import com.deadmandungeons.audioconnect.AudioConnect;
import com.deadmandungeons.audioconnect.flags.FlagAdapter;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.SetFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class WorldGuardAdapter {

    private static final String VERSION_REGEX = "\\d+(\\.\\d+)*";
    private static final Pattern VERSION_PATTERN = Pattern.compile(VERSION_REGEX);
    private static final Comparator<String> VERSION_COMPARATOR = new VersionComparator();

    public abstract <T, F extends Flag<T> & FlagAdapter<T>> SetFlag<T> initSetFlag(String flagName, F flag);

    public abstract void installFlags(Flag<?>... flags);

    public abstract List<RegionManager> getRegionManagers();

    public abstract RegionManager getRegionManager(World world);

    public abstract ApplicableRegionSet getApplicableRegions(RegionManager regionManager, Location location);


    public static WorldGuardAdapter getInstance() {
        Plugin worldGuardPlugin = Bukkit.getPluginManager().getPlugin("WorldGuard");
        String worldGuardVersion = worldGuardPlugin.getDescription().getVersion();

        Matcher versionMatcher = VERSION_PATTERN.matcher(worldGuardVersion);
        if (versionMatcher.find()) {
            String versionString = versionMatcher.group();
            getLogger().info("Detected WorldGuard version (" + versionString + "). Attempting to make AudioConnect compatible...");

            if (VERSION_COMPARATOR.compare(versionString, "7.0.0") >= 0) {
                return new WorldGuardAdapterV7();
            } else if (VERSION_COMPARATOR.compare(versionString, "6.1.3") >= 0) {
                return new WorldGuardAdapterV6_1_3();
            } else {
                return new WorldGuardAdapterLegacy();
            }
        } else {
            throw new UnsupportedOperationException("Unable to parse invalid WorldGuard version string: \"" + worldGuardVersion + "\"");
        }
    }

    private static Logger getLogger() {
        return AudioConnect.getInstance().getLogger();
    }

    private static class VersionComparator implements Comparator<String> {

        @Override
        public int compare(String v1, String v2) {
            if (!VERSION_PATTERN.matcher(v1).matches() || !VERSION_PATTERN.matcher(v2).matches()) {
                String msg = "One of the version Strings was not in valid version format and did not match against: ";
                throw new IllegalArgumentException(msg + VERSION_REGEX);
            }
            // Split version strings to get an array of each version level
            String[] levels1 = v1.split("\\.");
            String[] levels2 = v2.split("\\.");

            // Check if any of the version levels are unequal
            int maxLength = Math.max(levels1.length, levels2.length);
            for (int i = 0; i < maxLength; i++) {
                if (i < levels1.length && i < levels2.length) {
                    String level1 = levels1[i], level2 = levels2[i];
                    // If one of the non-major levels has a leading zero, compare them as doubles. otherwise integers
                    if (i > 0 && (level1.charAt(0) == '0' || level2.charAt(0) == '0')) {
                        // EX: 1.3.01 <=> 1.3.1 (.01 < .1)
                        int doubleDiff = Double.valueOf("." + level1).compareTo(Double.valueOf("." + level2));
                        if (doubleDiff != 0) {
                            return doubleDiff;
                        }
                    } else {
                        // EX: 1.3.10 <=> 1.3.1 (10 > 1)
                        int intDiff = Integer.valueOf(level1).compareTo(Integer.valueOf(level2));
                        if (intDiff != 0) {
                            return intDiff;
                        }
                    }
                } else if (i >= levels1.length && Integer.parseInt(levels2[i]) != 0) {
                    // EX: 1.3 < 1.3.2
                    return -1;
                } else if (i >= levels2.length && Integer.parseInt(levels1[i]) != 0) {
                    // EX: 1.3.2 > 1.3
                    return 1;
                }
            }
            // EX: 1.3.1 = 1.3.1.0
            return 0;
        }
    }

}
