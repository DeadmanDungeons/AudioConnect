package com.deadmandungeons.audioconnect.command.verify;

import com.deadmandungeons.audioconnect.AudioConnect;
import com.google.common.base.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerListPingEvent;

/**
 * Inject the verify code into the MOTD from the bukkit ServerListPingEvent
 */
class VerifyRequestEventListener implements VerifyCommand.VerifyRequestListener, Listener {

    private final Supplier<String> verifyCodeSupplier;

    VerifyRequestEventListener(Supplier<String> verifyCodeSupplier) {
        this.verifyCodeSupplier = verifyCodeSupplier;
    }

    @Override
    public void register() {
        Bukkit.getPluginManager().registerEvents(this, AudioConnect.getInstance());
    }

    @Override
    public void unregister() {
        ServerListPingEvent.getHandlerList().unregister(this);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onServerPing(ServerListPingEvent event) {
        String encodedVerifyCode = verifyCodeSupplier.get();
        if (encodedVerifyCode != null) {
            event.setMotd(encodedVerifyCode + ChatColor.RESET + event.getMotd());
        }
    }

}
