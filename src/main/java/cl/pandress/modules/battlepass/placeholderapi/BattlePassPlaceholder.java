package cl.pandress.modules.battlepass.placeholderapi;

import cl.pandress.Etherium;
import cl.pandress.modules.battlepass.BattlePassManager;
import cl.pandress.utils.ChatUtils;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class BattlePassPlaceholder extends PlaceholderExpansion {

    @Override
    public @NotNull String getIdentifier() {
        return "battlepass";
    }

    @Override
    public @NotNull String getAuthor() {
        return "pandress";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0";
    }

    @Override
    public boolean persist() {
        return true; 
    }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) {
            return "";
        }

        BattlePassManager bp = Etherium.getInstance().getManagerHandler().getBattlePassManager();

        // --- VERIFICACIÓN DE MÓDULO APAGADO ---
        if (!bp.getConfig().getBoolean("settings.enabled", true)) {
            if (params.equalsIgnoreCase("badge")) return ""; 
            return "Desactivado"; // Devuelve "Desactivado" para nivel/xp
        }

        // 1. Placeholder del Distintivo: %battlepass_badge%
        if (params.equalsIgnoreCase("badge")) {
            if (bp.hasPremium(player)) {
                String badge = bp.getConfig().getString("settings.premium-badge", "&8[&e⭐&8]");
                return ChatUtils.colorize(badge);
            } else {
                return ""; 
            }
        }

        // 2. Placeholder del Nivel: %battlepass_level%
        if (params.equalsIgnoreCase("level")) {
            return String.valueOf(bp.getLevel(player.getUniqueId()));
        }

        // 3. Placeholder de la Experiencia: %battlepass_xp%
        if (params.equalsIgnoreCase("xp")) {
            return String.valueOf(bp.getXp(player.getUniqueId()));
        }

        return null;
    }
}