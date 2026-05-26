package cl.pandress.core;

import cl.pandress.Etherium;
import cl.pandress.command.admin.*;
import cl.pandress.command.player.*;
import cl.pandress.modules.customspawners.menus.CustomSpawnerMenu;
import cl.pandress.utils.ChatUtils;
import org.bukkit.command.PluginCommand;

/**
 * Registra todos los comandos del plugin en el servidor.
 * Cada comando obtiene su ejecutor (y TabCompleter si aplica)
 * desde aquí, manteniendo Etherium.java limpio de esta lógica.
 */
public class CommandRegistry {

    private final Etherium plugin;
    private final ManagerHandler managers;
    private final CustomSpawnerMenu spawnerMenu;

    public CommandRegistry(Etherium plugin, ManagerHandler managers, CustomSpawnerMenu spawnerMenu) {
        this.plugin      = plugin;
        this.managers    = managers;
        this.spawnerMenu = spawnerMenu;
    }

    /**
     * Punto de entrada único. Llamar desde Etherium.onEnable()
     * después de inicializar el ManagerHandler.
     */
    public void registerAll() {
        registerAdminCommands();
        registerPlayerCommands();
        registerModuleCommands();
    }

    // =========================================================
    //  ADMIN
    // =========================================================

    private void registerAdminCommands() {
        // /eth reload [módulo]
        ETHReloadCommand ethCmd = new ETHReloadCommand();
        register("eth", ethCmd, ethCmd);

        // /setspawn
        register("setspawn", new SetSpawnCommand(managers.getSpawnManager()));

        // /misionesadmin test|reset
        register("misionesadmin", new QuestAdminCommand());

        // /tempfly <jugador> <tiempo>
        register("tempfly", new TempFlyCommand());

        // /deathcoins give|take|set
        DeathCoinsCommand deathCoinsCmd = new DeathCoinsCommand(managers.getHeadDeathManager());
        register("deathcoins", deathCoinsCmd, deathCoinsCmd);

        // /deathmenu (admin)
        register("deathmenu", new DeathMenuCommand(managers.getHeadDeathManager(), plugin.getCosmeticsGUI()));

        // /ethtools give|menu
        AreaToolCommand areaToolCmd = new AreaToolCommand(managers.getAreaToolManager());
        register("ethtools", areaToolCmd, areaToolCmd);

        // /ethloadchunk give|givetemp|recharge|adminmenu|cleanup
        KeepChunkCommand keepChunkCmd = new KeepChunkCommand(managers.getKeepChunkManager());
        register("ethloadchunk", keepChunkCmd, keepChunkCmd);

        // /ethspawners give|menu
        CustomSpawnerCommand spawnerCmd = new CustomSpawnerCommand(managers.getCustomSpawnerManager(), spawnerMenu);
        register("ethspawners", spawnerCmd, spawnerCmd);

        // /ethdragon start|stop|set
        DragonEventCommand dragonCmd = new DragonEventCommand(
            managers.getDragonEventManager(),
            managers.getDragonScheduleManager(),
            plugin
        );
        register("ethdragon", dragonCmd, dragonCmd);

        // /ethhopperlinks give
        if (managers.getHopperLinkManager().isEnabled()) {
            HopperLinkCommand hopperCmd = new HopperLinkCommand(managers.getHopperLinkManager());
            register("ethhopperlinks", hopperCmd, hopperCmd);
        } else {
            register("ethhopperlinks", (sender, cmd, label, args) -> {
                sender.sendMessage(ChatUtils.colorize("&cEl módulo de tolvas inalámbricas está desactivado."));
                return true;
            });
        }
    }

    // =========================================================
    //  JUGADOR
    // =========================================================

    private void registerPlayerCommands() {
        // /spawn
        register("spawn", new SpawnCommand(managers.getSpawnManager()));

        // /misiones
        register("misiones", new QuestCommand());

        // /battlepass [bossbar]
        register("battlepass", new BattlePassCommand());

        // /rankup
        register("rankup", new RankupCommand());

        // /descubrimientos
        if (managers.getDiscoveryManager().isEnabled()) {
            register("descubrimientos", new DiscoveryCommand(managers.getDiscoveryManager()));
        } else {
            register("descubrimientos", (sender, cmd, label, args) -> {
                sender.sendMessage(ChatUtils.colorize("&cEl módulo de descubrimientos está desactivado."));
                return true;
            });
        }
    }

    // =========================================================
    //  MÓDULOS CON LÓGICA EXTRA
    // =========================================================

    private void registerModuleCommands() {
        // No hay comandos de módulo adicionales por ahora.
        // Este método existe como punto de extensión para futuros módulos
        // que necesiten registro condicional o con lógica compleja.
    }

    // =========================================================
    //  HELPERS
    // =========================================================

    /** Registra ejecutor sin TabCompleter. */
    private void register(String name, org.bukkit.command.CommandExecutor executor) {
        PluginCommand cmd = plugin.getCommand(name);
        if (cmd == null) {
            plugin.log("&e[CommandRegistry] Comando '/" + name + "' no encontrado en plugin.yml — ignorado.");
            return;
        }
        cmd.setExecutor(executor);
    }

    /** Registra ejecutor con TabCompleter. */
    private void register(String name,
                          org.bukkit.command.CommandExecutor executor,
                          org.bukkit.command.TabCompleter completer) {
        PluginCommand cmd = plugin.getCommand(name);
        if (cmd == null) {
            plugin.log("&e[CommandRegistry] Comando '/" + name + "' no encontrado en plugin.yml — ignorado.");
            return;
        }
        cmd.setExecutor(executor);
        cmd.setTabCompleter(completer);
    }
}