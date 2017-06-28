package com.deadmandungeons.audioconnect.command;

import com.deadmandungeons.audioconnect.AudioConnect;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;


/**
 * Contains and isolates Spigot API usage for {@link ListCommand}
 * to prevent NoClassDefFoundError when running on non-spigot servers
 */
class ListCommandSpigot {

    private static final AudioConnect plugin = AudioConnect.getInstance();

    static void sendAudioId(Player player, String audioId, ChatColor color) {
        String audioUrl = plugin.getConfiguration().getConnectionWebappUrl() + "/account/audio/" + audioId;
        TextComponent audioLink = new TextComponent("  " + audioId);
        audioLink.setColor(net.md_5.bungee.api.ChatColor.valueOf(color.name()));
        audioLink.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, audioUrl));
        TextComponent[] hoverText = {new TextComponent("Click for more info")};
        audioLink.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverText));

        player.spigot().sendMessage(audioLink);
    }

    static void sendPageButtons(Player player, String type, int pageNum, int maxPage) {
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

        String indent = "                         ";
        TextComponent pageButtons = new TextComponent(indent);
        pageButtons.addExtra(previousButton);
        pageButtons.addExtra("    ");
        pageButtons.addExtra(nextButton);

        player.sendMessage("");
        player.spigot().sendMessage(pageButtons);
    }

    private static void initPageButton(TextComponent pageButton, String type, int page) {
        pageButton.setColor(net.md_5.bungee.api.ChatColor.GRAY);
        String command = "/ac list " + type + " " + page;
        pageButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command));
        TextComponent[] hoverText = {new TextComponent("Show page " + page)};
        pageButton.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverText));
    }
}
