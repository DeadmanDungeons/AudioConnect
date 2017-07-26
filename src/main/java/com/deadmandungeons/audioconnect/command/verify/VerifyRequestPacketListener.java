package com.deadmandungeons.audioconnect.command.verify;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.comphenix.protocol.wrappers.WrappedServerPing;
import com.deadmandungeons.audioconnect.AudioConnect;
import com.google.common.base.Supplier;
import org.bukkit.ChatColor;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.util.logging.Level;

/**
 * Inject the verify code into the MOTD from the outgoing server status packet.
 * This prevents the verify code from being overwritten by another plugin.
 */
class VerifyRequestPacketListener implements VerifyCommand.VerifyRequestListener {

    private final AudioConnect ac = AudioConnect.getInstance();
    private final JSONParser jsonParser = new JSONParser();

    private final PacketAdapter packetListener = new StatusPacketListener();
    private final Supplier<String> verifyCodeSupplier;

    VerifyRequestPacketListener(Supplier<String> verifyCodeSupplier) {
        this.verifyCodeSupplier = verifyCodeSupplier;
    }

    @Override
    public void register() {
        ProtocolLibrary.getProtocolManager().addPacketListener(packetListener);
    }

    @Override
    public void unregister() {
        ProtocolLibrary.getProtocolManager().removePacketListener(packetListener);
    }

    private class StatusPacketListener extends PacketAdapter {

        private StatusPacketListener() {
            super(params(ac, PacketType.Status.Server.OUT_SERVER_INFO).listenerPriority(ListenerPriority.HIGHEST).optionAsync());
        }

        @Override
        public void onPacketSending(PacketEvent event) {
            String verifyCode = verifyCodeSupplier.get();
            if (verifyCode != null) {
                try {
                    WrappedServerPing ping = event.getPacket().getServerPings().read(0);

                    JSONObject motd = (JSONObject) jsonParser.parse(ping.getMotD().getJson());
                    motd.put("text", verifyCode + ChatColor.RESET + motd.get("text"));

                    ping.setMotD(WrappedChatComponent.fromJson(motd.toJSONString()));
                } catch (ParseException e) {
                    ac.getLogger().log(Level.WARNING, "Failed to parse MOTD for verify request", e);
                }
            }
        }

    }

}
