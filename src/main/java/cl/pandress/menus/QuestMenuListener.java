package cl.pandress.menus;

import cl.pandress.Fresh;
import cl.pandress.modules.quests.QuestManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

public class QuestMenuListener implements Listener {

    private final Fresh plugin = Fresh.getInstance();

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        // 1. Validar que el inventario sea el de misiones según el título de la imagen[cite: 1]
        if (event.getView().getTitle() == null || !event.getView().getTitle().contains("Misiones Diarias")) {
            return;
        }

        // Cancelar para que no puedan sacar el ítem del menú
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) return;
        
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        QuestManager manager = plugin.getManagerHandler().getQuestManager();
        int level = manager.getPlayerDailyLevel(player.getUniqueId());
        
        // 2. Si el nivel es > 10, ya terminó todo
        if (level > 10) return;

        String questKey = manager.getActiveQuestKey(level);
        if (questKey == null) return;

        // 3. Verificar progreso
        int currentProgress = manager.getProgress(player.getUniqueId());
        int requiredAmount = manager.getConfig().getInt("quest-pool." + questKey + ".action-amount");

        // 4. Lógica de reclamo al hacer clic en el ítem (Ender Pearl en la imagen)[cite: 1]
        if (currentProgress >= requiredAmount) {
            // Llamamos al método que ya tienes en QuestManager que entrega recompensas
            manager.completeQuest(player, level);
            player.closeInventory(); 
        } else {
            player.sendMessage("§c¡Aún no has completado el objetivo!");
        }
    }
}