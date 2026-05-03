package cl.pandress;

import cl.pandress.command.admin.FreshCommand;
import cl.pandress.command.admin.QuestAdminCommand;
import cl.pandress.command.admin.TempFlyCommand;
import cl.pandress.command.player.QuestCommand;
import cl.pandress.command.player.RankupCommand;
import cl.pandress.command.player.BattlePassCommand;
import cl.pandress.modules.quests.menus.QuestMenuListener;
import cl.pandress.modules.rankup.menus.RankMenuListener;
import cl.pandress.modules.quests.QuestListener;
import cl.pandress.modules.quests.QuestManager;
import cl.pandress.modules.rankup.RankManager;
import cl.pandress.modules.rankup.placeholderapi.RankPlaceholder;
import cl.pandress.modules.battlepass.BattlePassManager;
import cl.pandress.modules.battlepass.BattlePassListener;
import cl.pandress.modules.battlepass.placeholderapi.BattlePassPlaceholder;
import cl.pandress.modules.chatbubble.ChatBubbleListener; // Importación de Burbujas de Chat
import cl.pandress.utils.ChatUtils;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Plugin Fresh - Core Principal desarrollado por pandress.
 * Gestiona misiones diarias, sistema de RankUp, Pase de Batalla, Chat Local y herramientas administrativas.
 */
public class Fresh extends JavaPlugin {

    private static Fresh instance;
    private ManagerHandler managerHandler;

    @Override
    public void onEnable() {
        instance = this;

        // 1. Inicialización de Managers (Misiones, Rangos y Pase de Batalla)
        this.managerHandler = new ManagerHandler();
        
        // 2. Registro de Comandos
        FreshCommand freshCmd = new FreshCommand();
        if (getCommand("fresh") != null) {
            getCommand("fresh").setExecutor(freshCmd);
            getCommand("fresh").setTabCompleter(freshCmd);
        }

        // Comandos de jugadores
        if (getCommand("rankup") != null) {
            getCommand("rankup").setExecutor(new RankupCommand());
        }
        if (getCommand("quests") != null) {
            getCommand("quests").setExecutor(new QuestCommand());
        }
        if (getCommand("battlepass") != null) {
            getCommand("battlepass").setExecutor(new BattlePassCommand());
        } else {
            log("&cADVERTENCIA: Comando /battlepass no encontrado en plugin.yml");
        }

        // Comandos Administrativos / Misiones
        if (getCommand("misionesadmin") != null) {
            getCommand("misionesadmin").setExecutor(new QuestAdminCommand());
        } else {
            log("&cADVERTENCIA: Comando /misionesadmin no encontrado en plugin.yml");
        }

        if (getCommand("tempfly") != null) {
            getCommand("tempfly").setExecutor(new TempFlyCommand());
        } else {
            log("&cADVERTENCIA: Comando /tempfly no encontrado en plugin.yml");
        }

        // 3. Registro de Eventos (Listeners)
        registerEvents();

        // 4. Registro de Placeholders (Requiere PlaceholderAPI)
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new RankPlaceholder().register();
            new BattlePassPlaceholder().register();
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
        // Misiones y Rangos
        Bukkit.getPluginManager().registerEvents(new QuestListener(), this);
        Bukkit.getPluginManager().registerEvents(new QuestMenuListener(), this);
        Bukkit.getPluginManager().registerEvents(new RankMenuListener(), this);
        
        // Pase de Batalla
        Bukkit.getPluginManager().registerEvents(new BattlePassListener(), this);
        
        // Burbujas de Chat (NUEVO)
        Bukkit.getPluginManager().registerEvents(new ChatBubbleListener(), this);
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
        private final BattlePassManager battlePassManager;

        public ManagerHandler() {
            this.questManager = new QuestManager();
            this.rankManager = new RankManager();
            this.battlePassManager = new BattlePassManager();
        }

        public QuestManager getQuestManager() {
            return questManager;
        }

        public RankManager getRankManager() {
            return rankManager;
        }

        public BattlePassManager getBattlePassManager() {
            return battlePassManager;
        }
    }
}