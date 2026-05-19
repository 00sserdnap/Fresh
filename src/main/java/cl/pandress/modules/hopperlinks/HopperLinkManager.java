package cl.pandress.modules.hopperlinks;

import cl.pandress.Etherium;
import cl.pandress.utils.ChatUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.Hopper;
import org.bukkit.block.Container;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class HopperLinkManager {

    private final Etherium plugin;
    private FileConfiguration config;
    private FileConfiguration messages;
    private FileConfiguration menus;
    private File dataFile;
    private FileConfiguration data;

    private boolean enabled;

    private final Map<String, HopperLink> activeLinks = new HashMap<>();
    public final Map<UUID, List<Location>> playerSelections = new HashMap<>();
    private final Map<UUID, Integer> playerWandLevels = new HashMap<>();
    
    private final Map<Location, UUID> placedHoppersMap = new HashMap<>();

    public HopperLinkManager(Etherium plugin) {
        this.plugin = plugin;
        loadConfigs();
        
        this.enabled = config.getBoolean("settings.enabled", true);
        if (!this.enabled) {
            return; // Si está desactivado, no cargamos datos ni iniciamos la tarea de transferencia.
        }

        loadData();
        startTransferTask();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void loadConfigs() {
        this.config = loadCustomConfig("modules/hopperlinks/config.yml");
        this.messages = loadCustomConfig("modules/hopperlinks/messages.yml");
        this.menus = loadCustomConfig("modules/hopperlinks/menus.yml");
    }

    private FileConfiguration loadCustomConfig(String path) {
        File file = new File(plugin.getDataFolder(), path);
        if (!file.exists()) {
            plugin.saveResource(path, false);
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    public void loadData() {
        activeLinks.clear();
        playerWandLevels.clear();
        placedHoppersMap.clear();
        
        dataFile = new File(plugin.getDataFolder(), "modules/hopperlinks/data.yml");
        if (!dataFile.exists()) {
            try {
                dataFile.getParentFile().mkdirs();
                dataFile.createNewFile();
            } catch (IOException ignored) {}
        }
        data = YamlConfiguration.loadConfiguration(dataFile);

        if (data.contains("links")) {
            for (String linkId : data.getConfigurationSection("links").getKeys(false)) {
                String path = "links." + linkId;
                UUID owner = UUID.fromString(data.getString(path + ".owner"));
                Location chest = stringToLoc(data.getString(path + ".chest"));
                List<Location> hoppers = new ArrayList<>();
                for (String locStr : data.getStringList(path + ".hoppers")) {
                    hoppers.add(stringToLoc(locStr));
                }
                activeLinks.put(linkId, new HopperLink(linkId, owner, chest, hoppers));
            }
        }
        
        if (data.contains("wand-levels")) {
            for (String uuidStr : data.getConfigurationSection("wand-levels").getKeys(false)) {
                playerWandLevels.put(UUID.fromString(uuidStr), data.getInt("wand-levels." + uuidStr));
            }
        }

        if (data.contains("placed-hoppers")) {
            for (String locStr : data.getConfigurationSection("placed-hoppers").getKeys(false)) {
                UUID owner = UUID.fromString(data.getString("placed-hoppers." + locStr));
                placedHoppersMap.put(stringToLoc(locStr.replace("|", ";")), owner);
            }
        }
    }

    public void saveData() {
        if (!enabled) return; // Si está desactivado, evitamos sobrescribir datos vacíos.

        data.set("links", null); 
        data.set("wand-levels", null);
        data.set("placed-hoppers", null);
        
        for (HopperLink link : activeLinks.values()) {
            String path = "links." + link.getId();
            data.set(path + ".owner", link.getOwner().toString());
            data.set(path + ".chest", locToString(link.getChestLocation()));
            List<String> hopStrs = new ArrayList<>();
            for (Location loc : link.getHoppers()) hopStrs.add(locToString(loc));
            data.set(path + ".hoppers", hopStrs);
        }
        
        for (Map.Entry<UUID, Integer> entry : playerWandLevels.entrySet()) {
            data.set("wand-levels." + entry.getKey().toString(), entry.getValue());
        }

        for (Map.Entry<Location, UUID> entry : placedHoppersMap.entrySet()) {
            data.set("placed-hoppers." + locToString(entry.getKey()).replace(";", "|"), entry.getValue().toString());
        }
        
        try {
            data.save(dataFile);
        } catch (IOException ignored) {}
    }

    // ======================================================
    // VERIFICACIÓN DE MUNDOS PERMITIDOS
    // ======================================================
    public boolean isWorldAllowed(String worldName) {
        List<String> allowedWorlds = config.getStringList("settings.allowed-worlds");
        return allowedWorlds.contains(worldName);
    }

    // ======================================================
    // MOTOR DE TRANSFERENCIA CON FLUJO VERTICAL INFINITO
    // ======================================================
    private void startTransferTask() {
        long interval = config.getLong("settings.transfer-interval-ticks", 20L);
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (HopperLink link : activeLinks.values()) {
                Location chestLoc = link.getChestLocation();
                
                // 1. Verificaciones: Carga de Chunk y MUNDO PERMITIDO
                if (!isWorldAllowed(chestLoc.getWorld().getName())) continue;
                if (!chestLoc.getWorld().isChunkLoaded(chestLoc.getBlockX() >> 4, chestLoc.getBlockZ() >> 4)) continue;
                
                Block chestBlock = chestLoc.getBlock();
                if (!(chestBlock.getState() instanceof Container linkedChest)) continue; 
                
                Inventory chestInv = linkedChest.getInventory();
                int pLevel = getPlayerWandLevel(link.getOwner());
                int itemsToMove = config.getInt("upgrades.level_" + pLevel + ".items-per-transfer", 1);

                // 2. Transferencia: TOLVAS -> COFRE VINCULADO
                for (Location hopLoc : link.getHoppers()) {
                    if (!hopLoc.getWorld().isChunkLoaded(hopLoc.getBlockX() >> 4, hopLoc.getBlockZ() >> 4)) continue;
                    Block hopBlock = hopLoc.getBlock();
                    if (!(hopBlock.getState() instanceof Hopper hopper)) continue;

                    Inventory hopInv = hopper.getInventory();

                    for (int i = 0; i < hopInv.getSize(); i++) {
                        ItemStack item = hopInv.getItem(i);
                        if (item != null && item.getType() != Material.AIR) {
                            int amountToTake = Math.min(item.getAmount(), itemsToMove);
                            ItemStack toMove = item.clone();
                            toMove.setAmount(amountToTake);

                            HashMap<Integer, ItemStack> leftover = chestInv.addItem(toMove);
                            if (leftover.isEmpty()) {
                                item.setAmount(item.getAmount() - amountToTake);
                            } else {
                                int moved = amountToTake - leftover.get(0).getAmount();
                                item.setAmount(item.getAmount() - moved);
                            }
                            break; 
                        }
                    }
                }

                // 3. FLUJO VERTICAL CASCADA (Hacia abajo por toda la columna)
                List<Container> verticalColumn = new ArrayList<>();
                Location scanLoc = chestLoc.clone();
                
                while (scanLoc.getBlock().getState() instanceof Container container) {
                    verticalColumn.add(container);
                    scanLoc.add(0, -1, 0); 
                    if (scanLoc.getY() < scanLoc.getWorld().getMinHeight()) break; 
                }
                
                if (verticalColumn.size() > 1) {
                    for (int c = verticalColumn.size() - 2; c >= 0; c--) {
                        Inventory invUpper = verticalColumn.get(c).getInventory();
                        Inventory invLower = verticalColumn.get(c + 1).getInventory();
                        
                        for (int i = 0; i < invUpper.getSize(); i++) {
                            ItemStack itemUpper = invUpper.getItem(i);
                            if (itemUpper != null && itemUpper.getType() != Material.AIR) {
                                int amount = Math.min(itemUpper.getAmount(), itemsToMove);
                                ItemStack toMoveDown = itemUpper.clone();
                                toMoveDown.setAmount(amount);
                                
                                HashMap<Integer, ItemStack> leftover = invLower.addItem(toMoveDown);
                                if (leftover.isEmpty()) {
                                    itemUpper.setAmount(itemUpper.getAmount() - amount);
                                } else {
                                    int moved = amount - leftover.get(0).getAmount();
                                    itemUpper.setAmount(itemUpper.getAmount() - moved);
                                }
                                break; 
                            }
                        }
                    }
                }
            }
        }, interval, interval);
    }

    public void spawnLaserParticles(Player player, HopperLink link) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Location end = link.getChestLocation().clone().add(0.5, 0.5, 0.5);
            Particle.DustOptions dustOptions = new Particle.DustOptions(org.bukkit.Color.RED, 1.0F);

            for (int i = 0; i < 20; i++) { 
                for (Location hopper : link.getHoppers()) {
                    Location start = hopper.clone().add(0.5, 0.5, 0.5);
                    double distance = start.distance(end);
                    Vector p1 = start.toVector();
                    Vector p2 = end.toVector();
                    Vector vector = p2.clone().subtract(p1).normalize().multiply(0.5);
                    
                    double length = 0;
                    while (length < distance) {
                        player.spawnParticle(Particle.DUST, p1.getX(), p1.getY(), p1.getZ(), 1, 0, 0, 0, 0, dustOptions);
                        p1.add(vector);
                        length += 0.5;
                    }
                }
                try { Thread.sleep(500); } catch (InterruptedException ignored) {}
            }
        });
    }

    public ItemStack getWandItem() {
        Material mat = Material.valueOf(config.getString("wand.material", "BLAZE_ROD"));
        ItemStack wand = new ItemStack(mat);
        ItemMeta meta = wand.getItemMeta();
        meta.setDisplayName(ChatUtils.colorize(config.getString("wand.name")));
        List<String> lore = config.getStringList("wand.lore");
        meta.setLore(lore.stream().map(ChatUtils::colorize).toList());
        
        if (config.getBoolean("wand.glow", true)) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        
        NamespacedKey key = new NamespacedKey(plugin, "is_hopper_wand");
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        wand.setItemMeta(meta);
        return wand;
    }

    public boolean isWand(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        NamespacedKey key = new NamespacedKey(plugin, "is_hopper_wand");
        return item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    public int getPlayerLinkLimit(Player player) {
        int max = config.getInt("settings.default-hopper-link-limit", 10);
        for (org.bukkit.permissions.PermissionAttachmentInfo pio : player.getEffectivePermissions()) {
            if (pio.getPermission().startsWith("eth.hopperlink.limit.")) {
                try {
                    int val = Integer.parseInt(pio.getPermission().replace("eth.hopperlink.limit.", ""));
                    if (val > max) max = val;
                } catch (Exception ignore) {}
            }
        }
        return max;
    }

    public int getPlayerPlaceLimit(Player player) {
        int max = config.getInt("settings.default-hopper-place-limit", 30);
        for (org.bukkit.permissions.PermissionAttachmentInfo pio : player.getEffectivePermissions()) {
            if (pio.getPermission().startsWith("eth.hopper.place.")) {
                try {
                    int val = Integer.parseInt(pio.getPermission().replace("eth.hopper.place.", ""));
                    if (val > max) max = val;
                } catch (Exception ignore) {}
            }
        }
        return max;
    }

    public int getPlayerMaxLinksLimit(Player player) {
        int max = config.getInt("settings.default-active-links-limit", 2);
        for (org.bukkit.permissions.PermissionAttachmentInfo pio : player.getEffectivePermissions()) {
            if (pio.getPermission().startsWith("eth.hopperlink.maxlinks.")) {
                try {
                    int val = Integer.parseInt(pio.getPermission().replace("eth.hopperlink.maxlinks.", ""));
                    if (val > max) max = val;
                } catch (Exception ignore) {}
            }
        }
        return max;
    }

    public int getUsedHoppersCount(UUID uuid) {
        int count = 0;
        for (HopperLink link : activeLinks.values()) {
            if (link.getOwner().equals(uuid)) count += link.getHoppers().size();
        }
        return count;
    }

    public int getPlacedHoppersCount(UUID uuid) {
        int count = 0;
        for (UUID owner : placedHoppersMap.values()) {
            if (owner.equals(uuid)) count++;
        }
        return count;
    }

    public void addPlacedHopper(Location loc, UUID uuid) {
        placedHoppersMap.put(loc, uuid);
        saveData();
    }

    public void removePlacedHopper(Location loc) {
        if (placedHoppersMap.remove(loc) != null) {
            saveData();
        }
    }

    public void handleChestBreak(Location chestLoc) {
        List<String> toDelete = new ArrayList<>();
        for (HopperLink link : activeLinks.values()) {
            if (link.getChestLocation().equals(chestLoc)) {
                toDelete.add(link.getId());
                Player owner = Bukkit.getPlayer(link.getOwner());
                if (owner != null) {
                    owner.sendMessage(ChatUtils.colorize(messages.getString("prefix") + messages.getString("protection.chest-broken")));
                }
            }
        }
        if (!toDelete.isEmpty()) {
            toDelete.forEach(activeLinks::remove);
            saveData();
        }
    }

    public void handleHopperBreak(Location hopperLoc) {
        removePlacedHopper(hopperLoc); 
        
        for (HopperLink link : activeLinks.values()) {
            if (link.getHoppers().contains(hopperLoc)) {
                link.getHoppers().remove(hopperLoc);
                Player owner = Bukkit.getPlayer(link.getOwner());
                if (owner != null) {
                    owner.sendMessage(ChatUtils.colorize(messages.getString("prefix") + messages.getString("protection.hopper-broken")));
                }
                saveData();
                return;
            }
        }
    }

    public boolean isHopperLinked(Location loc) {
        for (HopperLink link : activeLinks.values()) {
            if (link.getHoppers().contains(loc)) return true;
        }
        return false;
    }

    public void createLink(Player player, Location chestLoc) {
        UUID uuid = player.getUniqueId();
        List<Location> selected = playerSelections.getOrDefault(uuid, new ArrayList<>());
        
        String id = UUID.randomUUID().toString().substring(0, 8);
        HopperLink newLink = new HopperLink(id, uuid, chestLoc, new ArrayList<>(selected));
        activeLinks.put(id, newLink);
        
        playerSelections.remove(uuid);
        saveData();
    }

    public void removeLink(String id) {
        activeLinks.remove(id);
        saveData();
    }

    public HopperLink getLink(String id) { return activeLinks.get(id); }
    public Map<String, HopperLink> getActiveLinks() { return activeLinks; }
    
    public int getActiveLinksCount(UUID uuid) {
        int count = 0;
        for (HopperLink link : activeLinks.values()) {
            if (link.getOwner().equals(uuid)) count++;
        }
        return count;
    }

    public int getPlayerWandLevel(UUID uuid) { return playerWandLevels.getOrDefault(uuid, 1); }
    public void setPlayerWandLevel(UUID uuid, int level) { playerWandLevels.put(uuid, level); }
    
    public FileConfiguration getConfig() { return config; }
    public FileConfiguration getMessages() { return messages; }
    public FileConfiguration getMenus() { return menus; }

    private String locToString(Location loc) {
        return loc.getWorld().getName() + ";" + loc.getBlockX() + ";" + loc.getBlockY() + ";" + loc.getBlockZ();
    }

    private Location stringToLoc(String str) {
        String[] parts = str.split(";");
        World w = Bukkit.getWorld(parts[0]);
        return new Location(w, Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
    }
}