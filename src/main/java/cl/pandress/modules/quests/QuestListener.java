package cl.pandress.modules.quests;

import cl.pandress.Fresh;
import cl.pandress.utils.ChatUtils;
import net.almamc.acore.api.BlockBreak3x3Event; // API de aCore integrada
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class QuestListener implements Listener {

    private final Fresh plugin = Fresh.getInstance();

    /**
     * Verifica si el jugador ha alcanzado el objetivo de la misión actual
     * y envía notificaciones en pantalla y sonido si acaba de terminar.
     */
    private void checkAndNotify(Player player, QuestManager manager, int level, int added) {
        String questKey = manager.getActiveQuestKey(level);
        if (questKey == null) return;

        int required = manager.getConfig().getInt("quest-pool." + questKey + ".action-amount");
        int current = manager.getProgress(player.getUniqueId());

        // Si el progreso alcanzó el requisito justo con esta acción
        if (current >= required && (current - added) < required) {
            // Notificación visual en pantalla (Title)
            player.sendTitle(ChatUtils.colorize("&e&lOBJETIVO COMPLETADO"), 
                           ChatUtils.colorize("&fUsa &b/misiones &fpara reclamar"), 10, 40, 10);
            
            // Sonido de aviso mientras juega
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1.5f);
            
            // Mensaje informativo en el chat
            player.sendMessage(ChatUtils.colorize("&b&lMISIONES &8» &fHas terminado el objetivo. ¡Reclama tu premio en el menú!"));
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        QuestManager manager = plugin.getManagerHandler().getQuestManager();
        if (manager == null) return;

        int level = manager.getPlayerDailyLevel(player.getUniqueId());
        if (level > 10) return;

        String questKey = manager.getActiveQuestKey(level);
        if (questKey == null) return;

        String path = "quest-pool." + questKey;
        if ("MINE".equalsIgnoreCase(manager.getConfig().getString(path + ".action-type"))) {
            if (event.getBlock().getType().name().equalsIgnoreCase(manager.getConfig().getString(path + ".action-target"))) {
                manager.addProgress(player.getUniqueId(), 1);
                checkAndNotify(player, manager, level, 1);
            }
        }
    }

    @EventHandler
    public void onBlockBreak3x3(BlockBreak3x3Event event) {
        if (event.isCancelled()) return;
        
        Player player = event.getPlayer();
        QuestManager manager = plugin.getManagerHandler().getQuestManager();
        if (manager == null) return;

        int level = manager.getPlayerDailyLevel(player.getUniqueId());
        if (level > 10) return;

        String questKey = manager.getActiveQuestKey(level);
        if (questKey == null) return;

        String path = "quest-pool." + questKey;
        if ("MINE".equalsIgnoreCase(manager.getConfig().getString(path + ".action-type"))) {
            String target = manager.getConfig().getString(path + ".action-target");
            int validCount = 0;

            // Escaneamos los bloques extra proporcionados por la API de aCore
            for (Block block : event.getExtraBlocks()) {
                if (block.getType().name().equalsIgnoreCase(target)) {
                    validCount++;
                }
            }

            if (validCount > 0) {
                manager.addProgress(player.getUniqueId(), validCount);
                checkAndNotify(player, manager, level, validCount);
            }
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity().getKiller() != null) {
            Player player = event.getEntity().getKiller();
            QuestManager manager = plugin.getManagerHandler().getQuestManager();
            if (manager == null) return;

            int level = manager.getPlayerDailyLevel(player.getUniqueId());
            if (level > 10) return;

            String questKey = manager.getActiveQuestKey(level);
            if (questKey == null) return;

            String path = "quest-pool." + questKey;
            if ("KILL".equalsIgnoreCase(manager.getConfig().getString(path + ".action-type"))) {
                if (event.getEntity().getType().name().equalsIgnoreCase(manager.getConfig().getString(path + ".action-target"))) {
                    manager.addProgress(player.getUniqueId(), 1);
                    checkAndNotify(player, manager, level, 1);
                }
            }
        }
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        
        QuestManager manager = plugin.getManagerHandler().getQuestManager();
        if (manager == null) return;

        int level = manager.getPlayerDailyLevel(player.getUniqueId());
        if (level > 10) return;

        String questKey = manager.getActiveQuestKey(level);
        if (questKey == null) return;

        String path = "quest-pool." + questKey;
        if ("PICKUP".equalsIgnoreCase(manager.getConfig().getString(path + ".action-type"))) {
            if (event.getItem().getItemStack().getType().name().equalsIgnoreCase(manager.getConfig().getString(path + ".action-target"))) {
                int amountPickedUp = event.getItem().getItemStack().getAmount();
                manager.addProgress(player.getUniqueId(), amountPickedUp);
                checkAndNotify(player, manager, level, amountPickedUp);
            }
        }
    }

    @EventHandler
    public void onBreed(EntityBreedEvent event) {
        if (!(event.getBreeder() instanceof Player player)) return;
        
        QuestManager manager = plugin.getManagerHandler().getQuestManager();
        if (manager == null) return;

        int level = manager.getPlayerDailyLevel(player.getUniqueId());
        if (level > 10) return;

        String questKey = manager.getActiveQuestKey(level);
        if (questKey == null) return;

        String path = "quest-pool." + questKey;
        if ("BREED".equalsIgnoreCase(manager.getConfig().getString(path + ".action-type"))) {
            if (event.getEntity().getType().name().equalsIgnoreCase(manager.getConfig().getString(path + ".action-target"))) {
                manager.addProgress(player.getUniqueId(), 1);
                checkAndNotify(player, manager, level, 1);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        QuestManager manager = plugin.getManagerHandler().getQuestManager();
        if (manager != null) {
            // Aseguramos que los datos se guarden físicamente al salir el jugador
            manager.saveUserData(event.getPlayer().getUniqueId());
        }
    }
}