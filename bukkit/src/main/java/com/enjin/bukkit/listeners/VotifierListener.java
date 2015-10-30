package com.enjin.bukkit.listeners;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import com.enjin.bukkit.EnjinMinecraftPlugin;
import com.enjin.core.Enjin;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import com.vexsoftware.votifier.model.Vote;
import com.vexsoftware.votifier.model.VotifierEvent;

public class VotifierListener implements Listener {
    EnjinMinecraftPlugin plugin;
    SimpleDateFormat date = new SimpleDateFormat("dd MMM yyyy");
    SimpleDateFormat time = new SimpleDateFormat("h:mm:ss a z");

    public VotifierListener(EnjinMinecraftPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void voteRecieved(VotifierEvent event) {
        Vote vote = event.getVote();

        Enjin.getPlugin().debug("Received vote from \"" + vote.getUsername() + "\" using \"" + vote.getServiceName());
        if (event.getVote().getUsername().equalsIgnoreCase("test") || event.getVote().getUsername().isEmpty()) {
            return;
        }

        String username = vote.getUsername().replaceAll("[^0-9A-Za-z_]", "");
        if (username.isEmpty() || username.length() > 16) {
            return;
        }

        String userid = username;
        if (EnjinMinecraftPlugin.supportsUUID()) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(username);
            userid = username + "|" + op.getUniqueId().toString();
        }

        if (plugin.playervotes.containsKey(username)) {
            plugin.playervotes.get(userid).add(vote.getServiceName().replaceAll("[^0-9A-Za-z.\\-]", ""));
        } else {
            plugin.playervotes.put(userid, new ArrayList<String>());
            plugin.playervotes.get(userid).add(vote.getServiceName().replaceAll("[^0-9A-Za-z.\\-]", ""));
        }

        long realvotetime = System.currentTimeMillis();
        Date votedate = new Date(realvotetime);
        String voteday = date.format(votedate);
        String svotetime = time.format(votedate);
    }
}