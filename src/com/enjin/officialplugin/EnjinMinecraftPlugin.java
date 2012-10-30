package com.enjin.officialplugin;

import java.io.*;
import java.net.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.net.ssl.SSLHandshakeException;

import net.milkbowl.vault.permission.Permission;
import net.milkbowl.vault.permission.plugins.Permission_GroupManager;

import org.anjocaido.groupmanager.GroupManager;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import com.enjin.officialplugin.listeners.EnjinStatsListener;
import com.enjin.officialplugin.listeners.NewPlayerChatListener;
import com.enjin.officialplugin.listeners.TekkitPlayerChatListener;
import com.enjin.officialplugin.listeners.VotifierListener;
import com.enjin.officialplugin.permlisteners.GroupManagerListener;
import com.enjin.officialplugin.permlisteners.PermissionsBukkitChangeListener;
import com.enjin.officialplugin.permlisteners.PexChangeListener;
import com.enjin.officialplugin.permlisteners.bPermsChangeListener;
import com.enjin.officialplugin.stats.StatsPlayer;
import com.enjin.officialplugin.stats.WriteStats;
import com.enjin.officialplugin.threaded.NewKeyVerifier;
import com.enjin.officialplugin.threaded.PeriodicEnjinTask;
import com.enjin.officialplugin.threaded.PeriodicVoteTask;
import com.enjin.officialplugin.threaded.ReportMakerThread;
import com.enjin.proto.stats.EnjinStats;
import com.platymuus.bukkit.permissions.PermissionsPlugin;

import de.bananaco.bpermissions.imp.Permissions;

import ru.tehkode.permissions.bukkit.PermissionsEx;

/**
 * 
 * @author OverCaste (Enjin LTE PTD).
 * This software is released under an Open Source license.
 * @copyright Enjin 2012.
 * 
 */

public class EnjinMinecraftPlugin extends JavaPlugin {
	
	public FileConfiguration config;
	public static boolean usingGroupManager = false;
	File hashFile;
	public static String hash = "";
	Server s;
	Logger logger;
	public static Permission permission = null;
	public boolean debug = false;
	public boolean collectstats = true;
	public PermissionsEx permissionsex;
	public GroupManager groupmanager;
	public Permissions bpermissions;
	public PermissionsPlugin permissionsbukkit;
	public boolean supportsglobalgroups = true;
	public boolean votifierinstalled = false;
	static public boolean bukkitversion = false;
	public int xpversion = 0;
	
	public final static Logger enjinlogger = Logger.getLogger(EnjinMinecraftPlugin.class .getName());
	
	public ConcurrentHashMap<String, StatsPlayer> playerstats = new ConcurrentHashMap<String, StatsPlayer>();
	
	static public String apiurl = "://api.enjin.com/api/";
	//static public String apiurl = "://gamers.enjin.ca/api/";
	//static public String apiurl = "://tuxreminder.info/api/";
	
	public boolean autoupdate = true;
	public String newversion = "";
	
	public boolean hasupdate = false;
	public boolean updatefailed = false;
	static public final String updatejar = "http://resources.guild-hosting.net/1/downloads/emp/";
	static public final String bukkitupdatejar = "http://dev.bukkit.org/media/files/";
	
	public final EMPListener listener = new EMPListener(this);
	final PeriodicEnjinTask task = new PeriodicEnjinTask(this);
	final PeriodicVoteTask votetask = new PeriodicVoteTask(this);
	static final ExecutorService exec = Executors.newCachedThreadPool();
	public static String minecraftport;
	public static boolean usingSSL = true;
	NewKeyVerifier verifier = null;
	public ConcurrentHashMap<String, String> playerperms = new ConcurrentHashMap<String, String>();
	//Player, lists voted on.
	public ConcurrentHashMap<String, String> playervotes = new ConcurrentHashMap<String, String>();
	
	public EnjinErrorReport lasterror = null;
	
	DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss z");
	
	public void debug(String s) {
		if(debug) {
			System.out.println("Enjin Debug: " + s);
			enjinlogger.fine(s);
		}
	}
	
	@Override
	public void onEnable() {
		try {
			File enjinlog = new File("enjin.log");
			//Start a new log every time the server boots. Keeps the files nice and small
			if(enjinlog.exists()) {
				DateFormat edateFormat = new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss");
				enjinlog.renameTo(new File("enjin-" + edateFormat.format(new Date()) + ".log"));
			}
			debug("Begin init");
			initVariables();
			enjinlogger.setLevel(Level.FINEST);
			FileHandler fileTxt = new FileHandler("enjin.log");
			EnjinLogFormatter formatterTxt = new EnjinLogFormatter();
		    fileTxt.setFormatter(formatterTxt);
		    enjinlogger.addHandler(fileTxt);
			debug("Init vars done.");
			initFiles();
			debug("Init files done.");
			initPlugins();
			debug("Init plugins done.");
			setupPermissions();
			debug("Setup permissions integration");
			setupVotifierListener();
			debug("Setup Votifier integration");
			if(collectstats) {
				Bukkit.getPluginManager().registerEvents(new EnjinStatsListener(this), this);
				File stats = new File("stats.stats");
				if(stats.exists()) {
					FileInputStream input = new FileInputStream(stats);
					EnjinStats.Server serverstats = EnjinStats.Server.parseFrom(input);
					debug("Parsing stats input.");
					for(EnjinStats.Server.Player player : serverstats.getPlayersList()) {
						debug("Adding player " + player.getName() + ".");
						playerstats.put(player.getName().toLowerCase(), new StatsPlayer(player));
					}
				}
				String[] cbversionstring = getServer().getVersion().split(":");
		        String[] versionstring = cbversionstring[1].split("\\.");
		        try{
		        	int majorversion = Integer.parseInt(versionstring[0].trim());
		        	int minorversion = Integer.parseInt(versionstring[1].trim());
		        	if(majorversion == 1) {
		        		if(minorversion > 2) {
		        			xpversion = 1;
		        			logger.info("[Enjin Minecraft Plugin] MC 1.3 or above found, enabling version 2 XP handling.");
		        		}else {
		        			logger.info("[Enjin Minecraft Plugin] MC 1.2 or below found, enabling version 1 XP handling.");
		        		}
		        	}else if(majorversion > 1) {
		        		xpversion = 1;
		    			logger.info("[Enjin Minecraft Plugin] MC 1.3 or above found, enabling version 2 XP handling.");
		        	}
		        }catch (Exception e) {
		        	logger.severe("[Enjin Minecraft Plugin] Unable to get server version! Inaccurate XP handling may occurr!");
		        	logger.severe("[Enjin Minecraft Plugin] Server Version String: " + getServer().getVersion());
		        }
		        //XP handling and chat event handling changed at 1.3, so we can use the same variable. :D
		        if(xpversion < 1) {
		        	//We only keep this around for backwards compatibility with tekkit as it is still on 1.2.5
		        	Bukkit.getPluginManager().registerEvents(new TekkitPlayerChatListener(this), this);
		        }else {
		        	Bukkit.getPluginManager().registerEvents(new NewPlayerChatListener(this), this);
		        }
			}
			usingGroupManager = (permission instanceof Permission_GroupManager);
			debug("Checking key valid.");
			if(verifier == null || verifier.completed) {
				verifier = new NewKeyVerifier(this, hash, null, true);
				Thread verifierthread = new Thread(verifier);
				verifierthread.start();
			}else {
				Bukkit.getLogger().warning("[Enjin Minecraft Plugin] A key verification is already running. Did you /reload?");
				enjinlogger.warning("A key verification is already running. Did you /reload?");
			}
		}
		catch(Throwable t) {
			Bukkit.getLogger().warning("[Enjin Minecraft Plugin] Couldn't enable EnjinMinecraftPlugin! Reason: " + t.getMessage());
			enjinlogger.warning("Couldn't enable EnjinMinecraftPlugin! Reason: " + t.getMessage());
			t.printStackTrace();
			this.setEnabled(false);
		}
	}
	
	@Override
	public void onDisable() {
		stopTask();
		//unregisterEvents();
	}
	
	private void initVariables() throws Throwable {
		hashFile = new File(this.getDataFolder(), "HASH.txt");
		s = Bukkit.getServer();
		logger = Bukkit.getLogger();
		try {
			Properties serverProperties = new Properties();
			FileInputStream in = new FileInputStream(new File("server.properties"));
			serverProperties.load(in);
			in.close();
			minecraftport = serverProperties.getProperty("server-port");
		} catch (Throwable t) {
			t.printStackTrace();
			enjinlogger.severe("Couldn't find a localhost ip! Please report this problem!");
			throw new Exception("[Enjin Minecraft Plugin] Couldn't find a localhost ip! Please report this problem!");
		}
	}
	
	private void initFiles() {
		//let's read in the old hash file if there is one and convert it to the new format.
		if(hashFile.exists()) {
			try {
				BufferedReader r = new BufferedReader(new FileReader(hashFile));
				hash = r.readLine();
				r.close();
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
			//Remove it, we won't ever need it again.
			hashFile.delete();
		}
		config = getConfig();
		File configfile = new File(getDataFolder().toString() + "/config.yml");
    	if(!configfile.exists()) {
    		createConfig();
    	}
    	debug = config.getBoolean("debug", false);
    	hash = config.getString("authkey", "");
    	debug("Key value retrieved: " + hash);
    	usingSSL = config.getBoolean("https", true);
    	autoupdate = config.getBoolean("autoupdate", true);
    	//Test to see if we need to update the config file.
    	String teststats = config.getString("collectstats", "");
    	if(teststats.equals("")) {
    		createConfig();
    	}
    	collectstats = config.getBoolean("collectstats", true);
	}
	
	private void createConfig() {
		config.set("debug", debug);
		config.set("authkey", hash);
		config.set("https", usingSSL);
		config.set("autoupdate", autoupdate);
		config.set("collectstats", collectstats);
		saveConfig();
	}

	public void startTask() {
		debug("Starting tasks.");
		Bukkit.getScheduler().scheduleAsyncRepeatingTask(this, task, 1200L, 1200L);
		//Only start the vote task if votifier is installed.
		if(votifierinstalled) {
			Bukkit.getScheduler().scheduleAsyncRepeatingTask(this, votetask, 80L, 80L);
		}
	}
	
	public void registerEvents() {
		debug("Registering events.");
		Bukkit.getPluginManager().registerEvents(listener, this);
	}
	
	public void stopTask() {
		debug("Stopping tasks.");
		Bukkit.getScheduler().cancelTasks(this);
	}
	
	public void unregisterEvents() {
		debug("Unregistering events.");
		HandlerList.unregisterAll(listener);
	}
	
	private void setupVotifierListener() {
		if(Bukkit.getPluginManager().isPluginEnabled("Votifier")) {
			System.out.println("[Enjin Minecraft Plugin] Votifier plugin found, enabling Votifier support.");
			enjinlogger.info("Votifier plugin found, enabling Votifier support.");
			Bukkit.getPluginManager().registerEvents(new VotifierListener(this), this);
			votifierinstalled = true;
		}
	}
	
	private void initPlugins() throws Throwable {
		if(!Bukkit.getPluginManager().isPluginEnabled("Vault")) {
			enjinlogger.warning("Couldn't find the vault plugin! Please get it from dev.bukkit.org/server-mods/vault/!");
			getLogger().warning("[Enjin Minecraft Plugin] Couldn't find the vault plugin! Please get it from dev.bukkit.org/server-mods/vault/!");
			return;
		}
		debug("Initializing permissions.");
		initPermissions();
	}
	
	private void initPermissions() throws Throwable {
		RegisteredServiceProvider<Permission> provider = Bukkit.getServicesManager().getRegistration(Permission.class);
		if(provider == null) {
			enjinlogger.warning("Couldn't find a vault compatible permission plugin! Please install one before using the Enjin Minecraft Plugin.");
			Bukkit.getLogger().warning("[Enjin Minecraft Plugin] Couldn't find a vault compatible permission plugin! Please install one before using the Enjin Minecraft Plugin.");
			return;
		}
		permission = provider.getProvider();
		if(permission == null) {
			enjinlogger.warning("Couldn't find a vault compatible permission plugin! Please install one before using the Enjin Minecraft Plugin.");
			Bukkit.getLogger().warning("[Enjin Minecraft Plugin] Couldn't find a vault compatible permission plugin! Please install one before using the Enjin Minecraft Plugin.");
			return;
		}
	}
	
	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if(command.getName().equals("enjinkey")) {
			if(!sender.hasPermission("enjin.setkey")) {
				sender.sendMessage(ChatColor.RED + "You need to have the \"enjin.setkey\" permission or OP to run that command!");
				return true;
			}
			if(args.length != 1) {
				return false;
			}
			enjinlogger.info("Checking if key is valid");
			Bukkit.getLogger().info("Checking if key is valid");
			//Make sure we don't have several verifier threads going at the same time.
			if(verifier == null || verifier.completed) {
				verifier = new NewKeyVerifier(this, args[0], sender, false);
				Thread verifierthread = new Thread(verifier);
				verifierthread.start();
			}else {
				sender.sendMessage(ChatColor.RED + "Please wait until we verify the key before you try again!");
			}
			return true;
		}if (command.getName().equalsIgnoreCase("enjin")) {
			if(args.length > 0) {
				if(args[0].equalsIgnoreCase("report")) {
					if(!sender.hasPermission("enjin.report")) {
						sender.sendMessage(ChatColor.RED + "You need to have the \"enjin.report\" permission or OP to run that command!");
						return true;
					}
					sender.sendMessage(ChatColor.GREEN + "Please wait as we generate the report");
					DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss z");
					Date date = new Date();
					StringBuilder report = new StringBuilder();
					report.append("Enjin Debug Report generated on " + dateFormat.format(date) + "\n");
					report.append("Enjin plugin version: " + getDescription().getVersion() + "\n");
					String permsmanager = "Generic";
					String permsversion = "Unknown";
					if(permissionsex != null) {
						permsmanager = "PermissionsEx";
						permsversion = permissionsex.getDescription().getVersion();
					}else if(bpermissions != null) {
						permsmanager = "bPermissions";
						permsversion = bpermissions.getDescription().getVersion();
					}else if(groupmanager != null) {
						permsmanager = "GroupManager";
						permsversion = groupmanager.getDescription().getVersion();
					}else if(permissionsbukkit != null) {
						permsmanager = "PermissionsBukkit";
						permsversion = permissionsbukkit.getDescription().getVersion();
					}
					report.append("Permissions plugin used: " + permsmanager + " version " + permsversion + "\n");
					if(permission != null) {
						report.append("Vault permissions system reported: " + permission.getName() + "\n");
					}
					report.append("Bukkit version: " + getServer().getVersion() + "\n");
					report.append("Java version: " + System.getProperty("java.version") + " " + System.getProperty("java.vendor") + "\n");
					report.append("Operating system: " + System.getProperty("os.name") + " " + System.getProperty("os.version") + " " + System.getProperty("os.arch") + "\n\n");
					report.append("Plugins: \n");
					for(Plugin p : Bukkit.getPluginManager().getPlugins()) {
						report.append(p.getName() + " version " + p.getDescription().getVersion() + "\n");
					}
					report.append("\nWorlds: \n");
					for(World world : getServer().getWorlds()) {
						report.append(world.getName() + "\n");
					}
					ReportMakerThread rmthread = new ReportMakerThread(this, report, sender);
					Thread dispatchThread = new Thread(rmthread);
		            dispatchThread.start();
		            return true;
				}else if(args[0].equalsIgnoreCase("debug")) {
					if(!sender.hasPermission("enjin.debug")) {
						sender.sendMessage(ChatColor.RED + "You need to have the \"enjin.debug\" permission or OP to run that command!");
						return true;
					}
					if(debug) {
						debug = false;
					}else {
						debug = true;
					}
					sender.sendMessage(ChatColor.GREEN + "Debugging has been set to " + debug);
					return true;
				}else if(args[0].equalsIgnoreCase("push")) {
					if(!sender.hasPermission("enjin.push")) {
						sender.sendMessage(ChatColor.RED + "You need to have the \"enjin.push\" permission or OP to run that command!");
						return true;
					}
					OfflinePlayer[] allplayers = getServer().getOfflinePlayers();
					if(playerperms.size() > 3000 || playerperms.size() >= allplayers.length) {
						int minutes = playerperms.size()/3000;
						//Make sure to tack on an extra minute for the leftover players.
						if(playerperms.size()%3000 > 0) {
							minutes++;
						}
						//Add an extra 10% if it's going to take more than one synch.
						//Just in case a synch fails.
						if(playerperms.size() > 3000) {
							minutes += minutes * 0.1;
						}
						sender.sendMessage(ChatColor.RED + "A rank sync is still in progress, please wait until the current sync completes.");
						sender.sendMessage(ChatColor.RED + "Progress: + Integer.toString(playerperms.size()) + more player ranks to transmit, ETA: " + minutes + " minute" + (minutes > 1 ? "s" : "") + ".");
						return true;
					}
					for(OfflinePlayer offlineplayer : allplayers) {
						playerperms.put(offlineplayer.getName(), "");
					}
					
					//Calculate how many minutes approximately it's going to take.
					int minutes = playerperms.size()/3000;
					//Make sure to tack on an extra minute for the leftover players.
					if(playerperms.size()%3000 > 0) {
						minutes++;
					}
					//Add an extra 10% if it's going to take more than one synch.
					//Just in case a synch fails.
					if(playerperms.size() > 3000) {
						minutes += minutes * 0.1;
					}
					sender.sendMessage(ChatColor.GREEN + Integer.toString(playerperms.size()) + " players have been queued for synching. This should take approximately " + Integer.toString(minutes) + " minutes.");
					return true;
				}else if(args[0].equalsIgnoreCase("savestats")) {
					if(!sender.hasPermission("enjin.savestats")) {
						sender.sendMessage(ChatColor.RED + "You need to have the \"enjin.savestats\" permission or OP to run that command!");
						return true;
					}
					new WriteStats(this).write("stats.stats");
					sender.sendMessage(ChatColor.GREEN + "Stats saved to stats.stats.");
					return true;
				}else if(args[0].equalsIgnoreCase("playerstats")) {
					if(!sender.hasPermission("enjin.playerstats")) {
						sender.sendMessage(ChatColor.RED + "You need to have the \"enjin.playerstats\" permission or OP to run that command!");
						return true;
					}
					if(args.length > 1) {
						if(playerstats.containsKey(args[1].toLowerCase())) {
							StatsPlayer player = playerstats.get(args[1].toLowerCase());
							sender.sendMessage(ChatColor.DARK_GREEN + "Player stats for player: " + ChatColor.GOLD + player.getName());
							sender.sendMessage(ChatColor.DARK_GREEN + "Deaths: " + ChatColor.GOLD + player.getDeaths());
							sender.sendMessage(ChatColor.DARK_GREEN + "Kills: " + ChatColor.GOLD + player.getKilled());
							sender.sendMessage(ChatColor.DARK_GREEN + "Blocks broken: " + ChatColor.GOLD + player.getBrokenblocks());
							sender.sendMessage(ChatColor.DARK_GREEN + "Blocks placed: " + ChatColor.GOLD + player.getPlacedblocks());
							sender.sendMessage(ChatColor.DARK_GREEN + "Block types broken: " + ChatColor.GOLD + player.getBrokenblocktypes().toString());
							sender.sendMessage(ChatColor.DARK_GREEN + "Block types placed: " + ChatColor.GOLD + player.getPlacedblocktypes().toString());
							sender.sendMessage(ChatColor.DARK_GREEN + "Foot distance traveled: " + ChatColor.GOLD + player.getFootdistance());
							sender.sendMessage(ChatColor.DARK_GREEN + "Boat distance traveled: " + ChatColor.GOLD + player.getBoatdistance());
							sender.sendMessage(ChatColor.DARK_GREEN + "Minecart distance traveled: " + ChatColor.GOLD + player.getMinecartdistance());
							sender.sendMessage(ChatColor.DARK_GREEN + "Pig distance traveled: " + ChatColor.GOLD + player.getPigdistance());
						}
					}else {
						return false;
					}
					return true;
				}else if(apiurl.equals("://gamers.enjin.ca/api/") && args[0].equalsIgnoreCase("vote") && args.length > 2) {
					String username = args[1];
					String lists = "";
					String listname = args[2];
					if(playervotes.containsKey(username)) {
						lists = playervotes.get(username);
						lists = lists + "," + listname.replaceAll("[^0-9A-Za-z.\\-]", "");
					}else {
						lists = listname.replaceAll("[^0-9A-Za-z.\\-]", "");
					}
					playervotes.put(username, lists);
					sender.sendMessage(ChatColor.GREEN + "You just added a vote for player " + username + " on list " + listname);
				}
			}
		}
		return false;
	}
	
	@Deprecated
	public static void sendAddRank(final String world, final String group, final String player) {
		exec.submit(
			new Runnable() {
				@Override
				public void run() {
					try {
						sendAPIQuery("minecraft-set-rank", "authkey=" + hash, "world=" + world, "player=" + player, "group=" + group);
					} catch (Throwable t) {
						enjinlogger.warning("There was an error synchronizing group " + group + ", for user " + player + ".");
						Bukkit.getLogger().warning("[Enjin Minecraft Plugin] There was an error synchronizing group " + group + ", for user " + player + ".");
						t.printStackTrace();
					}
				}
			}
		);
	}
	
	@Deprecated
	public static void sendRemoveRank(final String world, final String group, final String player) {
		exec.submit(
			new Runnable() {
				@Override
				public void run() {
					try {
						sendAPIQuery("minecraft-remove-rank", "authkey=" + hash, "world=" + world, "player=" + player, "group=" + group);
					} catch (Throwable t) {
						enjinlogger.warning("There was an error synchronizing group " + group + ", for user " + player + ".");
						Bukkit.getLogger().warning("[Enjin Minecraft Plugin] There was an error synchronizing group " + group + ", for user " + player + ".");
						t.printStackTrace();
					}
				}
			}
		);
	}
	
	/**
	 * 
	 * @param urls
	 * @param queryValues
	 * @return 0 = Invalid key, 1 = OK, 2 = Exception encountered.
	 * @throws MalformedURLException
	 */
	public static int sendAPIQuery(String urls, String... queryValues) throws MalformedURLException {
		URL url = new URL((usingSSL ? "https" : "http") + apiurl + urls);
		StringBuilder query = new StringBuilder();
		try {
			HttpURLConnection con = (HttpURLConnection) url.openConnection();
			con.setRequestMethod("GET");
			con.setReadTimeout(3000);
			con.setConnectTimeout(3000);
			con.setDoOutput(true);
			con.setDoInput(true);
			for(String val : queryValues) {
				query.append('&');
				query.append(val);
			}
			if(queryValues.length > 0) {
				query.deleteCharAt(0); //remove first &
			}
			con.setRequestProperty("Content-length", String.valueOf(query.length()));
			con.getOutputStream().write(query.toString().getBytes());
			if(con.getInputStream().read() == '1') {
				return 1;
			}
			return 0;
		} catch (SSLHandshakeException e) {
			enjinlogger.warning("SSLHandshakeException, The plugin will use http without SSL. This may be less secure.");
			Bukkit.getLogger().warning("[Enjin Minecraft Plugin] SSLHandshakeException, The plugin will use http without SSL. This may be less secure.");
			usingSSL = false;
			return sendAPIQuery(urls, queryValues);
		} catch (SocketTimeoutException e) {
			enjinlogger.warning("Timeout, the enjin server didn't respond within the required time. Please be patient and report this bug to enjin.");
			Bukkit.getLogger().warning("[Enjin Minecraft Plugin] Timeout, the enjin server didn't respond within the required time. Please be patient and report this bug to enjin.");
			return 2;
		} catch (Throwable t) {
			t.printStackTrace();
			enjinlogger.warning("Failed to send query to enjin server! " + t.getClass().getName() + ". Data: " + url + "?" + query.toString());
			Bukkit.getLogger().warning("[Enjin Minecraft Plugin] Failed to send query to enjin server! " + t.getClass().getName() + ". Data: " + url + "?" + query.toString());
			return 2;
		}
	}
	
	public static synchronized void setHash(String hash) {
		EnjinMinecraftPlugin.hash = hash;
	}
	
	public static synchronized String getHash() {
		return EnjinMinecraftPlugin.hash;
	}
	
	private void setupPermissions() {
    	Plugin pex = this.getServer().getPluginManager().getPlugin("PermissionsEx");
        if (pex != null) {
            permissionsex = (PermissionsEx)pex;
            debug("PermissionsEx found, hooking custom events.");
            Bukkit.getPluginManager().registerEvents(new PexChangeListener(this), this);
            return;
        }
        Plugin bperm = this.getServer().getPluginManager().getPlugin("bPermissions");
        if(bperm != null) {
        	bpermissions = (Permissions)bperm;
            debug("bPermissions found, hooking custom events.");
            supportsglobalgroups = false;
        	Bukkit.getPluginManager().registerEvents(new bPermsChangeListener(this), this);
        	return;
        }
        Plugin groupmanager = this.getServer().getPluginManager().getPlugin("GroupManager");
        if(groupmanager != null) {
        	this.groupmanager = (GroupManager)groupmanager;
            debug("GroupManager found, hooking custom events.");
            supportsglobalgroups = false;
        	Bukkit.getPluginManager().registerEvents(new GroupManagerListener(this), this);
        	return;
        }
        Plugin bukkitperms = this.getServer().getPluginManager().getPlugin("PermissionsBukkit");
        if(bukkitperms != null) {
        	this.permissionsbukkit = (PermissionsPlugin)bukkitperms;
            debug("PermissionsBukkit found, hooking custom events.");
        	Bukkit.getPluginManager().registerEvents(new PermissionsBukkitChangeListener(this), this);
        	return;
        }
        debug("No suitable permissions plugin found, falling back to synching on player disconnect.");
        debug("You might want to switch to PermissionsEx, bPermissions, or Essentials GroupManager.");
        
	}

	public int getTotalXP(int level, float xp) {
		int atlevel = 0;
		int totalxp = 0;
		int xpneededforlevel = 0;
		if(xpversion == 1) {
			xpneededforlevel = 17;
			while(atlevel < level) {
				atlevel++;
				totalxp += xpneededforlevel;
				if(atlevel >= 16) {
					xpneededforlevel += 3;
				}
			}
			//We only have 2 versions at the moment
		}else {
			xpneededforlevel = 7;
	    	boolean odd = true;
	    	while(atlevel < level) {
	    		atlevel++;
	    		totalxp += xpneededforlevel;
	    		if(odd) {
	    			xpneededforlevel += 3;
	    			odd = false;
	    		}else {
	    			xpneededforlevel += 4;
	    			odd = true;
	    		}
	    	}
		}
		totalxp = (int) (totalxp + (xp*xpneededforlevel));
		return totalxp;
	}
	
	public StatsPlayer GetPlayerStats(String name) {
		StatsPlayer stats = playerstats.get(name.toLowerCase());
		if(stats == null) {
			stats = new StatsPlayer(name);
			playerstats.put(name.toLowerCase(), stats);
		}
		return stats;
	}
}