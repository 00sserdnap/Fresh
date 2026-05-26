//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package cl.pandress.modules.customspawners.menus;

import cl.pandress.modules.customspawners.CustomSpawnerManager;
import cl.pandress.modules.customspawners.data.CustomSpawnerData;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
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
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

public class CustomSpawnerMenu implements Listener {
    private final CustomSpawnerManager manager;

    public CustomSpawnerMenu(CustomSpawnerManager manager) {
        this.manager = manager;
    }

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    private void setAction(ItemMeta meta, String action) {
        meta.getPersistentDataContainer().set(this.manager.keyAction, PersistentDataType.STRING, action);
    }

    public void openMainMenu(Player player) {
        FileConfiguration cfg = this.manager.getMenuMain();
        Inventory inv = Bukkit.createInventory((InventoryHolder)null, cfg.getInt("size", 27), this.color(cfg.getString("title")));
        ItemStack giveItem = new ItemStack(Material.valueOf(cfg.getString("items.give.material", "ZOMBIE_SPAWN_EGG")));
        ItemMeta giveMeta = giveItem.getItemMeta();
        giveMeta.setDisplayName(this.color(cfg.getString("items.give.name")));
        giveMeta.setLore((List)cfg.getStringList("items.give.lore").stream().map(this::color).collect(Collectors.toList()));
        this.setAction(giveMeta, "OPEN_GIVE");
        giveItem.setItemMeta(giveMeta);
        inv.setItem(cfg.getInt("items.give.slot", 11), giveItem);
        ItemStack statsItem = new ItemStack(Material.valueOf(cfg.getString("items.stats.material", "PAPER")));
        ItemMeta statsMeta = statsItem.getItemMeta();
        statsMeta.setDisplayName(this.color(cfg.getString("items.stats.name")));
        List<String> statsLore = new ArrayList();

        for(String line : cfg.getStringList("items.stats.lore")) {
            statsLore.add(this.color(line.replace("{total}", String.valueOf(this.manager.getActiveSpawners().size()))));
        }

        statsMeta.setLore(statsLore);
        this.setAction(statsMeta, "NONE");
        statsItem.setItemMeta(statsMeta);
        inv.setItem(cfg.getInt("items.stats.slot", 13), statsItem);
        ItemStack listItem = new ItemStack(Material.valueOf(cfg.getString("items.list.material", "ENDER_EYE")));
        ItemMeta listMeta = listItem.getItemMeta();
        listMeta.setDisplayName(this.color(cfg.getString("items.list.name")));
        listMeta.setLore((List)cfg.getStringList("items.list.lore").stream().map(this::color).collect(Collectors.toList()));
        this.setAction(listMeta, "OPEN_LIST");
        listItem.setItemMeta(listMeta);
        inv.setItem(cfg.getInt("items.list.slot", 15), listItem);
        ItemStack bg = new ItemStack(Material.valueOf(cfg.getString("background.material", "BLACK_STAINED_GLASS_PANE")));
        ItemMeta bgMeta = bg.getItemMeta();
        bgMeta.setDisplayName(" ");
        this.setAction(bgMeta, "NONE");
        bg.setItemMeta(bgMeta);

        for(int i = 0; i < inv.getSize(); ++i) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, bg);
            }
        }

        player.openInventory(inv);
    }

    public void openGiveMenu(Player player) {
        FileConfiguration cfg = this.manager.getMenuGive();
        Inventory inv = Bukkit.createInventory((InventoryHolder)null, cfg.getInt("size", 54), this.color(cfg.getString("title")));
        int slot = 0;

        for(EntityType type : EntityType.values()) {
            if (type.isAlive() && type.isSpawnable()) {
                if (slot >= 45) {
                    break;
                }

                Material mat = Material.getMaterial(type.name() + "_SPAWN_EGG");
                if (mat == null) {
                    mat = Material.SPAWNER;
                }

                ItemStack item = new ItemStack(mat);
                ItemMeta meta = item.getItemMeta();
                meta.setDisplayName(this.color(cfg.getString("item-format.name").replace("{type}", type.name())));
                List<String> lore = new ArrayList();

                for(String line : cfg.getStringList("item-format.lore")) {
                    lore.add(this.color(line.replace("{type}", type.name())));
                }

                meta.setLore(lore);
                this.setAction(meta, "GIVE_SPAWNER_" + type.name());
                item.setItemMeta(meta);
                inv.setItem(slot, item);
                ++slot;
            }
        }

        ItemStack back = new ItemStack(Material.valueOf(cfg.getString("back-button.material", "BARRIER")));
        ItemMeta backMeta = back.getItemMeta();
        backMeta.setDisplayName(this.color(cfg.getString("back-button.name")));
        this.setAction(backMeta, "BACK_MAIN");
        back.setItemMeta(backMeta);
        inv.setItem(cfg.getInt("back-button.slot", 49), back);
        player.openInventory(inv);
    }

    public void openPlayerListMenu(Player admin, int page) {
        FileConfiguration cfg = this.manager.getMenuPlayerList();
        String title = this.color(cfg.getString("title").replace("{page}", String.valueOf(page)));
        Inventory inv = Bukkit.createInventory((InventoryHolder)null, cfg.getInt("size", 54), title);
        Map<UUID, List<CustomSpawnerData>> spawnersByOwner = (Map)this.manager.getActiveSpawners().values().stream().filter((data) -> data.getOwnerId() != null).collect(Collectors.groupingBy(CustomSpawnerData::getOwnerId));
        List<UUID> owners = new ArrayList(spawnersByOwner.keySet());
        owners.sort((u1, u2) -> ((CustomSpawnerData)((List)spawnersByOwner.get(u1)).get(0)).getOwnerName().compareToIgnoreCase(((CustomSpawnerData)((List)spawnersByOwner.get(u2)).get(0)).getOwnerName()));
        int itemsPerPage = 45;
        int startIndex = page * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, owners.size());
        int slot = 0;

        for(int i = startIndex; i < endIndex; ++i) {
            UUID ownerUUID = (UUID)owners.get(i);
            List<CustomSpawnerData> playerSpawners = (List)spawnersByOwner.get(ownerUUID);
            String ownerName = ((CustomSpawnerData)playerSpawners.get(0)).getOwnerName();
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta)head.getItemMeta();
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(ownerUUID));
            meta.setDisplayName(this.color(cfg.getString("head-format.name").replace("{player}", ownerName)));
            Set<String> chunks = new HashSet();

            for(CustomSpawnerData sd : playerSpawners) {
                int cX = sd.getLocation().getBlockX() >> 4;
                int cZ = sd.getLocation().getBlockZ() >> 4;
                chunks.add(sd.getLocation().getWorld().getName() + ":" + cX + ":" + cZ);
            }

            List<String> lore = new ArrayList();

            for(String line : cfg.getStringList("head-format.lore")) {
                lore.add(this.color(line.replace("{uuid}", ownerUUID.toString()).replace("{total}", String.valueOf(playerSpawners.size())).replace("{chunks}", String.valueOf(chunks.size()))));
            }

            meta.setLore(lore);
            this.setAction(meta, "OPEN_PLAYER_SPAWNERS");
            meta.getPersistentDataContainer().set(this.manager.keyOwner, PersistentDataType.STRING, ownerUUID.toString());
            meta.getPersistentDataContainer().set(this.manager.keyAction, PersistentDataType.STRING, ownerName);
            head.setItemMeta(meta);
            inv.setItem(slot, head);
            ++slot;
        }

        this.addNavigation(inv, cfg, page, owners.size(), itemsPerPage, "BACK_MAIN", "PAGE_LIST_");
        admin.openInventory(inv);
    }

    public void openPlayerSpawnersMenu(Player admin, UUID ownerUUID, String ownerName, int page) {
        FileConfiguration cfg = this.manager.getMenuPlayerSpawners();
        String title = this.color(cfg.getString("title").replace("{player}", ownerName).replace("{page}", String.valueOf(page)));
        Inventory inv = Bukkit.createInventory((InventoryHolder)null, cfg.getInt("size", 54), title);
        List<CustomSpawnerData> playerSpawners = (List)this.manager.getActiveSpawners().values().stream().filter((datax) -> Objects.equals(datax.getOwnerId(), ownerUUID)).collect(Collectors.toList());
        int itemsPerPage = 45;
        int startIndex = page * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, playerSpawners.size());
        int slot = 0;

        for(int i = startIndex; i < endIndex; ++i) {
            CustomSpawnerData data = (CustomSpawnerData)playerSpawners.get(i);
            ItemStack item = new ItemStack(Material.valueOf(cfg.getString("spawner-format.material", "SPAWNER")));
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(this.color(cfg.getString("spawner-format.name").replace("{type}", data.getEntityType().name())));
            String worldName = data.getLocation().getWorld().getName();
            String worldColor = worldName.endsWith("_nether") ? "&c" : (worldName.endsWith("_the_end") ? "&d" : "&a");
            List<String> lore = new ArrayList();

            for(String line : cfg.getStringList("spawner-format.lore")) {
                lore.add(this.color(line.replace("{player}", data.getOwnerName()).replace("{world_color}", worldColor).replace("{world}", worldName).replace("{x}", String.valueOf(data.getLocation().getBlockX())).replace("{y}", String.valueOf(data.getLocation().getBlockY())).replace("{z}", String.valueOf(data.getLocation().getBlockZ()))));
            }

            meta.setLore(lore);
            this.setAction(meta, "MANAGE_SPAWNER");
            String locStr = worldName + "," + data.getLocation().getBlockX() + "," + data.getLocation().getBlockY() + "," + data.getLocation().getBlockZ();
            meta.getPersistentDataContainer().set(this.manager.keyLoc, PersistentDataType.STRING, locStr);
            meta.getPersistentDataContainer().set(this.manager.keyOwner, PersistentDataType.STRING, ownerUUID.toString());
            item.setItemMeta(meta);
            inv.setItem(slot, item);
            ++slot;
        }

        int var10004 = playerSpawners.size();
        String var10007 = ownerUUID.toString();
        this.addNavigation(inv, cfg, page, var10004, itemsPerPage, "BACK_LIST", "PAGE_SPAWNERS_" + var10007 + "_" + ownerName + "_");
        admin.openInventory(inv);
    }

    private void addNavigation(Inventory inv, FileConfiguration cfg, int page, int totalItems, int itemsPerPage, String backAction, String pageActionPrefix) {
        ItemStack back = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.setDisplayName(this.color(cfg.getString("navigation.back.name")));
        this.setAction(backMeta, backAction);
        back.setItemMeta(backMeta);
        inv.setItem(49, back);
        if (page > 0) {
            ItemStack prev = new ItemStack(Material.ARROW);
            ItemMeta prevMeta = prev.getItemMeta();
            prevMeta.setDisplayName(this.color(cfg.getString("navigation.prev.name").replace("{page}", String.valueOf(page))));
            this.setAction(prevMeta, pageActionPrefix + (page - 1));
            prev.setItemMeta(prevMeta);
            inv.setItem(45, prev);
        }

        if (totalItems > (page + 1) * itemsPerPage) {
            ItemStack next = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = next.getItemMeta();
            nextMeta.setDisplayName(this.color(cfg.getString("navigation.next.name").replace("{page}", String.valueOf(page + 2))));
            List<String> lore = new ArrayList();

            for(String line : cfg.getStringList("navigation.next.lore")) {
                lore.add(this.color(line.replace("{left}", String.valueOf(totalItems - (page + 1) * itemsPerPage))));
            }

            nextMeta.setLore(lore);
            this.setAction(nextMeta, pageActionPrefix + (page + 1));
            next.setItemMeta(nextMeta);
            inv.setItem(53, next);
        }

    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getCurrentItem() != null && event.getCurrentItem().hasItemMeta()) {
            ItemMeta meta = event.getCurrentItem().getItemMeta();
            if (meta.getPersistentDataContainer().has(this.manager.keyAction, PersistentDataType.STRING)) {
                event.setCancelled(true);
                String action = (String)meta.getPersistentDataContainer().get(this.manager.keyAction, PersistentDataType.STRING);
                Player player = (Player)event.getWhoClicked();
                if (!action.equals("NONE")) {
                    if (action.equals("BACK_MAIN")) {
                        this.openMainMenu(player);
                    } else if (action.equals("BACK_LIST")) {
                        this.openPlayerListMenu(player, 0);
                    } else if (action.equals("OPEN_GIVE")) {
                        this.openGiveMenu(player);
                    } else if (action.equals("OPEN_LIST")) {
                        this.openPlayerListMenu(player, 0);
                    } else if (action.startsWith("GIVE_SPAWNER_")) {
                        String typeName = action.replace("GIVE_SPAWNER_", "");

                        try {
                            EntityType type = EntityType.valueOf(typeName);
                            player.getInventory().addItem(new ItemStack[]{this.manager.createSpawnerItem(type)});
                            String msg = this.manager.getMessage("receive").replace("{amount}", "1").replace("{type}", type.name());
                            player.sendMessage(msg);
                        } catch (Exception var10) {
                        }

                    } else if (action.equals("OPEN_PLAYER_SPAWNERS")) {
                        String uuidStr = (String)meta.getPersistentDataContainer().get(this.manager.keyOwner, PersistentDataType.STRING);
                        String ownerName = action;
                        if (meta.getDisplayName() != null) {
                            ownerName = ChatColor.stripColor(meta.getDisplayName()).replace("► ", "");
                        }

                        this.openPlayerSpawnersMenu(player, UUID.fromString(uuidStr), ownerName, 0);
                    } else if (action.startsWith("PAGE_LIST_")) {
                        int page = Integer.parseInt(action.replace("PAGE_LIST_", ""));
                        this.openPlayerListMenu(player, page);
                    } else if (action.startsWith("PAGE_SPAWNERS_")) {
                        String[] parts = action.replace("PAGE_SPAWNERS_", "").split("_");
                        UUID uuid = UUID.fromString(parts[0]);
                        String name = parts[1];
                        int page = Integer.parseInt(parts[2]);
                        this.openPlayerSpawnersMenu(player, uuid, name, page);
                    } else {
                        if (action.equals("MANAGE_SPAWNER")) {
                            String locStr = (String)meta.getPersistentDataContainer().get(this.manager.keyLoc, PersistentDataType.STRING);
                            String uuidStr = (String)meta.getPersistentDataContainer().get(this.manager.keyOwner, PersistentDataType.STRING);
                            if (locStr == null || uuidStr == null) {
                                return;
                            }

                            String[] locParts = locStr.split(",");

                            try {
                                Location blockLoc = new Location(Bukkit.getWorld(locParts[0]), (double)Integer.parseInt(locParts[1]), (double)Integer.parseInt(locParts[2]), (double)Integer.parseInt(locParts[3]));
                                if (event.getClick() == ClickType.LEFT) {
                                    player.teleport(blockLoc.clone().add((double)0.5F, (double)1.0F, (double)0.5F));
                                    player.sendMessage(this.manager.getMessage("tp-success"));
                                    player.closeInventory();
                                } else if (event.getClick() == ClickType.RIGHT) {
                                    this.manager.removeSpawner(blockLoc);
                                    if (blockLoc.getBlock().getType() == Material.SPAWNER) {
                                        blockLoc.getBlock().setType(Material.AIR);
                                    }

                                    player.sendMessage(this.manager.getMessage("removed-success"));
                                    CustomSpawnerData ref = this.manager.getActiveSpawners().values().stream().filter((d) -> d.getOwnerId().toString().equals(uuidStr)).findFirst().orElse(null);
                                    if (ref != null) {
                                        this.openPlayerSpawnersMenu(player, UUID.fromString(uuidStr), ref.getOwnerName(), 0);
                                    } else {
                                        this.openPlayerListMenu(player, 0);
                                    }
                                }
                            } catch (Exception var11) {
                                player.sendMessage(this.manager.getMessage("error-loc"));
                            }
                        }

                    }
                }
            }
        }
    }
}
