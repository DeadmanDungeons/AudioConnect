package com.deadmandungeons.audioconnect.flags;

import com.sk89q.worldguard.protection.flags.Flag;

public interface FlagAdapter<T> {

    Flag<T> toLegacy();

}
