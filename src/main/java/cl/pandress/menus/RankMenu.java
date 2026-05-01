package cl.pandress.menus;

import cl.pandress.Fresh;
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
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class RankMenu {

    // Formateador seguro para evitar comas raras según el idioma del servidor
    private static final DecimalFormat df = new DecimalFormat("#.#");

    public static void open(Player player) {
        RankManager manager = Fresh.getInstance().getManagerHandler().getRankManager();
        FileConfiguration config = manager.getConfig();

        String title = config.getString("settings.menu.title", "&8Menu | RankUp");
        String typeStr = config.getString("settings.menu.type", "CHEST").toUpperCase();
        Inventory inv;

        // Configuración dinámica del tipo y tamaño de inventario
        if (typeStr.equals("CHEST")) {
            int rows = config.getInt("settings.menu.rows", 3);
            int size = Math.min(6, Math.max(1, rows)) * 9;
            inv = Bukkit.createInventory(null, size, ChatUtils.colorize(title));
        } else {
            InventoryType type = InventoryType.valueOf(typeStr);
            inv = Bukkit.createInventory(null, type, ChatUtils.colorize(title));
        }

        // Sistema de Fillers (Cristales de relleno)
        if (config.getBoolean("settings.menu.items.fillers.enabled", true)) {
            String matStr = config.getString("settings.menu.items.fillers.material", "GRAY_STAINED_GLASS_PANE");
            Material mat = Material.matchMaterial(matStr);
            if (mat != null) {
                ItemStack filler = new ItemStack(mat);
                ItemMeta meta = filler.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(ChatUtils.colorize(config.getString("settings.menu.items.fillers.name", " ")));
                    filler.setItemMeta(meta);
                }
                for (int slot : config.getIntegerList("settings.menu.items.fillers.slots")) {
                    if (slot < inv.getSize()) inv.setItem(slot, filler);
                }
            }
        }

        int currentRank = manager.getPlayerRank(player.getUniqueId());
        int nextRank = currentRank + 1;

        // Botón RankUp
        int rankupSlot = config.getInt("settings.menu.items.rankup_button.slot", 12);
        if (rankupSlot < inv.getSize()) {
            inv.setItem(rankupSlot, getEmeraldItem(player, nextRank, manager, config));
        }

        // Botón Top
        int topSlot = config.getInt("settings.menu.items.top_button.slot", 14);
        if (topSlot < inv.getSize()) {
            inv.setItem(topSlot, getTopItem(manager, config));
        }

        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.8f, 1.2f);
        player.openInventory(inv);
    }

    private static ItemStack getEmeraldItem(Player player, int nextRank, RankManager manager, FileConfiguration config) {
        String pathMenu = "settings.menu.items.rankup_button.";
        String matStr = config.getString(pathMenu + "material", "EMERALD");
        ItemStack item = new ItemStack(Material.matchMaterial(matStr) != null ? Material.valueOf(matStr) : Material.EMERALD);
        ItemMeta meta = item.getItemMeta();

        String pathRank = "ranks." + nextRank;

        if (config.getConfigurationSection(pathRank) == null) {
            meta.setDisplayName(ChatUtils.colorize(config.getString(pathMenu + "name_max", "&a&lRango Máximo Alcanzado")));
        } else {
            meta.setDisplayName(ChatUtils.colorize(config.getString(pathMenu + "name", "&a&lRango #%next_rank%").replace("%next_rank%", String.valueOf(nextRank))));

            List<String> finalLore = new ArrayList<>();
            List<String> rewards = config.getStringList(pathRank + ".rewards_lore");

            for (String line : config.getStringList(pathMenu + "lore")) {
                if (line.contains("%rewards%")) {
                    for (String reward : rewards) {
                        finalLore.add(ChatUtils.colorize(" &8| &a" + reward));
                    }
                } else if (line.contains("%requirements%")) {
                    
                    // --- DINERO (VAULT) CON FORMATO k/m/b ---
                    if (config.contains(pathRank + ".requirements.money")) {
                        double reqMoney = config.getDouble(pathRank + ".requirements.money");
                        double bal = manager.getEconomy() != null ? manager.getEconomy().getBalance(player) : 0.0;
                        String color = bal >= reqMoney ? "&a" : "&c";
                        
                        // Utilizamos el formateador visual
                        String formattedMoney = formatNumber(reqMoney);
                        
                        finalLore.add(ChatUtils.colorize(" &8| &7Dinero: " + color + "$" + formattedMoney));
                    }
                    // --- HORAS JUGADAS ---
                    if (config.contains(pathRank + ".requirements.playtime_hours")) {
                        int reqHours = config.getInt(pathRank + ".requirements.playtime_hours");
                        int currentHours = player.getStatistic(Statistic.PLAY_ONE_MINUTE) / (20 * 60 * 60);
                        String color = currentHours >= reqHours ? "&a" : "&c";
                        finalLore.add(ChatUtils.colorize(" &8| &7Horas: " + color + currentHours + "&8/" + color + reqHours + "h"));
                    }
                    // --- KILLS DE JUGADORES ---
                    if (config.contains(pathRank + ".requirements.player_kills")) {
                        int req = config.getInt(pathRank + ".requirements.player_kills");
                        int cur = manager.getProgress(player.getUniqueId(), "general", "player_kills");
                        String color = cur >= req ? "&a" : "&c";
                        finalLore.add(ChatUtils.colorize(" &8| &7Kills PvP: " + color + cur + "&8/" + color + req));
                    }
                    // --- BLOQUES PICADOS ---
                    if (config.getConfigurationSection(pathRank + ".requirements.blocks_mine") != null) {
                        for (String block : config.getConfigurationSection(pathRank + ".requirements.blocks_mine").getKeys(false)) {
                            int req = config.getInt(pathRank + ".requirements.blocks_mine." + block);
                            int cur = manager.getProgress(player.getUniqueId(), "blocks_mine", block);
                            String color = cur >= req ? "&a" : "&c";
                            finalLore.add(ChatUtils.colorize(" &8| &7Picar " + block + ": " + color + cur + "&8/" + color + req));
                        }
                    }
                    // --- BLOQUES COLOCADOS ---
                    if (config.getConfigurationSection(pathRank + ".requirements.blocks_place") != null) {
                        for (String block : config.getConfigurationSection(pathRank + ".requirements.blocks_place").getKeys(false)) {
                            int req = config.getInt(pathRank + ".requirements.blocks_place." + block);
                            int cur = manager.getProgress(player.getUniqueId(), "blocks_place", block);
                            String color = cur >= req ? "&a" : "&c";
                            finalLore.add(ChatUtils.colorize(" &8| &7Colocar " + block + ": " + color + cur + "&8/" + color + req));
                        }
                    }
                    // --- MOBS MUERTOS ---
                    if (config.getConfigurationSection(pathRank + ".requirements.mob_kills") != null) {
                        for (String mob : config.getConfigurationSection(pathRank + ".requirements.mob_kills").getKeys(false)) {
                            int req = config.getInt(pathRank + ".requirements.mob_kills." + mob);
                            int cur = manager.getProgress(player.getUniqueId(), "mob_kills", mob);
                            String color = cur >= req ? "&a" : "&c";
                            finalLore.add(ChatUtils.colorize(" &8| &7Matar " + mob + ": " + color + cur + "&8/" + color + req));
                        }
                    }

                } else {
                    finalLore.add(ChatUtils.colorize(line));
                }
            }
            meta.setLore(finalLore);
        }
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack getTopItem(RankManager manager, FileConfiguration config) {
        String pathMenu = "settings.menu.items.top_button.";
        String matStr = config.getString(pathMenu + "material", "BELL");
        ItemStack item = new ItemStack(Material.matchMaterial(matStr) != null ? Material.valueOf(matStr) : Material.BELL);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(ChatUtils.colorize(config.getString(pathMenu + "name", "&6&lTop Rangos")));

        List<String> finalLore = new ArrayList<>();
        for (String line : config.getStringList(pathMenu + "lore_header")) {
            finalLore.add(ChatUtils.colorize(line));
        }

        String format = config.getString(pathMenu + "lore_format", " &8| &eRango #%pos%: &f%player% &7(%rank%)");
        int pos = 1;
        
        for (Map.Entry<UUID, Integer> entry : manager.getTopRanks()) {
            String name = Bukkit.getOfflinePlayer(entry.getKey()).getName();
            if (name == null) name = "Desconocido";
            finalLore.add(ChatUtils.colorize(format
                    .replace("%pos%", String.valueOf(pos))
                    .replace("%player%", name)
                    .replace("%rank%", String.valueOf(entry.getValue()))));
            pos++;
        }

        for (String line : config.getStringList(pathMenu + "lore_footer")) {
            finalLore.add(ChatUtils.colorize(line));
        }

        meta.setLore(finalLore);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Método interno para transformar números grandes en formato amigable (ej: 5000 -> 5k).
     */
    private static String formatNumber(double value) {
        if (value >= 1_000_000_000) return df.format(value / 1_000_000_000) + "b";
        if (value >= 1_000_000) return df.format(value / 1_000_000) + "m";
        if (value >= 1000) return df.format(value / 1000) + "k";
        return df.format(value);
    }
}