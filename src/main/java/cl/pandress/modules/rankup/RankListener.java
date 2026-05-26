package cl.pandress.modules.rankup;

import cl.pandress.Etherium;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;

public class RankListener implements Listener {

    private final Etherium plugin = Etherium.getInstance();

    // =========================================================
    //  HELPER INTERNO
    // =========================================================

    /**
     * Añade progreso a un requisito del siguiente rango del jugador,
     * pero solo si ese requisito existe en la config y el jugador no
     * lo ha completado todavía.
     *
     * addProgress() ya no hace I/O: solo actualiza el Map en memoria
     * y marca el UUID como dirty para el guardado async. Costo: O(1).
     */
    private void checkAndAddProgress(Player player, String category, String type, int amount) {
        RankManager manager = plugin.getManagerHandler().getRankManager();
        FileConfiguration config = manager.getConfig();

        int nextRank = manager.getPlayerRank(player.getUniqueId()) + 1;
        String path = "ranks." + nextRank + ".requirements." + category + "." + type;

        if (!config.contains(path)) return;

        int required = config.getInt(path);
        int current  = manager.getProgress(player.getUniqueId(), category, type);

        // No añadir más progreso del necesario — evita inflar el YAML
        if (current < required) {
            manager.addProgress(player.getUniqueId(), category, type, amount);
        }
    }

    // =========================================================
    //  EVENTOS
    // =========================================================

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        checkAndAddProgress(
            event.getPlayer(),
            "blocks_mine",
            event.getBlock().getType().name(),
            1
        );
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        checkAndAddProgress(
            event.getPlayer(),
            "blocks_place",
            event.getBlock().getType().name(),
            1
        );
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity().getKiller() == null) return;

        Player killer = event.getEntity().getKiller();
        RankManager manager = plugin.getManagerHandler().getRankManager();

        if (event.getEntity() instanceof Player) {
            // Kills PvP — usan la categoría "general" con clave "player_kills"
            int nextRank = manager.getPlayerRank(killer.getUniqueId()) + 1;
            String path  = "ranks." + nextRank + ".requirements.player_kills";

            if (manager.getConfig().contains(path)) {
                int required = manager.getConfig().getInt(path);
                int current  = manager.getProgress(killer.getUniqueId(), "general", "player_kills");
                if (current < required) {
                    manager.addProgress(killer.getUniqueId(), "general", "player_kills", 1);
                }
            }
        } else {
            // Kills de mobs
            checkAndAddProgress(
                killer,
                "mob_kills",
                event.getEntity().getType().name(),
                1
            );
        }
    }
}