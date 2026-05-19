package cl.pandress.modules.battlepass.menus;

import cl.pandress.Etherium;
import cl.pandress.modules.battlepass.BattlePassManager;
import cl.pandress.utils.ChatUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PassMainMenu {

    public static void open(Player player) {
        BattlePassManager bp = Etherium.getInstance().getManagerHandler().getBattlePassManager();
        FileConfiguration cfg = bp.getMenuMain();
        
        Inventory inv = Bukkit.createInventory(null, cfg.getInt("size", 27), ChatUtils.colorize(cfg.getString("title")));
        UUID uuid = player.getUniqueId();

        inv.setItem(cfg.getInt("items.misiones.slot"), getItem(cfg, "items.misiones"));
        inv.setItem(cfg.getInt("items.pase.slot"), getItem(cfg, "items.pase"));
        inv.setItem(cfg.getInt("items.salir.slot"), getItem(cfg, "items.salir"));

        ItemStack info = getItem(cfg, "items.info");
        ItemMeta meta = info.getItemMeta();
        List<String> lore = new ArrayList<>();
        
        int currentLvl = bp.getLevel(uuid);
        int currentXp = bp.getXp(uuid);
        int reqXp = bp.getConfig().getInt("levels." + (currentLvl + 1) + ".xp-required", 500);
        String passType = bp.hasPremium(player) ? cfg.getString("formats.pass-premium") : cfg.getString("formats.pass-free");
        
        for (String line : cfg.getStringList("items.info.lore")) {
            lore.add(ChatUtils.colorize(line
                    .replace("%level%", String.valueOf(currentLvl))
                    .replace("%xp%", String.valueOf(currentXp))
                    .replace("%req_xp%", String.valueOf(reqXp))
                    .replace("%pass_type%", passType)
            ));
        }
        
        meta.setLore(lore);
        info.setItemMeta(meta);
        inv.setItem(cfg.getInt("items.info.slot"), info);

        fillGlass(inv);
        player.openInventory(inv);
    }

    protected static ItemStack getItem(FileConfiguration cfg, String path) {
        Material mat = Material.matchMaterial(cfg.getString(path + ".material", "PAPER"));
        ItemStack item = new ItemStack(mat != null ? mat : Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatUtils.colorize(cfg.getString(path + ".name", "")));
        List<String> lore = new ArrayList<>();
        if (cfg.contains(path + ".lore")) {
            for (String l : cfg.getStringList(path + ".lore")) lore.add(ChatUtils.colorize(l));
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    protected static void fillGlass(Inventory inv) {
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = glass.getItemMeta();
        meta.setDisplayName(" ");
        glass.setItemMeta(meta);
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) inv.setItem(i, glass);
        }
    }
}