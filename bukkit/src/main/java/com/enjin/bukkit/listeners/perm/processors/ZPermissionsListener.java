package com.enjin.bukkit.listeners.perm.processors;

import com.enjin.bukkit.listeners.perm.PermissionListener;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.tyrannyofheaven.bukkit.zPermissions.ZPermissionsRankChangeEvent;

public class ZPermissionsListener extends PermissionListener {
    @EventHandler
    public void onRankChange(ZPermissionsRankChangeEvent event) {
        update(Bukkit.getOfflinePlayer(event.getPlayerName()));
    }

    @Override
    public void processCommand(CommandSender sender, String command, Event event) {
        String[] args = command.split(" ");
        if (args.length >= 5 && (args[0].equalsIgnoreCase("perm") || args[0].equalsIgnoreCase("perms") || args[0].equalsIgnoreCase(
                "permissions"))) {
            if (args[1].equalsIgnoreCase("player")) {
                String        name = args[2];
                OfflinePlayer op   = Bukkit.getOfflinePlayer(name);

                if (args[3].equalsIgnoreCase("setgroup") || args[3].equalsIgnoreCase("group")) {
                    if (args[4].equalsIgnoreCase("-A") || args[4].equalsIgnoreCase("--add") || args[4].equalsIgnoreCase(
                            "--add-no-reset")) {
                        if (args.length < 6) {
                        } else {
                            update(op);
                        }
                    } else {
                        update(op);
                    }
                } else if (args[3].equalsIgnoreCase("removegroup") || args[3].equalsIgnoreCase("rmgroup") || args[3].equalsIgnoreCase(
                        "remove") || args[3].equalsIgnoreCase("rm")) {
                    update(op);
                } else if (args[3].equalsIgnoreCase("addgroup") || args[3].equalsIgnoreCase("add")) {
                    update(op);
                }
            } else if (args[1].equalsIgnoreCase("group")) {
                String        name = args.length > 5 ? args[5] : args[4];
                OfflinePlayer op   = Bukkit.getOfflinePlayer(name);
                if (args[3].equalsIgnoreCase("add") || args[3].equalsIgnoreCase("remove")) {
                    update(op);
                }
            }
        }
    }
}