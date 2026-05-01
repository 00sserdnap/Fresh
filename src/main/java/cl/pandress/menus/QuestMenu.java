package cl.pandress.menus;

import cl.pandress.Fresh;
import cl.pandress.modules.quests.QuestManager;
import cl.pandress.utils.ChatUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class QuestMenu {

    public static void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, InventoryType.HOPPER, ChatUtils.colorize("&8Misiones Diarias"));
        QuestManager manager = Fresh.getInstance().getManagerHandler().getQuestManager();
        int currentLevel = manager.getPlayerDailyLevel(player.getUniqueId());

        inv.setItem(1, getQuestItem(currentLevel, player, manager));

        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glass.getItemMeta();
        if (glassMeta != null) {
            glassMeta.setDisplayName(" ");
            glass.setItemMeta(glassMeta);
        }
        inv.setItem(3, glass);

        ItemStack bed = new ItemStack(Material.RED_BED);
        ItemMeta bedMeta = bed.getItemMeta();
        if (bedMeta != null) {
            bedMeta.setDisplayName(ChatUtils.colorize("&c&lSalir"));
            bed.setItemMeta(bedMeta);
        }
        inv.setItem(4, bed);

        // --- EFECTO DE SONIDO AL ABRIR ---
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.8f, 1.2f);
        player.openInventory(inv);
    }

    private static ItemStack getQuestItem(int level, Player player, QuestManager manager) {
        FileConfiguration config = manager.getConfig();
        ItemStack item;
        ItemMeta meta;

        if (level > 10) {
            item = new ItemStack(Material.EMERALD);
            meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatUtils.colorize(config.getString("messages.completed-all", "&a&l¡Completadas!")));
                List<String> lore = new ArrayList<>();
                for (String line : config.getStringList("messages.completed-lore")) { lore.add(ChatUtils.colorize(line)); }
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
            return item;
        }

        String questKey = manager.getActiveQuestKey(level);
        if (questKey == null) return new ItemStack(Material.PAPER);

        String path = "quest-pool." + questKey;
        String matStr = config.getString(path + ".material", "PAPER");
        item = new ItemStack(Material.matchMaterial(matStr) != null ? Material.valueOf(matStr) : Material.PAPER);
        
        meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatUtils.colorize("&8[&e" + level + "&8/&e10&8] " + config.getString(path + ".name")));
            List<String> lore = new ArrayList<>();
            for (String line : config.getStringList(path + ".lore")) { lore.add(ChatUtils.colorize(line)); }
            
            int progress = manager.getProgress(player.getUniqueId());
            int required = config.getInt(path + ".action-amount");
            
            lore.add("");
            lore.add(ChatUtils.colorize("&7Progreso: &e" + progress + "&8/&e" + required));
            
            if (progress >= required) {
                lore.add(ChatUtils.colorize("&a¡Click para reclamar!"));
            } else {
                lore.add(ChatUtils.colorize("&cMisión en progreso..."));
            }
            
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }
}