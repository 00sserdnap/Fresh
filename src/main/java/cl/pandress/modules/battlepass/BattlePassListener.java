package cl.pandress.modules.battlepass;

import cl.pandress.Etherium;
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
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

public class BattlePassListener implements Listener {

    private final Etherium plugin = Etherium.getInstance();

    private boolean isModuleEnabled() {
        BattlePassManager bp = plugin.getManagerHandler().getBattlePassManager();
        if (bp == null || bp.getConfig() == null) return false;
        return bp.getConfig().getBoolean("settings.enabled", true);
    }

    // --- NUEVO: Mostrar BossBar al entrar ---
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!isModuleEnabled()) return;
        BattlePassManager bp = plugin.getManagerHandler().getBattlePassManager();
        bp.updateBossBar(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        BattlePassManager bp = plugin.getManagerHandler().getBattlePassManager();
        bp.savePlayerDataNow(event.getPlayer().getUniqueId());
        
        // --- NUEVO: Limpiar BossBar al salir ---
        bp.removeBossBar(event.getPlayer());
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        BattlePassManager bp = plugin.getManagerHandler().getBattlePassManager();
        String title = ChatColor.stripColor(event.getView().getTitle());

        String mainTitle      = ChatColor.stripColor(ChatUtils.colorize(bp.getMenuMain().getString("title", "")));
        String catTitle       = ChatColor.stripColor(ChatUtils.colorize(bp.getMenuCategories().getString("title", "")));
        String missTitlePrefix = ChatColor.stripColor(ChatUtils.colorize(bp.getMenuMissions().getString("title-prefix", "")));
        String rewTitlePrefix  = ChatColor.stripColor(ChatUtils.colorize(bp.getMenuRewards().getString("title-prefix", "")));

        if (title != null && (title.equals(mainTitle) || title.equals(catTitle) || title.startsWith(missTitlePrefix) || title.startsWith(rewTitlePrefix))) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        BattlePassManager bp = plugin.getManagerHandler().getBattlePassManager();
        String title = ChatColor.stripColor(event.getView().getTitle());

        String mainTitle       = ChatColor.stripColor(ChatUtils.colorize(bp.getMenuMain().getString("title", "")));
        String catTitle        = ChatColor.stripColor(ChatUtils.colorize(bp.getMenuCategories().getString("title", "")));
        String missTitlePrefix = ChatColor.stripColor(ChatUtils.colorize(bp.getMenuMissions().getString("title-prefix", "")));
        String rewTitlePrefix  = ChatColor.stripColor(ChatUtils.colorize(bp.getMenuRewards().getString("title-prefix", "")));

        if (title == null || (!title.equals(mainTitle) && !title.equals(catTitle) && !title.startsWith(missTitlePrefix) && !title.startsWith(rewTitlePrefix))) return;

        event.setCancelled(true);
        
        if (!isModuleEnabled()) {
            if (event.getWhoClicked() instanceof Player player) {
                player.sendMessage(ChatUtils.colorize("&cEl Pase de Batalla se encuentra desactivado temporalmente."));
                player.closeInventory();
            }
            return;
        }

        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getView().getTopInventory())) return;

        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType() == Material.AIR) return;
        int slot = event.getSlot();

        bp.playSound(player, "sounds.click");

        if (title.equals(mainTitle)) {
            if (slot == bp.getMenuMain().getInt("items.misiones.slot")) PassCategoryMenu.open(player);
            if (slot == bp.getMenuMain().getInt("items.pase.slot"))     PassRewardsMenu.open(player, 1);
            if (slot == bp.getMenuMain().getInt("items.salir.slot"))    player.closeInventory();
            return;
        }

        if (title.equals(catTitle)) {
            if (slot == bp.getMenuCategories().getInt("items.facil.slot"))   PassMissionsMenu.open(player, "facil",   1);
            if (slot == bp.getMenuCategories().getInt("items.medio.slot"))   PassMissionsMenu.open(player, "medio",   1);
            if (slot == bp.getMenuCategories().getInt("items.dificil.slot")) PassMissionsMenu.open(player, "dificil", 1);
            if (slot == bp.getMenuCategories().getInt("items.volver.slot"))  PassMainMenu.open(player);
            return;
        }

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

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (!isModuleEnabled()) return; 
        if (event.getBlock().hasMetadata("quest_placed_block")) return;
        if (event.getBlock().getBlockData() instanceof Ageable ageable && ageable.getAge() != ageable.getMaximumAge()) return;

        BattlePassManager bp = plugin.getManagerHandler().getBattlePassManager();
        bp.addProgress(event.getPlayer(), "MINE", event.getBlock().getType().name(), 1);
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (!isModuleEnabled()) return; 
        if (event.getEntity().getKiller() != null) {
            BattlePassManager bp = plugin.getManagerHandler().getBattlePassManager();
            bp.addProgress(event.getEntity().getKiller(), "KILL", event.getEntity().getType().name(), 1);
        }
    }
}