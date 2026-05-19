package cl.pandress.modules.battlepass.menus;

import cl.pandress.Etherium;
import cl.pandress.modules.battlepass.BattlePassManager;
import cl.pandress.utils.ChatUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PassRewardsMenu {

    public static void open(Player player, int page) {
        BattlePassManager bp = Etherium.getInstance().getManagerHandler().getBattlePassManager();
        FileConfiguration cfg = bp.getMenuRewards();
        FileConfiguration bpCfg = bp.getConfig();
        UUID uuid = player.getUniqueId();

        String title = cfg.getString("title-prefix") + page;
        Inventory inv = Bukkit.createInventory(null, cfg.getInt("size", 54), ChatUtils.colorize(title));

        int maxLevels = bpCfg.getInt("settings.max-level", 54);
        int maxPages = (int) Math.ceil((double) maxLevels / 9.0);
        int startLevel = ((page - 1) * 9) + 1;
        int endLevel = Math.min(startLevel + 8, maxLevels);

        int slotGratis = 9; 
        int slotPremium = 27; 
        int currentXp = bp.getXp(uuid);
        boolean hasPremium = bp.hasPremium(player);

        for (int lvl = startLevel; lvl <= endLevel; lvl++) {
            boolean unlocked = bp.getLevel(uuid) >= lvl;
            int requiredXp = bpCfg.getInt("levels." + lvl + ".xp-required", 500);
            
            // Fila Gratis (Intercalada)
            if (bpCfg.contains("levels." + lvl + ".free")) {
                boolean claimedF = bp.isClaimed(uuid, lvl, false);
                String descF = bpCfg.getString("levels." + lvl + ".free.description", "&7Recompensa");
                inv.setItem(slotGratis, getRewardItem(cfg, lvl, false, unlocked, claimedF, currentXp, requiredXp, descF, true));
            }
            // Si no hay recompensa gratis en el YAML, se queda vacío y el cristal de fondo lo cubrirá.
            slotGratis++;

            // Fila Premium (Todos los niveles)
            if (bpCfg.contains("levels." + lvl + ".premium")) {
                boolean claimedP = bp.isClaimed(uuid, lvl, true);
                String descP = bpCfg.getString("levels." + lvl + ".premium.description", "&7Recompensa");
                inv.setItem(slotPremium, getRewardItem(cfg, lvl, true, unlocked, claimedP, currentXp, requiredXp, descP, hasPremium));
            }
            slotPremium++;
        }

        inv.setItem(cfg.getInt("items.volver.slot"), PassMainMenu.getItem(cfg, "items.volver"));
        
        ItemStack info = PassMainMenu.getItem(cfg, "items.info");
        ItemMeta infoMeta = info.getItemMeta();
        infoMeta.setDisplayName(infoMeta.getDisplayName().replace("%nivel%", String.valueOf(bp.getLevel(uuid))));
        info.setItemMeta(infoMeta);
        inv.setItem(cfg.getInt("items.info.slot"), info);

        if (page > 1) inv.setItem(cfg.getInt("items.anterior.slot"), PassMainMenu.getItem(cfg, "items.anterior"));
        if (page < maxPages) {
            ItemStack next = PassMainMenu.getItem(cfg, "items.siguiente");
            ItemMeta nextMeta = next.getItemMeta();
            List<String> lore = nextMeta.getLore() == null ? new ArrayList<>() : nextMeta.getLore();
            lore.add(ChatUtils.colorize(cfg.getString("formats.page-indicator").replace("%page%", String.valueOf(page + 1))));
            nextMeta.setLore(lore);
            next.setItemMeta(nextMeta);
            inv.setItem(cfg.getInt("items.siguiente.slot"), next);
        }

        PassMainMenu.fillGlass(inv);
        bp.playSound(player, "sounds.turn-page");
        player.openInventory(inv);
    }

    private static ItemStack getRewardItem(FileConfiguration cfg, int level, boolean isPremium, boolean unlocked, boolean claimed, int currentXp, int requiredXp, String desc, boolean hasPremiumPerm) {
        Material mat;
        boolean readyToClaim = unlocked && (!isPremium || hasPremiumPerm);

        // Selección de Vagoneta
        if (claimed) {
            mat = Material.matchMaterial(cfg.getString("formats.claimed-material", "HOPPER_MINECART"));
        } else if (readyToClaim) {
            mat = Material.matchMaterial(cfg.getString("formats.ready-material", "FURNACE_MINECART"));
        } else {
            mat = Material.matchMaterial(cfg.getString("formats.locked-material", "MINECART"));
        }

        ItemStack item = new ItemStack(mat != null ? mat : Material.MINECART);
        ItemMeta meta = item.getItemMeta();
        List<String> lore = new ArrayList<>();
        
        if (claimed) {
            meta.setDisplayName(ChatUtils.colorize(cfg.getString("formats.claimed-name").replace("%level%", String.valueOf(level))));
            for (String l : cfg.getStringList("formats.claimed-lore")) lore.add(ChatUtils.colorize(l.replace("%reward_desc%", desc)));
            
        } else if (!readyToClaim) {
            
            // Si el problema es que NO tiene el pase premium comprado, pero SI tiene el nivel:
            if (isPremium && unlocked && !hasPremiumPerm) {
                meta.setDisplayName(ChatUtils.colorize(cfg.getString("formats.locked-premium-name").replace("%level%", String.valueOf(level))));
                for (String l : cfg.getStringList("formats.locked-premium-lore")) {
                    lore.add(ChatUtils.colorize(l.replace("%reward_desc%", desc)));
                }
            } else {
                // Si el problema es que le falta XP
                meta.setDisplayName(ChatUtils.colorize(cfg.getString("formats.blocked-name").replace("%level%", String.valueOf(level))));
                int xpRestante = Math.max(0, requiredXp - currentXp);
                for (String l : cfg.getStringList("formats.blocked-lore")) {
                    lore.add(ChatUtils.colorize(l
                        .replace("%reward_desc%", desc)
                        .replace("%current%", String.valueOf(currentXp))
                        .replace("%required%", String.valueOf(requiredXp))
                        .replace("%missing%", String.valueOf(xpRestante))
                    ));
                }
            }
            
        } else {
            // Listo para reclamar
            String type = isPremium ? cfg.getString("formats.type-premium") : cfg.getString("formats.type-free");
            meta.setDisplayName(ChatUtils.colorize(cfg.getString("formats.unlocked-name")
                .replace("%level%", String.valueOf(level))
                .replace("%type%", type)
            ));
            for (String l : cfg.getStringList("formats.unlocked-lore")) lore.add(ChatUtils.colorize(l.replace("%reward_desc%", desc)));
            
            meta.addEnchant(Enchantment.UNBREAKING, 1, true); // Brillo encantado
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }
}