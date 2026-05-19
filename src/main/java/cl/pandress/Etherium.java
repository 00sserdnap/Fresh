package cl.pandress;

// Comandos
import cl.pandress.command.admin.*;
import cl.pandress.command.player.DiscoveryCommand;
import cl.pandress.command.player.QuestCommand;
import cl.pandress.command.player.BattlePassCommand;

// Listeners y Menús
import cl.pandress.modules.discord.ServerStatusTask;
import cl.pandress.modules.quests.menus.QuestMenuListener;
import cl.pandress.modules.quests.QuestListener;
import cl.pandress.modules.battlepass.BattlePassListener;
import cl.pandress.modules.chatbubble.ChatBubbleListener;
import cl.pandress.modules.areatools.AreaToolListener;
import cl.pandress.modules.areatools.menus.AreaToolMenu;
import cl.pandress.modules.areatools.menus.AdminMenu;
import cl.pandress.modules.headdeath.HeadDeathListener;
import cl.pandress.modules.discoveries.menus.DiscoveryMenuListener;

// --- HEAD DEATH COSMETICS ---
import cl.pandress.modules.headdeath.gui.CosmeticsGUI;
import cl.pandress.modules.headdeath.gui.CosmeticsGUIListener;
import cl.pandress.modules.headdeath.placeholders.HeadDeathPlaceholder;

// Cargadores de Chunks (KeepChunk)
import cl.pandress.modules.keepchunk.KeepChunkManager;
import cl.pandress.modules.keepchunk.KeepChunkListener;

// Spawners Custom (Módulo de Spawners)
import cl.pandress.modules.customspawners.CustomSpawnerManager;
import cl.pandress.modules.customspawners.CustomSpawnerListener;
import cl.pandress.modules.customspawners.menus.CustomSpawnerMenu;

// Descubrimientos
import cl.pandress.modules.discoveries.DiscoveryManager;

// HopperLinks (Tolvas Inalámbricas)
import cl.pandress.modules.hopperlinks.HopperLinkManager;
import cl.pandress.modules.hopperlinks.listeners.HopperLinkListener;

// Evento del Dragón
import cl.pandress.modules.bosses.dragon.DragonEventManager;
import cl.pandress.modules.bosses.dragon.listeners.DragonEventListener;
import cl.pandress.modules.bosses.dragon.managers.DragonScheduleManager;
import cl.pandress.modules.bosses.dragon.placeholders.DragonPlaceholders;
import cl.pandress.command.admin.DragonEventCommand;

// Managers
import cl.pandress.modules.quests.QuestManager;
import cl.pandress.modules.battlepass.BattlePassManager;
import cl.pandress.modules.discord.DiscordManager;
import cl.pandress.modules.areatools.AreaToolManager;
import cl.pandress.modules.headdeath.HeadDeathManager;

import cl.pandress.modules.battlepass.placeholderapi.BattlePassPlaceholder;
import cl.pandress.modules.discord.placeholderapi.DiscordPlaceholder;

// Utilidades, APIs y Vault
import cl.pandress.utils.ChatUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.milkbowl.vault.economy.Economy;

public class Etherium extends JavaPlugin {

    private static Etherium instance;
    private ManagerHandler managerHandler;
    private JDA jda;
    private Economy economy;
    private CustomSpawnerMenu spawnerMenuHandler;
    private CosmeticsGUI cosmeticsGUI;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        if (!setupEconomy()) {
            log("&cNo se encontró Vault o un plugin de economía compatible. ¡La economía podría no funcionar!");
        } else {
            log("&aConectado exitosamente con Vault (Economía).");
        }

        this.managerHandler = new ManagerHandler();
        this.spawnerMenuHandler = new CustomSpawnerMenu(managerHandler.getCustomSpawnerManager());
        
        this.cosmeticsGUI = new CosmeticsGUI(managerHandler.getHeadDeathManager());

        registerCommands();
        registerEvents();
        setupDiscord();
        setupPlaceholders();

        loadDragonSchedule();

        log("&8[&eEtherium&8] &f &aactivado correctamente. Todos los módulos cargados.");
    }

    @Override
    public void onDisable() {
        Bukkit.getScheduler().cancelTasks(this);

        if (managerHandler != null) {
            log("&eGuardando datos y limpiando entidades...");

            if (managerHandler.getHeadDeathManager() != null) {
                managerHandler.getHeadDeathManager().cleanup();
            }

            if (managerHandler.getKeepChunkManager() != null) {
                managerHandler.getKeepChunkManager().saveLoaderData();
            }

            if (managerHandler.getCustomSpawnerManager() != null) {
                managerHandler.getCustomSpawnerManager().saveSpawnerData();
            }

            if (managerHandler.getDiscoveryManager() != null) {
                managerHandler.getDiscoveryManager().shutdown();
            }

            if (managerHandler.getHopperLinkManager() != null && managerHandler.getHopperLinkManager().isEnabled()) {
                managerHandler.getHopperLinkManager().saveData();
            }

            if (managerHandler.getDragonEventManager() != null) {
                managerHandler.getDragonEventManager().cleanup();
            }
            managerHandler.getQuestManager().shutdown();
            managerHandler.getBattlePassManager().shutdown();
        }

        shutdownDiscord();
        log("&8[&eEtherium&8] &cPlugin Etherium desactivado.");
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        economy = rsp.getProvider();
        return economy != null;
    }

    private void loadDragonSchedule() {
        if (getConfig().contains("dragon-event.world")) {
            String w = getConfig().getString("dragon-event.world");
            double x = getConfig().getDouble("dragon-event.x");
            double y = getConfig().getDouble("dragon-event.y");
            double z = getConfig().getDouble("dragon-event.z");
            float yaw = (float) getConfig().getDouble("dragon-event.yaw");
            float pitch = (float) getConfig().getDouble("dragon-event.pitch");
            long interval = getConfig().getLong("dragon-event.interval");
            long duration = getConfig().getLong("dragon-event.duration");
            long targetTimestamp = getConfig().getLong("dragon-event.target-timestamp", -1);

            World world = Bukkit.getWorld(w);
            if (world != null) {
                Location loc = new Location(world, x, y, z, yaw, pitch);
                managerHandler.getDragonScheduleManager().setupSchedule(loc, interval, duration);

                if (targetTimestamp != -1) {
                    long now = System.currentTimeMillis();
                    long remainingSeconds = (targetTimestamp - now) / 1000;

                    if (remainingSeconds > 0) {
                        managerHandler.getDragonScheduleManager().setTimeUntilNext(remainingSeconds);
                    } else {
                        managerHandler.getDragonScheduleManager().setTimeUntilNext(15);
                    }
                }

                log("&aHorario del dragón cargado y sincronizado desde la configuración.");
            }
        }
    }

    private void registerCommands() {
        ETHReloadCommand EthCmd = new ETHReloadCommand();
        if (getCommand("eth") != null) {
            getCommand("eth").setExecutor(EthCmd);
            getCommand("eth").setTabCompleter(EthCmd);
        }

        if (getCommand("misiones") != null) getCommand("misiones").setExecutor(new QuestCommand());
        if (getCommand("battlepass") != null) getCommand("battlepass").setExecutor(new BattlePassCommand());
        if (getCommand("misionesadmin") != null) getCommand("misionesadmin").setExecutor(new QuestAdminCommand());
        if (getCommand("tempfly") != null) getCommand("tempfly").setExecutor(new TempFlyCommand());

        if (getCommand("ethtools") != null) {
            AreaToolCommand areaToolCommand = new AreaToolCommand(managerHandler.getAreaToolManager());
            getCommand("ethtools").setExecutor(areaToolCommand);
            getCommand("ethtools").setTabCompleter(areaToolCommand);
        }

        if (getCommand("ethloadchunk") != null) {
            cl.pandress.command.admin.KeepChunkCommand keepChunkCommand = new cl.pandress.command.admin.KeepChunkCommand(managerHandler.getKeepChunkManager());
            getCommand("ethloadchunk").setExecutor(keepChunkCommand);
            getCommand("ethloadchunk").setTabCompleter(keepChunkCommand);
        }

        if (getCommand("ethspawners") != null) {
            CustomSpawnerCommand customSpawnerCmd = new CustomSpawnerCommand(managerHandler.getCustomSpawnerManager(), spawnerMenuHandler);
            getCommand("ethspawners").setExecutor(customSpawnerCmd);
            getCommand("ethspawners").setTabCompleter(customSpawnerCmd);
        }

        if (getCommand("descubrimientos") != null) {
            if (managerHandler.getDiscoveryManager().isEnabled()) {
                DiscoveryCommand discoveryCmd = new DiscoveryCommand(managerHandler.getDiscoveryManager());
                getCommand("descubrimientos").setExecutor(discoveryCmd);
            } else {
                getCommand("descubrimientos").setExecutor((sender, command, label, args) -> {
                    sender.sendMessage(ChatUtils.colorize("&cEl módulo de descubrimientos está desactivado."));
                    return true;
                });
            }
        }

        if (getCommand("ethhopperlinks") != null) {
            if (managerHandler.getHopperLinkManager().isEnabled()) {
                HopperLinkCommand hopperCmd = new HopperLinkCommand(managerHandler.getHopperLinkManager());
                getCommand("ethhopperlinks").setExecutor(hopperCmd);
                getCommand("ethhopperlinks").setTabCompleter(hopperCmd);
            } else {
                getCommand("ethhopperlinks").setExecutor((sender, command, label, args) -> {
                    sender.sendMessage(ChatUtils.colorize("&cEl módulo de tolvas inalámbricas está desactivado."));
                    return true;
                });
            }
        }

        if (getCommand("ethdragon") != null) {
            DragonEventCommand dragonCmd = new DragonEventCommand(managerHandler.getDragonEventManager(), managerHandler.getDragonScheduleManager(), this);
            getCommand("ethdragon").setExecutor(dragonCmd);
            getCommand("ethdragon").setTabCompleter(dragonCmd);
        }

        if (getCommand("deathcoins") != null) {
            DeathCoinsCommand deathCoinsCmd = new DeathCoinsCommand(managerHandler.getHeadDeathManager());
            getCommand("deathcoins").setExecutor(deathCoinsCmd);
            getCommand("deathcoins").setTabCompleter(deathCoinsCmd);
        }

        if (getCommand("deathmenu") != null) {
            getCommand("deathmenu").setExecutor(new DeathMenuCommand(managerHandler.getHeadDeathManager(), this.cosmeticsGUI));
        }
    }

    private void registerEvents() {
        Bukkit.getPluginManager().registerEvents(new QuestListener(), this);
        Bukkit.getPluginManager().registerEvents(new QuestMenuListener(), this);
        Bukkit.getPluginManager().registerEvents(new BattlePassListener(), this);
        Bukkit.getPluginManager().registerEvents(new ChatBubbleListener(), this);

        Bukkit.getPluginManager().registerEvents(new AreaToolListener(managerHandler.getAreaToolManager()), this);
        Bukkit.getPluginManager().registerEvents(new AreaToolMenu(managerHandler.getAreaToolManager()), this);
        Bukkit.getPluginManager().registerEvents(new AdminMenu(managerHandler.getAreaToolManager()), this);

        Bukkit.getPluginManager().registerEvents(new HeadDeathListener(managerHandler.getHeadDeathManager()), this);
        
        Bukkit.getPluginManager().registerEvents(new CosmeticsGUIListener(managerHandler.getHeadDeathManager(), this.cosmeticsGUI), this);

        Bukkit.getPluginManager().registerEvents(new KeepChunkListener(managerHandler.getKeepChunkManager()), this);
        getServer().getPluginManager().registerEvents(new cl.pandress.modules.keepchunk.menus.KeepChunkMenu(managerHandler.getKeepChunkManager()), this);
        getServer().getPluginManager().registerEvents(new cl.pandress.modules.keepchunk.menus.KeepChunkAdminMenu(managerHandler.getKeepChunkManager()), this);

        Bukkit.getPluginManager().registerEvents(new CustomSpawnerListener(managerHandler.getCustomSpawnerManager()), this);
        Bukkit.getPluginManager().registerEvents(spawnerMenuHandler, this);

        if (managerHandler.getDiscoveryManager().isEnabled()) {
            Bukkit.getPluginManager().registerEvents(new DiscoveryMenuListener(managerHandler.getDiscoveryManager()), this);
        }

        if (managerHandler.getHopperLinkManager().isEnabled()) {
            Bukkit.getPluginManager().registerEvents(new HopperLinkListener(managerHandler.getHopperLinkManager()), this);
        }

        DragonEventManager dragonManager = managerHandler.getDragonEventManager();
        DragonEventListener dragonListener = new DragonEventListener(dragonManager, this);
        dragonManager.setEventListener(dragonListener);
        Bukkit.getPluginManager().registerEvents(dragonListener, this);
    }

    private void setupDiscord() {
        boolean isEnabled = managerHandler.getDiscordManager().getConfig().getBoolean("discord.enabled", true);
        if (!isEnabled) {
            log("&eMódulo de Discord desactivado desde la configuración.");
            return;
        }

        String token = managerHandler.getDiscordManager().getConfig().getString("discord.token", "");
        if (token == null || token.isEmpty() || token.equals("AQUI_PEGA_EL_TOKEN_DE_TU_BOT")) {
            log("&eDiscord desactivado (Token inválido o vacío).");
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                jda = JDABuilder.createDefault(token)
                        .enableIntents(GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT)
                        .build();

                jda.awaitReady();

                ServerStatusTask statusTask = new ServerStatusTask(this, jda);
                statusTask.runTaskTimerAsynchronously(this, 100L, 1200L);

                log("&9Bot de Discord conectado. Sistemas activos.");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log("&cDiscord init interrumpido: " + e.getMessage());
            } catch (Exception e) {
                log("&cError al iniciar el bot de Discord: " + e.getMessage());
            }
        });
    }

    private void setupPlaceholders() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            Bukkit.getScheduler().runTaskLater(this, () -> {
                new BattlePassPlaceholder().register();
                new DiscordPlaceholder(this).register();
                new DragonPlaceholders(this, managerHandler.getDragonScheduleManager()).register();
                
                new HeadDeathPlaceholder(this).register();
                
                log("&aMódulo interno de Placeholders anclado correctamente a PlaceholderAPI.");
            }, 1L);
        }
    }

    private void shutdownDiscord() {
        if (jda == null) return;
        log("&9Actualizando el estado de Discord a Offline...");

        try {
            String channelId = managerHandler.getDiscordManager().getConfig().getString("status-monitor.channel-id", "");
            String messageId = managerHandler.getDiscordManager().getConfig().getString("status-monitor.message-id", "");

            if (channelId != null && !channelId.isEmpty() && messageId != null && !messageId.isEmpty()) {
                TextChannel channel = jda.getTextChannelById(channelId);
                if (channel != null) {
                    ServerStatusTask tempTask = new ServerStatusTask(this, jda);
                    channel.editMessageEmbedsById(messageId, tempTask.buildStatusEmbed(false).build())
                            .queue(
                                    success -> log("&9Estado de Discord actualizado a Offline."),
                                    error -> log("&cNo se pudo actualizar el embed de Discord: " + error.getMessage())
                            );
                }
            }
        } catch (Exception e) {
            log("&cError apagando Discord: " + e.getMessage());
        }

        try {
            jda.shutdown();
            if (!jda.awaitShutdown(java.time.Duration.ofSeconds(2))) {
                jda.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            jda.shutdownNow();
        }
    }

    public void log(String msg) {
        Bukkit.getConsoleSender().sendMessage(ChatUtils.colorize("&8[&eEtherium&8] &r" + msg));
    }

    public static Etherium getInstance() { return instance; }
    public ManagerHandler getManagerHandler() { return managerHandler; }
    public JDA getJda() { return jda; }
    public Economy getEconomy() { return economy; }
    public CosmeticsGUI getCosmeticsGUI() { return cosmeticsGUI; }

    public class ManagerHandler {
        private final QuestManager questManager;
        private final BattlePassManager battlePassManager;
        private final DiscordManager discordManager;
        private final AreaToolManager areaToolManager;
        private final HeadDeathManager headDeathManager;
        private final KeepChunkManager keepChunkManager;
        private final CustomSpawnerManager customSpawnerManager;
        private final DiscoveryManager discoveryManager;
        private final HopperLinkManager hopperLinkManager;
        private final DragonEventManager dragonEventManager;
        private final DragonScheduleManager dragonScheduleManager;

        public ManagerHandler() {
            this.questManager = new QuestManager();
            this.battlePassManager = new BattlePassManager();
            this.discordManager = new DiscordManager();
            this.areaToolManager = new AreaToolManager(Etherium.this);
            this.headDeathManager = new HeadDeathManager(Etherium.this);
            this.keepChunkManager = new KeepChunkManager(Etherium.this);
            this.customSpawnerManager = new CustomSpawnerManager(Etherium.this);
            this.discoveryManager = new DiscoveryManager(Etherium.this);
            this.hopperLinkManager = new HopperLinkManager(Etherium.this);
            this.dragonEventManager = new DragonEventManager(Etherium.this);
            this.dragonScheduleManager = new DragonScheduleManager(Etherium.this, this.dragonEventManager);
        }

        public QuestManager getQuestManager() { return questManager; }
        public BattlePassManager getBattlePassManager() { return battlePassManager; }
        public DiscordManager getDiscordManager() { return discordManager; }
        public AreaToolManager getAreaToolManager() { return areaToolManager; }
        public HeadDeathManager getHeadDeathManager() { return headDeathManager; }
        public KeepChunkManager getKeepChunkManager() { return keepChunkManager; }
        public CustomSpawnerManager getCustomSpawnerManager() { return customSpawnerManager; }
        public DiscoveryManager getDiscoveryManager() { return discoveryManager; }
        public HopperLinkManager getHopperLinkManager() { return hopperLinkManager; }
        public DragonEventManager getDragonEventManager() { return dragonEventManager; }
        public DragonScheduleManager getDragonScheduleManager() { return dragonScheduleManager; }
    }
}