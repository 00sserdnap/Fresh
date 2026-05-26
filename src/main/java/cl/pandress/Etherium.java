package cl.pandress;

import cl.pandress.core.CommandRegistry;
import cl.pandress.core.ListenerRegistry;
import cl.pandress.core.ManagerHandler;
import cl.pandress.core.PlaceholderRegistry;
import cl.pandress.modules.customspawners.menus.CustomSpawnerMenu;
import cl.pandress.modules.headdeath.gui.CosmeticsGUI;
import cl.pandress.utils.ChatUtils;
import net.dv8tion.jda.api.JDA;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Clase principal del plugin Etherium.
 *
 * Responsabilidades de esta clase:
 *   1. Ciclo de vida (onEnable / onDisable)
 *   2. Inicialización de Vault
 *   3. Restauración del timer del Dragon Event tras reinicios
 *   4. Exponer la instancia estática y los getters de módulos transversales
 *
 * Todo lo demás está delegado:
 *   - Comandos    → CommandRegistry
 *   - Listeners   → ListenerRegistry
 *   - Placeholders → PlaceholderRegistry
 *   - Managers    → ManagerHandler
 */
public class Etherium extends JavaPlugin {

    private static Etherium instance;

    // ── Módulos de infraestructura ────────────────────────────────────────────
    private ManagerHandler      managerHandler;
    private CommandRegistry     commandRegistry;
    private ListenerRegistry    listenerRegistry;
    private PlaceholderRegistry placeholderRegistry;

    // ── Objetos que necesitan acceso externo ──────────────────────────────────
    private JDA           jda;
    private Economy       economy;
    private CosmeticsGUI  cosmeticsGUI;
    private CustomSpawnerMenu spawnerMenu;

    // =========================================================
    //  ON ENABLE
    // =========================================================

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        // 1. Vault — debe ir antes que ManagerHandler porque RankManager
        //    intenta obtener la economía en su constructor.
        if (!setupEconomy()) {
            log("&cNo se encontró Vault o un plugin de economía compatible.");
        } else {
            log("&aConectado exitosamente con Vault (Economía).");
        }

        // 2. Managers — todos los módulos del plugin
        managerHandler = new ManagerHandler(this);

        // 3. Objetos que dependen de los managers
        spawnerMenu  = new CustomSpawnerMenu(managerHandler.getCustomSpawnerManager());
        cosmeticsGUI = new CosmeticsGUI(managerHandler.getHeadDeathManager());

        // 4. Comandos
        commandRegistry = new CommandRegistry(this, managerHandler, spawnerMenu);
        commandRegistry.registerAll();

        // 5. Listeners
        listenerRegistry = new ListenerRegistry(this, managerHandler, spawnerMenu);
        listenerRegistry.registerAll();

        // 6. Placeholders
        placeholderRegistry = new PlaceholderRegistry(this, managerHandler);
        placeholderRegistry.registerAll();

        // 7. Restaurar timer del dragón si el servidor fue reiniciado
        loadDragonSchedule();

        log("&8[&eEtherium&8] &aActivado correctamente. Todos los módulos cargados.");
    }

    // =========================================================
    //  ON DISABLE
    // =========================================================

    @Override
    public void onDisable() {
        // Cancelar todas las tareas de Bukkit primero para evitar
        // que alguna tarea async intente escribir mientras apagamos.
        Bukkit.getScheduler().cancelTasks(this);

        // El ManagerHandler sabe el orden correcto de apagado de cada módulo.
        if (managerHandler != null) {
            log("&eGuardando datos y limpiando entidades...");
            managerHandler.shutdown();
        }

        log("&8[&eEtherium&8] &cPlugin desactivado.");
    }

    // =========================================================
    //  VAULT
    // =========================================================

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) return false;
        RegisteredServiceProvider<Economy> rsp =
            getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        economy = rsp.getProvider();
        return economy != null;
    }

    // =========================================================
    //  DRAGON SCHEDULE — restaura el timer tras un reinicio
    // =========================================================

    private void loadDragonSchedule() {
        if (!getConfig().contains("dragon-event.world")) return;

        String w      = getConfig().getString("dragon-event.world");
        double x      = getConfig().getDouble("dragon-event.x");
        double y      = getConfig().getDouble("dragon-event.y");
        double z      = getConfig().getDouble("dragon-event.z");
        float  yaw    = (float) getConfig().getDouble("dragon-event.yaw");
        float  pitch  = (float) getConfig().getDouble("dragon-event.pitch");
        long interval = getConfig().getLong("dragon-event.interval");
        long duration = getConfig().getLong("dragon-event.duration");
        long targetTs = getConfig().getLong("dragon-event.target-timestamp", -1);

        World world = Bukkit.getWorld(w);
        if (world == null) return;

        Location loc = new Location(world, x, y, z, yaw, pitch);
        managerHandler.getDragonScheduleManager().setupSchedule(loc, interval, duration);

        if (targetTs != -1) {
            long remaining = (targetTs - System.currentTimeMillis()) / 1000;
            managerHandler.getDragonScheduleManager()
                          .setTimeUntilNext(remaining > 0 ? remaining : 15);
        }

        log("&aHorario del dragón cargado y sincronizado desde la configuración.");
    }

    // =========================================================
    //  UTILIDADES
    // =========================================================

    public void log(String msg) {
        Bukkit.getConsoleSender().sendMessage(ChatUtils.colorize("&8[&eEtherium&8] &r" + msg));
    }

    // =========================================================
    //  GETTERS
    // =========================================================

    public static Etherium    getInstance()        { return instance; }
    public ManagerHandler     getManagerHandler()  { return managerHandler; }
    public JDA                getJda()             { return jda; }
    public Economy            getEconomy()         { return economy; }
    public CosmeticsGUI       getCosmeticsGUI()    { return cosmeticsGUI; }
    public CustomSpawnerMenu  getSpawnerMenu()     { return spawnerMenu; }
}