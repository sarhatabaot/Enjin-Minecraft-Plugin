package com.enjin.bukkit.listeners;

import com.enjin.bukkit.EnjinMinecraftPlugin;
import com.enjin.bukkit.modules.impl.VotifierModule;
import com.enjin.core.Enjin;
import com.vexsoftware.votifier.model.Vote;
import com.vexsoftware.votifier.model.VotifierEvent;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.ArrayList;
import java.util.regex.Pattern;

public class VotifierListener implements Listener {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("[0-9a-zA-Z_]{3,16}");

    private EnjinMinecraftPlugin plugin;

    public VotifierListener(EnjinMinecraftPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void voteRecieved(VotifierEvent event) {
        Vote vote = event.getVote();
        String username = vote.getUsername();

        Enjin.getLogger().debug("Received vote from \"" + vote.getUsername() + "\" using \"" + vote.getServiceName());
        if (username == null || username.isEmpty() || !USERNAME_PATTERN.matcher(username).matches()) {
            return;
        }

        OfflinePlayer player = Bukkit.getPlayer(username);
        if (player == null) {
            for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
                if (op == null) {
                    continue;
                }

                if (op.getName() != null & op.getName().equalsIgnoreCase(username)) {
                    player = op;
                }

                if (player != null) {
                    break;
                }
            }
        }

        if (player != null) {
            String userId   = username + "|" + player.getUniqueId().toString();
            String listName = event.getVote().getServiceName().replaceAll("[^0-9A-Za-z.\\-]", "");

            VotifierModule module = plugin.getModuleManager().getModule(VotifierModule.class);
            if (!module.getPlayerVotes().containsKey(listName)) {
                module.getPlayerVotes().put(listName, new ArrayList<Object[]>());
            }

            module.getPlayerVotes().get(listName).add(new Object[] {userId, System.currentTimeMillis() / 1000});
        } else {
            Enjin.getLogger().debug("Could not find correspond player of vote: " + username);
        }
    }
}
