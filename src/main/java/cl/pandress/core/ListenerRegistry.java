package cl.pandress.core;

import cl.pandress.Etherium;
import cl.pandress.modules.areatools.AreaToolListener;
import cl.pandress.modules.areatools.menus.AdminMenu;
import cl.pandress.modules.areatools.menus.AreaToolMenu;
import cl.pandress.modules.battlepass.BattlePassListener;
import cl.pandress.modules.bosses.dragon.DragonEventManager;
import cl.pandress.modules.bosses.dragon.listeners.DragonEventListener;
import cl.pandress.modules.chatbubble.ChatBubbleListener;
import cl.pandress.modules.customspawners.CustomSpawnerListener;
import cl.pandress.modules.customspawners.menus.CustomSpawnerMenu;
import cl.pandress.modules.discoveries.menus.DiscoveryMenuListener;
import cl.pandress.modules.headdeath.HeadDeathListener;
import cl.pandress.modules.headdeath.gui.CosmeticsGUIListener;
import cl.pandress.modules.hopperlinks.listeners.HopperLinkListener;
import cl.pandress.modules.keepchunk.KeepChunkListener;
import cl.pandress.modules.keepchunk.menus.KeepChunkAdminMenu;
import cl.pandress.modules.keepchunk.menus.KeepChunkMenu;
import cl.pandress.modules.quests.QuestListener;
import cl.pandress.modules.quests.menus.QuestMenuListener;
import cl.pandress.modules.rankup.RankListener;
import cl.pandress.modules.rankup.menus.RankMenuListener;
import cl.pandress.modules.rankup.menus.RankTopMenuListener;
import org.bukkit.plugin.PluginManager;

/**
 * Registra todos los listeners del plugin.
 * Separado del main para que añadir o quitar un listener
 * no requiera tocar Etherium.java.
 */
public class ListenerRegistry {

    private final Etherium plugin;
    private final ManagerHandler managers;
    private final CustomSpawnerMenu spawnerMenu;

    public ListenerRegistry(Etherium plugin, ManagerHandler managers, CustomSpawnerMenu spawnerMenu) {
        this.plugin      = plugin;
        this.managers    = managers;
        this.spawnerMenu = spawnerMenu;
    }

    /**
     * Punto de entrada único. Llamar desde Etherium.onEnable()
     * después de inicializar el ManagerHandler.
     */
    public void registerAll() {
        PluginManager pm = plugin.getServer().getPluginManager();

        registerCoreListeners(pm);
        registerGameplayListeners(pm);
        registerWorldListeners(pm);
        registerDragonListeners(pm);
        registerConditionalListeners(pm);
    }

    // =========================================================
    //  CORE — siempre activos
    // =========================================================

    private void registerCoreListeners(PluginManager pm) {
        // Misiones diarias
        pm.registerEvents(new QuestListener(), plugin);
        pm.registerEvents(new QuestMenuListener(), plugin);

        // BattlePass
        pm.registerEvents(new BattlePassListener(), plugin);

        // Chat bubble (#mensaje)
        pm.registerEvents(new ChatBubbleListener(), plugin);

        // RankUp
        pm.registerEvents(new RankListener(), plugin);
        pm.registerEvents(new RankMenuListener(), plugin);
        pm.registerEvents(new RankTopMenuListener(), plugin);
    }

    // =========================================================
    //  GAMEPLAY — cosméticos, herramientas, spawners
    // =========================================================

    private void registerGameplayListeners(PluginManager pm) {
        // AreaTools
        pm.registerEvents(new AreaToolListener(managers.getAreaToolManager()), plugin);
        pm.registerEvents(new AreaToolMenu(managers.getAreaToolManager()), plugin);
        pm.registerEvents(new AdminMenu(managers.getAreaToolManager()), plugin);

        // HeadDeath + cosméticos
        pm.registerEvents(new HeadDeathListener(managers.getHeadDeathManager()), plugin);
        pm.registerEvents(
            new CosmeticsGUIListener(managers.getHeadDeathManager(), plugin.getCosmeticsGUI()), plugin
        );

        // Custom Spawners
        pm.registerEvents(new CustomSpawnerListener(managers.getCustomSpawnerManager()), plugin);
        pm.registerEvents(spawnerMenu, plugin);
    }

    // =========================================================
    //  MUNDO — cargadores, tolvas
    // =========================================================

    private void registerWorldListeners(PluginManager pm) {
        // KeepChunk + sus menús
        pm.registerEvents(new KeepChunkListener(managers.getKeepChunkManager()), plugin);
        pm.registerEvents(new KeepChunkMenu(managers.getKeepChunkManager()), plugin);
        pm.registerEvents(new KeepChunkAdminMenu(managers.getKeepChunkManager()), plugin);
    }

    // =========================================================
    //  DRAGON EVENT — requiere enlace bidireccional manager ↔ listener
    // =========================================================

    private void registerDragonListeners(PluginManager pm) {
        DragonEventManager dragonManager = managers.getDragonEventManager();
        DragonEventListener dragonListener = new DragonEventListener(dragonManager, plugin);

        // El manager necesita la referencia al listener para poder llamar
        // a launchShockwave() y resetDeathCounts() desde sus mecánicas.
        dragonManager.setEventListener(dragonListener);

        pm.registerEvents(dragonListener, plugin);
    }

    // =========================================================
    //  CONDICIONALES — solo si el módulo está habilitado
    // =========================================================

    private void registerConditionalListeners(PluginManager pm) {
        // Discoveries
        if (managers.getDiscoveryManager().isEnabled()) {
            pm.registerEvents(new DiscoveryMenuListener(managers.getDiscoveryManager()), plugin);
        }

        // HopperLinks
        if (managers.getHopperLinkManager().isEnabled()) {
            pm.registerEvents(new HopperLinkListener(managers.getHopperLinkManager()), plugin);
        }
    }
}