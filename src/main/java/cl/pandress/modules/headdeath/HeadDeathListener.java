package cl.pandress.modules.headdeath;

import cl.pandress.modules.headdeath.data.GraveData;
import cl.pandress.modules.headdeath.data.PlayerCosmetics;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class HeadDeathListener implements Listener {

    private final HeadDeathManager manager;

    public HeadDeathListener(HeadDeathManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        if (!manager.isEnabled()) return;
        if (event.getKeepInventory() || event.getDrops().isEmpty()) return;

        Player victim = event.getEntity();
        if (!manager.isWorldEnabled(victim.getWorld().getName())) return;

        Player owner = victim.getKiller() != null ? victim.getKiller() : victim;
        Location loc = victim.getLocation().clone();

        if (loc.getY() < loc.getWorld().getMinHeight()) loc.setY(loc.getWorld().getMinHeight() + 1);
        if (!loc.getBlock().getType().isAir() && !loc.getBlock().isReplaceable()) loc.add(0, 1, 0);

        List<ItemStack> drops = new ArrayList<>(event.getDrops());

        // Obtener cosméticos del jugador
        PlayerCosmetics cosmetics = manager.getCosmetics(victim.getUniqueId());
        
        // Ejecutar efecto de muerte
        manager.playDeathEffect(loc, cosmetics.getSelectedEffect());

        // Validar material de tumba
        Material graveMat = Material.matchMaterial(cosmetics.getSelectedGrave());
        if (graveMat == null || !graveMat.isBlock()) {
            graveMat = Material.SOUL_CAMPFIRE;
        }

        try {
            manager.createGrave(victim, owner, drops, loc.getBlock().getLocation(), graveMat);
            event.getDrops().clear();
        } catch (Exception e) {
            victim.sendMessage(manager.getMsg("error-generate"));
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (!manager.isEnabled() || event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() == EquipmentSlot.OFF_HAND) return;

        Block block = event.getClickedBlock();
        if (block == null) return;

        GraveData grave = manager.getGraves().get(block.getLocation());
        if (grave == null) return;

        event.setCancelled(true);
        Player p = event.getPlayer();

        if (!manager.canLoot(p, grave)) {
            int prot = manager.getConfig().getInt("settings.protection-seconds", 30);
            long rem = prot - ((System.currentTimeMillis() - grave.getDeathTimeMillis()) / 1000);
            p.sendMessage(manager.getMsg("protected").replace("{remaining}", String.valueOf(rem)));
            return;
        }

        p.sendMessage(manager.getMsg("opened").replace("{victim}", grave.getVictimName()));
        manager.logLootToDiscord(p.getName(), grave.getVictimName(), block.getLocation(), grave.getInventory());

        p.openInventory(grave.getInventory());
        p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_BARREL_OPEN, 1.0f, 1.0f);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!manager.isEnabled()) return;
        
        GraveData grave = manager.getGraveByInventory(event.getInventory());
        if (grave != null) {
            boolean empty = true;
            for (ItemStack item : grave.getInventory().getContents()) {
                if (item != null && item.getType() != Material.AIR) {
                    empty = false;
                    break;
                }
            }

            if (empty && !grave.isEmptying()) {
                manager.setEmptying(grave);
                if (event.getPlayer() instanceof Player p) {
                    p.sendMessage(manager.getMsg("emptied"));
                }
            }
        }
    }

    /* ============================================================
       PROTECCIÓN CONTRA ENTORNO (1.21+)
       ============================================================ */

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        if (manager.getGraves().containsKey(event.getBlock().getLocation())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(manager.getMsg("no-break"));
        }
    }

    @EventHandler
    public void onWaterFlow(BlockFromToEvent event) {
        if (manager.getGraves().containsKey(event.getToBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPhysics(BlockPhysicsEvent event) {
        if (manager.getGraves().containsKey(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onExplosion(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> manager.getGraves().containsKey(block.getLocation()));
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> manager.getGraves().containsKey(block.getLocation()));
    }

    @EventHandler
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block block : event.getBlocks()) {
            if (manager.getGraves().containsKey(block.getLocation())) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block block : event.getBlocks()) {
            if (manager.getGraves().containsKey(block.getLocation())) {
                event.setCancelled(true);
                return;
            }
        }
    }

    /* ============================================================
       LIMPIEZA DE HOLOGRAMAS HUÉRFANOS AL CARGAR CHUNKS
       ============================================================ */
/* ============================================================
       LIMPIEZA DE HOLOGRAMAS HUÉRFANOS AL CARGAR CHUNKS
       ============================================================ */
    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!manager.isEnabled()) return;
        for (org.bukkit.entity.Entity entity : event.getChunk().getEntities()) {
            if (entity instanceof TextDisplay && entity.getScoreboardTags().contains("etherium_grave")) {
                Location entLoc = entity.getLocation();
                boolean found = false;

                for (GraveData grave : manager.getGraves().values()) {
                    // Comparar por nombre de mundo para evitar problemas de instancias de World en Bukkit
                    if (grave.getLocation().getWorld() != null &&
                            grave.getLocation().getWorld().getName().equals(entLoc.getWorld().getName())) {

                        // Calcular distancia manualmente para evitar excepciones de dimensiones
                        double dx = grave.getLocation().getX() - entLoc.getX();
                        double dy = grave.getLocation().getY() - entLoc.getY();
                        double dz = grave.getLocation().getZ() - entLoc.getZ();

                        if ((dx * dx + dy * dy + dz * dz) <= 9.0) { // Si está dentro de 3 bloques de radio
                            found = true;

                            // Si la tumba había perdido la referencia a su holograma tras un reinicio, lo reconectamos
                            if (grave.getHologram() == null || grave.getHologram().isDead()) {
                                grave.setHologram((TextDisplay) entity);
                            }
                            break;
                        }
                    }
                }

                if (!found) {
                    entity.remove();
                }
            }
        }
    }
}