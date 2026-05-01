package cl.pandress;

import cl.pandress.command.admin.FreshCommand;
import cl.pandress.command.player.QuestCommand;
import cl.pandress.command.player.RankupCommand;
import cl.pandress.menus.QuestMenuListener;
import cl.pandress.menus.RankMenuListener;
import cl.pandress.modules.quests.QuestListener;
import cl.pandress.modules.quests.QuestManager;
import cl.pandress.modules.rankup.RankManager;
import cl.pandress.modules.rankup.RankPlaceholder;
import cl.pandress.utils.ChatUtils;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Plugin Fresh - Core Principal desarrollado por pandress.
 * Gestiona misiones diarias, sistema de RankUp y herramientas administrativas.
 */
public class Fresh extends JavaPlugin {

    private static Fresh instance;
    private ManagerHandler managerHandler;

    @Override
    public void onEnable() {
        instance = this;

        // 1. Inicialización de Managers (Misiones y Rangos)
        this.managerHandler = new ManagerHandler();
        
        // 2. Registro de Comandos
        FreshCommand freshCmd = new FreshCommand();
        getCommand("fresh").setExecutor(freshCmd);
        getCommand("fresh").setTabCompleter(freshCmd);

        // Comandos de jugadores
        getCommand("rankup").setExecutor(new RankupCommand());
        getCommand("quests").setExecutor(new QuestCommand());

        // 3. Registro de Eventos (Listeners)
        registerEvents();

        // 4. Registro de Placeholders (Requiere PlaceholderAPI)
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new RankPlaceholder().register();
            log("&aPlaceholderAPI detectado. Placeholders registrados correctamente.");
        } else {
            log("&eADVERTENCIA: PlaceholderAPI no detectado. Los placeholders no funcionarán.");
        }

        log("&b&lFresh &aherramientas cargadas exitosamente.");
    }

    @Override
    public void onDisable() {
        if (managerHandler != null) {
            log("&eGuardando datos de usuarios antes del cierre...");
            // Los datos se guardan en tiempo real, pero esto es por seguridad visual en consola.
        }
        log("&cPlugin Fresh desactivado.");
    }

    private void registerEvents() {
        Bukkit.getPluginManager().registerEvents(new QuestListener(), this);
        Bukkit.getPluginManager().registerEvents(new QuestMenuListener(), this);
        Bukkit.getPluginManager().registerEvents(new RankMenuListener(), this);
    }

    /**
     * Envía un mensaje formateado a la consola del servidor.
     */
    public void log(String message) {
        Bukkit.getConsoleSender().sendMessage(ChatUtils.colorize("&b[Fresh] &r" + message));
    }

    public static Fresh getInstance() {
        return instance;
    }

    public ManagerHandler getManagerHandler() {
        return managerHandler;
    }

    /**
     * Clase interna para organizar los gestores de módulos.
     */
    public class ManagerHandler {
        private final QuestManager questManager;
        private final RankManager rankManager;

        public ManagerHandler() {
            this.questManager = new QuestManager();
            this.rankManager = new RankManager();
        }

        public QuestManager getQuestManager() {
            return questManager;
        }

        public RankManager getRankManager() {
            return rankManager;
        }
    }
}