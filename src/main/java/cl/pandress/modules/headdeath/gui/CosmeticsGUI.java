package cl.pandress.modules.headdeath.gui;

import cl.pandress.modules.headdeath.HeadDeathManager;
import cl.pandress.modules.headdeath.data.PlayerCosmetics;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CosmeticsGUI {

    private final HeadDeathManager manager;

    private final Map<Integer, CosmeticItem> graveItems = new HashMap<>();
    private final Map<Integer, CosmeticItem> effectItems = new HashMap<>();

    public enum FilterType {
        UNLOCKED, COINS, VIP
    }

    public CosmeticsGUI(HeadDeathManager manager) {
        this.manager = manager;
        loadCosmeticsFromConfig();
    }

    public void loadCosmeticsFromConfig() {
        graveItems.clear();
        effectItems.clear();
        FileConfiguration config = manager.getCosmeticsConfig();

        ConfigurationSection gravesSection = config.getConfigurationSection("graves");
        if (gravesSection != null) {
            for (String key : gravesSection.getKeys(false)) {
                String path = "graves." + key + ".";
                Material mat = Material.matchMaterial(config.getString(path + "material", "BARRIER"));
                if (mat == null) mat = Material.BARRIER;
                String name = ChatColor.translateAlternateColorCodes('&', config.getString(path + "name", key));
                int price = config.getInt(path + "price", 0);
                boolean requiresCoins = config.getBoolean(path + "requires-coins", price > 0);
                String permission = config.getString(path + "permission", "");
                int slot = config.getInt(path + "slot", 0);
                
                graveItems.put(slot, new CosmeticItem(key, mat, name, price, requiresCoins, permission, slot));
            }
        }

        ConfigurationSection effectsSection = config.getConfigurationSection("effects");
        if (effectsSection != null) {
            for (String key : effectsSection.getKeys(false)) {
                String path = "effects." + key + ".";
                Material mat = Material.matchMaterial(config.getString(path + "material", "BARRIER"));
                if (mat == null) mat = Material.BARRIER;
                String name = ChatColor.translateAlternateColorCodes('&', config.getString(path + "name", key));
                int price = config.getInt(path + "price", 0);
                boolean requiresCoins = config.getBoolean(path + "requires-coins", price > 0);
                String permission = config.getString(path + "permission", "");
                int slot = config.getInt(path + "slot", 0);

                effectItems.put(slot, new CosmeticItem(key, mat, name, price, requiresCoins, permission, slot));
            }
        }
    }

    public String getTitle(String menuPath) {
        return ChatColor.translateAlternateColorCodes('&', manager.getCosmeticsConfig().getString("menus." + menuPath, "Menu"));
    }

    private int getSize(String menuPath) {
        return manager.getCosmeticsConfig().getInt("menus." + menuPath + ".size", 27);
    }

    public int getSlot(String path) {
        return manager.getCosmeticsConfig().getInt("menus." + path + ".slot", -1);
    }

    /* ===================== MENÚ PRINCIPAL ===================== */
    public void openMainMenu(Player p) {
        Inventory inv = Bukkit.createInventory(null, getSize("main"), getTitle("main.title"));
        
        inv.setItem(getSlot("main.items.balance"), getConfigItem("menus.main.items.balance", p));
        inv.setItem(getSlot("main.items.graves_category"), getConfigItem("menus.main.items.graves_category", p));
        inv.setItem(getSlot("main.items.effects_category"), getConfigItem("menus.main.items.effects_category", p));

        fillBorders(inv);
        p.openInventory(inv);
    }

    /* ===================== MENÚS DE CATEGORÍAS ===================== */
    public void openCategoryMenu(Player p, boolean isGrave) {
        String type = isGrave ? "graves_category" : "effects_category";
        Inventory inv = Bukkit.createInventory(null, getSize(type), getTitle(type + ".title"));

        inv.setItem(getSlot(type + ".items.unlocked"), getConfigItem("menus." + type + ".items.unlocked", p));
        inv.setItem(getSlot(type + ".items.coins"), getConfigItem("menus." + type + ".items.coins", p));
        inv.setItem(getSlot(type + ".items.vip"), getConfigItem("menus." + type + ".items.vip", p));
        inv.setItem(getSlot(type + ".items.back"), getConfigItem("menus." + type + ".items.back", p));

        fillBorders(inv);
        p.openInventory(inv);
    }

    /* ===================== MENÚS DE LISTAS (FILTRADAS) ===================== */
    public void openListMenu(Player p, boolean isGrave, FilterType filter) {
        String titlePath = "lists." + (isGrave ? "graves" : "effects") + "_" + filter.name().toLowerCase() + "_title";
        Inventory inv = Bukkit.createInventory(null, getSize("lists"), getTitle(titlePath));
        PlayerCosmetics cosmetics = manager.getCosmetics(p.getUniqueId());

        Map<Integer, CosmeticItem> itemsToIterate = isGrave ? graveItems : effectItems;

        for (CosmeticItem item : itemsToIterate.values()) {
            boolean hasPerm = item.permission == null || item.permission.isEmpty() || p.hasPermission(item.permission);
            boolean isUnlockedInDB = isGrave ? cosmetics.hasGraveUnlocked(item.id) : cosmetics.hasEffectUnlocked(item.id);
            boolean unlocked = isUnlockedInDB || (!item.requiresCoins && item.price == 0 && hasPerm);

            boolean shouldShow = false;

            switch (filter) {
                case UNLOCKED:
                    // Muestra todo lo que ya posees
                    if (unlocked) shouldShow = true;
                    break;
                case COINS:
                    // Se muestra en Tienda solo si cuesta coins Y aún NO lo tienes
                    if (item.requiresCoins && !unlocked) shouldShow = true;
                    break;
                case VIP:
                    // Se muestra en VIP solo si es exclusivo VIP Y aún NO lo tienes (ej. un usuario sin VIP viéndolo)
                    if (!item.requiresCoins && item.permission != null && !item.permission.isEmpty() && !unlocked) shouldShow = true;
                    break;
            }

            if (shouldShow) {
                boolean selected = isGrave ? cosmetics.getSelectedGrave().equals(item.id) : cosmetics.getSelectedEffect().equals(item.id);
                inv.setItem(item.slot, formatCosmeticItem(p, item, unlocked, selected));
            }
        }

        inv.setItem(getSlot("lists.items.back"), getConfigItem("menus.lists.items.back", p));
        fillBorders(inv);
        p.openInventory(inv);
    }

    /* ===================== UTILIDADES INTERNAS ===================== */
    private ItemStack getConfigItem(String path, Player p) {
        FileConfiguration config = manager.getCosmeticsConfig();
        Material mat = Material.matchMaterial(config.getString(path + ".material", "STONE"));
        if (mat == null) mat = Material.STONE;
        String name = ChatColor.translateAlternateColorCodes('&', config.getString(path + ".name", ""));
        
        List<String> lore = new ArrayList<>();
        PlayerCosmetics cosmetics = manager.getCosmetics(p.getUniqueId());
        
        for (String line : config.getStringList(path + ".lore")) {
            lore.add(ChatColor.translateAlternateColorCodes('&', line.replace("{coins}", String.valueOf(cosmetics.getCoins()))));
        }

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack formatCosmeticItem(Player p, CosmeticItem itemInfo, boolean unlocked, boolean selected) {
        ItemStack item = new ItemStack(itemInfo.icon);
        
        if (itemInfo.icon == Material.PLAYER_HEAD) {
            ItemMeta skullMetaTemp = item.getItemMeta();
            if (skullMetaTemp instanceof SkullMeta skullMeta) {
                skullMeta.setOwningPlayer(p);
                item.setItemMeta(skullMeta);
            }
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(itemInfo.displayName);
            List<String> lore = new ArrayList<>();
            boolean hasPerm = itemInfo.permission == null || itemInfo.permission.isEmpty() || p.hasPermission(itemInfo.permission);

            String formatPath = "";
            if (!hasPerm) {
                formatPath = "cosmetic-format.locked-permission";
            } else if (selected) {
                formatPath = "cosmetic-format.selected";
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            } else if (unlocked) {
                formatPath = "cosmetic-format.unlocked";
            } else {
                formatPath = "cosmetic-format.locked-coins";
            }

            for (String line : manager.getCosmeticsConfig().getStringList(formatPath)) {
                lore.add(ChatColor.translateAlternateColorCodes('&', line.replace("{price}", String.valueOf(itemInfo.price))));
            }
            
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void fillBorders(Inventory inv) {
        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            filler.setItemMeta(meta);
        }
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null || inv.getItem(i).getType() == Material.AIR) {
                inv.setItem(i, filler);
            }
        }
    }

    public Map<Integer, CosmeticItem> getGraveItems() { return graveItems; }
    public Map<Integer, CosmeticItem> getEffectItems() { return effectItems; }

    public static class CosmeticItem {
        public final String id;
        public final Material icon;
        public final String displayName;
        public final int price;
        public final boolean requiresCoins;
        public final String permission;
        public final int slot;

        public CosmeticItem(String id, Material icon, String displayName, int price, boolean requiresCoins, String permission, int slot) {
            this.id = id;
            this.icon = icon;
            this.displayName = displayName;
            this.price = price;
            this.requiresCoins = requiresCoins;
            this.permission = permission;
            this.slot = slot;
        }
    }
}