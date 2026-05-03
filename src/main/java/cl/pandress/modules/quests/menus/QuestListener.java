package cl.pandress.modules.quests.menus;

import cl.pandress.Fresh;
import cl.pandress.modules.quests.QuestManager;
import cl.pandress.utils.ChatUtils;
import net.almamc.acore.api.BlockBreak3x3Event; 
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.List;

public class QuestListener implements Listener {

    private final Fresh plugin = Fresh.getInstance();
    
    private final String PLACED_BLOCK_META = "quest_placed_block";

    private void checkAndNotify(Player player, QuestManager manager, int level, int added) {
        String questKey = manager.getActiveQuestKey(level);
        if (questKey == null) return;

        int required = manager.getConfig().getInt("quest-pool." + questKey + ".action-amount");
        int current = manager.getProgress(player.getUniqueId());

        if (current >= required && (current - added) < required) {
            player.sendTitle(ChatUtils.colorize("&e&lOBJETIVO COMPLETADO"), 
                           ChatUtils.colorize("&fUsa &b/misiones &fpara reclamar"), 10, 40, 10);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1.5f);
            player.sendMessage(ChatUtils.colorize("&b&lMISIONES &8» &fHas terminado el objetivo. ¡Reclama tu premio en el menú!"));
        }
    }

    // Método auxiliar para identificar cultivos sembrables
    private boolean isFarmingCrop(Material mat) {
        String name = mat.name();
        return name.equals("WHEAT") || name.equals("POTATOES") || name.equals("CARROTS") || name.equals("BEETROOTS") || name.equals("NETHER_WART");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        QuestManager manager = plugin.getManagerHandler().getQuestManager();
        if (manager != null) {
            long expiry = manager.getFlyExpiry(player.getUniqueId());
            if (expiry > System.currentTimeMillis()) {
                List<String> disabledWorlds = plugin.getConfig().getStringList("tempfly.disabled-worlds");
                
                if (disabledWorlds.contains(player.getWorld().getName())) {
                    player.sendMessage(ChatUtils.colorize("&eTu Fly temporal sigue activo, pero está &cdesactivado &een este mundo."));
                } else {
                    player.setAllowFlight(true);
                    player.sendMessage(ChatUtils.colorize("&aTu Fly temporal sigue activo."));
                }
            }
        }
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        QuestManager manager = plugin.getManagerHandler().getQuestManager();
        if (manager != null) {
            long expiry = manager.getFlyExpiry(player.getUniqueId());
            if (expiry > System.currentTimeMillis()) {
                List<String> disabledWorlds = plugin.getConfig().getStringList("tempfly.disabled-worlds");
                
                if (disabledWorlds.contains(player.getWorld().getName())) {
                    if (player.getGameMode() != org.bukkit.GameMode.CREATIVE && player.getGameMode() != org.bukkit.GameMode.SPECTATOR) {
                        player.setAllowFlight(false);
                        player.setFlying(false);
                        player.sendMessage(ChatUtils.colorize("&cEl Fly temporal está desactivado en este mundo."));
                    }
                } else {
                    player.setAllowFlight(true);
                    player.sendMessage(ChatUtils.colorize("&aFly temporal reactivado."));
                }
            }
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        event.getBlock().setMetadata(PLACED_BLOCK_META, new FixedMetadataValue(plugin, true));
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        boolean isPlaced = block.hasMetadata(PLACED_BLOCK_META);
        boolean isCrop = isFarmingCrop(block.getType());

        // Si fue puesto por el jugador y NO es un cultivo, cancelamos para evitar el dupe de minería.
        if (isPlaced && !isCrop) return;

        // Si es un cultivo, obligamos a que esté en su etapa final de crecimiento.
        if (isCrop && block.getBlockData() instanceof Ageable ageable) {
            if (ageable.getAge() != ageable.getMaximumAge()) {
                return; // Si no está maduro al 100%, no cuenta (evita el dupe de plantar y romper inmediatamente).
            }
        }

        Player player = event.getPlayer();
        QuestManager manager = plugin.getManagerHandler().getQuestManager();
        if (manager == null) return;

        int level = manager.getPlayerDailyLevel(player.getUniqueId());
        if (level > 10) return;

        String questKey = manager.getActiveQuestKey(level);
        if (questKey == null) return;

        String path = "quest-pool." + questKey;
        if ("MINE".equalsIgnoreCase(manager.getConfig().getString(path + ".action-type"))) {
            String target = manager.getConfig().getString(path + ".action-target").toUpperCase();
            if (event.getBlock().getType().name().endsWith(target)) {
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
            String target = manager.getConfig().getString(path + ".action-target").toUpperCase();
            int validCount = 0;

            for (Block block : event.getExtraBlocks()) {
                boolean isPlaced = block.hasMetadata(PLACED_BLOCK_META);
                boolean isCrop = isFarmingCrop(block.getType());

                if (isPlaced && !isCrop) continue;

                if (isCrop && block.getBlockData() instanceof Ageable ageable) {
                    if (ageable.getAge() != ageable.getMaximumAge()) {
                        continue; 
                    }
                }

                if (block.getType().name().endsWith(target)) {
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
            manager.saveUserData(event.getPlayer().getUniqueId());
        }
    }
}