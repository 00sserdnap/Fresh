package cl.pandress.modules.customspawners;

import cl.pandress.modules.customspawners.data.CustomSpawnerData;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.SpawnerSpawnEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public class CustomSpawnerListener implements Listener {

    private final CustomSpawnerManager manager;

    public CustomSpawnerListener(CustomSpawnerManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onSpawnerPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (item.getType() != Material.SPAWNER || !item.hasItemMeta()) return;

        String typeStr = item.getItemMeta().getPersistentDataContainer().get(manager.getTypeKey(), PersistentDataType.STRING);
        if (typeStr != null) {
            try {
                EntityType type = EntityType.valueOf(typeStr);
                Block block = event.getBlockPlaced();
                Player player = event.getPlayer();
                
                manager.addSpawner(block.getLocation(), type, player.getUniqueId(), player.getName());
                
                if (block.getState() instanceof CreatureSpawner) {
                    CreatureSpawner spawner = (CreatureSpawner) block.getState();
                    spawner.setSpawnedType(type);
                    spawner.setSpawnCount(1);
                    spawner.setRequiredPlayerRange(16);
                    spawner.setMaxNearbyEntities(0);
                    spawner.setMaxSpawnDelay(30000);
                    spawner.setMinSpawnDelay(30000);
                    spawner.update(true, false); 
                }
                
                String msg = manager.getMessage("placed").replace("{type}", type.name());
                event.getPlayer().sendMessage(msg);
                
            } catch (IllegalArgumentException e) {
                String errorMsg = manager.getMessage("error").replace("{error}", e.getMessage());
                event.getPlayer().sendMessage(errorMsg);
            }
        }
    }

    @EventHandler
    public void onSpawnerBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.SPAWNER) return;

        Location loc = block.getLocation();
        CustomSpawnerData spawner = manager.getSpawnerAt(loc);
        
        if (spawner != null) {
            Player player = event.getPlayer();
            manager.removeSpawner(loc);

            if (player.getGameMode() != GameMode.CREATIVE) {
                ItemStack drop = manager.createSpawnerItem(spawner.getEntityType());
                loc.getWorld().dropItemNaturally(loc, drop);
            }
            
            event.setExpToDrop(0); 
            player.sendMessage(manager.getMessage("picked-up"));
        }
    }

    @EventHandler
    public void onVanillaSpawn(SpawnerSpawnEvent event) {
        Location loc = event.getSpawner().getLocation();
        if (manager.getSpawnerAt(loc) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onSpawnerInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Block block = event.getClickedBlock();
            if (block != null && block.getType() == Material.SPAWNER) {
                ItemStack item = event.getItem();
                if (item != null && item.getType().name().endsWith("_SPAWN_EGG")) {
                    if (manager.getSpawnerAt(block.getLocation()) != null) {
                        event.setCancelled(true);
                    }
                }
            }
        }
    }
}