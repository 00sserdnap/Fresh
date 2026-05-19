package cl.pandress.modules.areatools.menus;

import cl.pandress.modules.areatools.AreaToolManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
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

public class AdminMenu implements Listener {

    private final AreaToolManager manager;

    public AdminMenu(AreaToolManager manager) {
        this.manager = manager;
    }

    // Holders para identificar cada menú
    public static class MainAdminHolder implements InventoryHolder { @Override public Inventory getInventory() { return null; } }
    public static class GiveToolsHolder implements InventoryHolder { @Override public Inventory getInventory() { return null; } }
    public static class OwnerListHolder implements InventoryHolder { @Override public Inventory getInventory() { return null; } }

    // ==========================================
    // 1. MENÚ PRINCIPAL (Panel de Control)
    // ==========================================
    public static void open(Player player, AreaToolManager manager) {
        Inventory inv = Bukkit.createInventory(new MainAdminHolder(), 27, "§8Admin: Panel de Herramientas");
        fillBorders(inv);

        // Ítem: Obtener Herramientas (Slot 11)
        ItemStack getTools = createMenuButton(Material.NETHERITE_PICKAXE, 
                "§a▶ §lObtener Herramientas", 
                "§7Abre el menú para sacar herramientas", 
                "§7directamente a tu inventario.", 
                "", "§cSolo Administradores", "minecraft:netherite_pickaxe");
        inv.setItem(11, getTools);

        // Ítem: Estadísticas (Slot 13)
        int total = countTotalItems();
        ItemStack stats = createMenuButton(Material.PAPER, 
                "§e▶ §lEstadísticas Globales", 
                "§7Total Activos: §b" + total, 
                "", "§7Sistema optimizado por Etherium", "minecraft:paper");
        inv.setItem(13, stats);

        // Ítem: Gestionar por Dueño (Slot 15)
        ItemStack owners = createMenuButton(Material.ENDER_EYE, 
                "§d▶ §lGestionar por Dueño", 
                "§7Mira la lista de jugadores con herramientas", 
                "§7y gestiona sus cantidades.", 
                "", "minecraft:ender_eye");
        inv.setItem(15, owners);

        player.openInventory(inv);
    }

    // ==========================================
    // 2. MENÚ DE OBTENCIÓN (Selector de Items)
    // ==========================================
    public void openGiveMenu(Player player) {
        Inventory inv = Bukkit.createInventory(new GiveToolsHolder(), 45, "Obtener Herramientas");
        
        // Picos y Palas del config
        for (String id : manager.getAvailableTools()) {
            ItemStack item = manager.createTool(id, false).clone();
            ItemMeta meta = item.getItemMeta();
            List<String> lore = meta.getLore() != null ? meta.getLore() : new ArrayList<>();
            lore.add("");
            lore.add("§eClick para darte 1x §f" + id);
            meta.setLore(lore);
            item.setItemMeta(meta);
            inv.addItem(item);
        }

        // Baterías del config
        for (String id : manager.getAvailableBatteries()) {
            ItemStack item = manager.createBattery(id).clone();
            ItemMeta meta = item.getItemMeta();
            List<String> lore = meta.getLore() != null ? meta.getLore() : new ArrayList<>();
            lore.add("");
            lore.add("§eClick para darte 1x §f" + id);
            meta.setLore(lore);
            item.setItemMeta(meta);
            inv.addItem(item);
        }

        inv.setItem(40, createMenuButton(Material.BARRIER, "§cVolver", "§7Regresar al panel anterior", "", "minecraft:barrier"));
        player.openInventory(inv);
    }

    // ==========================================
    // 3. MENÚ DE DUEÑOS (Lista de Cabezas)
    // ==========================================
    public void openOwnerMenu(Player player) {
        Inventory inv = Bukkit.createInventory(new OwnerListHolder(), 54, "Lista de Dueños");

        for (Player online : Bukkit.getOnlinePlayers()) {
            int count = 0;
            for (ItemStack item : online.getInventory().getContents()) {
                if (manager.getToolId(item) != null || manager.isBattery(item)) count += item.getAmount();
            }

            if (count > 0) {
                ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta meta = (SkullMeta) head.getItemMeta();
                meta.setOwningPlayer(online);
                meta.setDisplayName("§b▶ §l" + online.getName());
                List<String> lore = new ArrayList<>();
                lore.add("§8UUID: " + online.getUniqueId());
                lore.add("");
                lore.add("§7Ítems Totales: §f" + count);
                lore.add("");
                lore.add("§aClick para ver detalles.");
                lore.add("§8minecraft:player_head");
                meta.setLore(lore);
                head.setItemMeta(meta);
                inv.addItem(head);
            }
        }

        inv.setItem(49, createMenuButton(Material.BARRIER, "§cVolver", "§7Regresar al panel anterior", "", "minecraft:barrier"));
        player.openInventory(inv);
    }

    // ==========================================
    // LÓGICA DE CLICKS
    // ==========================================
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory inv = event.getInventory();
        Player player = (Player) event.getWhoClicked();
        ItemStack clicked = event.getCurrentItem();

        if (clicked == null || clicked.getType() == Material.AIR) return;

        // Click en Menú Principal
        if (inv.getHolder() instanceof MainAdminHolder) {
            event.setCancelled(true);
            if (event.getSlot() == 11) openGiveMenu(player);
            else if (event.getSlot() == 15) openOwnerMenu(player);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
        }

        // Click en Menú de Dar
        else if (inv.getHolder() instanceof GiveToolsHolder) {
            event.setCancelled(true);
            if (clicked.getType() == Material.BARRIER) {
                open(player, manager);
                return;
            }
            player.getInventory().addItem(clicked.clone());
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1f);
        }

        // Click en Menú de Dueños
        else if (inv.getHolder() instanceof OwnerListHolder) {
            event.setCancelled(true);
            if (clicked.getType() == Material.BARRIER) open(player, manager);
        }
    }

    // ==========================================
    // UTILIDADES VISUALES (Footer Estilo Etherium)
    // ==========================================
    private static ItemStack createMenuButton(Material mat, String name, String... loreLines) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        List<String> lore = new ArrayList<>();
        int components = 10; // Simulación de los "componentes" de las fotos
        
        for (String line : loreLines) {
            if (line.startsWith("minecraft:")) {
                lore.add("§8" + line);
                components = 10 + (line.length() / 2);
            } else {
                lore.add(line);
            }
        }
        lore.add("§8" + components + " componente(s)");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static void fillBorders(Inventory inv) {
        ItemStack glass = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = glass.getItemMeta();
        meta.setDisplayName(" ");
        glass.setItemMeta(meta);
        for (int i = 0; i < inv.getSize(); i++) {
            if (i < 9 || i >= inv.getSize() - 9 || i % 9 == 0 || i % 9 == 8) {
                inv.setItem(i, glass);
            }
        }
    }

    private static int countTotalItems() {
        int total = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            // Aquí iría la lógica de escaneo que ya tenemos en AreaToolManager
            total++; // Simplificado para el ejemplo
        }
        return total;
    }
}