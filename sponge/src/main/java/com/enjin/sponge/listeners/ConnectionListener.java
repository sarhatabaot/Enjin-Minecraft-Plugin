package com.enjin.sponge.listeners;

import com.enjin.core.Enjin;
import com.enjin.rpc.mappings.mappings.plugin.PlayerGroupInfo;
import com.enjin.sponge.EnjinMinecraftPlugin;
import com.enjin.sponge.permissions.PermissionHandler;
import lombok.Getter;
import ninja.leaping.permissionsex.data.SubjectDataReference;
import ninja.leaping.permissionsex.sponge.PEXHandler;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.network.ClientConnectionEvent.Disconnect;
import org.spongepowered.api.event.network.ClientConnectionEvent.Join;
import org.spongepowered.api.plugin.PluginContainer;
import org.spongepowered.api.service.ProviderRegistration;
import org.spongepowered.api.service.ServiceManager;
import org.spongepowered.api.service.permission.PermissionService;
import org.spongepowered.api.service.permission.Subject;

import java.util.*;

public class ConnectionListener {
	@Getter
	private static ConnectionListener instance;
	private ProviderRegistration permissionProviderRegistration = null;
	private PermissionHandler permissionHandler = null;

	public ConnectionListener() {
		ConnectionListener.instance = this;
		init();
	}

	private void init() {
		final ServiceManager services = Sponge.getServiceManager();
		if (services.isRegistered(PermissionService.class)) {
			final Optional<ProviderRegistration<PermissionService>> potential = services.getRegistration(PermissionService.class);
			if (potential.isPresent()) {
				initPermissions(potential.get());
			}
		}
	}

	private void initPermissions(ProviderRegistration<PermissionService> registration) {
		permissionProviderRegistration = registration;

		final PluginContainer container = registration.getPlugin();
		if (container.getId().equals("ninja.leaping.permissionsex")) {
			permissionHandler = new PEXHandler(registration.getProvider());
		}
	}

	@Listener
	public void onJoin(Join event) {
		if (permissionHandler != null) {
			permissionHandler.onJoin(event);

			updatePlayerRanks(event.getTargetEntity());
		}
	}

	@Listener
	public void onDisconnect(Disconnect event) {
		if (permissionHandler != null) {
			permissionHandler.onDisconnect(event);
		}
	}

	public static void updatePlayerRanks(Player player) {
		if (instance.permissionHandler != null) {
			updatePlayerRanks1(player);
			EnjinMinecraftPlugin.saveRankUpdatesConfiguration();
		}
	}

	public static void updatePlayersRanks(Player[] players) {
		if (instance.permissionHandler != null) {
			for (Player player : players) {
				updatePlayerRanks1(player);
			}

			EnjinMinecraftPlugin.saveRankUpdatesConfiguration();
		}
	}

	private static void updatePlayerRanks1(Player player) {
		if (player == null || player.getName() == null) {
			Enjin.getLogger().debug("[ConnectionListener::updatePlayerRanks] Player or their name is null. Unable to update their ranks.");
			return;
		}

		PlayerGroupInfo info = new PlayerGroupInfo(player.getUniqueId());

		if (info == null) {
			Enjin.getLogger().debug("[ConnectionListener::updatePlayerRanks] PlayerGroupInfo is null. Unable to update " + player.getName() + "'s ranks.");
			return;
		}

		if (EnjinMinecraftPlugin.getRankUpdatesConfiguration() == null) {
			Enjin.getLogger().debug("[ConnectionListener::updatePlayerRanks] RankUpdatesConfiguration is null.");
			return;
		}

		if (EnjinMinecraftPlugin.getRankUpdatesConfiguration().getPlayerPerms() == null) {
			Enjin.getLogger().debug("[ConnectionListener::updatePlayerRanks] Player perms is null.");
			return;
		}

		info.getWorlds().putAll(getPlayerGroups(player));
		EnjinMinecraftPlugin.getRankUpdatesConfiguration().getPlayerPerms().put(player.getName(), info);
	}

	public static Map<String, List<String>> getPlayerGroups(Player player) {
		Map<String, List<String>> worlds = null;

		if (instance.permissionHandler != null) {
			worlds = instance.permissionHandler.fetchPlayerGroups(player);
		}

		return worlds;
	}

	public static List<String> getGroups() {
		List<String> groups = null;

		if (instance.permissionHandler != null) {
			groups = instance.permissionHandler.fetchGroups();
		}

		return groups;
	}
}
