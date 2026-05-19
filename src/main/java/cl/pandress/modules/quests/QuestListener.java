package cl.pandress.modules.quests;

import cl.pandress.Etherium;
import cl.pandress.utils.ChatUtils;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
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

    private final Etherium plugin = Etherium.getInstance();
    private final String PLACED_BLOCK_META = "quest_placed_block";

    private boolean isModuleEnabled() {
        QuestManager manager = plugin.getManagerHandler().getQuestManager();
        if (manager == null || manager.getConfig() == null) return false;
        return manager.getConfig().getBoolean("settings.enabled", true);
    }

    private void checkAndNotify(Player player, QuestManager manager, int level, int added) {
        String questKey = manager.getActiveQuestKey(level);
        if (questKey == null) return;

        int required = manager.getConfig().getInt("quest-pool." + questKey + ".action-amount");
        int current  = manager.getProgress(player.getUniqueId());

        if (current >= required && (current - added) < required) {
            player.sendTitle(ChatUtils.colorize("&e&lOBJETIVO COMPLETADO"),
                             ChatUtils.colorize("&fUsa &b/misiones &fpara reclamar"), 10, 40, 10);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1.5f);
            player.sendMessage(ChatUtils.colorize("&b&lMISIONES &8» &fHas terminado el objetivo. ¡Reclama tu premio en el menú!"));
        }
    }

    // --- EVENTOS DEL TEMPFLY & DATOS (NO SE BLOQUEAN) ---

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        QuestManager manager = plugin.getManagerHandler().getQuestManager();
        if (manager == null) return;

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

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        QuestManager manager = plugin.getManagerHandler().getQuestManager();
        if (manager == null) return;

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

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        QuestManager manager = plugin.getManagerHandler().getQuestManager();
        if (manager != null) manager.saveUserDataNow(event.getPlayer().getUniqueId());
    }

    // --- EVENTOS DE MISIONES (SE BLOQUEAN SI ESTÁ APAGADO) ---

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!isModuleEnabled()) return;
        event.getBlock().setMetadata(PLACED_BLOCK_META, new FixedMetadataValue(plugin, true));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!isModuleEnabled()) return;

        Block block = event.getBlock();
        boolean isPlaced = block.hasMetadata(PLACED_BLOCK_META);

        if (block.getBlockData() instanceof Ageable ageable) {
            if (ageable.getAge() != ageable.getMaximumAge()) return;
            isPlaced = false;
        }
        
        // Bloqueo Anti-Abuso: Ignora bloques puestos por jugadores
        if (isPlaced) return;

        Player player = event.getPlayer();
        QuestManager manager = plugin.getManagerHandler().getQuestManager();
        if (manager == null) return;

        int level = manager.getPlayerDailyLevel(player.getUniqueId());
        if (level > 10) return;

        String questKey = manager.getActiveQuestKey(level);
        if (questKey == null) return;

        String path = "quest-pool." + questKey;
        String actionType = manager.getConfig().getString(path + ".action-type");
        
        if ("MINE".equalsIgnoreCase(actionType) || "BREAK".equalsIgnoreCase(actionType)) {
            boolean match = false;
            
            // Soporta que configures un solo String o una Lista de bloques
            if (manager.getConfig().isList(path + ".action-target")) {
                for (String t : manager.getConfig().getStringList(path + ".action-target")) {
                    if (block.getType().name().contains(t.toUpperCase())) {
                        match = true;
                        break;
                    }
                }
            } else {
                String targetStr = manager.getConfig().getString(path + ".action-target");
                if (targetStr != null && block.getType().name().contains(targetStr.toUpperCase())) {
                    match = true;
                }
            }

            if (match) {
                manager.addProgress(player.getUniqueId(), 1);
                checkAndNotify(player, manager, level, 1);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        if (!isModuleEnabled()) return;
        if (event.getEntity().getKiller() == null) return;

        Player player = event.getEntity().getKiller();
        QuestManager manager = plugin.getManagerHandler().getQuestManager();
        if (manager == null) return;

        int level = manager.getPlayerDailyLevel(player.getUniqueId());
        if (level > 10) return;

        String questKey = manager.getActiveQuestKey(level);
        if (questKey == null) return;

        String path = "quest-pool." + questKey;
        if ("KILL".equalsIgnoreCase(manager.getConfig().getString(path + ".action-type"))) {
            String target = manager.getConfig().getString(path + ".action-target");
            if (target != null && event.getEntity().getType().name().equalsIgnoreCase(target.toUpperCase())) {
                manager.addProgress(player.getUniqueId(), 1);
                checkAndNotify(player, manager, level, 1);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!isModuleEnabled()) return;
        if (!(event.getEntity() instanceof Player player)) return;

        QuestManager manager = plugin.getManagerHandler().getQuestManager();
        if (manager == null) return;

        int level = manager.getPlayerDailyLevel(player.getUniqueId());
        if (level > 10) return;

        String questKey = manager.getActiveQuestKey(level);
        if (questKey == null) return;

        String path = "quest-pool." + questKey;
        if ("PICKUP".equalsIgnoreCase(manager.getConfig().getString(path + ".action-type"))) {
            String target = manager.getConfig().getString(path + ".action-target");
            if (target != null && event.getItem().getItemStack().getType().name().equalsIgnoreCase(target.toUpperCase())) {
                int amount = event.getItem().getItemStack().getAmount();
                manager.addProgress(player.getUniqueId(), amount);
                checkAndNotify(player, manager, level, amount);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreed(EntityBreedEvent event) {
        if (!isModuleEnabled()) return;
        if (!(event.getBreeder() instanceof Player player)) return;

        QuestManager manager = plugin.getManagerHandler().getQuestManager();
        if (manager == null) return;

        int level = manager.getPlayerDailyLevel(player.getUniqueId());
        if (level > 10) return;

        String questKey = manager.getActiveQuestKey(level);
        if (questKey == null) return;

        String path = "quest-pool." + questKey;
        if ("BREED".equalsIgnoreCase(manager.getConfig().getString(path + ".action-type"))) {
            String target = manager.getConfig().getString(path + ".action-target");
            if (target != null && event.getEntity().getType().name().equalsIgnoreCase(target.toUpperCase())) {
                manager.addProgress(player.getUniqueId(), 1);
                checkAndNotify(player, manager, level, 1);
            }
        }
    }
}