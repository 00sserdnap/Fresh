package cl.pandress.modules.customspawners;

import cl.pandress.modules.customspawners.data.CustomSpawnerData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class CustomSpawnerManager {

    public static final String NBT_SPAWNER_TYPE = "eth_spawner_type";

    private final JavaPlugin plugin;

    // Archivos de Configuración
    private FileConfiguration config;
    private FileConfiguration messages;
    private FileConfiguration menuMain;
    private FileConfiguration menuGive;
    private FileConfiguration menuPlayerList;
    private FileConfiguration menuPlayerSpawners;

    // Archivo de Datos
    private File dataFile;
    private FileConfiguration data;

    // Llaves para datos invisibles (PDC)
    private final NamespacedKey typeKey;
    public final NamespacedKey keyAction;
    public final NamespacedKey keyPage;
    public final NamespacedKey keyOwner;
    public final NamespacedKey keyLoc;

    private final Map<Location, CustomSpawnerData> activeSpawners = new HashMap<>();

    // --- OPTIMIZACIÓN: Dirty flag para evitar guardados innecesarios ---
    private final AtomicBoolean dirty = new AtomicBoolean(false);
    private boolean saveInProgress = false;

    public CustomSpawnerManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.typeKey = new NamespacedKey(plugin, NBT_SPAWNER_TYPE);

        this.keyAction = new NamespacedKey(plugin, "cs_action");
        this.keyPage = new NamespacedKey(plugin, "cs_page");
        this.keyOwner = new NamespacedKey(plugin, "cs_owner");
        this.keyLoc = new NamespacedKey(plugin, "cs_loc");

        loadConfig();
        loadMessages();
        loadMenus();
        loadData();

        new CustomSpawnerTask(this).runTaskTimer(plugin, 20L, 20L);

        // --- OPTIMIZACIÓN: Guardado periódico asíncrono cada 5 minutos ---
        // Reemplaza el guardado síncrono en cada evento por un ciclo periódico
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (dirty.compareAndSet(true, false)) {
                saveSpawnerDataAsync();
            }
        }, 6000L, 6000L); // 6000 ticks = 5 minutos
    }

    // --- OPTIMIZACIÓN: Guardado asíncrono real ---
    // El hilo principal prepara una copia de los datos y el hilo async escribe el archivo
    public void saveSpawnerData() {
        dirty.set(true); // Marca como pendiente; el ciclo periódico lo guardará
    }

    // Llamado forzoso al apagar el servidor (síncrono, está bien en onDisable)
    public void saveSpawnerDataSync() {
        writeDataToFile();
    }

    private void saveSpawnerDataAsync() {
        if (saveInProgress) return;
        saveInProgress = true;

        // 1. Crear snapshot en el hilo principal
        final Map<String, Object[]> snapshot = new HashMap<>();
        for (CustomSpawnerData spawner : activeSpawners.values()) {
            snapshot.put(spawner.getId().toString(), new Object[]{
                spawner.getLocation(),
                spawner.getEntityType().name(),
                spawner.getOwnerId() != null ? spawner.getOwnerId().toString() : null,
                spawner.getOwnerName()
            });
        }

        // 2. Escribir en disco en hilo async
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

        try {
            tempData.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("[CustomSpawners] Error al guardar datos: " + e.getMessage());
        } finally {
            saveInProgress = false;
        }
    }

    private void writeDataToFile() {
        data.set("spawners", null);
        for (CustomSpawnerData spawner : activeSpawners.values()) {
            String path = "spawners." + spawner.getId().toString();
            data.set(path + ".location", spawner.getLocation());
            data.set(path + ".type", spawner.getEntityType().name());
            if (spawner.getOwnerId() != null) {
                data.set(path + ".ownerId", spawner.getOwnerId().toString());
                data.set(path + ".ownerName", spawner.getOwnerName());
            }
        }
        try {
            data.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("[CustomSpawners] Error al guardar datos: " + e.getMessage());
        }
    }

    public void addSpawner(Location loc, EntityType type, UUID ownerId, String ownerName) {
        CustomSpawnerData spawner = new CustomSpawnerData(UUID.randomUUID(), loc, type, ownerId, ownerName);
        activeSpawners.put(loc, spawner);
        saveSpawnerData();
    }

    public void removeSpawner(Location loc) {
        if (activeSpawners.remove(loc) != null) {
            saveSpawnerData();
        }
    }

    private void loadConfig() {
        File configFile = new File(plugin.getDataFolder(), "modules/customspawners/config.yml");
        configFile.getParentFile().mkdirs();
        if (!configFile.exists()) {
            try { configFile.createNewFile(); } catch (IOException ignored) {}
        }
        config = YamlConfiguration.loadConfiguration(configFile);

        config.addDefault("settings.max-nearby-entities", 10);
        config.addDefault("settings.min-delay-seconds", 10);
        config.addDefault("settings.max-delay-seconds", 25);
        config.addDefault("settings.mobs-per-spawn", 3);

        config.addDefault("item.name", "&eSpawner Custom &7({type})");
        config.addDefault("item.lore", Arrays.asList(
                "&7¡Funciona con los Cargadores de Chunks!",
                "&eColócalo en el suelo para activarlo."
        ));

        config.addDefault("webhook.enabled", false);
        config.addDefault("webhook.url", "TU_WEBHOOK_AQUI");

        config.options().copyDefaults(true);
        try { config.save(configFile); } catch (IOException ignored) {}
    }

    private void loadMessages() {
        File msgFile = new File(plugin.getDataFolder(), "modules/customspawners/messages.yml");
        if (!msgFile.exists()) {
            try { msgFile.createNewFile(); } catch (IOException ignored) {}
        }
        messages = YamlConfiguration.loadConfiguration(msgFile);

        messages.addDefault("prefix", "&8[&eSpawners&8] ");
        messages.addDefault("placed", "&a¡Spawner de {type} colocado!");
        messages.addDefault("error", "&cError al configurar el Spawner: {error}");
        messages.addDefault("picked-up", "&eSpawner recogido.");
        messages.addDefault("give", "&aHas dado {amount} Spawners de {type} a {player}.");
        messages.addDefault("receive", "&aHas recibido {amount} Spawners de {type}.");
        messages.addDefault("no-permission", "&cNo tienes permisos.");
        messages.addDefault("invalid-player", "&cJugador no encontrado.");
        messages.addDefault("invalid-type", "&cTipo de entidad inválido.");
        messages.addDefault("invalid-number", "&cLa cantidad debe ser un número válido.");
        messages.addDefault("usage-give", "&eUso: /ethspawners give <jugador> <tipo> [cantidad]");
        messages.addDefault("usage-menu", "&eUso: /ethspawners menu");
        messages.addDefault("player-only", "&cSolo jugadores pueden abrir el menú.");
        messages.addDefault("tp-success", "&a¡Teletransportado con éxito al spawner!");
        messages.addDefault("removed-success", "&c¡Spawner eliminado permanentemente!");
        messages.addDefault("error-loc", "&cError al gestionar la ubicación.");

        messages.options().copyDefaults(true);
        try { messages.save(msgFile); } catch (IOException ignored) {}
    }

    private void loadMenus() {
        File menusFolder = new File(plugin.getDataFolder(), "modules/customspawners/menus");
        if (!menusFolder.exists()) menusFolder.mkdirs();

        File fMain = new File(menusFolder, "main.yml");
        menuMain = YamlConfiguration.loadConfiguration(fMain);
        menuMain.addDefault("title", "&8Menú de Spawners");
        menuMain.addDefault("size", 27);
        menuMain.addDefault("items.give.slot", 11);
        menuMain.addDefault("items.give.material", "ZOMBIE_SPAWN_EGG");
        menuMain.addDefault("items.give.name", "&a► Obtener Spawners");
        menuMain.addDefault("items.give.lore", Arrays.asList("&7Abre el menú para sacar spawners", "&7directamente a tu inventario."));
        menuMain.addDefault("items.stats.slot", 13);
        menuMain.addDefault("items.stats.material", "PAPER");
        menuMain.addDefault("items.stats.name", "&e► Estadísticas Globales");
        menuMain.addDefault("items.stats.lore", Arrays.asList("&7Total Activos: &b{total}", "", "&7Sistema optimizado por Etherium"));
        menuMain.addDefault("items.list.slot", 15);
        menuMain.addDefault("items.list.material", "ENDER_EYE");
        menuMain.addDefault("items.list.name", "&d► Gestionar por Dueño");
        menuMain.addDefault("items.list.lore", Arrays.asList("&7Mira la lista de jugadores con spawners", "&7y gestiona sus ubicaciones."));
        menuMain.addDefault("background.material", "BLACK_STAINED_GLASS_PANE");
        menuMain.options().copyDefaults(true);
        try { menuMain.save(fMain); } catch (IOException ignored) {}

        File fGive = new File(menusFolder, "give.yml");
        menuGive = YamlConfiguration.loadConfiguration(fGive);
        menuGive.addDefault("title", "&8Obtener Spawners");
        menuGive.addDefault("size", 54);
        menuGive.addDefault("item-format.name", "&eSpawner de {type}");
        menuGive.addDefault("item-format.lore", Arrays.asList("&aClick para darte 1x {type}"));
        menuGive.addDefault("back-button.slot", 49);
        menuGive.addDefault("back-button.material", "BARRIER");
        menuGive.addDefault("back-button.name", "&cVolver");
        menuGive.options().copyDefaults(true);
        try { menuGive.save(fGive); } catch (IOException ignored) {}

        File fPlayerList = new File(menusFolder, "player_list.yml");
        menuPlayerList = YamlConfiguration.loadConfiguration(fPlayerList);
        menuPlayerList.addDefault("title", "&8Lista de Dueños - Pag {page}");
        menuPlayerList.addDefault("size", 54);
        menuPlayerList.addDefault("head-format.name", "&e► {player}");
        menuPlayerList.addDefault("head-format.lore", Arrays.asList(
                "&8UUID: {uuid}", "", "&7Spawners Totales: &b{total}", "&7Chunks Ocupados: &f{chunks}", "", "&aClick para ver detalles."
        ));
        menuPlayerList.addDefault("navigation.back.name", "&cVolver Menú Principal");
        menuPlayerList.addDefault("navigation.prev.name", "&ePágina Anterior ({page})");
        menuPlayerList.addDefault("navigation.next.name", "&ePágina Siguiente ({page})");
        menuPlayerList.addDefault("navigation.next.lore", Arrays.asList("&7Restantes: &f{left}"));
        menuPlayerList.options().copyDefaults(true);
        try { menuPlayerList.save(fPlayerList); } catch (IOException ignored) {}

        File fPlayerSpawners = new File(menusFolder, "player_spawners.yml");
        menuPlayerSpawners = YamlConfiguration.loadConfiguration(fPlayerSpawners);
        menuPlayerSpawners.addDefault("title", "&0Spawners de &3{player} &0- Pag {page}");
        menuPlayerSpawners.addDefault("size", 54);
        menuPlayerSpawners.addDefault("spawner-format.material", "SPAWNER");
        menuPlayerSpawners.addDefault("spawner-format.name", "&6Spawner: {type}");
        menuPlayerSpawners.addDefault("spawner-format.lore", Arrays.asList(
                "&7Dueño: &f{player}", "&7Mundo: {world_color}{world}", "&7Coordenadas: &f{x}, {y}, {z}", "", "&a► Click Izquierdo para TP", "&c► Click Derecho para Eliminar"
        ));
        menuPlayerSpawners.addDefault("navigation.back.name", "&cVolver a Dueños");
        menuPlayerSpawners.addDefault("navigation.prev.name", "&ePágina Anterior ({page})");
        menuPlayerSpawners.addDefault("navigation.next.name", "&ePágina Siguiente ({page})");
        menuPlayerSpawners.addDefault("navigation.next.lore", Arrays.asList("&7Restantes: &f{left}"));
        menuPlayerSpawners.options().copyDefaults(true);
        try { menuPlayerSpawners.save(fPlayerSpawners); } catch (IOException ignored) {}
    }

    private void loadData() {
        dataFile = new File(plugin.getDataFolder(), "modules/customspawners/data.yml");
        if (!dataFile.exists()) {
            try { dataFile.createNewFile(); } catch (IOException ignored) {}
        }
        data = YamlConfiguration.loadConfiguration(dataFile);

        if (data.contains("spawners")) {
            for (String key : data.getConfigurationSection("spawners").getKeys(false)) {
                try {
                    UUID id = UUID.fromString(key);
                    Location loc = data.getLocation("spawners." + key + ".location");
                    EntityType type = EntityType.valueOf(data.getString("spawners." + key + ".type"));

                    UUID ownerId = null;
                    String ownerName = "Desconocido";
                    if (data.contains("spawners." + key + ".ownerId")) {
                        ownerId = UUID.fromString(data.getString("spawners." + key + ".ownerId"));
                        ownerName = data.getString("spawners." + key + ".ownerName");
                    }

                    CustomSpawnerData spawner = new CustomSpawnerData(id, loc, type, ownerId, ownerName);
                    activeSpawners.put(loc, spawner);
                } catch (Exception e) {
                    plugin.getLogger().warning("[CustomSpawners] Error cargando spawner: " + key);
                }
            }
        }
    }

    public String getMessage(String path) {
        String prefix = messages.getString("prefix", "&8[&eSpawners&8] ");
        String msg = messages.getString(path, "&cMensaje no encontrado: " + path);
        return ChatColor.translateAlternateColorCodes('&', prefix + msg);
    }

    public CustomSpawnerData getSpawnerAt(Location loc) { return activeSpawners.get(loc); }
    public Map<Location, CustomSpawnerData> getActiveSpawners() { return activeSpawners; }

    public FileConfiguration getConfig() { return config; }
    public FileConfiguration getMessages() { return messages; }
    public FileConfiguration getMenuMain() { return menuMain; }
    public FileConfiguration getMenuGive() { return menuGive; }
    public FileConfiguration getMenuPlayerList() { return menuPlayerList; }
    public FileConfiguration getMenuPlayerSpawners() { return menuPlayerSpawners; }
    public NamespacedKey getTypeKey() { return typeKey; }
    public JavaPlugin getPlugin() { return plugin; }

    public ItemStack createSpawnerItem(EntityType type) {
        ItemStack item = new ItemStack(Material.SPAWNER);
        ItemMeta meta = item.getItemMeta();

        meta.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, type.name());

        String nameFormat = config.getString("item.name", "&eSpawner Custom &7({type})");
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', nameFormat.replace("{type}", type.name())));

        List<String> configLore = config.getStringList("item.lore");
        List<String> finalLore = new ArrayList<>();
        for (String line : configLore) {
            finalLore.add(ChatColor.translateAlternateColorCodes('&', line));
        }
        meta.setLore(finalLore);

        // Agregamos la flag solo si existe en tu versión, si no, se ignora silenciosamente
        try {
            meta.addItemFlags(ItemFlag.valueOf("HIDE_ADDITIONAL_TOOLTIP"));
        } catch (Exception ignored) {
            // No hacemos nada, la versión es antigua y no requiere flag adicional
        }

        item.setItemMeta(meta);
        return item;
    }
}