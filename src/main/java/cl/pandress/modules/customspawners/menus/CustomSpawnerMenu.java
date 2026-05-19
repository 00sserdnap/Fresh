package cl.pandress.modules.customspawners.menus;

import cl.pandress.modules.customspawners.data.CustomSpawnerData;
import cl.pandress.modules.customspawners.CustomSpawnerManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class CustomSpawnerMenu implements Listener {

    private final CustomSpawnerManager manager;

    public CustomSpawnerMenu(CustomSpawnerManager manager) {
        this.manager = manager;
    }

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    private void setAction(ItemMeta meta, String action) {
        meta.getPersistentDataContainer().set(manager.keyAction, PersistentDataType.STRING, action);
    }

    public void openMainMenu(Player player) {
        FileConfiguration cfg = manager.getMenuMain();
        Inventory inv = Bukkit.createInventory(null, cfg.getInt("size", 27), color(cfg.getString("title")));

        // Obtener Spawners
        ItemStack giveItem = new ItemStack(Material.valueOf(cfg.getString("items.give.material", "ZOMBIE_SPAWN_EGG")));
        ItemMeta giveMeta = giveItem.getItemMeta();
        giveMeta.setDisplayName(color(cfg.getString("items.give.name")));
        giveMeta.setLore(cfg.getStringList("items.give.lore").stream().map(this::color).collect(Collectors.toList()));
        setAction(giveMeta, "OPEN_GIVE");
        giveItem.setItemMeta(giveMeta);
        inv.setItem(cfg.getInt("items.give.slot", 11), giveItem);

        // Estadísticas
        ItemStack statsItem = new ItemStack(Material.valueOf(cfg.getString("items.stats.material", "PAPER")));
        ItemMeta statsMeta = statsItem.getItemMeta();
        statsMeta.setDisplayName(color(cfg.getString("items.stats.name")));
        List<String> statsLore = new ArrayList<>();
        for (String line : cfg.getStringList("items.stats.lore")) {
            statsLore.add(color(line.replace("{total}", String.valueOf(manager.getActiveSpawners().size()))));
        }
        statsMeta.setLore(statsLore);
        setAction(statsMeta, "NONE");
        statsItem.setItemMeta(statsMeta);
        inv.setItem(cfg.getInt("items.stats.slot", 13), statsItem);

        // Gestionar
        ItemStack listItem = new ItemStack(Material.valueOf(cfg.getString("items.list.material", "ENDER_EYE")));
        ItemMeta listMeta = listItem.getItemMeta();
        listMeta.setDisplayName(color(cfg.getString("items.list.name")));
        listMeta.setLore(cfg.getStringList("items.list.lore").stream().map(this::color).collect(Collectors.toList()));
        setAction(listMeta, "OPEN_LIST");
        listItem.setItemMeta(listMeta);
        inv.setItem(cfg.getInt("items.list.slot", 15), listItem);

        // Fondo
        ItemStack bg = new ItemStack(Material.valueOf(cfg.getString("background.material", "BLACK_STAINED_GLASS_PANE")));
        ItemMeta bgMeta = bg.getItemMeta();
        bgMeta.setDisplayName(" ");
        setAction(bgMeta, "NONE");
        bg.setItemMeta(bgMeta);
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) inv.setItem(i, bg);
        }

        player.openInventory(inv);
    }

    public void openGiveMenu(Player player) {
        FileConfiguration cfg = manager.getMenuGive();
        Inventory inv = Bukkit.createInventory(null, cfg.getInt("size", 54), color(cfg.getString("title")));

        int slot = 0;
        for (EntityType type : EntityType.values()) {
            if (!type.isAlive() || !type.isSpawnable()) continue;
            if (slot >= 45) break; 
            
            Material mat = Material.getMaterial(type.name() + "_SPAWN_EGG");
            if (mat == null) mat = Material.SPAWNER;

            ItemStack item = new ItemStack(mat);
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(color(cfg.getString("item-format.name").replace("{type}", type.name())));
            
            List<String> lore = new ArrayList<>();
            for (String line : cfg.getStringList("item-format.lore")) {
                lore.add(color(line.replace("{type}", type.name())));
            }
            meta.setLore(lore);
            setAction(meta, "GIVE_SPAWNER_" + type.name());
            item.setItemMeta(meta);
            
            inv.setItem(slot, item);
            slot++;
        }

        ItemStack back = new ItemStack(Material.valueOf(cfg.getString("back-button.material", "BARRIER")));
        ItemMeta backMeta = back.getItemMeta();
        backMeta.setDisplayName(color(cfg.getString("back-button.name")));
        setAction(backMeta, "BACK_MAIN");
        back.setItemMeta(backMeta);
        inv.setItem(cfg.getInt("back-button.slot", 49), back);

        player.openInventory(inv);
    }

    public void openPlayerListMenu(Player admin, int page) {
        FileConfiguration cfg = manager.getMenuPlayerList();
        String title = color(cfg.getString("title").replace("{page}", String.valueOf(page)));
        Inventory inv = Bukkit.createInventory(null, cfg.getInt("size", 54), title);

        Map<UUID, List<CustomSpawnerData>> spawnersByOwner = manager.getActiveSpawners().values().stream()
                .filter(data -> data.getOwnerId() != null)
                .collect(Collectors.groupingBy(CustomSpawnerData::getOwnerId));

        List<UUID> owners = new ArrayList<>(spawnersByOwner.keySet());
        owners.sort((u1, u2) -> spawnersByOwner.get(u1).get(0).getOwnerName().compareToIgnoreCase(spawnersByOwner.get(u2).get(0).getOwnerName()));

        int itemsPerPage = 45;
        int startIndex = page * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, owners.size());

        int slot = 0;
        for (int i = startIndex; i < endIndex; i++) {
            UUID ownerUUID = owners.get(i);
            List<CustomSpawnerData> playerSpawners = spawnersByOwner.get(ownerUUID);
            String ownerName = playerSpawners.get(0).getOwnerName();

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(ownerUUID));
            meta.setDisplayName(color(cfg.getString("head-format.name").replace("{player}", ownerName)));

            Set<String> chunks = new HashSet<>();
            for (CustomSpawnerData sd : playerSpawners) {
                int cX = sd.getLocation().getBlockX() >> 4;
                int cZ = sd.getLocation().getBlockZ() >> 4;
                chunks.add(sd.getLocation().getWorld().getName() + ":" + cX + ":" + cZ);
            }

            List<String> lore = new ArrayList<>();
            for (String line : cfg.getStringList("head-format.lore")) {
                lore.add(color(line.replace("{uuid}", ownerUUID.toString())
                                   .replace("{total}", String.valueOf(playerSpawners.size()))
                                   .replace("{chunks}", String.valueOf(chunks.size()))));
            }
            meta.setLore(lore);
            
            // Datos Ocultos PDC
            setAction(meta, "OPEN_PLAYER_SPAWNERS");
            meta.getPersistentDataContainer().set(manager.keyOwner, PersistentDataType.STRING, ownerUUID.toString());
            meta.getPersistentDataContainer().set(manager.keyAction, PersistentDataType.STRING, ownerName); // Reusamos key temporalmente para el nombre
            
            head.setItemMeta(meta);
            inv.setItem(slot, head);
            slot++;
        }

        addNavigation(inv, cfg, page, owners.size(), itemsPerPage, "BACK_MAIN", "PAGE_LIST_");
        admin.openInventory(inv);
    }

    public void openPlayerSpawnersMenu(Player admin, UUID ownerUUID, String ownerName, int page) {
        FileConfiguration cfg = manager.getMenuPlayerSpawners();
        String title = color(cfg.getString("title").replace("{player}", ownerName).replace("{page}", String.valueOf(page)));
        Inventory inv = Bukkit.createInventory(null, cfg.getInt("size", 54), title);

        List<CustomSpawnerData> playerSpawners = manager.getActiveSpawners().values().stream()
                .filter(data -> Objects.equals(data.getOwnerId(), ownerUUID))
                .collect(Collectors.toList());

        int itemsPerPage = 45;
        int startIndex = page * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, playerSpawners.size());

        int slot = 0;
        for (int i = startIndex; i < endIndex; i++) {
            CustomSpawnerData data = playerSpawners.get(i);
            
            ItemStack item = new ItemStack(Material.valueOf(cfg.getString("spawner-format.material", "SPAWNER")));
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(color(cfg.getString("spawner-format.name").replace("{type}", data.getEntityType().name())));
            
            String worldName = data.getLocation().getWorld().getName();
            String worldColor = worldName.endsWith("_nether") ? "&c" : worldName.endsWith("_the_end") ? "&d" : "&a";

            List<String> lore = new ArrayList<>();
            for (String line : cfg.getStringList("spawner-format.lore")) {
                lore.add(color(line.replace("{player}", data.getOwnerName())
                                   .replace("{world_color}", worldColor)
                                   .replace("{world}", worldName)
                                   .replace("{x}", String.valueOf(data.getLocation().getBlockX()))
                                   .replace("{y}", String.valueOf(data.getLocation().getBlockY()))
                                   .replace("{z}", String.valueOf(data.getLocation().getBlockZ()))));
            }
            meta.setLore(lore);
            
            // Datos Ocultos PDC
            setAction(meta, "MANAGE_SPAWNER");
            String locStr = worldName + "," + data.getLocation().getBlockX() + "," + data.getLocation().getBlockY() + "," + data.getLocation().getBlockZ();
            meta.getPersistentDataContainer().set(manager.keyLoc, PersistentDataType.STRING, locStr);
            meta.getPersistentDataContainer().set(manager.keyOwner, PersistentDataType.STRING, ownerUUID.toString());
            
            item.setItemMeta(meta);
            inv.setItem(slot, item);
            slot++;
        }

        // Usamos key de UUID para pasar la info a la paginación
        addNavigation(inv, cfg, page, playerSpawners.size(), itemsPerPage, "BACK_LIST", "PAGE_SPAWNERS_" + ownerUUID.toString() + "_" + ownerName + "_");
        admin.openInventory(inv);
    }

    private void addNavigation(Inventory inv, FileConfiguration cfg, int page, int totalItems, int itemsPerPage, String backAction, String pageActionPrefix) {
        ItemStack back = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.setDisplayName(color(cfg.getString("navigation.back.name")));
        setAction(backMeta, backAction);
        back.setItemMeta(backMeta);
        inv.setItem(49, back);

        if (page > 0) {
            ItemStack prev = new ItemStack(Material.ARROW);
            ItemMeta prevMeta = prev.getItemMeta();
            prevMeta.setDisplayName(color(cfg.getString("navigation.prev.name").replace("{page}", String.valueOf(page))));
            setAction(prevMeta, pageActionPrefix + (page - 1));
            prev.setItemMeta(prevMeta);
            inv.setItem(45, prev);
        }

        if (totalItems > (page + 1) * itemsPerPage) {
            ItemStack next = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = next.getItemMeta();
            nextMeta.setDisplayName(color(cfg.getString("navigation.next.name").replace("{page}", String.valueOf(page + 2))));
            
            List<String> lore = new ArrayList<>();
            for (String line : cfg.getStringList("navigation.next.lore")) {
                lore.add(color(line.replace("{left}", String.valueOf(totalItems - ((page+1)*itemsPerPage)))));
            }
            nextMeta.setLore(lore);
            setAction(nextMeta, pageActionPrefix + (page + 1));
            next.setItemMeta(nextMeta);
            inv.setItem(53, next);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getCurrentItem() == null || !event.getCurrentItem().hasItemMeta()) return;
        ItemMeta meta = event.getCurrentItem().getItemMeta();
        
        // Verificamos si este ítem tiene nuestro tag de acción invisible
        if (!meta.getPersistentDataContainer().has(manager.keyAction, PersistentDataType.STRING)) return;
        
        event.setCancelled(true); // Siempre cancelar si es nuestro menú
        String action = meta.getPersistentDataContainer().get(manager.keyAction, PersistentDataType.STRING);
        Player player = (Player) event.getWhoClicked();

        if (action.equals("NONE")) return;
        
        if (action.equals("BACK_MAIN")) { openMainMenu(player); return; }
        if (action.equals("BACK_LIST")) { openPlayerListMenu(player, 0); return; }
        if (action.equals("OPEN_GIVE")) { openGiveMenu(player); return; }
        if (action.equals("OPEN_LIST")) { openPlayerListMenu(player, 0); return; }

        if (action.startsWith("GIVE_SPAWNER_")) {
            String typeName = action.replace("GIVE_SPAWNER_", "");
            try {
                EntityType type = EntityType.valueOf(typeName);
                player.getInventory().addItem(manager.createSpawnerItem(type));
                String msg = manager.getMessage("receive").replace("{amount}", "1").replace("{type}", type.name());
                player.sendMessage(msg);
            } catch (Exception ignored) {}
            return;
        }

        if (action.equals("OPEN_PLAYER_SPAWNERS")) {
            String uuidStr = meta.getPersistentDataContainer().get(manager.keyOwner, PersistentDataType.STRING);
            String ownerName = action; // Usamos el nombre del meta temporalmente
            if (meta.getDisplayName() != null) ownerName = ChatColor.stripColor(meta.getDisplayName()).replace("► ", "");
            openPlayerSpawnersMenu(player, UUID.fromString(uuidStr), ownerName, 0);
            return;
        }

        if (action.startsWith("PAGE_LIST_")) {
            int page = Integer.parseInt(action.replace("PAGE_LIST_", ""));
            openPlayerListMenu(player, page);
            return;
        }

        if (action.startsWith("PAGE_SPAWNERS_")) {
            // Estructura: PAGE_SPAWNERS_{UUID}_{Name}_{Page}
            String[] parts = action.replace("PAGE_SPAWNERS_", "").split("_");
            UUID uuid = UUID.fromString(parts[0]);
            String name = parts[1];
            int page = Integer.parseInt(parts[2]);
            openPlayerSpawnersMenu(player, uuid, name, page);
            return;
        }

        if (action.equals("MANAGE_SPAWNER")) {
            String locStr = meta.getPersistentDataContainer().get(manager.keyLoc, PersistentDataType.STRING);
            String uuidStr = meta.getPersistentDataContainer().get(manager.keyOwner, PersistentDataType.STRING);
            if (locStr == null || uuidStr == null) return;
            
            String[] locParts = locStr.split(",");
            try {
                Location blockLoc = new Location(Bukkit.getWorld(locParts[0]), Integer.parseInt(locParts[1]), Integer.parseInt(locParts[2]), Integer.parseInt(locParts[3]));
                
                if (event.getClick() == ClickType.LEFT) {
                    player.teleport(blockLoc.clone().add(0.5, 1, 0.5));
                    player.sendMessage(manager.getMessage("tp-success"));
                    player.closeInventory();
                } 
                else if (event.getClick() == ClickType.RIGHT) {
                    manager.removeSpawner(blockLoc);
                    if (blockLoc.getBlock().getType() == Material.SPAWNER) {
                        blockLoc.getBlock().setType(Material.AIR);
                    }
                    player.sendMessage(manager.getMessage("removed-success"));
                    
                    // Recargar menú
                    CustomSpawnerData ref = manager.getActiveSpawners().values().stream().filter(d -> d.getOwnerId().toString().equals(uuidStr)).findFirst().orElse(null);
                    if (ref != null) {
                        openPlayerSpawnersMenu(player, UUID.fromString(uuidStr), ref.getOwnerName(), 0);
                    } else {
                        openPlayerListMenu(player, 0); // Si ya no tiene spawners, lo devolvemos a la lista
                    }
                }
            } catch (Exception e) {
                player.sendMessage(manager.getMessage("error-loc"));
            }
        }
    }
}