package cl.pandress.modules.battlepass.menus;

import cl.pandress.Etherium;
import cl.pandress.modules.battlepass.BattlePassManager;
import cl.pandress.utils.ChatUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BattlePassMenus {

    public static void openMain(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, ChatUtils.colorize("&8Menu | BattlePass"));
        BattlePassManager bp = Etherium.getInstance().getManagerHandler().getBattlePassManager();
        UUID uuid = player.getUniqueId();

        inv.setItem(11, createItem(Material.NETHER_STAR, "&e&lMisiones del Pase", "&7Ver misiones disponibles", "&7separadas por dificultad."));

        int currentLvl = bp.getLevel(uuid);
        int currentXp = bp.getXp(uuid);
        int reqXp = bp.getConfig().getInt("levels." + (currentLvl + 1) + ".xp-required", 500);
        String passType = bp.hasPremium(player) ? "&dPremium" : "&7Gratis";
        
        inv.setItem(13, createItem(Material.BOOK, "&a&lTu Información", 
                "&fNivel Actual: &e" + currentLvl, 
                "&fExperiencia: &b" + currentXp + "&8/&b" + reqXp + " XP",
                "&fTipo de Pase: " + passType));

        inv.setItem(15, createItem(Material.FEATHER, "&d&lVer Pase de Batalla", "&7Haz clic para reclamar", "&7tus recompensas por nivel."));
        inv.setItem(22, createItem(Material.IRON_DOOR, "&cSalir", "&7Cerrar el menú."));

        fillGlass(inv);
        player.openInventory(inv);
    }

    public static void openCategories(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, ChatUtils.colorize("&8Categorías de Misiones"));
        
        inv.setItem(11, createItem(Material.LIME_DYE, "&a&lFácil", "&7Misiones de completado rápido."));
        inv.setItem(13, createItem(Material.ORANGE_DYE, "&6&lMedio", "&7Retos intermedios."));
        inv.setItem(15, createItem(Material.RED_DYE, "&c&lDifícil", "&7Para jugadores experimentados."));
        inv.setItem(22, createItem(Material.IRON_DOOR, "&cVolver al Menú"));

        fillGlass(inv);
        player.openInventory(inv);
    }

    public static void openCategoryMissions(Player player, String difficulty, int page) {
        String diffName = difficulty.substring(0, 1).toUpperCase() + difficulty.substring(1);
        Inventory inv = Bukkit.createInventory(null, 54, ChatUtils.colorize("&8Misiones: " + diffName + " | Pág " + page));
        BattlePassManager bp = Etherium.getInstance().getManagerHandler().getBattlePassManager();
        FileConfiguration cfg = bp.getConfig();
        
        List<String> missions = bp.getMissionsByCategory(difficulty);
        int missionsPerPage = 45;
        int maxPages = (int) Math.ceil((double) missions.size() / missionsPerPage);
        if (maxPages == 0) maxPages = 1;
        
        int startIndex = (page - 1) * missionsPerPage;
        int endIndex = Math.min(startIndex + missionsPerPage, missions.size());
        
        int slot = 0;
        for (int i = startIndex; i < endIndex; i++) {
            String mKey = missions.get(i);
            String path = "missions-pool." + mKey;
            int req = cfg.getInt(path + ".action-amount");
            int prog = bp.getMissionProgress(player.getUniqueId(), mKey);
            int xp = cfg.getInt(path + ".xp-reward");
            String matStr = cfg.getString(path + ".material", "PAPER");
            Material mat = Material.matchMaterial(matStr) != null ? Material.valueOf(matStr) : Material.PAPER;
            
            String status = prog >= req ? "&a&lCOMPLETADA" : "&e" + prog + "&8/&e" + req;
            
            inv.setItem(slot++, createItem(mat, cfg.getString(path + ".name"), 
                    cfg.getString(path + ".lore"), "", 
                    "&fRecompensa: &d" + xp + " XP", 
                    "&fProgreso: " + status));
        }
        
        if (page > 1) inv.setItem(45, createItem(Material.ARROW, "&ePágina Anterior"));
        if (page < maxPages) inv.setItem(53, createItem(Material.ARROW, "&eSiguiente Página", "&0PAGE:" + (page + 1)));
        
        inv.setItem(49, createItem(Material.IRON_DOOR, "&cVolver a Categorías"));
        fillGlass(inv);
        player.openInventory(inv);
    }

    public static void openPass(Player player, int page) {
        Inventory inv = Bukkit.createInventory(null, 54, ChatUtils.colorize("&8Pase de Batalla | Pág " + page));
        BattlePassManager bp = Etherium.getInstance().getManagerHandler().getBattlePassManager();
        FileConfiguration cfg = bp.getConfig();
        UUID uuid = player.getUniqueId();

        int maxLevels = cfg.getInt("settings.max-level", 50);
        int maxPages = (int) Math.ceil((double) maxLevels / 9.0);
        
        int startLevel = ((page - 1) * 9) + 1;
        int endLevel = Math.min(startLevel + 8, maxLevels);

        int slotGratis = 9; 
        int slotPremium = 27; 

        for (int lvl = startLevel; lvl <= endLevel; lvl++) {
            boolean unlocked = bp.getLevel(uuid) >= lvl;
            boolean claimedF = bp.isClaimed(uuid, lvl, false);
            Material matF = Material.matchMaterial(cfg.getString("levels." + lvl + ".free.material", "GLASS_PANE"));
            inv.setItem(slotGratis, getRewardItem(lvl, matF, false, unlocked, claimedF));

            boolean claimedP = bp.isClaimed(uuid, lvl, true);
            Material matP = Material.matchMaterial(cfg.getString("levels." + lvl + ".premium.material", "GLASS_PANE"));
            inv.setItem(slotPremium, getRewardItem(lvl, matP, true, unlocked, claimedP));

            slotGratis++; slotPremium++;
        }

        if (page > 1) inv.setItem(45, createItem(Material.ARROW, "&ePágina Anterior"));
        if (page < maxPages) inv.setItem(53, createItem(Material.ARROW, "&eSiguiente Página", "&0PAGE:" + (page + 1)));
        
        inv.setItem(49, createItem(Material.BOOK, "&aTu Nivel: &e" + bp.getLevel(uuid)));
        inv.setItem(48, createItem(Material.IRON_DOOR, "&cVolver al Menú"));

        fillGlass(inv);
        player.openInventory(inv);
    }

    private static ItemStack getRewardItem(int level, Material mat, boolean isPremium, boolean unlocked, boolean claimed) {
        if (claimed) return createItem(Material.GREEN_STAINED_GLASS_PANE, "&a&lNivel " + level + " (Reclamado)");
        if (!unlocked) return createItem(isPremium ? Material.PURPLE_STAINED_GLASS_PANE : Material.WHITE_STAINED_GLASS_PANE, 
                                         "&c&lNivel " + level + " (Bloqueado)", "&7Sube al nivel " + level + " para revelar.");
        return createItem(mat != null ? mat : Material.CHEST, 
                "&e&lNivel " + level + (isPremium ? " &d[PREMIUM]" : " &7[GRATIS]"), 
                "&a¡Click para reclamar tu recompensa!");
    }

    private static ItemStack createItem(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatUtils.colorize(name));
        List<String> loreList = new ArrayList<>();
        for (String l : lore) loreList.add(ChatUtils.colorize(l));
        meta.setLore(loreList);
        item.setItemMeta(meta);
        return item;
    }

    private static void fillGlass(Inventory inv) {
        ItemStack glass = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) inv.setItem(i, glass);
        }
    }
}