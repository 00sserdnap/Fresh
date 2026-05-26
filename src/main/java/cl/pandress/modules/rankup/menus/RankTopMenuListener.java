//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package cl.pandress.modules.rankup.menus;

import cl.pandress.utils.ChatUtils;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

public class RankTopMenuListener implements Listener {
    public RankTopMenuListener() {
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getView().getTitle().equals(ChatUtils.colorize("&8» &6&lTop 10 Global &8«"))) {
            event.setCancelled(true);
        }

    }
}
