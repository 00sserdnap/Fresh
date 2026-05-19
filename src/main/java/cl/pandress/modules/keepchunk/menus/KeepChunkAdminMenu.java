package cl.pandress.modules.keepchunk.menus;

import cl.pandress.modules.keepchunk.KeepChunkManager;
import cl.pandress.modules.keepchunk.KeepChunkType;
import cl.pandress.modules.keepchunk.data.KeepChunkData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class KeepChunkAdminMenu implements Listener {
    private final KeepChunkManager manager;

    public KeepChunkAdminMenu(KeepChunkManager manager) {
        this.manager = manager;
    }

    public static class AdminHolder implements InventoryHolder {
        public String menuType;
        public UUID targetPlayer;
        public String giveId;
        public boolean isCore;

        public AdminHolder(String type) {
            this.menuType = type;
        }
        @Override public Inventory getInventory() { return null; }
    }

    // 1. MENÚ PRINCIPAL (Basado en la imagen de CustomSpawners)
    public static void openMainMenu(Player admin, KeepChunkManager manager) {
        FileConfiguration cfg = manager.getAdminMenuCfg();
        String title = ChatColor.translateAlternateColorCodes('&', cfg.getString("title", "&8Menú de Cargadores"));
        int size = cfg.getInt("size", 27);
        Inventory inv = Bukkit.createInventory(new AdminHolder("MAIN"), size, title);

        // Borde decorativo inferior izquierdo (como en tu imagen)
        Material borderMat = Material.valueOf(cfg.getString("border.material", "GRAY_STAINED_GLASS_PANE").toUpperCase());
        ItemStack border = new ItemStack(borderMat);
        ItemMeta borderMeta = border.getItemMeta();
        borderMeta.setDisplayName(" ");
        border.setItemMeta(borderMeta);

        // Llenar bordes superior e inferior para que se vea limpio
        for(int i = 0; i < 9; i++) inv.setItem(i, border);
        for(int i = 18; i < 27; i++) inv.setItem(i, border);

        // Calcular estadísticas
        int totalLoaders = manager.getActiveLoaders().size();
        int totalChunks = 0;
        for (KeepChunkData data : manager.getActiveLoaders().values()) {
             if (data.isActive()) {
                 KeepChunkType type = manager.getType(data.getTypeId());
                 if(type != null) {
                     int side = (type.getRadius() * 2) + 1;
                     totalChunks += (side * side);
                 }
             }
        }

        // Ítems centrales
        if (cfg.contains("items.get_loaders")) {
            inv.setItem(cfg.getInt("items.get_loaders.slot", 11), buildItemFromConfig(cfg, "items.get_loaders", "", ""));
        }
        if (cfg.contains("items.stats")) {
            inv.setItem(cfg.getInt("items.stats.slot", 13), buildItemFromConfig(cfg, "items.stats", String.valueOf(totalLoaders), String.valueOf(totalChunks)));
        }
        if (cfg.contains("items.players")) {
            inv.setItem(cfg.getInt("items.players.slot", 15), buildItemFromConfig(cfg, "items.players", "", ""));
        }

        admin.openInventory(inv);
    }

    private static ItemStack buildItemFromConfig(FileConfiguration cfg, String path, String total, String chunks) {
        Material mat = Material.valueOf(cfg.getString(path + ".material", "STONE").toUpperCase());
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', cfg.getString(path + ".name", " ")));

        List<String> rawLore = cfg.getStringList(path + ".lore");
        List<String> lore = new ArrayList<>();
        for (String line : rawLore) {
            lore.add(ChatColor.translateAlternateColorCodes('&', line.replace("{total}", total).replace("{chunks}", chunks)));
        }
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    // 2. MENÚ PARA OBTENER ÍTEMS (Similar al de Obtener Spawners)
    public static void openGiveMenu(Player admin, KeepChunkManager manager) {
        Inventory inv = Bukkit.createInventory(new AdminHolder("GIVE"), 54, "§8Obtener Cargadores");

        int slot = 0;
        // Añadir todos los tipos de cargadores
        for (String typeId : manager.getLoadedTypes().keySet()) {
            if (slot >= 53) break;
            KeepChunkType type = manager.getType(typeId);
            ItemStack item = manager.createLoaderItem(typeId, type.getMaxFuel(), null, null, false);

            ItemMeta meta = item.getItemMeta();
            List<String> lore = new ArrayList<>();
            lore.add("§aClick para darte 1x " + typeId.toUpperCase());
            meta.setLore(lore);
            item.setItemMeta(meta);

            inv.setItem(slot++, item);
        }

        // Añadir núcleos de energía si existen
        if (manager.getConfig().contains("cores")) {
            for (String coreId : manager.getConfig().getConfigurationSection("cores").getKeys(false)) {
                if (slot >= 53) break;
                ItemStack core = manager.createCore(coreId);
                if (core != null) {
                    ItemMeta meta = core.getItemMeta();
                    List<String> lore = new ArrayList<>();
                    lore.add("§aClick para darte 1x " + coreId.toUpperCase());
                    meta.setLore(lore);
                    core.setItemMeta(meta);
                    inv.setItem(slot++, core);
                }
            }
        }

        admin.openInventory(inv);
    }

    // 3. MENÚ DE LISTA DE DUEÑOS (Formato exacto de la imagen)
    public static void openPlayersMenu(Player admin, KeepChunkManager manager) {
        Inventory inv = Bukkit.createInventory(new AdminHolder("PLAYERS"), 54, "§8Lista de Dueños - Pag 0");

        List<UUID> owners = new ArrayList<>();
        for (KeepChunkData data : manager.getActiveLoaders().values()) {
            if (!owners.contains(data.getOwner())) owners.add(data.getOwner());
        }

        int slot = 0;
        for (UUID uuid : owners) {
            if (slot >= 53) break;
            OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);

            int playerLoaders = 0;
            int playerChunks = 0;

            for (KeepChunkData data : manager.getActiveLoaders().values()) {
                if (data.getOwner().equals(uuid)) {
                    playerLoaders++;
                    if(data.isActive()) {
                        KeepChunkType type = manager.getType(data.getTypeId());
                        if(type != null) {
                            int side = (type.getRadius() * 2) + 1;
                            playerChunks += (side * side);
                        }
                    }
                }
            }

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(op);

            String pName = op.getName() != null ? op.getName() : "Desconocido";
            meta.setDisplayName("§e▶ " + pName);

            List<String> lore = new ArrayList<>();
            lore.add("§7UUID: " + uuid.toString());
            lore.add("");
            lore.add("§7Cargadores Totales: §b" + playerLoaders);
            lore.add("§7Chunks Ocupados: §b" + playerChunks);
            lore.add("");
            lore.add("§aClick para ver detalles.");

            meta.setLore(lore);
            head.setItemMeta(meta);
            inv.setItem(slot++, head);
        }
        admin.openInventory(inv);
    }

    // 4. DETALLES DE CARGADORES DEL JUGADOR
    public static void openPlayerLoaders(Player admin, KeepChunkManager manager, UUID target) {
        AdminHolder holder = new AdminHolder("PLAYER_LOADERS");
        holder.targetPlayer = target;
        String pName = Bukkit.getOfflinePlayer(target).getName();
        Inventory inv = Bukkit.createInventory(holder, 54, "§8Cargadores de " + (pName != null ? pName : "Desconocido"));

        int slot = 0;
        for (KeepChunkData data : manager.getActiveLoaders().values()) {
            if (data.getOwner().equals(target) && slot < 53) {
                ItemStack item = new ItemStack(data.isActive() ? Material.LIME_STAINED_GLASS : Material.RED_STAINED_GLASS);
                ItemMeta meta = item.getItemMeta();
                meta.setDisplayName("§a" + data.getCustomName() + (data.isTemporary() ? " §c(Temporal)" : ""));

                List<String> lore = new ArrayList<>();
                lore.add("§7ID: §8" + data.getId().toString());
                lore.add("§7Mundo: §f" + (data.getLocation().getWorld() != null ? data.getLocation().getWorld().getName() : "Desconocido"));
                lore.add("§7Ubicación: §fX:" + data.getLocation().getBlockX() + " Y:" + data.getLocation().getBlockY() + " Z:" + data.getLocation().getBlockZ());
                lore.add("§7Energía: §b" + data.getFuel() + " min");
                lore.add("");
                lore.add("§e▶ Clic Izq: §fTeletransportarse al Cargador");
                lore.add("§c▶ Clic Der: §fEliminar Cargador del Servidor");

                meta.setLore(lore);
                item.setItemMeta(meta);
                inv.setItem(slot++, item);
            }
        }
        admin.openInventory(inv);
    }

    // GESTIÓN DE CLICKS
    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof AdminHolder)) return;
        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        AdminHolder holder = (AdminHolder) event.getInventory().getHolder();
        ItemStack clicked = event.getCurrentItem();

        if (clicked == null || !clicked.hasItemMeta()) return;

        FileConfiguration cfg = manager.getAdminMenuCfg();

        // ACCIONES DEL MENÚ PRINCIPAL
        if (holder.menuType.equals("MAIN")) {
            if (event.getSlot() == cfg.getInt("items.get_loaders.slot", 11)) {
                openGiveMenu(player, manager);
            }
            if (event.getSlot() == cfg.getInt("items.players.slot", 15)) {
                openPlayersMenu(player, manager);
            }
        }

        // ACCIONES DEL MENÚ DE OBTENER ÍTEMS
        else if (holder.menuType.equals("GIVE")) {
            if (clicked.getType() == Material.VILLAGER_SPAWN_EGG) {
                String typeId = clicked.getItemMeta().getPersistentDataContainer().get(manager.getChunkIdKey(), org.bukkit.persistence.PersistentDataType.STRING);
                if(typeId != null) {
                    player.performCommand("ethloadchunk give " + player.getName() + " " + typeId + " 1");
                }
            } else if (clicked.getType() == Material.NETHER_STAR) {
                String coreId = clicked.getItemMeta().getPersistentDataContainer().get(manager.getCoreKey(), org.bukkit.persistence.PersistentDataType.STRING);
                if(coreId != null) {
                    player.performCommand("ethloadchunk recharge " + player.getName() + " " + coreId + " 1");
                }
            }
        }

        // ACCIONES DE LA LISTA DE JUGADORES
        else if (holder.menuType.equals("PLAYERS")) {
            if (clicked.getType() == Material.PLAYER_HEAD) {
                SkullMeta meta = (SkullMeta) clicked.getItemMeta();
                if (meta.getOwningPlayer() != null) {
                    openPlayerLoaders(player, manager, meta.getOwningPlayer().getUniqueId());
                }
            }
        }

        // ACCIONES DE DETALLES DEL CARGADOR (TP / ELIMINAR)
        else if (holder.menuType.equals("PLAYER_LOADERS")) {
            String idStr = ChatColor.stripColor(clicked.getItemMeta().getLore().get(0)).replace("ID: ", "");
            UUID loaderId = UUID.fromString(idStr);
            KeepChunkData data = manager.getLoader(loaderId);
            
            if (data != null) {
                if (event.isLeftClick()) { 
                    player.teleport(data.getLocation());
                    player.sendMessage("§aTeletransportado a las coordenadas del cargador.");
                    player.closeInventory();
                } else if (event.isRightClick()) { 
                    if(data.getLocation().getWorld() != null) {
                        for(org.bukkit.entity.Entity e : data.getLocation().getWorld().getNearbyEntities(data.getLocation(), 1, 2, 1)) {
                             if(e instanceof org.bukkit.entity.Villager && e.getPersistentDataContainer().has(manager.getChunkIdKey(), org.bukkit.persistence.PersistentDataType.STRING)) {
                                 e.remove();
                             }
                        }
                    }
                    manager.removeLoader(loaderId);
                    player.sendMessage("§cCargador eliminado administrativamente del mundo.");
                    openPlayerLoaders(player, manager, holder.targetPlayer); 
                }
            }
        }
    }
}