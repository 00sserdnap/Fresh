package cl.pandress.modules.rankup;

import cl.pandress.Etherium;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;

public class RankListener implements Listener {
    private final Etherium plugin = Etherium.getInstance();

    public RankListener() {
    }

    private void checkAndAddProgress(Player player, String category, String type, int amount) {
        RankManager manager = this.plugin.getManagerHandler().getRankManager();
        FileConfiguration config = manager.getConfig();
        int nextRank = manager.getPlayerRank(player.getUniqueId()) + 1;
        String path = "ranks." + nextRank + ".requirements." + category + "." + type;
        if (config.contains(path)) {
            int required = config.getInt(path);
            int current = manager.getProgress(player.getUniqueId(), category, type);
            if (current < required) {
                manager.addProgress(player.getUniqueId(), category, type, amount);
            }
        }

    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        this.checkAndAddProgress(event.getPlayer(), "blocks_mine", event.getBlock().getType().name(), 1);
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        this.checkAndAddProgress(event.getPlayer(), "blocks_place", event.getBlock().getType().name(), 1);
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (event.getEntity().getKiller() != null) {
            Player killer = event.getEntity().getKiller();
            if (event.getEntity() instanceof Player) {
                RankManager manager = this.plugin.getManagerHandler().getRankManager();
                int nextRank = manager.getPlayerRank(killer.getUniqueId()) + 1;
                String path = "ranks." + nextRank + ".requirements.player_kills";
                if (manager.getConfig().contains(path)) {
                    int required = manager.getConfig().getInt(path);
                    int current = manager.getProgress(killer.getUniqueId(), "general", "player_kills");
                    if (current < required) {
                        manager.addProgress(killer.getUniqueId(), "general", "player_kills", 1);
                    }
                }
            } else {
                this.checkAndAddProgress(killer, "mob_kills", event.getEntity().getType().name(), 1);
            }
        }

    }
}
