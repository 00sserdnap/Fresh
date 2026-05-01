package cl.pandress.menus;

import cl.pandress.Fresh;
import cl.pandress.modules.quests.QuestManager;
import cl.pandress.utils.ChatUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class QuestMenu {

    public static void open(Player player) {
        // Inventario de 3 filas (27 slots)
        Inventory inv = Bukkit.createInventory(null, 27, ChatUtils.colorize("&8Misiones Diarias"));
        QuestManager manager = Fresh.getInstance().getManagerHandler().getQuestManager();
        int currentLevel = manager.getPlayerDailyLevel(player.getUniqueId());

        // Slot 11: Misión Actual (Lado Izquierdo)
        inv.setItem(11, getQuestItem(currentLevel, player, manager));

        // Slot 15: Top / Global (Lado Derecho)
        inv.setItem(15, getStatsItem(player, manager));

        // Rellenar el resto con paneles de cristal gris (la cama fue removida)
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glass.getItemMeta();
        if (glassMeta != null) {
            glassMeta.setDisplayName(" ");
            glass.setItemMeta(glassMeta);
        }
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, glass);
            }
        }

        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.8f, 1.2f);
        player.openInventory(inv);
    }

    private static ItemStack getStatsItem(Player player, QuestManager manager) {
        ItemStack item = new ItemStack(Material.BELL);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatUtils.colorize("&e&lTop Misiones & Estadísticas"));
            List<String> lore = new ArrayList<>();
            lore.add(ChatUtils.colorize("&7Total global de misiones completadas."));
            lore.add("");
            lore.add(ChatUtils.colorize("&6&lTOP 10 GLOBAL:"));
            
            // Obtener el Top 10 y agregarlo al Lore
            List<Map.Entry<UUID, Integer>> top10 = manager.getTop10GlobalMissions();
            if (top10.isEmpty()) {
                lore.add(ChatUtils.colorize("&cNo hay misiones completadas aún."));
            } else {
                int rank = 1;
                for (Map.Entry<UUID, Integer> entry : top10) {
                    String playerName = Bukkit.getOfflinePlayer(entry.getKey()).getName();
                    if (playerName == null) playerName = "Desconocido"; // Por si el jugador no existe
                    
                    lore.add(ChatUtils.colorize("&e" + rank + ". &f" + playerName + " &8- &a" + entry.getValue()));
                    rank++;
                }
            }
            
            lore.add("");
            lore.add(ChatUtils.colorize("&e&lTUS ESTADÍSTICAS:"));
            int totalCompleted = manager.getGlobalCompleted(player.getUniqueId());
            lore.add(ChatUtils.colorize("&fCompletadas: &a" + totalCompleted));
            
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
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