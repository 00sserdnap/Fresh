package cl.pandress.modules.battlepass.menus;

import cl.pandress.Etherium;
import cl.pandress.utils.ChatUtils;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

public class PassCategoryMenu {

    public static void open(Player player) {
        FileConfiguration cfg = Etherium.getInstance().getManagerHandler().getBattlePassManager().getMenuCategories();
        Inventory inv = Bukkit.createInventory(null, cfg.getInt("size", 27), ChatUtils.colorize(cfg.getString("title")));

        inv.setItem(cfg.getInt("items.facil.slot"), PassMainMenu.getItem(cfg, "items.facil"));
        inv.setItem(cfg.getInt("items.medio.slot"), PassMainMenu.getItem(cfg, "items.medio"));
        inv.setItem(cfg.getInt("items.dificil.slot"), PassMainMenu.getItem(cfg, "items.dificil"));
        inv.setItem(cfg.getInt("items.volver.slot"), PassMainMenu.getItem(cfg, "items.volver"));

        PassMainMenu.fillGlass(inv);
        player.openInventory(inv);
    }
}