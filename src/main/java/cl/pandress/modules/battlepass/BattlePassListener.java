package cl.pandress.modules.battlepass;

import cl.pandress.Fresh;
import cl.pandress.modules.battlepass.menus.PassCategoryMenu;
import cl.pandress.modules.battlepass.menus.PassMainMenu;
import cl.pandress.modules.battlepass.menus.PassMissionsMenu;
import cl.pandress.modules.battlepass.menus.PassRewardsMenu;
import cl.pandress.utils.ChatUtils;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

public class BattlePassListener implements Listener {

    private final Fresh plugin = Fresh.getInstance();

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        BattlePassManager bp = plugin.getManagerHandler().getBattlePassManager();
        String title = ChatColor.stripColor(event.getView().getTitle());
        
        // Obtenemos los títulos base limpiados de color para el anti-dupeo
        String mainTitle = ChatColor.stripColor(ChatUtils.colorize(bp.getMenuMain().getString("title", "")));
        String catTitle = ChatColor.stripColor(ChatUtils.colorize(bp.getMenuCategories().getString("title", "")));
        String missTitlePrefix = ChatColor.stripColor(ChatUtils.colorize(bp.getMenuMissions().getString("title-prefix", "")));
        String rewTitlePrefix = ChatColor.stripColor(ChatUtils.colorize(bp.getMenuRewards().getString("title-prefix", "")));

        if (title != null && (title.equals(mainTitle) || title.equals(catTitle) || title.startsWith(missTitlePrefix) || title.startsWith(rewTitlePrefix))) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        BattlePassManager bp = plugin.getManagerHandler().getBattlePassManager();
        String title = ChatColor.stripColor(event.getView().getTitle());
        
        // Obtenemos los títulos base limpiados de color
        String mainTitle = ChatColor.stripColor(ChatUtils.colorize(bp.getMenuMain().getString("title", "")));
        String catTitle = ChatColor.stripColor(ChatUtils.colorize(bp.getMenuCategories().getString("title", "")));
        String missTitlePrefix = ChatColor.stripColor(ChatUtils.colorize(bp.getMenuMissions().getString("title-prefix", "")));
        String rewTitlePrefix = ChatColor.stripColor(ChatUtils.colorize(bp.getMenuRewards().getString("title-prefix", "")));

        // Verificamos si el inventario clickeado pertenece a nuestro sistema
        if (title == null || (!title.equals(mainTitle) && !title.equals(catTitle) && !title.startsWith(missTitlePrefix) && !title.startsWith(rewTitlePrefix))) return;
        
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getView().getTopInventory())) return;

        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType() == Material.AIR) return;
        int slot = event.getSlot();

        // Reproducir sonido dinámico desde messages.yml
        bp.playSound(player, "sounds.click");

        // 1. Logica Menú Principal
        if (title.equals(mainTitle)) {
            if (slot == bp.getMenuMain().getInt("items.misiones.slot")) PassCategoryMenu.open(player);
            if (slot == bp.getMenuMain().getInt("items.pase.slot")) PassRewardsMenu.open(player, 1);
            if (slot == bp.getMenuMain().getInt("items.salir.slot")) player.closeInventory();
            return;
        }

        // 2. Logica Menú de Categorías (Dificultad)
        if (title.equals(catTitle)) {
            if (slot == bp.getMenuCategories().getInt("items.facil.slot")) PassMissionsMenu.open(player, "facil", 1);
            if (slot == bp.getMenuCategories().getInt("items.medio.slot")) PassMissionsMenu.open(player, "medio", 1);
            if (slot == bp.getMenuCategories().getInt("items.dificil.slot")) PassMissionsMenu.open(player, "dificil", 1);
            if (slot == bp.getMenuCategories().getInt("items.volver.slot")) PassMainMenu.open(player);
            return;
        }

        // 3. Logica Menú de Misiones por Categoría
        if (title.startsWith(missTitlePrefix)) {
            if (slot == bp.getMenuMissions().getInt("items.volver.slot")) { PassCategoryMenu.open(player); return; }
            
            String difficulty = title.replace(missTitlePrefix, "").split("\\|")[0].trim().toLowerCase();

            if (slot == bp.getMenuMissions().getInt("items.siguiente.slot") && item.getType() == Material.ARROW) {
                int nextPage = Integer.parseInt(ChatColor.stripColor(item.getItemMeta().getLore().get(0)).replace("PAGE:", ""));
                PassMissionsMenu.open(player, difficulty, nextPage);
                return;
            }
            if (slot == bp.getMenuMissions().getInt("items.anterior.slot") && item.getType() == Material.ARROW) {
                int currentPage = Integer.parseInt(title.split("Pág ")[1].trim());
                PassMissionsMenu.open(player, difficulty, currentPage - 1);
                return;
            }
            return;
        }

        // 4. Logica Pase Menu (Recompensas)
        if (title.startsWith(rewTitlePrefix)) {
            if (slot == bp.getMenuRewards().getInt("items.volver.slot")) { PassMainMenu.open(player); return; }
            
            if (slot == bp.getMenuRewards().getInt("items.siguiente.slot") && item.getType() == Material.ARROW) {
                int nextPage = Integer.parseInt(ChatColor.stripColor(item.getItemMeta().getLore().get(0)).replace("PAGE:", ""));
                PassRewardsMenu.open(player, nextPage);
                return;
            }
            if (slot == bp.getMenuRewards().getInt("items.anterior.slot") && item.getType() == Material.ARROW) {
                int currentPage = Integer.parseInt(title.split("Pág ")[1].trim());
                PassRewardsMenu.open(player, currentPage - 1);
                return;
            }

            int page = Integer.parseInt(title.split("Pág ")[1].trim());
            int startLvl = ((page - 1) * 9) + 1;

            if (slot >= 9 && slot <= 17) {
                int lvl = startLvl + (slot - 9);
                bp.claimReward(player, lvl, false);
                PassRewardsMenu.open(player, page); 
            } else if (slot >= 27 && slot <= 35) {
                int lvl = startLvl + (slot - 27);
                bp.claimReward(player, lvl, true);
                PassRewardsMenu.open(player, page);
            }
        }
    }

    // --- EVENTOS PARA GANAR EXPERIENCIA ---
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.getBlock().hasMetadata("quest_placed_block")) return;
        if (event.getBlock().getBlockData() instanceof Ageable ageable && ageable.getAge() != ageable.getMaximumAge()) return;

        BattlePassManager bp = plugin.getManagerHandler().getBattlePassManager();
        bp.addProgress(event.getPlayer(), "MINE", event.getBlock().getType().name(), 1);
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity().getKiller() != null) {
            BattlePassManager bp = plugin.getManagerHandler().getBattlePassManager();
            bp.addProgress(event.getEntity().getKiller(), "KILL", event.getEntity().getType().name(), 1);
        }
    }
}