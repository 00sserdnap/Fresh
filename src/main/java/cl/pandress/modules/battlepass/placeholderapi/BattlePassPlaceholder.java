package cl.pandress.modules.battlepass.placeholderapi;

import cl.pandress.Fresh;
import cl.pandress.modules.battlepass.BattlePassManager;
import cl.pandress.utils.ChatUtils;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class BattlePassPlaceholder extends PlaceholderExpansion {

    @Override
    public @NotNull String getIdentifier() {
        return "freshbp"; // Este será el inicio de todos tus placeholders
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
        return true; // Mantiene el placeholder activo al recargar PAPI
    }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) {
            return "";
        }

        BattlePassManager bp = Fresh.getInstance().getManagerHandler().getBattlePassManager();

        // 1. Placeholder del Distintivo: %freshbp_badge%
        if (params.equalsIgnoreCase("badge")) {
            if (bp.hasPremium(player)) {
                // Lee el diseño desde tu config.yml
                String badge = bp.getConfig().getString("settings.premium-badge", "&8[&e⭐&8]");
                return ChatUtils.colorize(badge);
            } else {
                return ""; // Si no tiene el pase premium, no devuelve absolutamente nada
            }
        }

        // 2. Placeholder del Nivel: %freshbp_level%
        if (params.equalsIgnoreCase("level")) {
            return String.valueOf(bp.getLevel(player.getUniqueId()));
        }

        // 3. Placeholder de la Experiencia: %freshbp_xp%
        if (params.equalsIgnoreCase("xp")) {
            return String.valueOf(bp.getXp(player.getUniqueId()));
        }

        return null;
    }
}