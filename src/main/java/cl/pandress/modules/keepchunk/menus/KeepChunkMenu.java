package cl.pandress.modules.keepchunk.menus;

import cl.pandress.modules.keepchunk.KeepChunkManager;
import cl.pandress.modules.keepchunk.KeepChunkType;
import cl.pandress.modules.keepchunk.data.KeepChunkData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class KeepChunkMenu implements Listener {

    private final KeepChunkManager manager;

    public KeepChunkMenu(KeepChunkManager manager) {
        this.manager = manager;
    }

    public static class LoaderMenuHolder implements InventoryHolder {
        private final KeepChunkData data;
        private final Villager npc;
        public LoaderMenuHolder(KeepChunkData data, Villager npc) {
            this.data = data;
            this.npc = npc;
        }
        public KeepChunkData getData() { return data; }
        public Villager getNpc()       { return npc; }
        @Override public Inventory getInventory() { return null; }
    }

    public static void open(Player player, KeepChunkManager manager, KeepChunkData data, Villager npc) {
        FileConfiguration cfg = manager.getMenuCfg();
        KeepChunkType type = manager.getType(data.getTypeId());
        
        boolean isTemp = data.isTemporary();
        boolean isPerm = type != null && type.isPermanent();

        String title = color(cfg.getString("title", "&8⚡ Panel del Cargador"));
        int size = cfg.getInt("size", 54); 
        Inventory inv = Bukkit.createInventory(new LoaderMenuHolder(data, npc), size, title);

        Material borderMat = parseMaterial(cfg.getString("border.material", "BLACK_STAINED_GLASS_PANE"));
        Material fillMat   = parseMaterial(cfg.getString("fill.material", "GRAY_STAINED_GLASS_PANE"));
        Material reactorMat = parseMaterial(cfg.getString("reactor-glass.material", "PURPLE_STAINED_GLASS_PANE"));

        ItemStack border = makeItem(borderMat, " ", null);
        ItemStack fill   = makeItem(fillMat,   " ", null);
        ItemStack reactor = makeItem(reactorMat, color(cfg.getString("reactor-glass.name", " ")), null);

        // Llenar fondo
        for (int i = 0; i < size; i++) {
            if (i < 9 || i >= (size - 9) || i % 9 == 0 || i % 9 == 8) {
                inv.setItem(i, border);
            } else {
                inv.setItem(i, fill);
            }
        }

        // Dibujar Reactor
        int coreSlot = cfg.getInt("items.core_slot.slot", 31);
        int[] reactorSlots = {coreSlot - 10, coreSlot - 9, coreSlot - 8, coreSlot - 1, coreSlot + 1, coreSlot + 8, coreSlot + 10}; 
        for (int slot : reactorSlots) {
             if (slot >= 0 && slot < size && inv.getItem(slot) != null && inv.getItem(slot).getType() == fillMat) {
                 inv.setItem(slot, reactor);
             }
        }

        // 1. INFO (Siempre visible)
        inv.setItem(cfg.getInt("items.info.slot", 13), buildInfoItem(cfg, data, type));
        
        // 2. TOGGLE (Encender/Apagar)
        String togglePath = data.isActive() ? "items.toggle.on" : "items.toggle.off";
        inv.setItem(cfg.getInt("items.toggle.slot", 28), buildFromConfig(cfg, togglePath));
        
        // 3. PARTICULAS (Siempre visible)
        String partPath = data.isShowParticles() ? "items.particles.on" : "items.particles.off";
        inv.setItem(cfg.getInt("items.particles.slot", 34), buildFromConfig(cfg, partPath));

        // --- LÓGICA DE BLOQUEO POR TIPO ---

        // 4. RECARGA (Solo cargadores normales, NO permanentes NI temporales)
        if (!isPerm && !isTemp) {
            if (cfg.contains("items.recharge")) inv.setItem(cfg.getInt("items.recharge.slot", 40), buildRechargeItem(cfg, manager));
            inv.setItem(coreSlot, new ItemStack(Material.AIR)); // Slot vacío para poner el núcleo
        } else {
            // Bloquear entrada de núcleos con cristal si es infinito o temporal
            inv.setItem(coreSlot, reactor);
            inv.setItem(cfg.getInt("items.recharge.slot", 40), makeItem(Material.BARRIER, "&cInyección Deshabilitada", 
                    List.of("&7Este sistema no requiere núcleos", "&7de energía externos.")));
        }

        // 5. RENOMBRAR (Bloqueado para temporales)
        if (!isTemp) {
            inv.setItem(cfg.getInt("items.rename.slot", 20), buildFromConfig(cfg, "items.rename"));
        } else {
            inv.setItem(cfg.getInt("items.rename.slot", 20), makeItem(Material.BARRIER, "&cNo Renombrable", 
                    List.of("&7Los sistemas temporales tienen", "&7identificadores fijos.")));
        }

        // 6. RECOGER (Bloqueado para temporales)
        if (!isTemp) {
            inv.setItem(cfg.getInt("items.pickup.slot", 24), buildFromConfig(cfg, "items.pickup"));
        } else {
            inv.setItem(cfg.getInt("items.pickup.slot", 24), makeItem(Material.BARRIER, "&cNo Desmantelable", 
                    List.of("&7Este sistema se auto-destruirá", "&7al terminar su tiempo.")));
        }

        player.openInventory(inv);
    }

    private static ItemStack buildInfoItem(FileConfiguration cfg, KeepChunkData data, KeepChunkType type) {
        String basePath = "items.info";
        Material mat = parseMaterial(cfg.getString(basePath + ".material", "NETHER_STAR"));
        
        String ownerName = Bukkit.getOfflinePlayer(data.getOwner()).getName();
        if (ownerName == null) ownerName = "Desconocido";
        
        String customName = data.getCustomName() != null ? data.getCustomName() : ownerName;
        String typeName = type != null ? color(type.getName()) : "Desconocido";
        String chunkSize = type != null ? (type.getRadius() * 2 + 1) + "x" + (type.getRadius() * 2 + 1) : "?x?";

        String name = color(cfg.getString(basePath + ".name", "&fEstadísticas").replace("{custom_name}", customName));
        String statusKey = data.isActive() ? basePath + ".status-active" : basePath + ".status-inactive";
        String statusText = color(cfg.getString(statusKey, data.isActive() ? "&a&l● OPERATIVO" : "&c&l● INACTIVO"));

        String uptimeText;
        if (data.isActive() && data.getActiveSince() > 0) {
            long diff = System.currentTimeMillis() - data.getActiveSince();
            long hours = (diff / (1000 * 60 * 60)) % 24;
            long mins = (diff / (1000 * 60)) % 60;
            uptimeText = color(cfg.getString(basePath + ".uptime-format", "&e{hours}h {mins}m")
                    .replace("{hours}", String.valueOf(hours)).replace("{mins}", String.valueOf(mins)));
        } else {
            uptimeText = color(cfg.getString(basePath + ".uptime-offline", "&8—"));
        }

        String fuelText;
        String fuelMin;
        if (type == null) {
            fuelText = "&8—"; fuelMin = "—";
        } else if (type.isPermanent() && !data.isTemporary()) {
            fuelText = color(cfg.getString(basePath + ".fuel-infinite", "&a&l∞ PERMANENTE"));
            fuelMin = "∞";
        } else if (data.isTemporary()) {
            fuelText = color("&c⏳ TEMPORAL");
            fuelMin = String.valueOf(data.getFuel());
        } else {
            int percent = type.getMaxFuel() > 0 ? (int) (((double) data.getFuel() / type.getMaxFuel()) * 100) : 0;
            fuelText = getFuelBar(percent);
            fuelMin = String.valueOf(data.getFuel());
        }

        List<String> rawLore = cfg.getStringList(basePath + ".lore");
        List<String> lore = new ArrayList<>();
        for (String line : rawLore) {
            lore.add(color(line.replace("{owner}", ownerName).replace("{custom_name}", customName)
                    .replace("{type_name}", typeName).replace("{chunk_size}", chunkSize)
                    .replace("{status}", statusText).replace("{uptime}", uptimeText)
                    .replace("{fuel_bar}", fuelText).replace("{fuel}", fuelMin)));
        }

        return makeItem(mat, name, lore);
    }

    private static ItemStack buildRechargeItem(FileConfiguration cfg, KeepChunkManager manager) {
        Material mat = parseMaterial(cfg.getString("items.recharge.material", "BEACON"));
        String name = color(cfg.getString("items.recharge.name", "&b&lCONFIRMAR RECARGA"));
        int defaultAmount = manager.getConfig().getInt("cores.standard.recharge-amount", 1440);

        List<String> rawLore = cfg.getStringList("items.recharge.lore");
        List<String> lore = new ArrayList<>();
        for (String line : rawLore) lore.add(color(line.replace("{recharge_amount}", String.valueOf(defaultAmount))));
        return makeItem(mat, name, lore);
    }

    private static ItemStack buildFromConfig(FileConfiguration cfg, String section) {
        String matStr = cfg.getString(section + ".material");
        Material mat = parseMaterial(matStr != null ? matStr : "STONE");

        String rawName = cfg.getString(section + ".name");
        // Si el nombre es null, el YML no se cargó bien o la ruta de sección es incorrecta
        String name = color(rawName != null ? rawName : "&cError: revisa menus/control_panel.yml [" + section + "]");

        List<String> rawLore = cfg.getStringList(section + ".lore");
        List<String> lore = new ArrayList<>();
        if (rawLore.isEmpty() && rawName == null) {
            lore.add("&7Sección no encontrada en el YML.");
            lore.add("&ePath buscado: &e" + section);
        }
        for (String line : rawLore) lore.add(color(line));
        return makeItem(mat, name, lore);
    }

    private static String getFuelBar(int percent) {
        int filled = Math.max(0, Math.min(10, percent / 10));
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < 10; i++) bar.append(i < filled ? "&a■" : "&8■");
        return color(bar.toString());
    }

    private static ItemStack makeItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        if (lore != null) {
            List<String> coloredLore = new ArrayList<>();
            for(String l : lore) coloredLore.add(ChatColor.translateAlternateColorCodes('&', l));
            meta.setLore(coloredLore);
        }
        item.setItemMeta(meta);
        return item;
    }

    private static Material parseMaterial(String name) {
        try { return Material.valueOf(name.toUpperCase()); }
        catch (IllegalArgumentException e) { return Material.STONE; }
    }

    private static String color(String text) {
        return text == null ? "" : ChatColor.translateAlternateColorCodes('&', text);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof LoaderMenuHolder)) return;
        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        LoaderMenuHolder holder = (LoaderMenuHolder) event.getInventory().getHolder();
        KeepChunkData data = holder.getData();
        KeepChunkType type = manager.getType(data.getTypeId());
        FileConfiguration cfg = manager.getMenuCfg();

        if (event.getClickedInventory() != null && event.getClickedInventory().equals(player.getInventory())) {
            event.setCancelled(false);
            return;
        }

        int coreSlot = cfg.getInt("items.core_slot.slot", 31);
        if (event.getSlot() == coreSlot) {
            // Solo permitir interacción si el slot no está bloqueado (no es aire)
            if (event.getInventory().getItem(coreSlot) == null || event.getInventory().getItem(coreSlot).getType() == Material.AIR 
                || !event.getInventory().getItem(coreSlot).getType().name().contains("GLASS")) {
                event.setCancelled(false);
            }
            return;
        }

        // ACCIÓN: ENCENDER / APAGAR
        if (event.getSlot() == cfg.getInt("items.toggle.slot", 28)) {
            if (data.isActive()) {
                data.setActive(false);
                data.setActiveSince(0);
                manager.toggleChunks(data, false);
                player.sendMessage(manager.getMsg("turned-off"));
            } else {
                if (type != null && !type.isPermanent() && data.getFuel() <= 0) {
                    player.sendMessage(manager.getMsg("no-fuel"));
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    return;
                }
                data.setActive(true);
                data.setActiveSince(System.currentTimeMillis());
                manager.toggleChunks(data, true);
                player.sendMessage(manager.getMsg("turned-on"));
            }
            manager.saveLoaderData();
            player.playSound(player.getLocation(), Sound.BLOCK_LEVER_CLICK, 1f, 1f);
            open(player, manager, data, holder.getNpc());
        }

        // ACCIÓN: PARTICULAS
        if (event.getSlot() == cfg.getInt("items.particles.slot", 34)) {
            data.setShowParticles(!data.isShowParticles());
            manager.saveLoaderData();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            open(player, manager, data, holder.getNpc());
        }

        // ACCIÓN: RECARGAR (Solo si no es temporal)
        if (event.getSlot() == cfg.getInt("items.recharge.slot", 40)) {
            if (data.isTemporary()) return;
            if (type != null && type.isPermanent()) {
                player.sendMessage(manager.getMsg("is-permanent"));
                return;
            }
            ItemStack coreItem = event.getInventory().getItem(coreSlot);
            if (coreItem != null && coreItem.hasItemMeta()) {
                String coreId = coreItem.getItemMeta().getPersistentDataContainer()
                        .get(manager.getCoreKey(), PersistentDataType.STRING);
                if (coreId != null && type != null) {
                    if (data.getFuel() >= type.getMaxFuel()) {
                        player.sendMessage(manager.getMsg("already-full"));
                        return;
                    }
                    int amount = manager.getConfig().getInt("cores." + coreId + ".recharge-amount", 1440);
                    data.setFuel(Math.min(type.getMaxFuel(), data.getFuel() + amount));
                    coreItem.setAmount(coreItem.getAmount() - 1);
                    manager.saveLoaderData();
                    player.sendMessage(manager.getMsg("recharged").replace("{fuel}", String.valueOf(data.getFuel())));
                    player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 1f);
                    open(player, manager, data, holder.getNpc());
                }
            }
        }

        // ACCIÓN: RENOMBRAR (Bloqueado para temporales)
        if (event.getSlot() == cfg.getInt("items.rename.slot", 20)) {
            if (data.isTemporary()) return;
            player.closeInventory();
            manager.getRenamingPlayers().put(player.getUniqueId(), data.getId());
            player.sendMessage(manager.getMsg("rename-prompt"));
        }

        // ACCIÓN: RECOGER (Bloqueado para temporales)
        if (event.getSlot() == cfg.getInt("items.pickup.slot", 24)) {
            if (data.isTemporary()) return;
            
            Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("Etherium"), () -> {
                holder.getNpc().remove();
                manager.removeLoader(data.getId());
            });
            ItemStack returned = manager.createLoaderItem(data.getTypeId(), data.getFuel(), data.getCustomName(), data.getOwner(), false);
            if (returned != null) player.getInventory().addItem(returned);
            player.sendMessage(manager.getMsg("picked-up"));
            player.closeInventory();
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof LoaderMenuHolder)) return;

        int coreSlot = manager.getMenuCfg().getInt("items.core_slot.slot", 31);
        ItemStack core = event.getInventory().getItem(coreSlot);
        
        // Solo devolver si es un ítem real (no el cristal bloqueador)
        if (core != null && core.getType() != Material.AIR && !core.getType().name().contains("GLASS_PANE")) {
            Player player = (Player) event.getPlayer();
            java.util.HashMap<Integer, ItemStack> leftOvers = player.getInventory().addItem(core);
            for (ItemStack left : leftOvers.values()) player.getWorld().dropItemNaturally(player.getLocation(), left);
        }
    }
}