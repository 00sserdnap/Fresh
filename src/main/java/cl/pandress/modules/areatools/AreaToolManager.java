package cl.pandress.modules.areatools;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class AreaToolManager {

    public static final String NBT_KEY = "area_tool_id";
    public static final String NBT_BLOCKS_KEY = "area_tool_blocks";
    public static final String NBT_CHARGE_KEY = "area_tool_charge";
    public static final String NBT_BATTERY_KEY = "area_tool_battery";
    public static final String NBT_TEMP_KEY = "area_tool_temp";

    private final NamespacedKey key;
    private final NamespacedKey blocksKey;
    private final NamespacedKey chargeKey;
    private final NamespacedKey batteryKey;
    private final NamespacedKey tempKey;

    private FileConfiguration config;
    private final JavaPlugin plugin;

    public AreaToolManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, NBT_KEY);
        this.blocksKey = new NamespacedKey(plugin, NBT_BLOCKS_KEY);
        this.chargeKey = new NamespacedKey(plugin, NBT_CHARGE_KEY);
        this.batteryKey = new NamespacedKey(plugin, NBT_BATTERY_KEY);
        this.tempKey = new NamespacedKey(plugin, NBT_TEMP_KEY);

        File configFile = new File(plugin.getDataFolder(), "modules/areatools/config.yml");
        if (!configFile.exists()) {
            configFile.getParentFile().mkdirs();
            plugin.saveResource("modules/areatools/config.yml", false);
        }

        this.config = YamlConfiguration.loadConfiguration(configFile);
    }

    public NamespacedKey getBlocksKey() { return blocksKey; }
    public NamespacedKey getChargeKey() { return chargeKey; }
    public NamespacedKey getTempKey() { return tempKey; }
    public FileConfiguration getConfig() { return config; }

    public String getToolId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    public boolean isBattery(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(batteryKey, PersistentDataType.STRING);
    }

    public int getCharge(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        return pdc.getOrDefault(chargeKey, PersistentDataType.INTEGER, config.getInt("settings.max-charge", 5000));
    }

    public boolean isTemporary(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        Byte isTemp = item.getItemMeta().getPersistentDataContainer().get(tempKey, PersistentDataType.BYTE);
        return isTemp != null && isTemp == (byte) 1;
    }

    public void setCharge(ItemStack item, int amount, int blocksMined) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(chargeKey, PersistentDataType.INTEGER, amount);
            meta.getPersistentDataContainer().set(blocksKey, PersistentDataType.INTEGER, blocksMined);

            String toolId = getToolId(item);
            if (toolId != null) {
                boolean isTemp = isTemporary(item);
                meta.setLore(getToolLore(toolId, blocksMined, amount, isTemp));
            }
            item.setItemMeta(meta);
        }
    }

    public ItemStack createBattery(String batteryId) {
        String path = "batteries." + batteryId;
        if (!config.contains(path)) return null;

        Material mat = Material.matchMaterial(config.getString(path + ".material", "REDSTONE_BLOCK"));
        ItemStack item = new ItemStack(mat != null ? mat : Material.REDSTONE_BLOCK);
        ItemMeta meta = item.getItemMeta();

        meta.getPersistentDataContainer().set(batteryKey, PersistentDataType.STRING, batteryId);
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', config.getString(path + ".name", "&c&lBatería")));

        List<String> lore = new ArrayList<>();
        for (String line : config.getStringList(path + ".lore")) {
            lore.add(ChatColor.translateAlternateColorCodes('&', line));
        }
        meta.setLore(lore);

        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);

        return item;
    }

    public int getBatteryRechargeAmount(ItemStack item) {
        if (!isBattery(item)) return 0;
        String batteryId = item.getItemMeta().getPersistentDataContainer().get(batteryKey, PersistentDataType.STRING);
        return config.getInt("batteries." + batteryId + ".recharge-amount", 0);
    }

    public ItemStack createTool(String toolId, boolean isTemporary) {
        String path = "tools." + toolId;
        if (!config.contains(path)) return null;

        Material mat = Material.matchMaterial(config.getString(path + ".material", "NETHERITE_PICKAXE"));
        ItemStack item = new ItemStack(mat != null ? mat : Material.NETHERITE_PICKAXE);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        int maxCharge = config.getInt("settings.max-charge", 5000);

        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, toolId);
        meta.getPersistentDataContainer().set(blocksKey, PersistentDataType.INTEGER, 0);
        meta.getPersistentDataContainer().set(chargeKey, PersistentDataType.INTEGER, maxCharge);
        meta.getPersistentDataContainer().set(tempKey, PersistentDataType.BYTE, isTemporary ? (byte) 1 : (byte) 0);

        int size = config.getInt(path + ".size", 3);
        String rawName = config.getString(path + ".name", "&cHerramienta");
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', rawName.replace("{size}", String.valueOf(size))));

        meta.setLore(getToolLore(toolId, 0, maxCharge, isTemporary));
        meta.addEnchant(Enchantment.EFFICIENCY, 5, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);

        item.setItemMeta(meta);
        return item;
    }

    public List<String> getToolLore(String toolId, int blocksMined, int charge, boolean isTemporary) {
        String path = "tools." + toolId;
        List<String> rawLore = config.getStringList(path + ".lore");
        List<String> formattedLore = new ArrayList<>();
        int maxCharge = config.getInt("settings.max-charge", 5000);
        int size = config.getInt(path + ".size", 3);
        String status = isTemporary ? "&cTemporal (Se destruye)" : "&aPermanente (Recargable)";

        for (String line : rawLore) {
            formattedLore.add(ChatColor.translateAlternateColorCodes('&', line
                    .replace("{size}", String.valueOf(size))
                    .replace("{blocks_mined}", String.valueOf(blocksMined))
                    .replace("{charge}", String.valueOf(charge))
                    .replace("{max_charge}", String.valueOf(maxCharge))
                    .replace("{status}", status)));
        }
        return formattedLore;
    }

    public boolean isPickaxe(String toolId) {
        return "PICKAXE".equalsIgnoreCase(config.getString("tools." + toolId + ".type", "PICKAXE"));
    }

    public int getToolSize(String toolId) {
        return config.getInt("tools." + toolId + ".size", 3);
    }
    
    public Set<String> getAvailableTools() {
        if (config.getConfigurationSection("tools") == null) return Set.of();
        return config.getConfigurationSection("tools").getKeys(false);
    }

    public Set<String> getAvailableBatteries() {
        if (config.getConfigurationSection("batteries") == null) return Set.of();
        return config.getConfigurationSection("batteries").getKeys(false);
    }
}