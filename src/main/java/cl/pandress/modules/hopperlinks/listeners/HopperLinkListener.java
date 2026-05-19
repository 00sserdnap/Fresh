package cl.pandress.modules.hopperlinks.listeners;

import cl.pandress.Etherium;
import cl.pandress.modules.hopperlinks.HopperLink;
import cl.pandress.modules.hopperlinks.HopperLinkManager;
import cl.pandress.utils.ChatUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class HopperLinkListener implements Listener {

    private final HopperLinkManager manager;

    public HopperLinkListener(HopperLinkManager manager) {
        this.manager = manager;
    }

    // ==========================================
    // SISTEMA DE PROTECCIÓN DE BLOQUES Y LÍMITES
    // ==========================================

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.isCancelled()) return;
        
        Block block = event.getBlock();
        if (block.getType() == Material.HOPPER) {
            if (!manager.isWorldAllowed(block.getWorld().getName())) return;

            Player player = event.getPlayer();
            int max = manager.getPlayerPlaceLimit(player);
            int current = manager.getPlacedHoppersCount(player.getUniqueId());

            if (current >= max) {
                event.setCancelled(true);
                String msg = manager.getMessages().getString("protection.place-limit-reached").replace("%limit%", String.valueOf(max));
                player.sendMessage(ChatUtils.colorize(manager.getMessages().getString("prefix") + msg));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            } else {
                manager.addPlacedHopper(block.getLocation(), player.getUniqueId());
            }
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.isCancelled()) return;
        handleBlockDestruction(event.getBlock());
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        if (event.isCancelled()) return;
        for (Block block : event.blockList()) {
            handleBlockDestruction(block);
        }
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        if (event.isCancelled()) return;
        for (Block block : event.blockList()) {
            handleBlockDestruction(block);
        }
    }

    private void handleBlockDestruction(Block block) {
        Material type = block.getType();
        if (type == Material.CHEST || type == Material.TRAPPED_CHEST) {
            manager.handleChestBreak(block.getLocation());
        } else if (type == Material.HOPPER) {
            manager.handleHopperBreak(block.getLocation());
        }
    }

    // ==========================================
    // INTERACCIÓN CON LA VARITA
    // ==========================================

    @EventHandler
    public void onWandInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (!manager.isWand(item)) return;
        event.setCancelled(true); 

        String prefix = manager.getMessages().getString("prefix", "&8[&c!&8] ");
        String usePerm = manager.getConfig().getString("settings.use-permission", "eth.hopperlinks.use");

        // NUEVO: VERIFICACIÓN DE PERMISO PARA USAR LA VARITA
        if (!player.hasPermission(usePerm)) {
            player.sendMessage(ChatUtils.colorize(prefix + manager.getMessages().getString("errors.no-use-permission")));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }

        Action action = event.getAction();
        UUID uuid = player.getUniqueId();

        if (player.isSneaking() && (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK)) {
            manager.playerSelections.remove(uuid);
            player.sendMessage(ChatUtils.colorize(prefix + manager.getMessages().getString("selection.cleared")));
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            return;
        }

        if (action == Action.RIGHT_CLICK_AIR) {
            openWandMenu(player); 
            return;
        }

        if (action == Action.RIGHT_CLICK_BLOCK) {
            if (!manager.isWorldAllowed(player.getWorld().getName())) {
                player.sendMessage(ChatUtils.colorize(prefix + manager.getMessages().getString("errors.disabled-world")));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                return;
            }

            Block block = event.getClickedBlock();
            if (block == null) return;

            // 1. SELECCIONAR TOLVAS
            if (block.getType() == Material.HOPPER) {
                if (manager.isHopperLinked(block.getLocation())) {
                    player.sendMessage(ChatUtils.colorize(prefix + manager.getMessages().getString("selection.already-linked")));
                    return;
                }

                List<Location> selected = manager.playerSelections.getOrDefault(uuid, new ArrayList<>());
                
                int max = manager.getPlayerLinkLimit(player);
                int currentUsed = manager.getUsedHoppersCount(uuid);
                if (currentUsed + selected.size() >= max) {
                    player.sendMessage(ChatUtils.colorize(prefix + manager.getMessages().getString("linking.limit-reached").replace("%limit%", String.valueOf(max))));
                    return;
                }

                if (selected.contains(block.getLocation())) {
                    selected.remove(block.getLocation());
                    player.sendMessage(ChatUtils.colorize(prefix + manager.getMessages().getString("selection.hopper-removed")));
                } else {
                    selected.add(block.getLocation());
                    String msg = manager.getMessages().getString("selection.hopper-added").replace("%current%", String.valueOf(selected.size()));
                    player.sendMessage(ChatUtils.colorize(prefix + msg));
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 2f);
                }
                manager.playerSelections.put(uuid, selected);
                return;
            }

            // 2. VINCULAR A COFRE
            if (block.getType() == Material.CHEST || block.getType() == Material.TRAPPED_CHEST) {
                List<Location> selected = manager.playerSelections.getOrDefault(uuid, new ArrayList<>());
                if (selected.isEmpty()) {
                    player.sendMessage(ChatUtils.colorize(prefix + manager.getMessages().getString("linking.no-selection")));
                    return;
                }

                int maxLinks = manager.getPlayerMaxLinksLimit(player);
                int currentLinks = manager.getActiveLinksCount(uuid);
                if (currentLinks >= maxLinks) {
                    String msg = manager.getMessages().getString("linking.max-links-reached").replace("%limit%", String.valueOf(maxLinks));
                    player.sendMessage(ChatUtils.colorize(prefix + msg));
                    manager.playerSelections.remove(uuid); 
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    return;
                }

                double maxDistance = manager.getConfig().getDouble("settings.max-link-distance", 50.0);
                Location chestLoc = block.getLocation();

                for (Location hopperLoc : selected) {
                    if (!hopperLoc.getWorld().equals(chestLoc.getWorld())) {
                        player.sendMessage(ChatUtils.colorize(prefix + manager.getMessages().getString("linking.different-world")));
                        manager.playerSelections.remove(uuid); 
                        return;
                    }
                    if (hopperLoc.distance(chestLoc) > maxDistance) {
                        String msg = manager.getMessages().getString("linking.too-far").replace("%distance%", String.valueOf(maxDistance));
                        player.sendMessage(ChatUtils.colorize(prefix + msg));
                        manager.playerSelections.remove(uuid); 
                        return;
                    }
                }

                manager.createLink(player, chestLoc);
                String msg = manager.getMessages().getString("linking.success").replace("%count%", String.valueOf(selected.size()));
                player.sendMessage(ChatUtils.colorize(prefix + msg));
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                return;
            }

            player.sendMessage(ChatUtils.colorize(prefix + manager.getMessages().getString("selection.invalid-block")));
        }
    }

    // ==========================================
    // SISTEMA DE MENÚS (INVENTARIO)
    // ==========================================
    
    public void openWandMenu(Player player) {
        String title = ChatUtils.colorize(manager.getMenus().getString("wand-menu.title", "Panel de la Varita"));
        int rows = manager.getMenus().getInt("wand-menu.rows", 4) * 9;
        Inventory inv = Bukkit.createInventory(null, rows, title);
        
        ItemStack fillItem = createItem(Material.valueOf(manager.getMenus().getString("wand-menu.fill-item.material", "BLACK_STAINED_GLASS_PANE")), " ", null, null);
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, fillItem);

        String pathRev = "wand-menu.review-item.";
        Material revMat = Material.valueOf(manager.getMenus().getString(pathRev + "material", "HOPPER"));
        String revName = manager.getMenus().getString(pathRev + "name");
        List<String> revLore = manager.getMenus().getStringList(pathRev + "lore").stream()
                .map(l -> l.replace("%active_links%", String.valueOf(manager.getActiveLinksCount(player.getUniqueId()))))
                .toList();
        inv.setItem(manager.getMenus().getInt(pathRev + "slot", 11), createItem(revMat, revName, revLore, "OPEN_LIST"));

        int currentLevel = manager.getPlayerWandLevel(player.getUniqueId());
        int nextLevel = currentLevel + 1;
        
        if (manager.getConfig().contains("upgrades.level_" + nextLevel)) {
            String pathUp = "wand-menu.upgrade-item.";
            Material upMat = Material.valueOf(manager.getMenus().getString(pathUp + "material", "GOLD_INGOT"));
            String upName = manager.getMenus().getString(pathUp + "name");
            int cost = manager.getConfig().getInt("upgrades.level_" + nextLevel + ".cost");
            List<String> upLore = manager.getMenus().getStringList(pathUp + "lore").stream()
                    .map(l -> l.replace("%current_level%", String.valueOf(currentLevel))
                               .replace("%next_level%", String.valueOf(nextLevel))
                               .replace("%cost%", String.valueOf(cost)))
                    .toList();
            inv.setItem(manager.getMenus().getInt(pathUp + "slot", 15), createItem(upMat, upName, upLore, "GLOBAL_UPGRADE"));
        } else {
            String pathMax = "wand-menu.max-upgrade-item.";
            Material maxMat = Material.valueOf(manager.getMenus().getString(pathMax + "material", "NETHER_STAR"));
            String maxName = manager.getMenus().getString(pathMax + "name");
            List<String> maxLore = manager.getMenus().getStringList(pathMax + "lore");
            inv.setItem(manager.getMenus().getInt(pathMax + "slot", 15), createItem(maxMat, maxName, maxLore, "MAX"));
        }

        player.openInventory(inv);
    }

    public void openLinksListMenu(Player player) {
        String title = ChatUtils.colorize(manager.getMenus().getString("list-menu.title", "Tus Vínculos"));
        int rows = manager.getMenus().getInt("list-menu.rows", 6) * 9;
        Inventory inv = Bukkit.createInventory(null, rows, title);
        
        ItemStack fillItem = createItem(Material.valueOf(manager.getMenus().getString("list-menu.fill-item.material", "GRAY_STAINED_GLASS_PANE")), " ", null, null);
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, fillItem);

        int slot = 10;
        int currentLevel = manager.getPlayerWandLevel(player.getUniqueId());
        
        for (HopperLink link : manager.getActiveLinks().values()) {
            if (!link.getOwner().equals(player.getUniqueId())) continue;
            
            List<String> lore = new ArrayList<>();
            lore.add("&7Tolvas: &f" + link.getHoppers().size());
            lore.add("&7Velocidad actual: &eNivel " + currentLevel);
            lore.add("");
            lore.add("&a► Click para gestionar");

            inv.setItem(slot, createItem(Material.HOPPER, "&6Vínculo ID: " + link.getId(), lore, "OPEN_LINK;" + link.getId()));
            slot++;
            if (slot % 9 == 8) slot += 2; 
        }
        player.openInventory(inv);
    }

    public void openLinkActionMenu(Player player, String linkId) {
        HopperLink link = manager.getLink(linkId);
        if (link == null) return;

        String title = ChatUtils.colorize(manager.getMenus().getString("action-menu.title", "Gestión"));
        int rows = manager.getMenus().getInt("action-menu.rows", 3) * 9;
        Inventory inv = Bukkit.createInventory(null, rows, title);

        ItemStack fillItem = createItem(Material.valueOf(manager.getMenus().getString("action-menu.fill-item.material", "BLACK_STAINED_GLASS_PANE")), " ", null, null);
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, fillItem);

        String pathInfo = "action-menu.info-item.";
        Material infoMat = Material.valueOf(manager.getMenus().getString(pathInfo + "material", "HOPPER"));
        String infoName = manager.getMenus().getString(pathInfo + "name");
        int pLevel = manager.getPlayerWandLevel(player.getUniqueId());
        int speed = manager.getConfig().getInt("upgrades.level_" + pLevel + ".items-per-transfer", 1);
        List<String> infoLore = manager.getMenus().getStringList(pathInfo + "lore").stream()
                .map(l -> l.replace("%count%", String.valueOf(link.getHoppers().size()))
                           .replace("%speed%", String.valueOf(speed)))
                .toList();
        inv.setItem(manager.getMenus().getInt(pathInfo + "slot", 11), createItem(infoMat, infoName, infoLore, "INFO"));

        String pathPart = "action-menu.particles-item.";
        inv.setItem(manager.getMenus().getInt(pathPart + "slot", 13), 
                createItem(Material.valueOf(manager.getMenus().getString(pathPart + "material")), 
                manager.getMenus().getString(pathPart + "name"), 
                manager.getMenus().getStringList(pathPart + "lore"), "PARTICLES;" + linkId));

        String pathDel = "action-menu.delete-item.";
        inv.setItem(manager.getMenus().getInt(pathDel + "slot", 15), 
                createItem(Material.valueOf(manager.getMenus().getString(pathDel + "material")), 
                manager.getMenus().getString(pathDel + "name"), 
                manager.getMenus().getStringList(pathDel + "lore"), "DELETE;" + linkId));

        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = ChatColor.stripColor(event.getView().getTitle());
        String wandMenuTitle = ChatColor.stripColor(ChatUtils.colorize(manager.getMenus().getString("wand-menu.title")));
        String listMenuTitle = ChatColor.stripColor(ChatUtils.colorize(manager.getMenus().getString("list-menu.title")));
        String actionMenuTitle = ChatColor.stripColor(ChatUtils.colorize(manager.getMenus().getString("action-menu.title")));

        if (title == null || (!title.equals(wandMenuTitle) && !title.equals(listMenuTitle) && !title.equals(actionMenuTitle))) return;
        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        
        Player player = (Player) event.getWhoClicked();
        NamespacedKey key = new NamespacedKey(Etherium.getInstance(), "hopperlink_action");
        if (!clicked.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.STRING)) return;

        String actionData = clicked.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
        String prefix = manager.getMessages().getString("prefix", "&8[&c!&8] ");

        if (title.equals(wandMenuTitle)) {
            if (actionData.equals("OPEN_LIST")) {
                openLinksListMenu(player);
            } 
            else if (actionData.equals("GLOBAL_UPGRADE")) {
                int currentLevel = manager.getPlayerWandLevel(player.getUniqueId());
                int nextLevel = currentLevel + 1;
                int cost = manager.getConfig().getInt("upgrades.level_" + nextLevel + ".cost");
                
                if (Etherium.getInstance().getEconomy().getBalance(player) >= cost) {
                    Etherium.getInstance().getEconomy().withdrawPlayer(player, cost);
                    manager.setPlayerWandLevel(player.getUniqueId(), nextLevel);
                    manager.saveData();
                    openWandMenu(player); 
                    
                    String msg = manager.getMessages().getString("menu.upgraded")
                            .replace("%level%", String.valueOf(nextLevel))
                            .replace("%cost%", String.valueOf(cost));
                    player.sendMessage(ChatUtils.colorize(prefix + msg));
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                } else {
                    String msg = manager.getMessages().getString("menu.no-money").replace("%cost%", String.valueOf(cost));
                    player.sendMessage(ChatUtils.colorize(prefix + msg));
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                }
            }
            return;
        }

        if (title.equals(listMenuTitle)) {
            String[] data = actionData.split(";");
            if (data[0].equals("OPEN_LINK") && data.length == 2) {
                openLinkActionMenu(player, data[1]);
            }
            return;
        }

        if (title.equals(actionMenuTitle)) {
            String[] data = actionData.split(";");
            if (data.length < 2) return;
            String action = data[0];
            String linkId = data[1];

            HopperLink link = manager.getLink(linkId);
            if (link == null) { player.closeInventory(); return; }

            if (action.equals("PARTICLES")) {
                player.closeInventory();
                manager.spawnLaserParticles(player, link);
                player.sendMessage(ChatUtils.colorize(prefix + manager.getMessages().getString("menu.showing-particles")));
            } 
            else if (action.equals("DELETE")) {
                manager.removeLink(linkId);
                openLinksListMenu(player); 
                player.sendMessage(ChatUtils.colorize(prefix + manager.getMessages().getString("menu.deleted")));
                player.playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 1f, 1f);
            }
        }
    }

    private ItemStack createItem(Material mat, String name, List<String> lore, String hiddenData) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatUtils.colorize(name));
        if (lore != null) meta.setLore(lore.stream().map(ChatUtils::colorize).collect(Collectors.toList()));
        if (hiddenData != null) {
            meta.getPersistentDataContainer().set(new NamespacedKey(Etherium.getInstance(), "hopperlink_action"), PersistentDataType.STRING, hiddenData);
        }
        item.setItemMeta(meta);
        return item;
    }
}