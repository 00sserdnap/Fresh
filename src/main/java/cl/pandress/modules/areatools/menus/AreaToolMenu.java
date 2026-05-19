package cl.pandress.modules.areatools.menus;

import cl.pandress.modules.areatools.AreaToolManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class AreaToolMenu implements Listener {

    private final AreaToolManager manager;

    public AreaToolMenu(AreaToolManager manager) {
        this.manager = manager;
    }

    public static class RechargeMenuHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    public static void open(Player player, AreaToolManager manager) {
        String title = ChatColor.translateAlternateColorCodes('&', manager.getConfig().getString("menu.recharge-title", "&8⚡ Recargar Herramienta"));
        Inventory inv = Bukkit.createInventory(new RechargeMenuHolder(), 27, title);

        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glass.getItemMeta();
        glassMeta.setDisplayName(" ");
        glass.setItemMeta(glassMeta);

        for (int i = 0; i < 27; i++) {
            inv.setItem(i, glass);
        }

        inv.setItem(11, player.getInventory().getItemInMainHand().clone());
        inv.setItem(13, new ItemStack(Material.AIR));

        ItemStack confirm = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        ItemMeta confirmMeta = confirm.getItemMeta();
        confirmMeta.setDisplayName(ChatColor.GREEN + "" + ChatColor.BOLD + "CONFIRMAR RECARGA");
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "Coloca un cargador en el espacio");
        lore.add(ChatColor.GRAY + "vacío y haz clic aquí.");
        confirmMeta.setLore(lore);
        confirm.setItemMeta(confirmMeta);
        inv.setItem(15, confirm);

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 1f, 1f);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof RechargeMenuHolder)) return;
        Player player = (Player) event.getWhoClicked();

        if (event.getClickedInventory() != null && event.getClickedInventory().equals(player.getInventory())) {
            return;
        }

        int slot = event.getSlot();

        if (slot != 13) {
            event.setCancelled(true);
        }

        if (slot == 15) {
            ItemStack batterySlot = event.getInventory().getItem(13);

            if (manager.isBattery(batterySlot)) {
                ItemStack tool = player.getInventory().getItemInMainHand();
                if (manager.getToolId(tool) == null) {
                    player.sendMessage(ChatColor.RED + "Debes tener la herramienta en tu mano principal.");
                    return;
                }

                if (manager.isTemporary(tool)) {
                    player.sendMessage(ChatColor.RED + "Las herramientas temporales no pueden ser recargadas.");
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    return;
                }

                int rechargeAmount = manager.getBatteryRechargeAmount(batterySlot);
                int maxCharge = manager.getConfig().getInt("settings.max-charge", 5000);
                int currentCharge = manager.getCharge(tool);

                if (currentCharge >= maxCharge) {
                    player.sendMessage(ChatColor.RED + "Esta herramienta ya está al máximo de energía.");
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    return;
                }

                int newCharge = Math.min(currentCharge + rechargeAmount, maxCharge);
                batterySlot.setAmount(batterySlot.getAmount() - 1);

                int blocksMined = tool.getItemMeta().getPersistentDataContainer().getOrDefault(manager.getBlocksKey(), org.bukkit.persistence.PersistentDataType.INTEGER, 0);
                manager.setCharge(tool, newCharge, blocksMined);

                player.sendMessage(ChatColor.GREEN + "¡Herramienta recargada con éxito! (" + newCharge + "/" + maxCharge + ")");
                player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 1f);
                player.closeInventory();
            } else {
                player.sendMessage(ChatColor.RED + "¡No has colocado una batería válida en el espacio vacío!");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof RechargeMenuHolder)) return;

        ItemStack batterySlot = event.getInventory().getItem(13);
        if (batterySlot != null && batterySlot.getType() != Material.AIR) {
            Player player = (Player) event.getPlayer();
            if (!player.getInventory().addItem(batterySlot).isEmpty()) {
                player.getWorld().dropItem(player.getLocation(), batterySlot);
            }
        }
    }
}