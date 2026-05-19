package cl.pandress.modules.headdeath.gui;

import cl.pandress.modules.headdeath.HeadDeathManager;
import cl.pandress.modules.headdeath.data.PlayerCosmetics;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

public class CosmeticsGUIListener implements Listener {

    private final HeadDeathManager manager;
    private final CosmeticsGUI gui;

    public CosmeticsGUIListener(HeadDeathManager manager, CosmeticsGUI gui) {
        this.manager = manager;
        this.gui = gui;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player p)) return;
        
        String title = event.getView().getTitle();

        // Identificar en qué menú estamos
        boolean isMainMenu = title.equals(gui.getTitle("main.title"));
        boolean isGraveCategory = title.equals(gui.getTitle("graves_category.title"));
        boolean isEffectCategory = title.equals(gui.getTitle("effects_category.title"));
        
        boolean isGraveList = title.contains(gui.getTitle("lists.graves_unlocked_title")) || 
                              title.contains(gui.getTitle("lists.graves_coins_title")) || 
                              title.contains(gui.getTitle("lists.graves_vip_title"));
                              
        boolean isEffectList = title.contains(gui.getTitle("lists.effects_unlocked_title")) || 
                               title.contains(gui.getTitle("lists.effects_coins_title")) || 
                               title.contains(gui.getTitle("lists.effects_vip_title"));

        // Si no es ninguno de nuestros menús, ignorar
        if (!isMainMenu && !isGraveCategory && !isEffectCategory && !isGraveList && !isEffectList) {
            return;
        }

        // Cancelar el evento para que no puedan robar ítems
        event.setCancelled(true);

        // EXTRA SEGURIDAD: Evitar que procese clicks en espacios vacíos o en los cristales de adorno
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR || clicked.getType() == Material.BLACK_STAINED_GLASS_PANE) {
            return;
        }

        // NAVEGACIÓN: MENÚ PRINCIPAL -> CATEGORÍAS
        if (isMainMenu) {
            if (event.getRawSlot() == gui.getSlot("main.items.graves_category")) {
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                gui.openCategoryMenu(p, true);
            } else if (event.getRawSlot() == gui.getSlot("main.items.effects_category")) {
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                gui.openCategoryMenu(p, false);
            }
            return;
        }

        // NAVEGACIÓN: CATEGORÍAS TUMBAS -> LISTAS
        if (isGraveCategory) {
            handleCategoryClicks(p, event.getRawSlot(), true, "graves_category");
            return;
        }

        // NAVEGACIÓN: CATEGORÍAS EFECTOS -> LISTAS
        if (isEffectCategory) {
            handleCategoryClicks(p, event.getRawSlot(), false, "effects_category");
            return;
        }

        // NAVEGACIÓN Y COMPRAS: LISTAS FILTRADAS
        if (isGraveList || isEffectList) {
            if (event.getRawSlot() == gui.getSlot("lists.items.back")) {
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                gui.openCategoryMenu(p, isGraveList);
                return;
            }
            handleCosmeticSelection(p, event.getRawSlot(), isGraveList, title);
        }
    }

    private void handleCategoryClicks(Player p, int slot, boolean isGrave, String categoryPath) {
        if (slot == gui.getSlot(categoryPath + ".items.back")) {
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            gui.openMainMenu(p);
        } else if (slot == gui.getSlot(categoryPath + ".items.unlocked")) {
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            gui.openListMenu(p, isGrave, CosmeticsGUI.FilterType.UNLOCKED);
        } else if (slot == gui.getSlot(categoryPath + ".items.coins")) {
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            gui.openListMenu(p, isGrave, CosmeticsGUI.FilterType.COINS);
        } else if (slot == gui.getSlot(categoryPath + ".items.vip")) {
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            gui.openListMenu(p, isGrave, CosmeticsGUI.FilterType.VIP);
        }
    }

    private void handleCosmeticSelection(Player p, int slot, boolean isGrave, String currentTitle) {
        PlayerCosmetics cosmetics = manager.getCosmetics(p.getUniqueId());
        CosmeticsGUI.CosmeticItem selectedItemInfo;
        
        if (isGrave) {
            selectedItemInfo = gui.getGraveItems().get(slot);
        } else {
            selectedItemInfo = gui.getEffectItems().get(slot);
        }

        // Verificación de seguridad adicional
        if (selectedItemInfo == null) return;

        boolean hasPerm = selectedItemInfo.permission == null || selectedItemInfo.permission.isEmpty() || p.hasPermission(selectedItemInfo.permission);
        if (!hasPerm) {
            p.sendMessage(manager.getRawMsg("gui.no-permission"));
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        boolean isUnlockedInDB = isGrave ? cosmetics.hasGraveUnlocked(selectedItemInfo.id) : cosmetics.hasEffectUnlocked(selectedItemInfo.id);
        boolean unlocked = isUnlockedInDB || (!selectedItemInfo.requiresCoins && selectedItemInfo.price == 0 && hasPerm);

        // Detectar en qué filtro estamos para recargar el mismo menú tras la compra
        CosmeticsGUI.FilterType currentFilter = CosmeticsGUI.FilterType.UNLOCKED;
        if (currentTitle.equals(gui.getTitle("lists.graves_coins_title")) || currentTitle.equals(gui.getTitle("lists.effects_coins_title"))) {
            currentFilter = CosmeticsGUI.FilterType.COINS;
        } else if (currentTitle.equals(gui.getTitle("lists.graves_vip_title")) || currentTitle.equals(gui.getTitle("lists.effects_vip_title"))) {
            currentFilter = CosmeticsGUI.FilterType.VIP;
        }

        if (unlocked) {
            if (isGrave) {
                cosmetics.setSelectedGrave(selectedItemInfo.id);
            } else {
                cosmetics.setSelectedEffect(selectedItemInfo.id);
            }
            
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 2.0f);
            p.sendMessage(manager.getRawMsg("gui.equipped").replace("{cosmetic}", selectedItemInfo.displayName));
            manager.savePlayerCosmetics();
            
            gui.openListMenu(p, isGrave, currentFilter);
            
        } else {
            if (cosmetics.getCoins() >= selectedItemInfo.price) {
                cosmetics.removeCoins(selectedItemInfo.price);
                
                if (isGrave) {
                    cosmetics.addUnlockedGrave(selectedItemInfo.id);
                    cosmetics.setSelectedGrave(selectedItemInfo.id);
                } else {
                    cosmetics.addUnlockedEffect(selectedItemInfo.id);
                    cosmetics.setSelectedEffect(selectedItemInfo.id);
                }

                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_YES, 1.0f, 1.0f);
                p.sendMessage(manager.getRawMsg("gui.bought").replace("{cosmetic}", selectedItemInfo.displayName));
                manager.savePlayerCosmetics();
                
                gui.openListMenu(p, isGrave, currentFilter);
                
            } else {
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                p.sendMessage(manager.getRawMsg("gui.no-coins").replace("{price}", String.valueOf(selectedItemInfo.price)));
            }
        }
    }
}