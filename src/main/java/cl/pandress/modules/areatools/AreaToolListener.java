package cl.pandress.modules.areatools;

import cl.pandress.modules.areatools.menus.AreaToolMenu;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class AreaToolListener implements Listener {

    private final AreaToolManager manager;
    private final Set<UUID> activeMiners = new HashSet<>();

    public AreaToolListener(AreaToolManager manager) { this.manager = manager; }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Player player = event.getPlayer();
            ItemStack item = player.getInventory().getItemInMainHand();

            String toolId = manager.getToolId(item);
            if (toolId != null) {
                event.setCancelled(true);
                if (manager.isTemporary(item)) {
                    player.sendMessage(ChatColor.RED + "Esta herramienta es temporal y no se puede recargar.");
                    return;
                }
                AreaToolMenu.open(player, manager);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();

        if (activeMiners.contains(player.getUniqueId())) return;

        ItemStack item = player.getInventory().getItemInMainHand();
        String toolId = manager.getToolId(item);
        if (toolId == null) return;

        int charge = manager.getCharge(item);
        if (charge <= 0) {
            if (manager.isTemporary(item)) {
                player.getInventory().setItemInMainHand(null);
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
                player.sendMessage(ChatColor.RED + "¡Tu herramienta temporal se ha destruido por falta de energía!");
            } else {
                player.sendMessage(ChatColor.RED + "¡Tu herramienta está sin energía! Haz clic derecho para recargarla.");
            }
            return;
        }

        Block center = event.getBlock();
        int size = manager.getToolSize(toolId);
        int radius = (size - 1) / 2;
        BlockFace face = getFace(player);
        boolean isPickaxe = manager.isPickaxe(toolId);

        breakArea(player, center, face, radius, isPickaxe, item, charge);
    }

    private void breakArea(Player p, Block center, BlockFace face, int r, boolean isPickaxe, ItemStack item, int charge) {
        int[] u = (face == BlockFace.UP || face == BlockFace.DOWN) ? new int[]{1,0,0} : (face == BlockFace.NORTH || face == BlockFace.SOUTH ? new int[]{1,0,0} : new int[]{0,0,1});
        int[] v = (face == BlockFace.UP || face == BlockFace.DOWN) ? new int[]{0,0,1} : new int[]{0,1,0};

        activeMiners.add(p.getUniqueId());

        int brokenCount = 1;
        charge--;

        try {
            for (int i = -r; i <= r; i++) {
                for (int j = -r; j <= r; j++) {
                    if (charge <= 0) break;
                    if (i == 0 && j == 0) continue;

                    Block b = center.getRelative(u[0]*i + v[0]*j, u[1]*i + v[1]*j, u[2]*i + v[2]*j);

                    boolean isAllowed = isPickaxe
                            ? Tag.MINEABLE_PICKAXE.isTagged(b.getType())
                            : Tag.MINEABLE_SHOVEL.isTagged(b.getType());

                    if (isAllowed) {
                        BlockBreakEvent e = new BlockBreakEvent(b, p);
                        p.getServer().getPluginManager().callEvent(e);
                        if (!e.isCancelled()) {
                            b.breakNaturally(item);
                            brokenCount++;
                            charge--;
                        }
                    }
                }
            }
        } finally {
            activeMiners.remove(p.getUniqueId());
        }

        if (charge <= 0 && manager.isTemporary(item)) {
            p.getInventory().setItemInMainHand(null);
            p.playSound(p.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
            p.sendMessage(ChatColor.RED + "¡Tu herramienta temporal se ha destruido!");
        } else if (item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                PersistentDataContainer pdc = meta.getPersistentDataContainer();
                int currentBlocks = pdc.getOrDefault(manager.getBlocksKey(), PersistentDataType.INTEGER, 0);
                manager.setCharge(item, charge, currentBlocks + brokenCount);
            }
        }
    }

    private BlockFace getFace(Player p) {
        float pitch = p.getLocation().getPitch();
        if (pitch > 45) return BlockFace.DOWN;
        if (pitch < -45) return BlockFace.UP;
        float yaw = (p.getLocation().getYaw() % 360 + 360) % 360;
        if (yaw < 45 || yaw >= 315) return BlockFace.SOUTH;
        if (yaw < 135) return BlockFace.WEST;
        if (yaw < 225) return BlockFace.NORTH;
        return BlockFace.EAST;
    }
}