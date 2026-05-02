package cl.pandress.menus;

import cl.pandress.Fresh;
import cl.pandress.modules.quests.QuestManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class QuestMenuListener implements Listener {

    private final Fresh plugin = Fresh.getInstance();

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTitle() == null || !event.getView().getTitle().contains("Misiones Diarias")) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        int slot = event.getSlot();
        QuestManager manager = plugin.getManagerHandler().getQuestManager();

        // 1. Clic en la Misión o en el Bonus (Slot 12)
        if (slot == 12) {
            int level = manager.getPlayerDailyLevel(player.getUniqueId());
            
            // Si ya terminó el bonus (Nivel 12), no hacer nada
            if (level >= 12) return;

            // Lógica si el clic es para reclamar el BONUS (Nivel 11)
            if (level == 11) {
                manager.claimDailyBonus(player);
                player.closeInventory();
                return;
            }

            // Lógica si el clic es en una misión normal (Nivel 1 a 10)
            String questKey = manager.getActiveQuestKey(level);
            if (questKey == null) return;

            int currentProgress = manager.getProgress(player.getUniqueId());
            int requiredAmount = manager.getConfig().getInt("quest-pool." + questKey + ".action-amount");

            if (currentProgress >= requiredAmount) {
                manager.completeQuest(player, level);
                player.closeInventory(); 
            } else {
                player.sendMessage("§c¡Aún no has completado el objetivo!");
            }
        }
        // 2. Clic en el Top (Slot 14 - La Campana)
        else if (slot == 14 && clickedItem.getType() == Material.BELL) {
            ItemMeta meta = clickedItem.getItemMeta();
            if (meta != null && meta.hasLore()) {
                List<String> lore = meta.getLore();
                int currentPage = 1;
                
                for (String line : lore) {
                    String uncolored = ChatColor.stripColor(line);
                    if (uncolored.startsWith("PAGE:")) {
                        try {
                            currentPage = Integer.parseInt(uncolored.replace("PAGE:", ""));
                        } catch (NumberFormatException ignored) {}
                        break;
                    }
                }

                int newPage = currentPage;
                
                if (event.isLeftClick()) {
                    newPage++;
                } 
                else if (event.isRightClick()) {
                    newPage--;
                }

                if (newPage != currentPage) {
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1f);
                    QuestMenu.open(player, newPage);
                }
            }
        }
    }
}