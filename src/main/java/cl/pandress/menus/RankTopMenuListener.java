package cl.pandress.menus;

import cl.pandress.utils.ChatUtils;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

public class RankTopMenuListener implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getView().getTitle().equals(ChatUtils.colorize("&8» &6&lTop 10 Global &8«"))) {
            event.setCancelled(true); // Evita que se roben las cabezas
        }
    }
}