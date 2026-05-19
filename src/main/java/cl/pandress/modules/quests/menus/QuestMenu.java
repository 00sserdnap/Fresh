package cl.pandress.modules.quests.menus;

import cl.pandress.Etherium;
import cl.pandress.modules.quests.QuestManager;
import cl.pandress.utils.ChatUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class QuestMenu {

    // ── Caché de nombres UUID→nombre para el top global ───────────────────────
    // getOfflinePlayer().getName() lee el archivo NBT del jugador desde disco
    // cada vez que se llama. Con 10-30 entradas en el top, eso son 10-30 lecturas
    // de disco en el hilo principal cada vez que alguien abre el menú.
    // La caché evita esas lecturas: el nombre se guarda la primera vez y se
    // reutiliza en adelante. Los jugadores online siempre actualizan su entrada.
    private static final Map<UUID, String> nameCache = new HashMap<>();

    /** Llamar en onPlayerJoin y onPlayerQuit para mantener la caché actualizada. */
    public static void cachePlayerName(UUID uuid, String name) {
        nameCache.put(uuid, name);
    }

    private static String getCachedName(UUID uuid) {
        // 1. Si está online, nombre garantizado y actualiza caché
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            nameCache.put(uuid, online.getName());
            return online.getName();
        }
        // 2. Si ya está en caché, usarlo directamente (sin leer disco)
        if (nameCache.containsKey(uuid)) return nameCache.get(uuid);
        // 3. Último recurso: leer desde Bukkit UNA sola vez y cachear
        String name = Bukkit.getOfflinePlayer(uuid).getName();
        if (name == null) name = "Desconocido";
        nameCache.put(uuid, name);
        return name;
    }

    public static void open(Player player) {
        open(player, 1);
    }

    public static void open(Player player, int page) {
        Inventory inv = Bukkit.createInventory(null, 27, ChatUtils.colorize("&8Misiones Diarias"));
        QuestManager manager = Etherium.getInstance().getManagerHandler().getQuestManager();
        int currentLevel = manager.getPlayerDailyLevel(player.getUniqueId());

        inv.setItem(12, getQuestItem(currentLevel, player, manager));
        inv.setItem(14, getStatsItem(player, manager, page));

        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glass.getItemMeta();
        if (glassMeta != null) {
            glassMeta.setDisplayName(" ");
            glass.setItemMeta(glassMeta);
        }
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) inv.setItem(i, glass);
        }

        player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.8f, 1.2f);
        player.openInventory(inv);
    }

    private static ItemStack getStatsItem(Player player, QuestManager manager, int page) {
        ItemStack item = new ItemStack(Material.BELL);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.setDisplayName(ChatUtils.colorize("&e&lTop Misiones & Estadísticas"));
        List<String> lore = new ArrayList<>();
        lore.add(ChatUtils.colorize("&7Total global de misiones completadas."));
        lore.add("");

        List<Map.Entry<UUID, Integer>> allTop = manager.getTopGlobalMissions();
        if (allTop.size() > 30) allTop = allTop.subList(0, 30);

        int maxPages = Math.max(1, (int) Math.ceil(allTop.size() / 10.0));
        page = Math.max(1, Math.min(page, maxPages));

        lore.add(ChatUtils.colorize("&6&lTOP GLOBAL (Pág " + page + "/" + maxPages + "):"));

        if (allTop.isEmpty()) {
            lore.add(ChatUtils.colorize("&cNo hay misiones completadas aún."));
        } else {
            int startIndex = (page - 1) * 10;
            int endIndex   = Math.min(startIndex + 10, allTop.size());

            for (int i = startIndex; i < endIndex; i++) {
                Map.Entry<UUID, Integer> entry = allTop.get(i);
                // Usa la caché — sin lecturas de disco en el hilo principal
                String playerName = getCachedName(entry.getKey());
                lore.add(ChatUtils.colorize("&e" + (i + 1) + ". &f" + playerName + " &8- &a" + entry.getValue()));
            }
        }

        lore.add("");
        lore.add(ChatUtils.colorize("&e&lTUS ESTADÍSTICAS:"));
        lore.add(ChatUtils.colorize("&fCompletadas: &a" + manager.getGlobalCompleted(player.getUniqueId())));
        lore.add("");
        lore.add(ChatUtils.colorize("&7Click Izquierdo &8» &fAvanzar Pág"));
        lore.add(ChatUtils.colorize("&7Click Derecho &8» &fRetroceder Pág"));
        lore.add(ChatUtils.colorize("&0PAGE:" + page));

        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack getQuestItem(int level, Player player, QuestManager manager) {
        FileConfiguration config = manager.getConfig();
        ItemStack item;
        ItemMeta meta;

        if (level == 11) {
            item = new ItemStack(Material.NETHER_STAR);
            meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatUtils.colorize("&d&l¡Recompensa Bonus!"));
                List<String> lore = new ArrayList<>();
                lore.add(ChatUtils.colorize("&7Has completado todas las misiones de hoy."));
                lore.add("");
                lore.add(ChatUtils.colorize("&fRecompensa:"));
                lore.add(ChatUtils.colorize("&d★ &7¡Reclama para saber qué ganaste!"));
                lore.add("");
                lore.add(ChatUtils.colorize("&a¡Click para reclamar!"));
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
            return item;
        }

        if (level >= 12) {
            item = new ItemStack(Material.EMERALD);
            meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatUtils.colorize(config.getString("messages.completed-all", "&a&l¡Completadas!")));
                List<String> lore = new ArrayList<>();
                for (String line : config.getStringList("messages.completed-lore")) lore.add(ChatUtils.colorize(line));
                meta.setLore(lore);
                item.setItemMeta(meta);
            }
            return item;
        }

        String questKey = manager.getActiveQuestKey(level);
        if (questKey == null) return new ItemStack(Material.PAPER);

        String path   = "quest-pool." + questKey;
        String matStr = config.getString(path + ".material", "PAPER");
        item = new ItemStack(Material.matchMaterial(matStr) != null ? Material.valueOf(matStr) : Material.PAPER);

        meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatUtils.colorize("&8[&e" + level + "&8/&e10&8] " + config.getString(path + ".name")));
            List<String> lore = new ArrayList<>();
            for (String line : config.getStringList(path + ".lore")) lore.add(ChatUtils.colorize(line));

            int progress = manager.getProgress(player.getUniqueId());
            int required = config.getInt(path + ".action-amount");
            lore.add("");
            lore.add(ChatUtils.colorize("&7Progreso: &e" + progress + "&8/&e" + required));
            lore.add(ChatUtils.colorize(progress >= required ? "&a¡Click para reclamar!" : "&cMisión en progreso..."));

            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }
}