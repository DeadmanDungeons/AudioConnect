package com.deadmandungeons.audioconnect.command;

import com.deadmandungeons.audioconnect.AudioConnect;
import com.deadmandungeons.audioconnect.AudioConnectClient.PlayerConnection;
import com.deadmandungeons.audioconnect.flags.AudioTrack;
import com.deadmandungeons.deadmanplugin.DeadmanUtils;
import com.deadmandungeons.deadmanplugin.command.ArgumentInfo;
import com.deadmandungeons.deadmanplugin.command.ArgumentInfo.ArgType;
import com.deadmandungeons.deadmanplugin.command.Arguments;
import com.deadmandungeons.deadmanplugin.command.Command;
import com.deadmandungeons.deadmanplugin.command.CommandInfo;
import com.deadmandungeons.deadmanplugin.command.SubCommandInfo;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

//@formatter:off
@CommandInfo(
    name = "List",
    permissions = {"audioconnect.admin.list"},
    subCommands = {
        @SubCommandInfo(
            arguments = {
                @ArgumentInfo(argName = "players", argType = ArgType.NON_VARIABLE),
                @ArgumentInfo(argName = "page", argType = ArgType.OPT_VARIABLE, varType = Integer.class)
            },
            description = "List all players that are connected to the web client for this server"
        ),
        @SubCommandInfo(
            arguments = {
                @ArgumentInfo(argName = "audio", argType = ArgType.NON_VARIABLE),
                @ArgumentInfo(argName = "page", argType = ArgType.OPT_VARIABLE, varType = Integer.class)
            },
            description = "List all the available audio IDs for files that have been added to your account"
        ),
        @SubCommandInfo(
            arguments = {
                @ArgumentInfo(argName = "regions", argType = ArgType.NON_VARIABLE),
                @ArgumentInfo(argName = "page", argType = ArgType.OPT_VARIABLE, varType = Integer.class)
            },
            description = "List all the audio regions in each world and their settings"
        )
    }
)//@formatter:on
public class ListCommand implements Command {

    private final AudioConnect plugin = AudioConnect.getInstance();
    private final ListMessenger<?>[] listMessengers = new ListMessenger[]{new PlayerListMessenger(), new AudioListMessenger(),
            new RegionListMessenger()};

    @Override
    public boolean execute(CommandSender sender, Arguments args) {
        Arguments.validateType(args, getClass());

        if (!plugin.getClient().isConnected()) {
            plugin.getMessenger().sendErrorMessage(sender, "failed.client-disconnected");
            return false;
        }

        String type = (String) args.getArgs()[0];
        int pageNum = (args.getArgs().length == 2 ? (Integer) args.getArgs()[1] : 1);

        ListMessenger<?> listMessenger = listMessengers[args.getSubCmdIndex()];
        listMessenger.sendListMessage(sender, type, pageNum);

        return true;
    }


    private abstract class ListMessenger<T> {

        private final String name;
        private final int itemsPerPage;

        private ListMessenger(String name, int itemsPerPage) {
            this.name = name;
            this.itemsPerPage = itemsPerPage;
        }

        private boolean sendListMessage(CommandSender sender, String type, int pageNum) {
            T[] list = getList();

            int maxPage = list.length / itemsPerPage + (list.length % itemsPerPage > 0 ? 1 : 0);
            if (pageNum * itemsPerPage > list.length + (itemsPerPage - 1)) {
                pageNum = maxPage;
            }

            String reset = ChatColor.RESET.toString();
            String paging = (list.length > itemsPerPage ? "[pg. " + pageNum + "/" + maxPage + "] " : "");
            String barSpace = (paging.isEmpty() ? "-----" : "");
            String title = reset + color2() + " " + name + " List " + paging + color3();
            String topBar = "---------------" + barSpace + title + ChatColor.STRIKETHROUGH + barSpace + "---------------";
            sender.sendMessage(color3() + "<" + ChatColor.STRIKETHROUGH + topBar + reset + color3() + ">");

            if (list.length > 0) {
                printHeader(sender);
            } else {
                sender.sendMessage(ChatColor.RED + "  * NONE *");
            }

            for (int i = 0; i < list.length && i < (pageNum * itemsPerPage); i++) {
                if (i >= (pageNum - 1) * itemsPerPage) {
                    printListItem(sender, list[i]);
                }
            }

            // Add previous/next page buttons if spigot api is available
            if (plugin.isSpigot() && sender instanceof Player && (pageNum > 1 || pageNum < maxPage)) {
                TextComponent previousButton = new TextComponent("<< Previous");
                previousButton.setColor(net.md_5.bungee.api.ChatColor.DARK_GRAY);
                if (pageNum > 1) {
                    initPageButton(previousButton, type, pageNum - 1);
                }
                TextComponent nextButton = new TextComponent("Next >>");
                nextButton.setColor(net.md_5.bungee.api.ChatColor.DARK_GRAY);
                if (pageNum < maxPage) {
                    initPageButton(nextButton, type, pageNum + 1);
                }

                TextComponent pageButtons = new TextComponent("                         ");
                pageButtons.addExtra(previousButton);
                pageButtons.addExtra("    ");
                pageButtons.addExtra(nextButton);

                sender.sendMessage("");
                ((Player) sender).spigot().sendMessage(pageButtons);
            }

            plugin.getMessenger().sendMessage(sender, "misc.bottom-bar");
            return true;
        }

        protected ChatColor color1() {
            return plugin.getMessenger().getPrimaryColor();
        }

        protected ChatColor color2() {
            return plugin.getMessenger().getSecondaryColor();
        }

        protected ChatColor color3() {
            return plugin.getMessenger().getTertiaryColor();
        }

        protected abstract T[] getList();

        protected abstract void printHeader(CommandSender sender);

        protected abstract void printListItem(CommandSender sender, T item);

        private void initPageButton(TextComponent pageButton, String type, int page) {
            pageButton.setColor(net.md_5.bungee.api.ChatColor.valueOf(color2().name()));
            String command = "/ac list " + type + " " + page;
            pageButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command));
            TextComponent[] hoverText = {new TextComponent("Show page " + page)};
            pageButton.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverText));
        }

    }

    private class PlayerListMessenger extends ListMessenger<PlayerConnection> {

        private PlayerListMessenger() {
            super("Player", 10);
        }

        @Override
        protected PlayerConnection[] getList() {
            Collection<PlayerConnection> connectedPlayers = plugin.getClient().getPlayerConnections();
            PlayerConnection[] list = connectedPlayers.toArray(new PlayerConnection[connectedPlayers.size()]);
            Arrays.sort(list, new Comparator<PlayerConnection>() {
                // Sort players alphabetically. Players with a known username appear first.
                @Override
                public int compare(PlayerConnection a, PlayerConnection b) {
                    String aName = a.getPlayer().getName(), bName = b.getPlayer().getName();
                    if (aName != null && bName != null) {
                        return aName.compareTo(bName);
                    } else if (aName == null && bName == null) {
                        return a.getPlayer().getUniqueId().compareTo(b.getPlayer().getUniqueId());
                    } else if (aName == null) {
                        return 1;
                    } else {
                        return -1;
                    }
                }
            });
            return list;
        }

        @Override
        protected void printHeader(CommandSender sender) {
            String header = color2() + "  KEY: ";
            header += color1() + color1().name() + color2() + " = Online, ";
            header += ChatColor.RED + "RED" + color2() + " = Offline";

            sender.sendMessage(header);
            sender.sendMessage("");
        }

        @Override
        protected void printListItem(CommandSender sender, PlayerConnection item) {
            long duration = System.currentTimeMillis() - item.getConnectionTimestamp();
            String connectedDuration = ChatColor.DARK_GRAY + " (" + DeadmanUtils.formatDuration(duration) + ")";

            OfflinePlayer connectedPlayer = item.getPlayer();
            if (connectedPlayer.isOnline()) {
                sender.sendMessage("  " + color1() + connectedPlayer.getName() + connectedDuration);
            } else {
                String name = connectedPlayer.getName();
                sender.sendMessage("  " + ChatColor.RED + (name != null ? name : connectedPlayer.getUniqueId()) + connectedDuration);
            }
        }
    }

    private class AudioListMessenger extends ListMessenger<String> {

        private AudioListMessenger() {
            super("Audio", 10);
        }

        @Override
        protected String[] getList() {
            Set<String> audioIds = plugin.getAudioList().getAudioIds();
            String[] list = audioIds.toArray(new String[audioIds.size()]);
            Arrays.sort(list);
            return list;
        }

        @Override
        protected void printHeader(CommandSender sender) {
            if (plugin.isSpigot() && sender instanceof Player) {
                sender.sendMessage(color2() + "  NOTICE: Click any audio ID to show in browser");
                sender.sendMessage("");
            }
        }

        @Override
        protected void printListItem(CommandSender sender, String audioId) {
            if (plugin.isSpigot() && sender instanceof Player) {
                String audioUrl = plugin.getConfiguration().getConnectionWebappUrl() + "/account/audio/" + audioId;
                TextComponent audioLink = new TextComponent("  " + audioId);
                audioLink.setColor(net.md_5.bungee.api.ChatColor.valueOf(color1().name()));
                audioLink.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, audioUrl));
                TextComponent[] hoverText = {new TextComponent("Click for more info")};
                audioLink.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverText));

                ((Player) sender).spigot().sendMessage(audioLink);
            } else {
                sender.sendMessage("  " + color1() + audioId);
            }
        }

    }

    private class RegionListMessenger extends ListMessenger<WorldRegion> {

        private RegionListMessenger() {
            super("Region", 8);
        }

        @Override
        protected WorldRegion[] getList() {
            List<WorldRegion> regions = new ArrayList<>();
            for (World world : Bukkit.getWorlds()) {
                RegionManager regionManager = WorldGuardPlugin.inst().getRegionManager(world);
                for (ProtectedRegion region : regionManager.getRegions().values()) {
                    if (region.getFlags().containsKey(plugin.getAudioFlag())) {
                        regions.add(new WorldRegion(world, region));
                    }
                }
            }
            WorldRegion[] list = regions.toArray(new WorldRegion[regions.size()]);
            Arrays.sort(list, new Comparator<WorldRegion>() {
                // Sort regions by world name and region ID alphabetically
                @Override
                public int compare(WorldRegion a, WorldRegion b) {
                    int worldDiff = a.world.getName().compareTo(b.world.getName());
                    return worldDiff != 0 ? worldDiff : a.region.getId().compareTo(b.region.getId());
                }
            });
            return list;
        }

        @Override
        protected void printHeader(CommandSender sender) {
            // no header
        }

        @Override
        protected void printListItem(CommandSender sender, WorldRegion item) {
            String regionLine = color2() + "  World: " + color1() + item.world.getName();
            regionLine += color2() + "  Region: " + color1() + item.region.getId();
            sender.sendMessage(regionLine);

            StringBuilder audioLine = new StringBuilder();
            audioLine.append(color3()).append("  - ");
            Set<AudioTrack> audioTracks = item.region.getFlag(plugin.getAudioFlag());
            if (audioTracks == null || audioTracks.isEmpty()) {
                audioLine.append(ChatColor.RED).append("* NONE *");
            } else {
                Iterator<AudioTrack> iterator = audioTracks.iterator();
                while (iterator.hasNext()) {
                    audioLine.append(color1()).append(iterator.next());
                    if (iterator.hasNext()) {
                        audioLine.append(color3()).append(",  ");
                    }
                }
            }
            sender.sendMessage(audioLine.toString());
        }

    }

    private static class WorldRegion {

        private final World world;
        private final ProtectedRegion region;

        private WorldRegion(World world, ProtectedRegion region) {
            this.world = world;
            this.region = region;
        }

    }

}
