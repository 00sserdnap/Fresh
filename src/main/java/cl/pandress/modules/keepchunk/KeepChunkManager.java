package cl.pandress.modules.keepchunk;

import cl.pandress.modules.keepchunk.data.KeepChunkData;
import cl.pandress.modules.keepchunk.discord.KeepChunkDiscord;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class KeepChunkManager {

    public static final String NBT_CHUNK_ID = "keepchunk_id";
    public static final String NBT_CORE_KEY = "keepchunk_core";
    public static final String NBT_CUSTOM_NAME = "keepchunk_name";
    public static final String NBT_FUEL = "keepchunk_fuel";
    public static final String NBT_OWNER = "keepchunk_owner";
    public static final String NBT_IS_TEMP = "keepchunk_istemp";

    private final JavaPlugin plugin;
    private FileConfiguration config;
    private FileConfiguration messagesCfg;
    private FileConfiguration menuCfg;
    private FileConfiguration adminMenuCfg;

    private File dataFile;
    private FileConfiguration data;

    private final NamespacedKey chunkIdKey;
    private final NamespacedKey coreKey;
    private final NamespacedKey customNameKey;
    private final NamespacedKey fuelKey;
    private final NamespacedKey ownerKey;
    private final NamespacedKey tempKey;

    private final Map<String, KeepChunkType> loadedTypes = new HashMap<>();
    private final Map<UUID, KeepChunkData> activeLoaders = new HashMap<>();
    private final Map<UUID, UUID> renamingPlayers = new HashMap<>();

    private KeepChunkDiscord discordHook;

    public KeepChunkManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.chunkIdKey = new NamespacedKey(plugin, NBT_CHUNK_ID);
        this.coreKey = new NamespacedKey(plugin, NBT_CORE_KEY);
        this.customNameKey = new NamespacedKey(plugin, NBT_CUSTOM_NAME);
        this.fuelKey = new NamespacedKey(plugin, NBT_FUEL);
        this.ownerKey = new NamespacedKey(plugin, NBT_OWNER);
        this.tempKey = new NamespacedKey(plugin, NBT_IS_TEMP);

        loadFiles();
        this.discordHook = new KeepChunkDiscord(this);

        loadData();
        startFuelTask();
        startDiscordTask();
        startParticleTask();
    }

    private void loadFiles() {
        this.config = loadYaml("config.yml");
        this.messagesCfg = loadYaml("messages.yml");
        this.menuCfg = loadYaml("menus/control_panel.yml");
        this.adminMenuCfg = loadYaml("menus/admin_menu.yml");

        loadedTypes.clear();
        if (config.contains("loaders")) {
            for (String key : config.getConfigurationSection("loaders").getKeys(false)) {
                String path = "loaders." + key;
                KeepChunkType type = new KeepChunkType(
                        key,
                        config.getString(path + ".name", "&aCargador"),
                        config.getInt(path + ".radius", 0),
                        config.getBoolean(path + ".permanent", false),
                        config.getInt(path + ".max-fuel", 1440),
                        config.getStringList(path + ".lore")
                );
                loadedTypes.put(key, type);
            }
        }
    }

    private FileConfiguration loadYaml(String fileName) {
        File file = new File(plugin.getDataFolder(), "modules/keepchunk/" + fileName);
        if (!file.exists()) {
            plugin.saveResource("modules/keepchunk/" + fileName, false);
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    public String getMsg(String key) {
        String prefix = messagesCfg.getString("prefix", "&8[&bChunkLoader&8] &r");
        return ChatColor.translateAlternateColorCodes('&', prefix + messagesCfg.getString(key, "&cMensaje no configurado: " + key));
    }

    private void loadData() {
        dataFile = new File(plugin.getDataFolder(), "modules/keepchunk/data.yml");
        if (!dataFile.exists()) {
            try { dataFile.createNewFile(); } catch (IOException ignored) {}
        }
        data = YamlConfiguration.loadConfiguration(dataFile);

        if (data.contains("loaders")) {
            for (String key : data.getConfigurationSection("loaders").getKeys(false)) {
                try {
                    UUID id = UUID.fromString(key);
                    UUID owner = UUID.fromString(data.getString("loaders." + key + ".owner"));
                    String typeId = data.getString("loaders." + key + ".type");
                    Location loc = data.getLocation("loaders." + key + ".location");
                    int fuel = data.getInt("loaders." + key + ".fuel");
                    boolean active = data.getBoolean("loaders." + key + ".active");
                    String customName = data.getString("loaders." + key + ".customName");
                    boolean particles = data.getBoolean("loaders." + key + ".particles", true);
                    long activeSince = data.getLong("loaders." + key + ".activeSince", 0);
                    boolean isTemporary = data.getBoolean("loaders." + key + ".isTemporary", false);

                    KeepChunkData loaderData = new KeepChunkData(id, owner, typeId, loc, fuel, isTemporary);
                    loaderData.setActive(active);
                    loaderData.setCustomName(customName);
                    loaderData.setShowParticles(particles);
                    loaderData.setActiveSince(activeSince);

                    activeLoaders.put(id, loaderData);

                    if (loadedTypes.containsKey(typeId) && active) toggleChunks(loaderData, true);
                } catch (Exception e) {
                    plugin.getLogger().warning("[KeepChunk] Error cargando loader: " + key);
                }
            }
        }
    }

    public void saveLoaderData() {
        data.set("loaders", null);
        for (KeepChunkData loader : activeLoaders.values()) {
            String path = "loaders." + loader.getId().toString();
            data.set(path + ".owner", loader.getOwner().toString());
            data.set(path + ".type", loader.getTypeId());
            data.set(path + ".location", loader.getLocation());
            data.set(path + ".fuel", loader.getFuel());
            data.set(path + ".active", loader.isActive());
            data.set(path + ".customName", loader.getCustomName());
            data.set(path + ".particles", loader.isShowParticles());
            data.set(path + ".activeSince", loader.getActiveSince());
            data.set(path + ".isTemporary", loader.isTemporary());
        }
        try { data.save(dataFile); } catch (IOException ignored) {}
    }

    public NamespacedKey getChunkIdKey() { return chunkIdKey; }
    public NamespacedKey getCoreKey() { return coreKey; }
    public NamespacedKey getCustomNameKey() { return customNameKey; }
    public NamespacedKey getFuelKey() { return fuelKey; }
    public NamespacedKey getOwnerKey() { return ownerKey; }
    public NamespacedKey getTempKey() { return tempKey; }

    public FileConfiguration getConfig() { return config; }
    public FileConfiguration getMenuCfg() { return menuCfg; }
    public FileConfiguration getAdminMenuCfg() { return adminMenuCfg; }

    public Map<UUID, UUID> getRenamingPlayers() { return renamingPlayers; }
    public Map<UUID, KeepChunkData> getActiveLoaders() { return activeLoaders; }
    public KeepChunkDiscord getDiscordHook() { return discordHook; }
    public KeepChunkType getType(String id) { return loadedTypes.get(id); }
    public Map<String, KeepChunkType> getLoadedTypes() { return loadedTypes; }

    public KeepChunkData getLoader(UUID id) { return activeLoaders.get(id); }

    public int getLoaderCount(UUID owner) {
        int count = 0;
        for (KeepChunkData data : activeLoaders.values()) {
            if (data.getOwner().equals(owner)) count++;
        }
        return count;
    }

    public int getMaxLoaders(Player player) {
        if (player.isOp() || player.hasPermission("keepchunk.admin")) return 999;
        int max = config.getInt("limits.default", 1);
        if (config.contains("limits")) {
            for (String key : config.getConfigurationSection("limits").getKeys(false)) {
                if (player.hasPermission("keepchunk.limit." + key)) {
                    int limit = config.getInt("limits." + key);
                    if (limit > max) max = limit;
                }
            }
        }
        return max;
    }

    public ItemStack createLoaderItem(String typeId, int fuelOverride, String customName, UUID owner, boolean isTemporary) {
        KeepChunkType type = loadedTypes.get(typeId);
        if (type == null) return null;

        int finalFuel = fuelOverride == -1 ? type.getMaxFuel() : fuelOverride;

        ItemStack item = new ItemStack(Material.VILLAGER_SPAWN_EGG);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setEnchantmentGlintOverride(true);
            meta.getPersistentDataContainer().set(chunkIdKey, PersistentDataType.STRING, type.getId());
            meta.getPersistentDataContainer().set(fuelKey, PersistentDataType.INTEGER, finalFuel);
            
            if (isTemporary) {
                meta.getPersistentDataContainer().set(tempKey, PersistentDataType.BYTE, (byte) 1);
            }

            if (customName != null) meta.getPersistentDataContainer().set(customNameKey, PersistentDataType.STRING, customName);
            if (owner != null) meta.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, owner.toString());

            String displayName = customName != null ? customName : type.getName();
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', displayName));

            List<String> lore = new ArrayList<>();
            for (String line : type.getLore()) lore.add(ChatColor.translateAlternateColorCodes('&', line));
            lore.add("");
            lore.add("§7Dueño: §f" + (owner != null ? Bukkit.getOfflinePlayer(owner).getName() : "Desconocido"));

            if (isTemporary) {
                lore.add("§c§l¡SISTEMA TEMPORAL!");
                lore.add("§7Duración: §e" + finalFuel + " minutos");
            } else if (type.isPermanent()) {
                lore.add("§7Energía: §a§lINFINITA");
            } else {
                lore.add("§7Energía restante: §b" + finalFuel + " min");
            }
            lore.add("");
            lore.add("§e¡Colócalo en el suelo para instalarlo!");
            meta.setLore(lore);

            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack createCore(String coreId) {
        String path = "cores." + coreId;
        if (!config.contains(path)) return null;

        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(coreKey, PersistentDataType.STRING, coreId);
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', config.getString(path + ".name", "&eNúcleo")));

        List<String> lore = new ArrayList<>();
        for (String line : config.getStringList(path + ".lore")) lore.add(ChatColor.translateAlternateColorCodes('&', line));
        meta.setLore(lore);

        item.setItemMeta(meta);
        return item;
    }

    public void spawnLoader(Location loc, Player placer, String typeId, int fuel, String customName, UUID originalOwner, boolean isTemporary) {
        KeepChunkType type = loadedTypes.get(typeId);
        if (type == null) return;

        Villager npc = (Villager) loc.getWorld().spawnEntity(loc.clone().add(0.5, 0, 0.5), EntityType.VILLAGER);

        npc.setAI(false);
        npc.setGravity(false);
        npc.setInvulnerable(true);
        npc.setSilent(true);
        npc.setVillagerType(Villager.Type.JUNGLE);
        npc.setProfession(Villager.Profession.NITWIT);

        UUID actualOwner = originalOwner != null ? originalOwner : placer.getUniqueId();
        String ownerName = Bukkit.getOfflinePlayer(actualOwner).getName();

        String displayName = customName != null ? customName : ChatColor.translateAlternateColorCodes('&', "&aCargador de " + ownerName);
        npc.setCustomNameVisible(true);
        npc.setCustomName(displayName);

        UUID loaderId = UUID.randomUUID();
        npc.getPersistentDataContainer().set(chunkIdKey, PersistentDataType.STRING, loaderId.toString());

        KeepChunkData loaderData = new KeepChunkData(loaderId, actualOwner, typeId, loc, fuel, isTemporary);
        loaderData.setCustomName(displayName);
        loaderData.setActive(true); // Se activa al colocarlo
        loaderData.setActiveSince(System.currentTimeMillis());

        activeLoaders.put(loaderId, loaderData);
        toggleChunks(loaderData, true);
        saveLoaderData();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> discordHook.updateStatusEmbed());
    }

    public void toggleChunks(KeepChunkData data, boolean load) {
        KeepChunkType type = loadedTypes.get(data.getTypeId());
        if (type == null) return;

        World world = data.getLocation().getWorld();
        if (world == null) return;
        Chunk center = data.getLocation().getChunk();
        int radius = type.getRadius();

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                world.setChunkForceLoaded(center.getX() + x, center.getZ() + z, load);
            }
        }
    }

    public void removeLoader(UUID id) {
        KeepChunkData loaderData = activeLoaders.remove(id);
        if (loaderData != null) {
            toggleChunks(loaderData, false);
            saveLoaderData();
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> discordHook.updateStatusEmbed());
        }
    }

    private void startFuelTask() {
        long autoOffMillis = config.getLong("settings.auto-off-offline-time", 259200000L);

        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            boolean needsSave = false;
            List<UUID> toRemove = new ArrayList<>();

            for (KeepChunkData loader : activeLoaders.values()) {
                if (!loader.isActive()) continue;

                KeepChunkType type = loadedTypes.get(loader.getTypeId());
                if (type == null) continue;

                // Solo apagar si NO es temporal y el dueño está offline mucho tiempo
                if (!loader.isTemporary()) {
                    Player ownerPlayer = Bukkit.getPlayer(loader.getOwner());
                    if (ownerPlayer == null) {
                        long lastPlayed = Bukkit.getOfflinePlayer(loader.getOwner()).getLastPlayed();
                        if (lastPlayed > 0 && (System.currentTimeMillis() - lastPlayed) >= autoOffMillis) {
                            loader.setActive(false);
                            loader.setActiveSince(0);
                            Bukkit.getScheduler().runTask(plugin, () -> toggleChunks(loader, false));
                            needsSave = true;
                            continue;
                        }
                    }
                }

                // Lógica de consumo de combustible
                if (!type.isPermanent() || loader.isTemporary()) {
                    loader.setFuel(loader.getFuel() - 1);
                    needsSave = true;

                    if (loader.getFuel() <= 0) {
                        if (loader.isTemporary()) {
                            toRemove.add(loader.getId());
                        } else {
                            loader.setActive(false);
                            loader.setActiveSince(0);
                            Bukkit.getScheduler().runTask(plugin, () -> toggleChunks(loader, false));
                            Player onlineOwner = Bukkit.getPlayer(loader.getOwner());
                            if (onlineOwner != null) onlineOwner.sendMessage(getMsg("auto-off-fuel"));
                        }
                    }
                }
            }

            if (!toRemove.isEmpty()) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    for(UUID id : toRemove) {
                        KeepChunkData data = activeLoaders.get(id);
                        if(data != null) {
                            for(org.bukkit.entity.Entity e : data.getLocation().getWorld().getNearbyEntities(data.getLocation(), 1, 2, 1)) {
                                if(e instanceof Villager && e.getPersistentDataContainer().has(getChunkIdKey(), PersistentDataType.STRING)) {
                                    e.remove();
                                }
                            }
                            Player onlineOwner = Bukkit.getPlayer(data.getOwner());
                            if (onlineOwner != null) onlineOwner.sendMessage(getMsg("temp-expired"));
                            removeLoader(id);
                        }
                    }
                });
            }

            if (needsSave) saveLoaderData();
        }, 1200L, 1200L); // 1 minuto
    }

    private void startDiscordTask() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (discordHook != null) discordHook.updateStatusEmbed();
        }, 200L, 6000L);
    }

    private void startParticleTask() {
        int ticks = config.getInt("settings.particle-update-ticks", 40);
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (KeepChunkData loader : activeLoaders.values()) {
                if (!loader.isShowParticles()) continue;

                Location loc = loader.getLocation().clone().add(0.5, 2.2, 0.5);
                if (loc.getWorld() == null || !loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) continue;

                KeepChunkType type = loadedTypes.get(loader.getTypeId());
                if (type == null) continue;

                if (!loader.isActive()) {
                    loc.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, loc, 3, 0.2, 0.2, 0.2, 0.02);
                } else if (type.isPermanent() && !loader.isTemporary()) {
                    loc.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc, 2, 0.3, 0.3, 0.3, 0);
                } else if (loader.getFuel() >= (type.getMaxFuel() * 0.5)) {
                    loc.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc, 2, 0.3, 0.3, 0.3, 0);
                } else if (loader.getFuel() < (type.getMaxFuel() * 0.2)) {
                    loc.getWorld().spawnParticle(Particle.FLAME, loc, 3, 0.2, 0.2, 0.2, 0.01);
                } else {
                    loc.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc, 1, 0.3, 0.3, 0.3, 0);
                }
            }
        }, ticks, ticks);
    }
}