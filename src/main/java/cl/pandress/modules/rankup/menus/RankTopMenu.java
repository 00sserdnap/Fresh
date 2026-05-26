//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package cl.pandress.modules.rankup.menus;

import cl.pandress.Etherium;
import cl.pandress.modules.rankup.RankManager;
import cl.pandress.utils.ChatUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

public class RankTopMenu {
    private static final int[] PODIUM_SLOTS = new int[]{13, 21, 23, 29, 31, 33, 37, 39, 41, 43};

    public RankTopMenu() {
    }

    public static void open(Player player) {
        Inventory inv = Bukkit.createInventory((InventoryHolder)null, 54, ChatUtils.colorize("&8» &6&lTop 10 Global &8«"));
        RankManager manager = Etherium.getInstance().getManagerHandler().getRankManager();
        List<Map.Entry<UUID, Integer>> top = manager.getTopRanks();

        for(int i = 0; i < top.size() && i < 10; ++i) {
            UUID uuid = (UUID)((Map.Entry)top.get(i)).getKey();
            int rank = (Integer)((Map.Entry)top.get(i)).getValue();
            OfflinePlayer target = Bukkit.getOfflinePlayer(uuid);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta)head.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(target);
                String color = i == 0 ? "&6&l" : (i == 1 ? "&f&l" : (i == 2 ? "&c&l" : "&e&l"));
                meta.setDisplayName(ChatUtils.colorize(color + "Top #" + (i + 1) + " &8» &f" + (target.getName() != null ? target.getName() : "Desconocido")));
                List<String> lore = new ArrayList();
                lore.add(ChatUtils.colorize("&7Rango actual: &a#" + rank));
                meta.setLore(lore);
                head.setItemMeta(meta);
            }

            inv.setItem(PODIUM_SLOTS[i], head);
        }

        player.openInventory(inv);
    }
}
