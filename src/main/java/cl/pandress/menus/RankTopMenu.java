package cl.pandress.menus;

import cl.pandress.Fresh;
import cl.pandress.modules.rankup.RankManager;
import cl.pandress.utils.ChatUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class RankTopMenu {

    // Slots calculados para formar una pirámide perfecta en un menú de 54 espacios
    private static final int[] PODIUM_SLOTS = {
            13,             // Top 1
          21, 23,           // Top 2, 3
        29, 31, 33,         // Top 4, 5, 6
      37, 39, 41, 43        // Top 7, 8, 9, 10
    };

    public static void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, ChatUtils.colorize("&8» &6&lTop 10 Global &8«"));
        RankManager manager = Fresh.getInstance().getManagerHandler().getRankManager();
        
        List<Map.Entry<UUID, Integer>> top = manager.getTopRanks();
        
        for (int i = 0; i < top.size() && i < 10; i++) {
            UUID uuid = top.get(i).getKey();
            int rank = top.get(i).getValue();
            OfflinePlayer target = Bukkit.getOfflinePlayer(uuid);
            
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            
            if (meta != null) {
                meta.setOwningPlayer(target);
                
                // Darle un color especial al Top 1, 2 y 3
                String color = (i == 0) ? "&6&l" : (i == 1) ? "&f&l" : (i == 2) ? "&c&l" : "&e&l";
                
                meta.setDisplayName(ChatUtils.colorize(color + "Top #" + (i + 1) + " &8» &f" + (target.getName() != null ? target.getName() : "Desconocido")));
                
                List<String> lore = new ArrayList<>();
                lore.add(ChatUtils.colorize("&7Rango actual: &a#" + rank));
                meta.setLore(lore);
                
                head.setItemMeta(meta);
            }
            inv.setItem(PODIUM_SLOTS[i], head);
        }

        player.openInventory(inv);
    }
}