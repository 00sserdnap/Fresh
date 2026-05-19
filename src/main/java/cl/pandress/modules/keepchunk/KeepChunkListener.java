package cl.pandress.modules.keepchunk;

import cl.pandress.Etherium;
import cl.pandress.modules.keepchunk.data.KeepChunkData;
import cl.pandress.modules.keepchunk.menus.KeepChunkMenu;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

public class KeepChunkListener implements Listener {

    private final KeepChunkManager manager;

    public KeepChunkListener(KeepChunkManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onPlaceLoader(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        
        if (item.getType() == Material.AIR || !item.hasItemMeta()) return;
        
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        String typeId = pdc.get(manager.getChunkIdKey(), PersistentDataType.STRING);
        
        if (typeId != null) {
            event.setCancelled(true);
            
            KeepChunkType type = manager.getType(typeId);
            if (type == null) {
                player.sendMessage(manager.getMsg("type-not-found"));
                return;
            }

            int maxAllowed = manager.getMaxLoaders(player);
            int currentLoaders = manager.getLoaderCount(player.getUniqueId());
            
            if (currentLoaders >= maxAllowed) {
                String msg = manager.getMsg("limit-reached").replace("{max}", String.valueOf(maxAllowed));
                player.sendMessage(msg);
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                return;
            }
            
            // Detectar si el ítem es temporal desde su NBT
            boolean isTemp = pdc.has(manager.getTempKey(), PersistentDataType.BYTE);
            int fuel = pdc.getOrDefault(manager.getFuelKey(), PersistentDataType.INTEGER, type.getMaxFuel());
            String customName = pdc.get(manager.getCustomNameKey(), PersistentDataType.STRING);
            String ownerStr = pdc.get(manager.getOwnerKey(), PersistentDataType.STRING);
            UUID ownerId = ownerStr != null ? UUID.fromString(ownerStr) : player.getUniqueId();

            item.setAmount(item.getAmount() - 1);

            manager.spawnLoader(event.getClickedBlock().getLocation().add(0, 1, 0), player, typeId, fuel, customName, ownerId, isTemp);
            
            player.getLocation().getWorld().spawnParticle(Particle.END_ROD, event.getClickedBlock().getLocation().add(0.5, 1.5, 0.5), 20, 0.5, 0.5, 0.5, 0.1);
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1f, 1f);
            
            String msg = manager.getMsg("placed").replace("{current}", String.valueOf(currentLoaders + 1)).replace("{max}", String.valueOf(maxAllowed));
            player.sendMessage(msg);
        }
    }

    @EventHandler
    public void onInteractNPC(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Villager)) return;
        Villager npc = (Villager) event.getRightClicked();
        
        String loaderIdStr = npc.getPersistentDataContainer().get(manager.getChunkIdKey(), PersistentDataType.STRING);
        if (loaderIdStr != null) {
            event.setCancelled(true); 
            UUID loaderId = UUID.fromString(loaderIdStr);
            KeepChunkData data = manager.getLoader(loaderId);
            
            if (data != null) {
                Player player = event.getPlayer();
                
                if (!data.getOwner().equals(player.getUniqueId()) && !player.hasPermission("keepchunk.admin")) {
                    player.sendMessage(manager.getMsg("not-owner").replace("{owner}", Bukkit.getOfflinePlayer(data.getOwner()).getName())); 
                    return;
                }

                Bukkit.getScheduler().runTask(Etherium.getInstance(), () -> {
                    KeepChunkMenu.open(player, manager, data, npc);
                });
            }
        }
    }
    
    @EventHandler
    public void onChatRename(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (manager.getRenamingPlayers().containsKey(player.getUniqueId())) {
            event.setCancelled(true);
            String msg = event.getMessage();
            UUID loaderId = manager.getRenamingPlayers().remove(player.getUniqueId());
            
            if (msg.equalsIgnoreCase("cancelar")) {
                player.sendMessage(manager.getMsg("rename-cancelled"));
                return;
            }
            
            KeepChunkData data = manager.getLoader(loaderId);
            if (data != null) {
                if (data.isTemporary()) {
                    player.sendMessage(ChatColor.RED + "No puedes renombrar un cargador temporal.");
                    return;
                }

                String coloredName = ChatColor.translateAlternateColorCodes('&', msg);
                data.setCustomName(coloredName);
                manager.saveLoaderData(); 
                
                Bukkit.getScheduler().runTask(Etherium.getInstance(), () -> {
                    for (Entity entity : data.getLocation().getWorld().getNearbyEntities(data.getLocation(), 2, 2, 2)) {
                        if (entity instanceof Villager) {
                            String idStr = entity.getPersistentDataContainer().get(manager.getChunkIdKey(), PersistentDataType.STRING);
                            if (idStr != null && idStr.equals(loaderId.toString())) {
                                entity.setCustomName(coloredName);
                                break;
                            }
                        }
                    }
                    player.sendMessage(manager.getMsg("rename-done"));
                });
            }
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Villager) {
            if (event.getEntity().getPersistentDataContainer().has(manager.getChunkIdKey(), PersistentDataType.STRING)) {
                event.setCancelled(true);
            }
        }
    }
}