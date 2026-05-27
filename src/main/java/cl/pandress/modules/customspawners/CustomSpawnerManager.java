package cl.pandress.modules.customspawners;

import cl.pandress.modules.customspawners.data.CustomSpawnerData;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public class CustomSpawnerManager implements Listener {
    public static final String NBT_SPAWNER_TYPE = "eth_spawner_type";
    private final JavaPlugin plugin;
    private FileConfiguration config;
    private FileConfiguration messages;
    private FileConfiguration menuMain;
    private FileConfiguration menuGive;
    private FileConfiguration menuPlayerList;
    private FileConfiguration menuPlayerSpawners;
    private File dataFile;
    private FileConfiguration data;
    private final NamespacedKey typeKey;
    public final NamespacedKey keyAction;
    public final NamespacedKey keyPage;
    public final NamespacedKey keyOwner;
    public final NamespacedKey keyLoc;
    private final Map<Location, CustomSpawnerData> activeSpawners = new HashMap<>();
    private final AtomicBoolean dirty = new AtomicBoolean(false);
    private boolean saveInProgress = false;

    public CustomSpawnerManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.typeKey = new NamespacedKey(plugin, "eth_spawner_type");
        this.keyAction = new NamespacedKey(plugin, "cs_action");
        this.keyPage = new NamespacedKey(plugin, "cs_page");
        this.keyOwner = new NamespacedKey(plugin, "cs_owner");
        this.keyLoc = new NamespacedKey(plugin, "cs_loc");
        this.loadConfig();
        this.loadMessages();
        this.loadMenus();
        this.loadData();

        Bukkit.getPluginManager().registerEvents(this, plugin);
        (new CustomSpawnerTask(this)).runTaskTimer(plugin, 20L, 20L);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            this.resolveNullWorlds();
            this.reapplyAllSpawnerBlocks();
            plugin.getLogger().info("[CustomSpawners] Inicialización completada. Spawners activos: " + activeSpawners.size());
        }, 60L);

        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (this.dirty.compareAndSet(true, false)) {
                this.saveSpawnerDataAsync();
            }
        }, 6000L, 6000L);
    }

    private void resolveNullWorlds() {
        FileConfiguration freshData = YamlConfiguration.loadConfiguration(this.dataFile);
        if (!freshData.contains("spawners")) return;

        Map<Location, CustomSpawnerData> resolved = new HashMap<>();
        for (Map.Entry<Location, CustomSpawnerData> entry : activeSpawners.entrySet()) {
            CustomSpawnerData spawner = entry.getValue();
            Location loc = spawner.getLocation();

            if (loc.getWorld() == null) {
                Location freshLoc = freshData.getLocation("spawners." + spawner.getId().toString() + ".location");
                if (freshLoc != null && freshLoc.getWorld() != null) {
                    CustomSpawnerData fixedSpawner = new CustomSpawnerData(
                            spawner.getId(), freshLoc, spawner.getEntityType(),
                            spawner.getOwnerId(), spawner.getOwnerName()
                    );
                    resolved.put(freshLoc, fixedSpawner);
                } else {
                    resolved.put(loc, spawner);
                }
            } else {
                resolved.put(loc, spawner);
            }
        }
        activeSpawners.clear();
        activeSpawners.putAll(resolved);
    }

    public void applySpawnerBlock(Location loc, EntityType type) {
        Block block = loc.getBlock();
        if (block.getType() != Material.SPAWNER) return;
        if (!(block.getState() instanceof CreatureSpawner cs)) return;

        cs.setSpawnedType(type);
        
        // VALORES 100% VANILLA - Esto asegura que el cliente renderice el giro y el fuego
        cs.setSpawnCount(4);
        cs.setRequiredPlayerRange(16);
        cs.setMaxNearbyEntities(6);
        cs.setMinSpawnDelay(200);
        cs.setMaxSpawnDelay(800);
        if (cs.getDelay() <= 0) {
            cs.setDelay(400); // Darle empujón visual si está atascado
        }
        cs.update(true, false);
    }

    private void reapplyAllSpawnerBlocks() {
        for (CustomSpawnerData spawner : activeSpawners.values()) {
            Location loc = spawner.getLocation();
            if (loc.getWorld() == null) continue;
            if (!loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) continue;
            if (loc.getBlock().getType() != Material.SPAWNER) continue;
            applySpawnerBlock(loc, spawner.getEntityType());
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        int cx = event.getChunk().getX();
        int cz = event.getChunk().getZ();
        World eventWorld = event.getWorld();

        for (CustomSpawnerData spawner : activeSpawners.values()) {
            Location loc = spawner.getLocation();
            if (loc.getWorld() == null || !loc.getWorld().equals(eventWorld)) continue;

            if ((loc.getBlockX() >> 4) == cx && (loc.getBlockZ() >> 4) == cz) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (loc.getBlock().getType() == Material.SPAWNER) {
                        applySpawnerBlock(loc, spawner.getEntityType());
                    }
                }, 10L);
            }
        }
    }

    public void saveSpawnerData() { this.dirty.set(true); }
    public void saveSpawnerDataSync() { this.writeDataToFile(); }

    private void saveSpawnerDataAsync() {
        if (!this.saveInProgress) {
            this.saveInProgress = true;
            Map<String, Object[]> snapshot = new HashMap<>();

            for (CustomSpawnerData spawner : this.activeSpawners.values()) {
                snapshot.put(spawner.getId().toString(), new Object[]{
                        spawner.getLocation().clone(),
                        spawner.getEntityType().name(),
                        spawner.getOwnerId() != null ? spawner.getOwnerId().toString() : null,
                        spawner.getOwnerName()
                });
            }

            Bukkit.getScheduler().runTaskAsynchronously(this.plugin, () -> {
                try {
                    FileConfiguration tempData = new YamlConfiguration();
                    for (Map.Entry<String, Object[]> entry : snapshot.entrySet()) {
                        String path = "spawners." + entry.getKey();
                        Object[] values = entry.getValue();
                        tempData.set(path + ".location", values[0]);
                        tempData.set(path + ".type", values[1]);
                        if (values[2] != null) {
                            tempData.set(path + ".ownerId", values[2]);
                            tempData.set(path + ".ownerName", values[3]);
                        }
                    }
                    tempData.save(this.dataFile);
                } catch (IOException e) {
                } finally {
                    this.saveInProgress = false;
                }
            });
        }
    }

    private void writeDataToFile() {
        this.data.set("spawners", null);
        for (CustomSpawnerData spawner : this.activeSpawners.values()) {
            String path = "spawners." + spawner.getId().toString();
            this.data.set(path + ".location", spawner.getLocation());
            this.data.set(path + ".type", spawner.getEntityType().name());
            if (spawner.getOwnerId() != null) {
                this.data.set(path + ".ownerId", spawner.getOwnerId().toString());
                this.data.set(path + ".ownerName", spawner.getOwnerName());
            }
        }
        try { this.data.save(this.dataFile); } catch (IOException ignored) {}
    }

    public void addSpawner(Location loc, EntityType type, UUID ownerId, String ownerName) {
        CustomSpawnerData spawner = new CustomSpawnerData(UUID.randomUUID(), loc, type, ownerId, ownerName);
        this.activeSpawners.put(loc, spawner);
        applySpawnerBlock(loc, type);
        this.saveSpawnerData();
    }

    public void removeSpawner(Location loc) {
        if (this.activeSpawners.remove(loc) != null) this.saveSpawnerData();
    }

    private void loadConfig() {
        File configFile = new File(this.plugin.getDataFolder(), "modules/customspawners/config.yml");
        configFile.getParentFile().mkdirs();
        if (!configFile.exists()) {
            try { configFile.createNewFile(); } catch (IOException ignored) {}
        }
        this.config = YamlConfiguration.loadConfiguration(configFile);
        this.config.addDefault("settings.max-nearby-entities", 10);
        this.config.addDefault("settings.min-delay-seconds", 10);
        this.config.addDefault("settings.max-delay-seconds", 25);
        this.config.addDefault("settings.mobs-per-spawn", 3);
        this.config.addDefault("item.name", "&eSpawner Custom &7({type})");
        this.config.addDefault("item.lore", Arrays.asList("&7¡Funciona con los Cargadores de Chunks!", "&eColócalo en el suelo para activarlo."));
        this.config.addDefault("webhook.enabled", false);
        this.config.addDefault("webhook.url", "TU_WEBHOOK_AQUI");
        this.config.options().copyDefaults(true);
        try { this.config.save(configFile); } catch (IOException ignored) {}
    }

    private void loadMessages() {
        File msgFile = new File(this.plugin.getDataFolder(), "modules/customspawners/messages.yml");
        if (!msgFile.exists()) {
            try { msgFile.createNewFile(); } catch (IOException ignored) {}
        }
        this.messages = YamlConfiguration.loadConfiguration(msgFile);
        this.messages.addDefault("prefix", "&8[&eSpawners&8] ");
        this.messages.addDefault("placed", "&a¡Spawner de {type} colocado!");
        this.messages.addDefault("error", "&cError al configurar el Spawner: {error}");
        this.messages.addDefault("picked-up", "&eSpawner recogido.");
        this.messages.addDefault("give", "&aHas dado {amount} Spawners de {type} a {player}.");
        this.messages.addDefault("receive", "&aHas recibido {amount} Spawners de {type}.");
        this.messages.addDefault("no-permission", "&cNo tienes permisos.");
        this.messages.addDefault("invalid-player", "&cJugador no encontrado.");
        this.messages.addDefault("invalid-type", "&cTipo de entidad inválido.");
        this.messages.addDefault("invalid-number", "&cLa cantidad debe ser un número válido.");
        this.messages.addDefault("usage-give", "&eUso: /ethspawners give <jugador> <tipo> [cantidad]");
        this.messages.addDefault("usage-menu", "&eUso: /ethspawners menu");
        this.messages.addDefault("player-only", "&cSolo jugadores pueden abrir el menú.");
        this.messages.addDefault("tp-success", "&a¡Teletransportado con éxito al spawner!");
        this.messages.addDefault("removed-success", "&c¡Spawner eliminado permanentemente!");
        this.messages.addDefault("error-loc", "&cError al gestionar la ubicación.");
        this.messages.options().copyDefaults(true);
        try { this.messages.save(msgFile); } catch (IOException ignored) {}
    }

    private void loadMenus() {
        File menusFolder = new File(this.plugin.getDataFolder(), "modules/customspawners/menus");
        if (!menusFolder.exists()) { menusFolder.mkdirs(); }
        File fMain = new File(menusFolder, "main.yml");
        this.menuMain = YamlConfiguration.loadConfiguration(fMain);
        this.menuMain.addDefault("title", "&8Menú de Spawners");
        this.menuMain.addDefault("size", 27);
        this.menuMain.addDefault("items.give.slot", 11);
        this.menuMain.addDefault("items.give.material", "ZOMBIE_SPAWN_EGG");
        this.menuMain.addDefault("items.give.name", "&a► Obtener Spawners");
        this.menuMain.addDefault("items.give.lore", Arrays.asList("&7Abre el menú para sacar spawners", "&7directamente a tu inventario."));
        this.menuMain.addDefault("items.stats.slot", 13);
        this.menuMain.addDefault("items.stats.material", "PAPER");
        this.menuMain.addDefault("items.stats.name", "&e► Estadísticas Globales");
        this.menuMain.addDefault("items.stats.lore", Arrays.asList("&7Total Activos: &b{total}", "", "&7Sistema optimizado por Etherium"));
        this.menuMain.addDefault("items.list.slot", 15);
        this.menuMain.addDefault("items.list.material", "ENDER_EYE");
        this.menuMain.addDefault("items.list.name", "&d► Gestionar por Dueño");
        this.menuMain.addDefault("items.list.lore", Arrays.asList("&7Mira la lista de jugadores con spawners", "&7y gestiona sus ubicaciones."));
        this.menuMain.addDefault("background.material", "BLACK_STAINED_GLASS_PANE");
        this.menuMain.options().copyDefaults(true);
        try { this.menuMain.save(fMain); } catch (IOException ignored) {}

        File fGive = new File(menusFolder, "give.yml");
        this.menuGive = YamlConfiguration.loadConfiguration(fGive);
        this.menuGive.addDefault("title", "&8Obtener Spawners");
        this.menuGive.addDefault("size", 54);
        this.menuGive.addDefault("item-format.name", "&eSpawner de {type}");
        this.menuGive.addDefault("item-format.lore", Arrays.asList("&aClick para darte 1x {type}"));
        this.menuGive.addDefault("back-button.slot", 49);
        this.menuGive.addDefault("back-button.material", "BARRIER");
        this.menuGive.addDefault("back-button.name", "&cVolver");
        this.menuGive.options().copyDefaults(true);
        try { this.menuGive.save(fGive); } catch (IOException ignored) {}

        File fPlayerList = new File(menusFolder, "player_list.yml");
        this.menuPlayerList = YamlConfiguration.loadConfiguration(fPlayerList);
        this.menuPlayerList.addDefault("title", "&8Lista de Dueños - Pag {page}");
        this.menuPlayerList.addDefault("size", 54);
        this.menuPlayerList.addDefault("head-format.name", "&e► {player}");
        this.menuPlayerList.addDefault("head-format.lore", Arrays.asList("&8UUID: {uuid}", "", "&7Spawners Totales: &b{total}", "&7Chunks Ocupados: &f{chunks}", "", "&aClick para ver detalles."));
        this.menuPlayerList.addDefault("navigation.back.name", "&cVolver Menú Principal");
        this.menuPlayerList.addDefault("navigation.prev.name", "&ePágina Anterior ({page})");
        this.menuPlayerList.addDefault("navigation.next.name", "&ePágina Siguiente ({page})");
        this.menuPlayerList.addDefault("navigation.next.lore", Arrays.asList("&7Restantes: &f{left}"));
        this.menuPlayerList.options().copyDefaults(true);
        try { this.menuPlayerList.save(fPlayerList); } catch (IOException ignored) {}

        File fPlayerSpawners = new File(menusFolder, "player_spawners.yml");
        this.menuPlayerSpawners = YamlConfiguration.loadConfiguration(fPlayerSpawners);
        this.menuPlayerSpawners.addDefault("title", "&0Spawners de &3{player} &0- Pag {page}");
        this.menuPlayerSpawners.addDefault("size", 54);
        this.menuPlayerSpawners.addDefault("spawner-format.material", "SPAWNER");
        this.menuPlayerSpawners.addDefault("spawner-format.name", "&6Spawner: {type}");
        this.menuPlayerSpawners.addDefault("spawner-format.lore", Arrays.asList("&7Dueño: &f{player}", "&7Mundo: {world_color}{world}", "&7Coordenadas: &f{x}, {y}, {z}", "", "&a► Click Izquierdo para TP", "&c► Click Derecho para Eliminar"));
        this.menuPlayerSpawners.addDefault("navigation.back.name", "&cVolver a Dueños");
        this.menuPlayerSpawners.addDefault("navigation.prev.name", "&ePágina Anterior ({page})");
        this.menuPlayerSpawners.addDefault("navigation.next.name", "&ePágina Siguiente ({page})");
        this.menuPlayerSpawners.addDefault("navigation.next.lore", Arrays.asList("&7Restantes: &f{left}"));
        this.menuPlayerSpawners.options().copyDefaults(true);
        try { this.menuPlayerSpawners.save(fPlayerSpawners); } catch (IOException ignored) {}
    }

    private void loadData() {
        this.dataFile = new File(this.plugin.getDataFolder(), "modules/customspawners/data.yml");
        if (!this.dataFile.exists()) {
            try { this.dataFile.createNewFile(); } catch (IOException ignored) {}
        }
        this.data = YamlConfiguration.loadConfiguration(this.dataFile);
        if (this.data.contains("spawners")) {
            for (String key : this.data.getConfigurationSection("spawners").getKeys(false)) {
                try {
                    UUID id = UUID.fromString(key);
                    Location loc = this.data.getLocation("spawners." + key + ".location");
                    EntityType type = EntityType.valueOf(this.data.getString("spawners." + key + ".type"));
                    UUID ownerId = null;
                    String ownerName = "Desconocido";
                    if (this.data.contains("spawners." + key + ".ownerId")) {
                        ownerId = UUID.fromString(this.data.getString("spawners." + key + ".ownerId"));
                        ownerName = this.data.getString("spawners." + key + ".ownerName");
                    }
                    CustomSpawnerData spawner = new CustomSpawnerData(id, loc, type, ownerId, ownerName);
                    this.activeSpawners.put(loc, spawner);
                } catch (Exception e) {}
            }
        }
    }

    public String getMessage(String path) {
        String prefix = this.messages.getString("prefix", "&8[&eSpawners&8] ");
        String msg = this.messages.getString(path, "&cMensaje no encontrado: " + path);
        return ChatColor.translateAlternateColorCodes('&', prefix + msg);
    }
    public CustomSpawnerData getSpawnerAt(Location loc) { return this.activeSpawners.get(loc); }
    public Map<Location, CustomSpawnerData> getActiveSpawners() { return this.activeSpawners; }
    public FileConfiguration getConfig() { return this.config; }
    public FileConfiguration getMessages() { return this.messages; }
    public FileConfiguration getMenuMain() { return this.menuMain; }
    public FileConfiguration getMenuGive() { return this.menuGive; }
    public FileConfiguration getMenuPlayerList() { return this.menuPlayerList; }
    public FileConfiguration getMenuPlayerSpawners() { return this.menuPlayerSpawners; }
    public NamespacedKey getTypeKey() { return this.typeKey; }
    public JavaPlugin getPlugin() { return this.plugin; }

    public ItemStack createSpawnerItem(EntityType type) {
        ItemStack item = new ItemStack(Material.SPAWNER);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(this.typeKey, PersistentDataType.STRING, type.name());
        String nameFormat = this.config.getString("item.name", "&eSpawner Custom &7({type})");
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', nameFormat.replace("{type}", type.name())));
        List<String> configLore = this.config.getStringList("item.lore");
        List<String> finalLore = new ArrayList<>();
        for (String line : configLore) {
            finalLore.add(ChatColor.translateAlternateColorCodes('&', line));
        }
        meta.setLore(finalLore);
        try { meta.addItemFlags(new ItemFlag[]{ItemFlag.valueOf("HIDE_ADDITIONAL_TOOLTIP")}); } catch (Exception ignored) {}
        item.setItemMeta(meta);
        return item;
    }
}