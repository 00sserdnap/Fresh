package cl.pandress.modules.battlepass.menus;

import cl.pandress.Fresh;
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

public class PassMissionsMenu {

    public static void open(Player player, String difficulty, int page) {
        BattlePassManager bp = Fresh.getInstance().getManagerHandler().getBattlePassManager();
        FileConfiguration cfg = bp.getMenuMissions();
        String diffName = difficulty.substring(0, 1).toUpperCase() + difficulty.substring(1);
        
        String title = cfg.getString("title-prefix") + diffName + " | Pág " + page;
        Inventory inv = Bukkit.createInventory(null, cfg.getInt("size", 54), ChatUtils.colorize(title));
        
        List<String> missions = bp.getMissionsByCategory(difficulty);
        int missionsPerPage = 45;
        int maxPages = (int) Math.ceil((double) missions.size() / missionsPerPage);
        if (maxPages == 0) maxPages = 1;
        
        int startIndex = (page - 1) * missionsPerPage;
        int endIndex = Math.min(startIndex + missionsPerPage, missions.size());
        
        int slot = 0;
        FileConfiguration bpCfg = bp.getConfig();
        for (int i = startIndex; i < endIndex; i++) {
            String mKey = missions.get(i);
            String path = "missions-pool." + mKey;
            int req = bpCfg.getInt(path + ".action-amount");
            int prog = bp.getMissionProgress(player.getUniqueId(), mKey);
            int xp = bpCfg.getInt(path + ".xp-reward");
            
            Material mat = Material.matchMaterial(bpCfg.getString(path + ".material", "PAPER"));
            ItemStack item = new ItemStack(mat != null ? mat : Material.PAPER);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(ChatUtils.colorize(bpCfg.getString(path + ".name")));
            
            List<String> lore = new ArrayList<>();
            lore.add(ChatUtils.colorize(bpCfg.getString(path + ".lore")));
            lore.add("");
            
            lore.add(ChatUtils.colorize(cfg.getString("formats.reward-text").replace("%xp%", String.valueOf(xp))));
            
            String status = prog >= req 
                ? cfg.getString("formats.status-completed") 
                : cfg.getString("formats.status-progress").replace("%current%", String.valueOf(prog)).replace("%required%", String.valueOf(req));
                
            lore.add(ChatUtils.colorize(cfg.getString("formats.progress-text").replace("%status%", status)));
            
            meta.setLore(lore);
            item.setItemMeta(meta);
            
            inv.setItem(slot++, item);
        }
        
        inv.setItem(cfg.getInt("items.volver.slot"), PassMainMenu.getItem(cfg, "items.volver"));
        if (page > 1) inv.setItem(cfg.getInt("items.anterior.slot"), PassMainMenu.getItem(cfg, "items.anterior"));
        
        if (page < maxPages) {
            ItemStack next = PassMainMenu.getItem(cfg, "items.siguiente");
            ItemMeta nextMeta = next.getItemMeta();
            List<String> lore = nextMeta.getLore() == null ? new ArrayList<>() : nextMeta.getLore();
            lore.add(ChatUtils.colorize(cfg.getString("formats.page-indicator").replace("%page%", String.valueOf(page + 1))));
            nextMeta.setLore(lore);
            next.setItemMeta(nextMeta);
            inv.setItem(cfg.getInt("items.siguiente.slot"), next);
        }
        
        PassMainMenu.fillGlass(inv);
        player.openInventory(inv);
    }
}