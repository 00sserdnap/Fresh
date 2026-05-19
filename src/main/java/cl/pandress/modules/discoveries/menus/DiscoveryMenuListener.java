package cl.pandress.modules.discoveries.menus;

import cl.pandress.modules.discoveries.DiscoveryManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public class DiscoveryMenuListener implements Listener {

    private final DiscoveryManager manager;

    public DiscoveryMenuListener(DiscoveryManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        
        InventoryView view = event.getView();
        String rawTitle = view.getTitle();
        
        String title = ChatColor.stripColor(rawTitle); 
        Player player = (Player) event.getWhoClicked();
        
        // --- MENÚ PRINCIPAL ---
        if (title != null && title.contains("Descubrimientos")) {
            event.setCancelled(true); 
            
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || !clicked.hasItemMeta() || !clicked.getItemMeta().hasDisplayName()) return;
            
            String itemName = ChatColor.stripColor(clicked.getItemMeta().getDisplayName()).toLowerCase();

            if (itemName.contains("bioma")) {
                manager.openCategoryMenu(player, "BIOME", "&#00FF00🌿 Tus Biomas", Material.GRASS_BLOCK);
            } else if (itemName.contains("estructura")) {
                manager.openCategoryMenu(player, "STRUCTURE", "&#FFA500🏛 Tus Estructuras", Material.CHISELED_STONE_BRICKS);
            } else if (itemName.contains("cueva")) {
                manager.openCategoryMenu(player, "CAVE", "&#00FFFF⛏ Tus Cuevas", Material.POINTED_DRIPSTONE);
            } else if (itemName.contains("mob") || itemName.contains("registrado")) {
                manager.openCategoryMenu(player, "MOB", "&#FF5555🧟 Tus Mobs", Material.ZOMBIE_HEAD);
            }
            return;
        }

        // --- SUB-MENÚS (SISTEMA DE COBRO MANUAL) ---
        if (title != null && (title.contains("Tus Biomas") || 
                              title.contains("Tus Estructuras") || 
                              title.contains("Tus Cuevas") ||
                              title.contains("Tus Mobs"))) {
            
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null) return;
            
            // Botón de Volver
            int slot = event.getRawSlot();
            if (slot == 49 || clicked.getType() == Material.ARROW) {
                manager.openMainMenu(player);
                return;
            }

            // Lógica para reclamar la recompensa (usando PersistentDataContainer)
            if (clicked.hasItemMeta()) {
                ItemMeta meta = clicked.getItemMeta();
                NamespacedKey key = new NamespacedKey(manager.getPlugin(), "discovery_id");
                
                // Verificamos si el ítem clickeado tiene un ID oculto
                if (meta.getPersistentDataContainer().has(key, PersistentDataType.STRING)) {
                    String discoveryId = meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
                    
                    String category = null;
                    if (title.contains("Biomas")) category = "BIOME";
                    else if (title.contains("Estructuras")) category = "STRUCTURE";
                    else if (title.contains("Cuevas")) category = "CAVE";
                    else if (title.contains("Mobs")) category = "MOB";
                    
                    if (category != null && discoveryId != null) {
                        manager.claimReward(player, category, discoveryId);
                    }
                }
            }
        }
    }
}