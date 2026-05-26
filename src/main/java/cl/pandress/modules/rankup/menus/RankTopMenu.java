package cl.pandress.modules.rankup.menus;

import cl.pandress.Etherium;
import cl.pandress.modules.rankup.RankManager;
import cl.pandress.utils.ChatUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class RankTopMenu {

    // Slots en disposición de podio visual dentro de un inventario 6x9 (54 slots)
    private static final int[] PODIUM_SLOTS = {13, 21, 23, 29, 31, 33, 37, 39, 41, 43};

    public static void open(Player player) {
        RankManager manager = Etherium.getInstance().getManagerHandler().getRankManager();
        Inventory inv = Bukkit.createInventory(
            (InventoryHolder) null,
            54,
            ChatUtils.colorize("&8» &6&lTop 10 Global &8«")
        );

        List<Map.Entry<UUID, Integer>> top = manager.getTopRanks();

        for (int i = 0; i < top.size() && i < PODIUM_SLOTS.length; i++) {
            UUID uuid   = top.get(i).getKey();
            int rank    = top.get(i).getValue();

            String name = Bukkit.getOfflinePlayer(uuid).getName();
            if (name == null) name = "Desconocido";

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta == null) continue;

            meta.setOwningPlayer(Bukkit.getOfflinePlayer(uuid));

            String color = switch (i) {
                case 0 -> "&6&l";
                case 1 -> "&f&l";
                case 2 -> "&c&l";
                default -> "&e&l";
            };

            meta.setDisplayName(ChatUtils.colorize(
                color + "Top #" + (i + 1) + " &8» &f" + name
            ));

            List<String> lore = new ArrayList<>();
            lore.add(ChatUtils.colorize("&7Rango actual: &a#" + rank));
            meta.setLore(lore);

            head.setItemMeta(meta);
            inv.setItem(PODIUM_SLOTS[i], head);
        }

        player.openInventory(inv);
    }
}