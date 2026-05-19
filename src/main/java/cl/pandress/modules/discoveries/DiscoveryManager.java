package cl.pandress.modules.discoveries;

import cl.pandress.Etherium;
import cl.pandress.utils.ChatUtils;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class DiscoveryManager {

    private final Etherium plugin;

    // Estado del módulo
    private boolean enabled;

    // Configuraciones
    private FileConfiguration menuConfig;
    private FileConfiguration mainConfig;
    private FileConfiguration mobsConfig;
    private FileConfiguration biomesConfig;
    private FileConfiguration cavesConfig;

    // Persistencia
    private File dataFile;
    private FileConfiguration dataConfig;

    // Mapas de almacenamiento en memoria
    private final Map<UUID, Map<String, List<String>>> playerDiscoveries;
    private final Map<UUID, Map<String, List<String>>> playerClaimedRewards;

    public DiscoveryManager(Etherium plugin) {
        this.plugin = plugin;
        this.playerDiscoveries = new HashMap<>();
        this.playerClaimedRewards = new HashMap<>();
        loadConfigs();
        loadData();
        startScannerTask();
        startAutoSaveTask();
    }

    public Etherium getPlugin() {
        return plugin;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void loadConfigs() {
        this.menuConfig = loadCustomConfig("modules/discoveries/menus/menu_main.yml");
        this.mainConfig = loadCustomConfig("modules/discoveries/config.yml");
        this.mobsConfig = loadCustomConfig("modules/discoveries/mobs.yml");
        this.biomesConfig = loadCustomConfig("modules/discoveries/biomes.yml");
        this.cavesConfig = loadCustomConfig("modules/discoveries/cuevas.yml");

        // Cargar estado enabled
        this.enabled = mainConfig.getBoolean("settings.enabled", true);
    }

    private FileConfiguration loadCustomConfig(String path) {
        File file = new File(plugin.getDataFolder(), path);
        if (!file.exists()) {
            plugin.saveResource(path, false);
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    public void loadData() {
        dataFile = new File(plugin.getDataFolder(), "modules/discoveries/data.yml");
        if (!dataFile.exists()) {
            try {
                dataFile.getParentFile().mkdirs();
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.log("&cError creando data.yml: " + e.getMessage());
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);

        // Cargar descubrimientos
        if (dataConfig.contains("users")) {
            for (String uuidStr : dataConfig.getConfigurationSection("users").getKeys(false)) {
                UUID uuid = UUID.fromString(uuidStr);
                Map<String, List<String>> categories = new HashMap<>();
                for (String category : dataConfig.getConfigurationSection("users." + uuidStr).getKeys(false)) {
                    categories.put(category, dataConfig.getStringList("users." + uuidStr + "." + category));
                }
                playerDiscoveries.put(uuid, categories);
            }
        }

        // Cargar recompensas reclamadas
        if (dataConfig.contains("claimed")) {
            for (String uuidStr : dataConfig.getConfigurationSection("claimed").getKeys(false)) {
                UUID uuid = UUID.fromString(uuidStr);
                Map<String, List<String>> categories = new HashMap<>();
                for (String category : dataConfig.getConfigurationSection("claimed." + uuidStr).getKeys(false)) {
                    categories.put(category, dataConfig.getStringList("claimed." + uuidStr + "." + category));
                }
                playerClaimedRewards.put(uuid, categories);
            }
        }
    }

    public void saveDataSync() {
        for (Map.Entry<UUID, Map<String, List<String>>> entry : playerDiscoveries.entrySet()) {
            String uuidStr = entry.getKey().toString();
            for (Map.Entry<String, List<String>> catEntry : entry.getValue().entrySet()) {
                dataConfig.set("users." + uuidStr + "." + catEntry.getKey(), catEntry.getValue());
            }
        }

        for (Map.Entry<UUID, Map<String, List<String>>> entry : playerClaimedRewards.entrySet()) {
            String uuidStr = entry.getKey().toString();
            for (Map.Entry<String, List<String>> catEntry : entry.getValue().entrySet()) {
                dataConfig.set("claimed." + uuidStr + "." + catEntry.getKey(), catEntry.getValue());
            }
        }

        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.log("&cError guardando persistencia de descubrimientos.");
        }
    }

    private void startAutoSaveTask() {
        if (!enabled) return;
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::saveDataSync, 6000L, 6000L);
    }

    private void startScannerTask() {
        if (!enabled) return;
        long interval = mainConfig.getLong("settings.scan-interval-ticks", 40L);
        double mobRadius = mainConfig.getDouble("settings.mob-scan-radius", 6.0);

        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                checkDiscoveries(player, mobRadius);
            }
        }, 100L, interval);
    }

    private void checkDiscoveries(Player player, double radius) {
        Location loc = player.getLocation();
        UUID uuid = player.getUniqueId();
        String biomeName = loc.getBlock().getBiome().name();

        // 1. Escaneo de Bioma y Cueva
        if (biomeName.contains("CAVE")) {
            if (!hasDiscovered(uuid, "CAVE", biomeName)) {
                registerDiscovery(player, "CAVE", biomeName);
            }
        } else {
            if (!hasDiscovered(uuid, "BIOME", biomeName)) {
                registerDiscovery(player, "BIOME", biomeName);
            }
        }

        // 2. Escaneo de Estructuras
        checkStructuresFast(player, loc, biomeName, uuid);

        // 3. Escaneo de Mobs
        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (entity instanceof LivingEntity && !(entity instanceof Player)) {
                String mobName = entity.getType().name();
                if (!hasDiscovered(uuid, "MOB", mobName)) {
                    registerDiscovery(player, "MOB", mobName);
                }
            }
        }
    }

    private void checkStructuresFast(Player player, Location loc, String biomeName, UUID uuid) {
        Block center = loc.getBlock();

        for (int x = -3; x <= 3; x++) {
            for (int y = -3; y <= 3; y++) {
                for (int z = -3; z <= 3; z++) {
                    Block b = center.getRelative(x, y, z);
                    Material type = b.getType();

                    String structureFound = null;

                    switch (type) {
                        case TRIAL_SPAWNER:
                        case VAULT:
                            structureFound = "TRIAL_CHAMBERS"; break;
                        case SCULK_SHRIEKER:
                        case SCULK_SENSOR:
                            if (b.getY() < 0) structureFound = "ANCIENT_CITY";
                            break;
                        case END_PORTAL_FRAME:
                            structureFound = "STRONGHOLD"; break;
                        case PRISMARINE:
                        case DARK_PRISMARINE:
                            structureFound = "OCEAN_MONUMENT"; break;
                        case PURPUR_BLOCK:
                            structureFound = "END_CITY"; break;
                        case NETHER_BRICKS:
                            if (loc.getWorld().getEnvironment() == org.bukkit.World.Environment.NETHER)
                                structureFound = "NETHER_FORTRESS";
                            break;
                        case POLISHED_BLACKSTONE_BRICKS:
                        case GILDED_BLACKSTONE:
                            if (loc.getWorld().getEnvironment() == org.bukkit.World.Environment.NETHER)
                                structureFound = "BASTION_REMNANT";
                            break;
                        case CRYING_OBSIDIAN:
                            structureFound = "RUINED_PORTAL"; break;
                        case BELL:
                            if (biomeName.contains("PLAINS")) structureFound = "VILLAGE_PLAINS";
                            else if (biomeName.contains("DESERT")) structureFound = "VILLAGE_DESERT";
                            else if (biomeName.contains("SAVANNA")) structureFound = "VILLAGE_SAVANNA";
                            else if (biomeName.contains("TAIGA")) structureFound = "VILLAGE_TAIGA";
                            else if (biomeName.contains("SNOWY")) structureFound = "VILLAGE_SNOWY";
                            break;
                        case CAULDRON:
                            if (biomeName.contains("SWAMP")) structureFound = "SWAMP_HUT";
                            break;
                        case CHISELED_SANDSTONE:
                            if (biomeName.contains("DESERT")) structureFound = "DESERT_PYRAMID";
                            break;
                        case MOSSY_COBBLESTONE:
                            if (biomeName.contains("JUNGLE")) structureFound = "JUNGLE_PYRAMID";
                            else if (biomeName.contains("OCEAN")) structureFound = "OCEAN_RUIN";
                            break;
                        case COBWEB:
                            if (b.getY() < 40 && !biomeName.contains("CAVE")) structureFound = "MINESHAFT";
                            break;
                        case DARK_OAK_LOG:
                            if (biomeName.contains("DARK_FOREST") && b.getY() > 60) structureFound = "WOODLAND_MANSION";
                            break;
                        default:
                            break;
                    }

                    if (structureFound != null && !hasDiscovered(uuid, "STRUCTURE", structureFound)) {
                        registerDiscovery(player, "STRUCTURE", structureFound);
                        return;
                    }
                }
            }
        }
    }

    private boolean hasDiscovered(UUID uuid, String category, String discoveryId) {
        if (!playerDiscoveries.containsKey(uuid)) return false;
        Map<String, List<String>> userCategories = playerDiscoveries.get(uuid);
        if (!userCategories.containsKey(category)) return false;
        return userCategories.get(category).contains(discoveryId);
    }

    public boolean hasClaimed(UUID uuid, String category, String discoveryId) {
        if (!playerClaimedRewards.containsKey(uuid)) return false;
        Map<String, List<String>> userCategories = playerClaimedRewards.get(uuid);
        if (!userCategories.containsKey(category)) return false;
        return userCategories.get(category).contains(discoveryId);
    }

    private void registerDiscovery(Player player, String category, String id) {
        UUID uuid = player.getUniqueId();
        playerDiscoveries.putIfAbsent(uuid, new HashMap<>());
        Map<String, List<String>> userCategories = playerDiscoveries.get(uuid);
        userCategories.putIfAbsent(category, new ArrayList<>());
        userCategories.get(category).add(id);

        playDiscoveryEffects(player, category, id);
    }

    private void playDiscoveryEffects(Player player, String category, String id) {
        FileConfiguration targetConfig;
        switch (category) {
            case "MOB": targetConfig = mobsConfig; break;
            case "BIOME": targetConfig = biomesConfig; break;
            default: targetConfig = cavesConfig; break;
        }

        String path = "discoveries." + id;

        FileConfiguration actualConfig = targetConfig;
        if (!actualConfig.contains(path)) {
            if (biomesConfig.contains(path)) actualConfig = biomesConfig;
            else if (cavesConfig.contains(path)) actualConfig = cavesConfig;
            else if (mobsConfig.contains(path)) actualConfig = mobsConfig;
        }

        if (!actualConfig.contains(path)) {
            path = "categories." + category;
            actualConfig = mainConfig;
        }

        String title = actualConfig.getString(path + ".title", "&a¡Nuevo Descubrimiento!");
        String subtitle = actualConfig.getString(path + ".subtitle", "&f" + id);

        String actionBarMessage = ChatUtils.colorize(title.replace("%name%", id) + " &8» " + subtitle.replace("%name%", id));
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(actionBarMessage));

        String soundStr = actualConfig.getString(path + ".sound", "ENTITY_PLAYER_LEVELUP");
        try {
            player.playSound(player.getLocation(), Sound.valueOf(soundStr.toUpperCase()), 1.0f, 1.0f);
        } catch (Exception ignore) {}
    }

    public void claimReward(Player player, String category, String id) {
        UUID uuid = player.getUniqueId();

        if (!hasDiscovered(uuid, category, id)) return;

        if (hasClaimed(uuid, category, id)) {
            player.sendMessage(ChatUtils.colorize("&c&lⓘ &7Ya has reclamado la recompensa de este descubrimiento."));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        playerClaimedRewards.putIfAbsent(uuid, new HashMap<>());
        Map<String, List<String>> userCategories = playerClaimedRewards.get(uuid);
        userCategories.putIfAbsent(category, new ArrayList<>());
        userCategories.get(category).add(id);

        FileConfiguration targetConfig;
        switch (category) {
            case "MOB": targetConfig = mobsConfig; break;
            case "BIOME": targetConfig = biomesConfig; break;
            default: targetConfig = cavesConfig; break;
        }

        String path = "discoveries." + id;

        FileConfiguration actualConfig = targetConfig;
        if (!actualConfig.contains(path)) {
            if (biomesConfig.contains(path)) actualConfig = biomesConfig;
            else if (cavesConfig.contains(path)) actualConfig = cavesConfig;
            else if (mobsConfig.contains(path)) actualConfig = mobsConfig;
        }

        if (!actualConfig.contains(path)) {
            path = "categories." + category;
            actualConfig = mainConfig;
        }

        List<String> rewards = actualConfig.getStringList(path + ".rewards");
        for (String cmd : rewards) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%player%", player.getName()).replace("%name%", id));
        }

        player.sendMessage(ChatUtils.colorize("&a¡Has reclamado exitosamente tu recompensa!"));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);

        String titleHex;
        Material mat;
        switch(category) {
            case "BIOME": titleHex = "&#00FF00🌿 Tus Biomas"; mat = Material.GRASS_BLOCK; break;
            case "STRUCTURE": titleHex = "&#FFA500🏛 Tus Estructuras"; mat = Material.CHISELED_STONE_BRICKS; break;
            case "CAVE": titleHex = "&#00FFFF⛏ Tus Cuevas"; mat = Material.POINTED_DRIPSTONE; break;
            default: titleHex = "&#FF5555🧟 Tus Mobs"; mat = Material.ZOMBIE_HEAD; break;
        }
        openCategoryMenu(player, category, titleHex, mat);
    }

    public void openMainMenu(Player player) {
        String title = ChatUtils.colorize(menuConfig.getString("menu-settings.title", "Descubrimientos"));
        int rows = menuConfig.getInt("menu-settings.rows", 6) * 9;
        Inventory inv = Bukkit.createInventory(null, rows, title);
        UUID uuid = player.getUniqueId();

        Material fillMat = Material.valueOf(menuConfig.getString("menu-settings.fill-item.material", "BLACK_STAINED_GLASS_PANE"));
        ItemStack fillItem = createGuiItem(fillMat, " ", null, false, null);
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, fillItem);

        if (menuConfig.getConfigurationSection("decorations") != null) {
            for (String key : menuConfig.getConfigurationSection("decorations").getKeys(false)) {
                Material decMat = Material.valueOf(menuConfig.getString("decorations." + key + ".material", "GRAY_STAINED_GLASS_PANE"));
                ItemStack decItem = createGuiItem(decMat, " ", null, false, null);
                List<Integer> slots = menuConfig.getIntegerList("decorations." + key + ".slots");
                for (int slot : slots) {
                    if (slot >= 0 && slot < inv.getSize()) inv.setItem(slot, decItem);
                }
            }
        }

        if (menuConfig.getConfigurationSection("categories") != null) {
            for (String key : menuConfig.getConfigurationSection("categories").getKeys(false)) {
                int slot = menuConfig.getInt("categories." + key + ".slot");
                Material mat = Material.valueOf(menuConfig.getString("categories." + key + ".material"));
                String name = menuConfig.getString("categories." + key + ".name");
                List<String> loreTemplate = menuConfig.getStringList("categories." + key + ".lore");

                int totalMobs = mobsConfig.contains("discoveries") ? mobsConfig.getConfigurationSection("discoveries").getKeys(false).size() : 0;
                int totalBiomes = biomesConfig.contains("discoveries") ? biomesConfig.getConfigurationSection("discoveries").getKeys(false).size() : 0;
                int totalCavesStructs = cavesConfig.contains("discoveries") ? cavesConfig.getConfigurationSection("discoveries").getKeys(false).size() : 0;

                List<String> replacedLore = new ArrayList<>();
                for (String line : loreTemplate) {
                    line = line.replace("%discovered_biomes%", String.valueOf(getDiscoveredCount(uuid, "BIOME")))
                            .replace("%total_biomes%", String.valueOf(totalBiomes))
                            .replace("%discovered_structures%", String.valueOf(getDiscoveredCount(uuid, "STRUCTURE")))
                            .replace("%total_structures%", String.valueOf(totalCavesStructs))
                            .replace("%discovered_caves%", String.valueOf(getDiscoveredCount(uuid, "CAVE")))
                            .replace("%total_caves%", String.valueOf(totalCavesStructs))
                            .replace("%discovered_mobs%", String.valueOf(getDiscoveredCount(uuid, "MOB")))
                            .replace("%total_mobs%", String.valueOf(totalMobs));
                    replacedLore.add(line);
                }
                inv.setItem(slot, createGuiItem(mat, name, replacedLore, false, null));
            }
        }
        player.openInventory(inv);
    }

    public void openCategoryMenu(Player player, String category, String titleHex, Material iconMaterial) {
        Inventory inv = Bukkit.createInventory(null, 54, ChatUtils.colorize(titleHex));

        ItemStack borderItem = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, " ", null, false, null);
        ItemStack cornerItem = createGuiItem(Material.BLACK_STAINED_GLASS_PANE, " ", null, false, null);

        int[] borders = {0,1,2,3,4,5,6,7,8, 9,17, 18,26, 27,35, 36,44, 45,46,47,48,50,51,52,53};
        for (int b : borders) inv.setItem(b, borderItem);
        inv.setItem(0, cornerItem); inv.setItem(8, cornerItem);
        inv.setItem(45, cornerItem); inv.setItem(53, cornerItem);

        int[] interiorSlots = {
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34,
                37, 38, 39, 40, 41, 42, 43
        };

        FileConfiguration targetConfig;
        switch (category) {
            case "MOB": targetConfig = mobsConfig; break;
            case "BIOME": targetConfig = biomesConfig; break;
            default: targetConfig = cavesConfig; break;
        }

        List<String> discovered = playerDiscoveries.getOrDefault(player.getUniqueId(), new HashMap<>()).getOrDefault(category, new ArrayList<>());

        if (discovered.isEmpty()) {
            List<String> emptyLore = new ArrayList<>();
            emptyLore.add("&7Aún no has descubierto nada");
            emptyLore.add("&7en esta categoría.");
            inv.setItem(22, createGuiItem(Material.BARRIER, "&cVacío", emptyLore, false, null));
        } else {
            int slotIndex = 0;
            for (String id : discovered) {
                if (slotIndex >= interiorSlots.length) break;

                String path = "discoveries." + id;

                FileConfiguration actualConfig = targetConfig;
                if (!actualConfig.contains(path)) {
                    if (biomesConfig.contains(path)) actualConfig = biomesConfig;
                    else if (cavesConfig.contains(path)) actualConfig = cavesConfig;
                    else if (mobsConfig.contains(path)) actualConfig = mobsConfig;
                }

                String niceName = actualConfig.getString(path + ".title", "&a" + id);
                String subName = actualConfig.getString(path + ".subtitle", "&7Registrado en tu bitácora.");

                Material itemMat = iconMaterial;
                if (actualConfig.contains(path + ".icon")) {
                    try {
                        itemMat = Material.valueOf(actualConfig.getString(path + ".icon").toUpperCase());
                    } catch (Exception ignored) {}
                } else if (category.equals("MOB")) {
                    try {
                        itemMat = Material.valueOf(id.toUpperCase() + "_SPAWN_EGG");
                    } catch (Exception ignored) {}
                }

                boolean isClaimed = hasClaimed(player.getUniqueId(), category, id);

                List<String> lore = new ArrayList<>();
                lore.add(subName);
                lore.add("");

                if (isClaimed) {
                    lore.add("&a✔ ¡Recompensa reclamada!");
                } else {
                    lore.add("&e▶ ¡Click para reclamar recompensa!");
                }

                inv.setItem(interiorSlots[slotIndex], createGuiItem(itemMat, niceName, lore, isClaimed, id));
                slotIndex++;
            }
        }

        List<String> backLore = new ArrayList<>();
        backLore.add("&7Click para regresar.");
        inv.setItem(49, createGuiItem(Material.ARROW, "&c⬅ Volver al Menú Principal", backLore, false, null));

        player.openInventory(inv);
    }

    private int getDiscoveredCount(UUID uuid, String category) {
        if (!playerDiscoveries.containsKey(uuid)) return 0;
        return playerDiscoveries.get(uuid).getOrDefault(category, new ArrayList<>()).size();
    }

    private ItemStack createGuiItem(Material material, String name, List<String> lore, boolean glow, String hiddenId) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatUtils.colorize(name));

            if (lore != null) {
                meta.setLore(lore.stream().map(ChatUtils::colorize).collect(Collectors.toList()));
            }

            if (glow) {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }

            if (hiddenId != null) {
                NamespacedKey key = new NamespacedKey(plugin, "discovery_id");
                meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, hiddenId);
            }

            item.setItemMeta(meta);
        }
        return item;
    }

    public void shutdown() { saveDataSync(); }
}