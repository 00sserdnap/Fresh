package cl.pandress.menus;

import cl.pandress.Fresh;
import cl.pandress.modules.quests.QuestManager;
import cl.pandress.utils.ChatUtils;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

public class QuestMenuListener implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getView().getTitle().equals(ChatUtils.colorize("&8Misiones Diarias"))) {
            event.setCancelled(true);

            if (event.getCurrentItem() == null) return;
            if (!(event.getWhoClicked() instanceof Player player)) return;

            // Detectar clic SOLAMENTE en el slot de la misión (Slot 11)
            if (event.getRawSlot() == 11) {
                QuestManager manager = Fresh.getInstance().getManagerHandler().getQuestManager();
                int currentLevel = manager.getPlayerDailyLevel(player.getUniqueId());
                if (currentLevel > 10) return;

                String questKey = manager.getActiveQuestKey(currentLevel);
                if (questKey == null) return;

                int progress = manager.getProgress(player.getUniqueId());
                int required = manager.getConfig().getInt("quest-pool." + questKey + ".action-amount");

                if (progress >= required) {
                    manager.completeQuest(player, currentLevel);
                    QuestMenu.open(player); 
                } else {
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    player.sendMessage(ChatUtils.colorize("&c&lERROR &8» &7Aún no completas el objetivo."));
                    player.closeInventory();
                }
            }
        }
    }
}