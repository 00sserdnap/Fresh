package cl.pandress.core;

import cl.pandress.Etherium;
import cl.pandress.modules.areatools.AreaToolManager;
import cl.pandress.modules.battlepass.BattlePassManager;
import cl.pandress.modules.bosses.dragon.DragonEventManager;
import cl.pandress.modules.bosses.dragon.managers.DragonScheduleManager;
import cl.pandress.modules.customspawners.CustomSpawnerManager;
import cl.pandress.modules.discoveries.DiscoveryManager;
import cl.pandress.modules.essentials.SpawnManager;
import cl.pandress.modules.headdeath.HeadDeathManager;
import cl.pandress.modules.hopperlinks.HopperLinkManager;
import cl.pandress.modules.keepchunk.KeepChunkManager;
import cl.pandress.modules.quests.QuestManager;
import cl.pandress.modules.rankup.RankManager;

/**
 * Contenedor central de todos los managers del plugin.
 * Antes era una inner class de Etherium — ahora es una clase independiente
 * en el paquete cl.pandress.core para que sea importable desde cualquier módulo
 * sin necesidad de referenciar la clase principal del plugin.
 */
public class ManagerHandler {

    private final QuestManager          questManager;
    private final BattlePassManager     battlePassManager;
    private final AreaToolManager       areaToolManager;
    private final HeadDeathManager      headDeathManager;
    private final KeepChunkManager      keepChunkManager;
    private final CustomSpawnerManager  customSpawnerManager;
    private final DiscoveryManager      discoveryManager;
    private final HopperLinkManager     hopperLinkManager;
    private final DragonEventManager    dragonEventManager;
    private final DragonScheduleManager dragonScheduleManager;
    private final SpawnManager          spawnManager;
    private final RankManager           rankManager;

    public ManagerHandler(Etherium plugin) {
        this.questManager          = new QuestManager();
        this.battlePassManager     = new BattlePassManager();
        this.areaToolManager       = new AreaToolManager(plugin);
        this.headDeathManager      = new HeadDeathManager(plugin);
        this.keepChunkManager      = new KeepChunkManager(plugin);
        this.customSpawnerManager  = new CustomSpawnerManager(plugin);
        this.discoveryManager      = new DiscoveryManager(plugin);
        this.hopperLinkManager     = new HopperLinkManager(plugin);
        this.dragonEventManager    = new DragonEventManager(plugin);
        this.dragonScheduleManager = new DragonScheduleManager(plugin, this.dragonEventManager);
        this.spawnManager          = new SpawnManager(plugin);
        this.rankManager           = new RankManager();
    }

    /**
     * Apaga todos los managers de forma ordenada.
     * Llamar desde Etherium.onDisable() después de cancelar las tareas.
     */
    public void shutdown() {
        if (headDeathManager      != null) headDeathManager.cleanup();
        if (keepChunkManager      != null) keepChunkManager.saveLoaderData();
        if (customSpawnerManager  != null) customSpawnerManager.saveSpawnerData();
        if (discoveryManager      != null) discoveryManager.shutdown();
        if (hopperLinkManager     != null && hopperLinkManager.isEnabled()) hopperLinkManager.saveData();
        if (dragonEventManager    != null) dragonEventManager.cleanup();
        if (rankManager           != null) rankManager.shutdown();
        if (questManager          != null) questManager.shutdown();
        if (battlePassManager     != null) battlePassManager.shutdown();
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public QuestManager          getQuestManager()          { return questManager; }
    public BattlePassManager     getBattlePassManager()     { return battlePassManager; }
    public AreaToolManager       getAreaToolManager()       { return areaToolManager; }
    public HeadDeathManager      getHeadDeathManager()      { return headDeathManager; }
    public KeepChunkManager      getKeepChunkManager()      { return keepChunkManager; }
    public CustomSpawnerManager  getCustomSpawnerManager()  { return customSpawnerManager; }
    public DiscoveryManager      getDiscoveryManager()      { return discoveryManager; }
    public HopperLinkManager     getHopperLinkManager()     { return hopperLinkManager; }
    public DragonEventManager    getDragonEventManager()    { return dragonEventManager; }
    public DragonScheduleManager getDragonScheduleManager() { return dragonScheduleManager; }
    public SpawnManager          getSpawnManager()          { return spawnManager; }
    public RankManager           getRankManager()           { return rankManager; }
}