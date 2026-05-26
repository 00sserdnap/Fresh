//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package cl.pandress.modules.rankup.menus;

import cl.pandress.Etherium;
import cl.pandress.modules.rankup.RankManager;
import cl.pandress.utils.ChatUtils;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.Statistic;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

public class RankMenuListener implements Listener {
    public RankMenuListener() {
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        RankManager manager = Etherium.getInstance().getManagerHandler().getRankManager();
        FileConfiguration config = manager.getConfig();
        String expectedTitle = ChatUtils.colorize(config.getString("settings.menu.title", "&8Menu | RankUp"));
        if (event.getView().getTitle().equals(expectedTitle)) {
            event.setCancelled(true);
            if (event.getCurrentItem() == null) {
                return;
            }

            HumanEntity var6 = event.getWhoClicked();
            if (!(var6 instanceof Player)) {
                return;
            }

            Player player = (Player)var6;
            int var12 = config.getInt("settings.menu.items.rankup_button.slot", 13);
            if (event.getRawSlot() == var12) {
                int nextRank = manager.getPlayerRank(player.getUniqueId()) + 1;
                String path = "ranks." + nextRank;
                if (config.getConfigurationSection(path) == null) {
                    return;
                }

                boolean canRankup = true;
                if (config.contains(path + ".requirements.money") && manager.getEconomy().getBalance(player) < config.getDouble(path + ".requirements.money")) {
                    canRankup = false;
                }

                if (config.contains(path + ".requirements.playtime_hours")) {
                    int currentHours = player.getStatistic(Statistic.PLAY_ONE_MINUTE) / 72000;
                    if (currentHours < config.getInt(path + ".requirements.playtime_hours")) {
                        canRankup = false;
                    }
                }

                if (config.contains(path + ".requirements.player_kills") && manager.getProgress(player.getUniqueId(), "general", "player_kills") < config.getInt(path + ".requirements.player_kills")) {
                    canRankup = false;
                }

                if (config.getConfigurationSection(path + ".requirements.blocks_mine") != null) {
                    for(String block : config.getConfigurationSection(path + ".requirements.blocks_mine").getKeys(false)) {
                        if (manager.getProgress(player.getUniqueId(), "blocks_mine", block) < config.getInt(path + ".requirements.blocks_mine." + block)) {
                            canRankup = false;
                        }
                    }
                }

                if (config.getConfigurationSection(path + ".requirements.mob_kills") != null) {
                    for(String mob : config.getConfigurationSection(path + ".requirements.mob_kills").getKeys(false)) {
                        if (manager.getProgress(player.getUniqueId(), "mob_kills", mob) < config.getInt(path + ".requirements.mob_kills." + mob)) {
                            canRankup = false;
                        }
                    }
                }

                if (!canRankup) {
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0F, 1.0F);
                    player.sendMessage(ChatUtils.colorize("&c&lERROR &8» &7Aún no cumples con todos los requisitos."));
                    player.closeInventory();
                    return;
                }

                if (config.contains(path + ".requirements.money")) {
                    manager.getEconomy().withdrawPlayer(player, config.getDouble(path + ".requirements.money"));
                }

                manager.setPlayerRank(player.getUniqueId(), nextRank);

                for(String cmd : config.getStringList(path + ".commands")) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%player%", player.getName()));
                }

                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.0F);
                player.sendTitle(ChatUtils.colorize("&a&lRANK UP!"), ChatUtils.colorize("&fAhora eres Rango #" + nextRank), 10, 40, 10);
                RankMenu.open(player);
            }
        }

    }
}
