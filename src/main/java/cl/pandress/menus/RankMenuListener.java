package cl.pandress.menus;

import cl.pandress.Fresh;
import cl.pandress.modules.rankup.RankManager;
import cl.pandress.utils.ChatUtils;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

public class RankMenuListener implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        RankManager manager = Fresh.getInstance().getManagerHandler().getRankManager();
        FileConfiguration config = manager.getConfig();
        String expectedTitle = ChatUtils.colorize(config.getString("settings.menu.title", "&8Menu | RankUp"));

        if (event.getView().getTitle().equals(expectedTitle)) {
            event.setCancelled(true);
            if (event.getCurrentItem() == null) return;
            if (!(event.getWhoClicked() instanceof Player player)) return;

            int rankupSlot = config.getInt("settings.menu.items.rankup_button.slot", 13);

            if (event.getRawSlot() == rankupSlot) {
                int nextRank = manager.getPlayerRank(player.getUniqueId()) + 1;
                String path = "ranks." + nextRank;

                if (config.getConfigurationSection(path) == null) return;

                // VALIDACIÓN DE TODOS LOS REQUISITOS
                boolean canRankup = true;

                // Dinero
                if (config.contains(path + ".requirements.money")) {
                    if (manager.getEconomy().getBalance(player) < config.getDouble(path + ".requirements.money")) canRankup = false;
                }
                // Horas
                if (config.contains(path + ".requirements.playtime_hours")) {
                    int currentHours = player.getStatistic(org.bukkit.Statistic.PLAY_ONE_MINUTE) / (20 * 60 * 60);
                    if (currentHours < config.getInt(path + ".requirements.playtime_hours")) canRankup = false;
                }
                // Player Kills
                if (config.contains(path + ".requirements.player_kills")) {
                    if (manager.getProgress(player.getUniqueId(), "general", "player_kills") < config.getInt(path + ".requirements.player_kills")) canRankup = false;
                }
                // Bloques
                if (config.getConfigurationSection(path + ".requirements.blocks_mine") != null) {
                    for (String block : config.getConfigurationSection(path + ".requirements.blocks_mine").getKeys(false)) {
                        if (manager.getProgress(player.getUniqueId(), "blocks_mine", block) < config.getInt(path + ".requirements.blocks_mine." + block)) canRankup = false;
                    }
                }
                // Mobs
                if (config.getConfigurationSection(path + ".requirements.mob_kills") != null) {
                    for (String mob : config.getConfigurationSection(path + ".requirements.mob_kills").getKeys(false)) {
                        if (manager.getProgress(player.getUniqueId(), "mob_kills", mob) < config.getInt(path + ".requirements.mob_kills." + mob)) canRankup = false;
                    }
                }

                if (!canRankup) {
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    player.sendMessage(ChatUtils.colorize("&c&lERROR &8» &7Aún no cumples con todos los requisitos."));
                    player.closeInventory();
                    return;
                }

                // SI TIENE TODO: COBRAR DINERO (Si es que pide dinero)
                if (config.contains(path + ".requirements.money")) {
                    manager.getEconomy().withdrawPlayer(player, config.getDouble(path + ".requirements.money"));
                }

                // SUBIR RANGO Y RESETEAR PROGRESO (setPlayerRank lo hace automático)
                manager.setPlayerRank(player.getUniqueId(), nextRank);
                
                // RECOMPENSAS
                for (String cmd : config.getStringList(path + ".commands")) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%player%", player.getName()));
                }

                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                player.sendTitle(ChatUtils.colorize("&a&lRANK UP!"), ChatUtils.colorize("&fAhora eres Rango #" + nextRank), 10, 40, 10);
                RankMenu.open(player);
            }
        }
    }
}