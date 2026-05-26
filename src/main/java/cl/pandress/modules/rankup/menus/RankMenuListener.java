package cl.pandress.modules.rankup.menus;

import cl.pandress.Etherium;
import cl.pandress.modules.rankup.RankManager;
import cl.pandress.utils.ChatUtils;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.Statistic;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

public class RankMenuListener implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        RankManager manager = Etherium.getInstance().getManagerHandler().getRankManager();
        FileConfiguration config = manager.getConfig();

        String expectedTitle = ChatUtils.colorize(config.getString("settings.menu.title", "&8Menu | RankUp"));
        if (!event.getView().getTitle().equals(expectedTitle)) return;

        event.setCancelled(true);

        if (event.getCurrentItem() == null) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        int rankupSlot = config.getInt("settings.menu.items.rankup_button.slot", 12);
        if (event.getRawSlot() != rankupSlot) return;

        // ── Verificar que existe el siguiente rango ───────────────────────
        int nextRank = manager.getPlayerRank(player.getUniqueId()) + 1;
        String path  = "ranks." + nextRank;

        if (config.getConfigurationSection(path) == null) return;

        // ── Verificar todos los requisitos ────────────────────────────────
        boolean canRankup = true;

        // Dinero
        if (config.contains(path + ".requirements.money")) {
            double required = config.getDouble(path + ".requirements.money");
            if (manager.getEconomy() == null || manager.getEconomy().getBalance(player) < required) {
                canRankup = false;
            }
        }

        // Horas de juego
        if (config.contains(path + ".requirements.playtime_hours")) {
            int currentHours = player.getStatistic(Statistic.PLAY_ONE_MINUTE) / 72000;
            if (currentHours < config.getInt(path + ".requirements.playtime_hours")) {
                canRankup = false;
            }
        }

        // Kills PvP
        if (config.contains(path + ".requirements.player_kills")) {
            int current  = manager.getProgress(player.getUniqueId(), "general", "player_kills");
            int required = config.getInt(path + ".requirements.player_kills");
            if (current < required) canRankup = false;
        }

        // Bloques rotos
        if (config.getConfigurationSection(path + ".requirements.blocks_mine") != null) {
            for (String block : config.getConfigurationSection(path + ".requirements.blocks_mine").getKeys(false)) {
                int current  = manager.getProgress(player.getUniqueId(), "blocks_mine", block);
                int required = config.getInt(path + ".requirements.blocks_mine." + block);
                if (current < required) { canRankup = false; break; }
            }
        }

        // Bloques colocados
        if (config.getConfigurationSection(path + ".requirements.blocks_place") != null) {
            for (String block : config.getConfigurationSection(path + ".requirements.blocks_place").getKeys(false)) {
                int current  = manager.getProgress(player.getUniqueId(), "blocks_place", block);
                int required = config.getInt(path + ".requirements.blocks_place." + block);
                if (current < required) { canRankup = false; break; }
            }
        }

        // Mobs matados
        if (config.getConfigurationSection(path + ".requirements.mob_kills") != null) {
            for (String mob : config.getConfigurationSection(path + ".requirements.mob_kills").getKeys(false)) {
                int current  = manager.getProgress(player.getUniqueId(), "mob_kills", mob);
                int required = config.getInt(path + ".requirements.mob_kills." + mob);
                if (current < required) { canRankup = false; break; }
            }
        }

        // ── Resultado ─────────────────────────────────────────────────────
        if (!canRankup) {
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            player.sendMessage(ChatUtils.colorize("&c&lERROR &8» &7Aún no cumples con todos los requisitos."));
            player.closeInventory();
            return;
        }

        // ── Cobrar dinero y subir rango ───────────────────────────────────
        if (config.contains(path + ".requirements.money")) {
            manager.getEconomy().withdrawPlayer(player, config.getDouble(path + ".requirements.money"));
        }

        // setPlayerRank llama internamente a savePlayerDataNow(uuid)
        // → guardado async inmediato, nunca en el main thread.
        manager.setPlayerRank(player.getUniqueId(), nextRank);

        // Ejecutar comandos de recompensa
        for (String cmd : config.getStringList(path + ".commands")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%player%", player.getName()));
        }

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        player.sendTitle(
            ChatUtils.colorize("&a&lRANK UP!"),
            ChatUtils.colorize("&fAhora eres Rango #" + nextRank),
            10, 40, 10
        );

        // Reabrir el menú para mostrar el nuevo estado
        RankMenu.open(player);
    }
}