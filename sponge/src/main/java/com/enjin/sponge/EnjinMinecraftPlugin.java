package com.enjin.sponge;

import com.enjin.core.config.JsonConfig;
import com.enjin.sponge.commands.EnjinCommand;
import com.enjin.sponge.commands.configuration.SetKeyCommand;
import com.enjin.sponge.commands.store.BuyCommand;
import com.enjin.sponge.config.EnjinConfig;
import com.enjin.sponge.shop.ShopListener;
import com.enjin.sponge.sync.RPCPacketManager;
import com.enjin.sponge.utils.commands.CommandWrapper;
import com.enjin.rpc.EnjinRPC;
import com.google.common.collect.Lists;
import com.google.inject.Inject;
import lombok.Getter;
import org.slf4j.Logger;
import org.spongepowered.api.Game;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.event.game.state.GameInitializationEvent;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.plugin.PluginContainer;
import org.spongepowered.api.service.config.ConfigDir;
import org.spongepowered.api.service.scheduler.Task;
import org.spongepowered.api.text.Texts;
import org.spongepowered.api.util.command.args.GenericArguments;
import org.spongepowered.api.util.command.spec.CommandSpec;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Plugin(id = "EnjinMinecraftPlugin", name = "Enjin Minecraft Plugin", version = "2.8.3-sponge")
public class EnjinMinecraftPlugin {
    @Getter
    private static EnjinMinecraftPlugin instance;
    @Getter
    private static List<CommandSpec> commands = Lists.newArrayList();
    @Getter
    private static List<CommandWrapper> processedCommands = Lists.newArrayList();

    @Inject
    @Getter
    private PluginContainer container;
    @Inject
    @Getter
    private Logger logger;
    @Inject
    @Getter
    private java.util.logging.Logger javaLogger;
    @Inject
    @ConfigDir(sharedRoot = false)
    @Getter
    private File configDir;
    @Getter
    private EnjinConfig config;
    @Inject
    @Getter
    private Game game;

    @Getter
    private Task syncTask;

    public EnjinMinecraftPlugin() {
        instance = this;
    }

    @Listener
    public void initialization(GameInitializationEvent event) {
        logger.info("Initializing Enjin Minecraft Plugin");
        initConfig();
        initJsonRPC();
        initCommands();
        initListeners();
        initTasks();
    }

    private void initConfig() {
        logger.info("Initializing EMP Config");
        config = JsonConfig.load(new File(configDir, "config.json"), EnjinConfig.class);
    }

    public void saveConfig() {
        logger.info("Saving EMP Config");
        config.save(new File(configDir, "config.json"));
    }

    private void initCommands() {
        logger.info("Initializing EMP Commands");
        if (!commands.isEmpty()) {
            commands.clear();
        }

        CommandSpec.Builder enjinCommandBuilder = CommandSpec.builder()
                .description(Texts.of("/enjin"))
                .executor(new EnjinCommand());
        enjinCommandBuilder.child(CommandSpec.builder()
                .description(Texts.of("Set the authentication key for this server"))
                .permission("enjin.setkey")
                .arguments(GenericArguments.string(Texts.of("key")))
                .executor(new SetKeyCommand()).build(), "setkey", "key", "sk");

        CommandSpec.Builder buyCommandBuilder = CommandSpec.builder()
                .description(Texts.of("/buy"))
                .permission("enjin.buy")
                .arguments(GenericArguments.optional(GenericArguments.integer(Texts.of("#"))))
                .executor(new BuyCommand());
        buyCommandBuilder.child(CommandSpec.builder()
                .description(Texts.of("/buy shop <#>"))
                .permission("enjin.buy")
                .arguments(GenericArguments.integer(Texts.of("#")))
                .executor(new BuyCommand.ShopCommand())
                .build(), "shop");

        CommandSpec buySpec = buyCommandBuilder.build();
        enjinCommandBuilder.child(buySpec, "buy");

        CommandSpec enjinSpec = enjinCommandBuilder.build();
        commands.add(enjinSpec);
        commands.add(buySpec);

        game.getCommandDispatcher().register(this, enjinSpec, "enjin", "emp", "e");
        game.getCommandDispatcher().register(this, buySpec, "buy");
    }

    private void initListeners() {
        game.getEventManager().registerListeners(this, new ShopListener());
    }

    public void initTasks() {
        if (syncTask != null) {
            stopTasks();
        }

        syncTask = game.getScheduler().createTaskBuilder()
                .execute(new RPCPacketManager(this))
                .async().interval(60, TimeUnit.SECONDS)
                .submit(this);
    }

    public void stopTasks() {
        syncTask.cancel();
        syncTask = null;
    }

    private void initJsonRPC() {
        if (javaLogger == null) {
            debug("Java logger is null, skipping rpc logger initialization.");
        } else {
            debug("Initializing rpc logger.");
            EnjinRPC.setLogger(javaLogger);
        }

        EnjinRPC.setDebug(config.isDebug());
        EnjinRPC.setHttps(config.isHttps());
        EnjinRPC.setApiUrl("://api.enjin.com/api/v1/");
    }

    public String getAuthKey() {
        if (config == null) {
            return "";
        }

        return config.getAuthkey();
    }

    public int getPort() {
        return game.getServer().getBoundAddress().get().getPort();
    }

    public void debug(String ... messages) {
        if (config.isDebug()) {
            for (String message : messages) {
                logger.info("[Debug] " + message);
            }
        }
    }

    public void addProcessedCommand(CommandWrapper wrapper) {
        if (wrapper.getId().isEmpty()) {
            return;
        }

        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(wrapper.getCommand().getBytes("UTF-8"));

            BigInteger bigInt = new BigInteger(1, digest);
            String hash = bigInt.toString(16);

            while (hash.length() < 32) {
                hash = "0" + hash;
            }

            wrapper.setHash(hash);
            processedCommands.add(wrapper);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }
}