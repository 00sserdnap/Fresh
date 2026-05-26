package cl.pandress.modules.rankup.menus;

import cl.pandress.Etherium;
import cl.pandress.modules.rankup.RankManager;
import cl.pandress.utils.ChatUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.Statistic;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class RankMenu {

    private static final DecimalFormat DF = new DecimalFormat("#.#");

    // =========================================================
    //  APERTURA DEL MENÚ
    // =========================================================

    public static void open(Player player) {
        RankManager manager = Etherium.getInstance().getManagerHandler().getRankManager();
        FileConfiguration config = manager.getConfig();

        String title   = config.getString("settings.menu.title", "&8Menu | RankUp");
        String typeStr = config.getString("settings.menu.type", "CHEST").toUpperCase();

        Inventory inv;
        if (typeStr.equals("CHEST")) {
            int rows = config.getInt("settings.menu.rows", 3);
            int size = Math.min(6, Math.max(1, rows)) * 9;
            inv = Bukkit.createInventory((InventoryHolder) null, size, ChatUtils.colorize(title));
        } else {
            InventoryType type = InventoryType.valueOf(typeStr);
            inv = Bukkit.createInventory((InventoryHolder) null, type, ChatUtils.colorize(title));
        }

        // Relleno decorativo
        if (config.getBoolean("settings.menu.items.fillers.enabled", true)) {
            String matStr = config.getString("settings.menu.items.fillers.material", "GRAY_STAINED_GLASS_PANE");
            Material mat  = Material.matchMaterial(matStr);
            if (mat != null) {
                ItemStack filler = new ItemStack(mat);
                ItemMeta meta = filler.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(ChatUtils.colorize(
                        config.getString("settings.menu.items.fillers.name", " ")
                    ));
                    filler.setItemMeta(meta);
                }
                for (int slot : config.getIntegerList("settings.menu.items.fillers.slots")) {
                    if (slot < inv.getSize()) inv.setItem(slot, filler);
                }
            }
        }

        // Botón de rankup
        int currentRank = manager.getPlayerRank(player.getUniqueId());
        int nextRank    = currentRank + 1;
        int rankupSlot  = config.getInt("settings.menu.items.rankup_button.slot", 12);

        if (rankupSlot < inv.getSize()) {
            inv.setItem(rankupSlot, buildRankupItem(player, nextRank, manager, config));
        }

        // Botón de top
        int topSlot = config.getInt("settings.menu.items.top_button.slot", 14);
        if (topSlot < inv.getSize()) {
            inv.setItem(topSlot, buildTopItem(manager, config));
        }

        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.8f, 1.2f);
        player.openInventory(inv);
    }

    // =========================================================
    //  CONSTRUCCIÓN DE ÍTEMS
    // =========================================================

    private static ItemStack buildRankupItem(Player player, int nextRank, RankManager manager, FileConfiguration config) {
        String pathMenu = "settings.menu.items.rankup_button.";
        String matStr   = config.getString(pathMenu + "material", "EMERALD");
        Material mat    = Material.matchMaterial(matStr);

        ItemStack item = new ItemStack(mat != null ? mat : Material.EMERALD);
        ItemMeta meta  = item.getItemMeta();
        if (meta == null) return item;

        String pathRank = "ranks." + nextRank;

        // Si no hay siguiente rango → mensaje de rango máximo
        if (config.getConfigurationSection(pathRank) == null) {
            meta.setDisplayName(ChatUtils.colorize(
                config.getString(pathMenu + "name_max", "&a&lRango Máximo Alcanzado")
            ));
            item.setItemMeta(meta);
            return item;
        }

        meta.setDisplayName(ChatUtils.colorize(
            config.getString(pathMenu + "name", "&a&lRango #%next_rank%")
                  .replace("%next_rank%", String.valueOf(nextRank))
        ));

        List<String> finalLore   = new ArrayList<>();
        List<String> rewardsLore = config.getStringList(pathRank + ".rewards_lore");

        for (String line : config.getStringList(pathMenu + "lore")) {

            if (line.contains("%rewards%")) {
                for (String reward : rewardsLore) {
                    finalLore.add(ChatUtils.colorize(" &8| &a" + reward));
                }
                continue;
            }

            if (line.contains("%requirements%")) {
                appendRequirementLines(finalLore, player, manager, config, pathRank);
                continue;
            }

            finalLore.add(ChatUtils.colorize(line));
        }

        meta.setLore(finalLore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Construye las líneas de requisitos para el lore del botón de rankup.
     * Verde = cumplido, rojo = falta.
     */
    private static void appendRequirementLines(List<String> lore, Player player, RankManager manager,
                                               FileConfiguration config, String pathRank) {
        UUID uuid = player.getUniqueId();

        // Dinero
        if (config.contains(pathRank + ".requirements.money")) {
            double req = config.getDouble(pathRank + ".requirements.money");
            double bal = manager.getEconomy() != null ? manager.getEconomy().getBalance(player) : 0.0;
            String color = bal >= req ? "&a" : "&c";
            lore.add(ChatUtils.colorize(" &8| &7Dinero: " + color + "$" + formatNumber(req)));
        }

        // Horas de juego
        if (config.contains(pathRank + ".requirements.playtime_hours")) {
            int req     = config.getInt(pathRank + ".requirements.playtime_hours");
            int current = player.getStatistic(Statistic.PLAY_ONE_MINUTE) / 72000;
            String color = current >= req ? "&a" : "&c";
            lore.add(ChatUtils.colorize(" &8| &7Horas: " + color + current + "&8/&" + color.charAt(1) + req + "h"));
        }

        // Kills PvP
        if (config.contains(pathRank + ".requirements.player_kills")) {
            int req     = config.getInt(pathRank + ".requirements.player_kills");
            int current = manager.getProgress(uuid, "general", "player_kills");
            String color = current >= req ? "&a" : "&c";
            lore.add(ChatUtils.colorize(" &8| &7Kills PvP: " + color + current + "&8/" + color + req));
        }

        // Bloques rotos
        if (config.getConfigurationSection(pathRank + ".requirements.blocks_mine") != null) {
            for (String block : config.getConfigurationSection(pathRank + ".requirements.blocks_mine").getKeys(false)) {
                int req     = config.getInt(pathRank + ".requirements.blocks_mine." + block);
                int current = manager.getProgress(uuid, "blocks_mine", block);
                String color = current >= req ? "&a" : "&c";
                lore.add(ChatUtils.colorize(" &8| &7Picar " + block + ": " + color + current + "&8/" + color + req));
            }
        }

        // Bloques colocados
        if (config.getConfigurationSection(pathRank + ".requirements.blocks_place") != null) {
            for (String block : config.getConfigurationSection(pathRank + ".requirements.blocks_place").getKeys(false)) {
                int req     = config.getInt(pathRank + ".requirements.blocks_place." + block);
                int current = manager.getProgress(uuid, "blocks_place", block);
                String color = current >= req ? "&a" : "&c";
                lore.add(ChatUtils.colorize(" &8| &7Colocar " + block + ": " + color + current + "&8/" + color + req));
            }
        }

        // Mobs matados
        if (config.getConfigurationSection(pathRank + ".requirements.mob_kills") != null) {
            for (String mob : config.getConfigurationSection(pathRank + ".requirements.mob_kills").getKeys(false)) {
                int req     = config.getInt(pathRank + ".requirements.mob_kills." + mob);
                int current = manager.getProgress(uuid, "mob_kills", mob);
                String color = current >= req ? "&a" : "&c";
                lore.add(ChatUtils.colorize(" &8| &7Matar " + mob + ": " + color + current + "&8/" + color + req));
            }
        }
    }

    private static ItemStack buildTopItem(RankManager manager, FileConfiguration config) {
        String pathMenu = "settings.menu.items.top_button.";
        String matStr   = config.getString(pathMenu + "material", "BELL");
        Material mat    = Material.matchMaterial(matStr);

        ItemStack item = new ItemStack(mat != null ? mat : Material.BELL);
        ItemMeta meta  = item.getItemMeta();
        if (meta == null) return item;

        meta.setDisplayName(ChatUtils.colorize(config.getString(pathMenu + "name", "&6&lTop Rangos")));

        List<String> finalLore = new ArrayList<>();

        for (String line : config.getStringList(pathMenu + "lore_header")) {
            finalLore.add(ChatUtils.colorize(line));
        }

        String format = config.getString(pathMenu + "lore_format",
            " &8| &eRango #%pos%: &f%player% &7(%rank%)");

        int pos = 1;
        for (Map.Entry<UUID, Integer> entry : manager.getTopRanks()) {
            String name = Bukkit.getOfflinePlayer(entry.getKey()).getName();
            if (name == null) name = "Desconocido";
            finalLore.add(ChatUtils.colorize(
                format.replace("%pos%", String.valueOf(pos))
                      .replace("%player%", name)
                      .replace("%rank%", String.valueOf(entry.getValue()))
            ));
            pos++;
        }

        for (String line : config.getStringList(pathMenu + "lore_footer")) {
            finalLore.add(ChatUtils.colorize(line));
        }

        meta.setLore(finalLore);
        item.setItemMeta(meta);
        return item;
    }

    // =========================================================
    //  UTILIDADES
    // =========================================================

    private static String formatNumber(double value) {
        if (value >= 1_000_000_000) return DF.format(value / 1_000_000_000) + "b";
        if (value >= 1_000_000)     return DF.format(value / 1_000_000)     + "m";
        if (value >= 1_000)         return DF.format(value / 1_000)         + "k";
        return DF.format(value);
    }
}