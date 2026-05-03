package cl.pandress.modules.rankup.placeholderapi;

import cl.pandress.Fresh;
import cl.pandress.modules.rankup.RankManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class RankPlaceholder extends PlaceholderExpansion {

    private final Fresh plugin = Fresh.getInstance();

    @Override
    public @NotNull String getIdentifier() {
        return "fresh";
    }

    @Override
    public @NotNull String getAuthor() {
        return "pandress";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0";
    }

    // ¡ESTO ES VITAL! Le dice a PlaceholderAPI que no borre nuestro
    // placeholder de su memoria si alguien ejecuta /papi reload
    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        RankManager manager = plugin.getManagerHandler().getRankManager();

        if (params.equalsIgnoreCase("rank")) {
            if (player == null) return "0";
            return String.valueOf(manager.getPlayerRank(player.getUniqueId()));
        }

        // Placeholder para el Top: %fresh_top_name_1%, %fresh_top_rank_1%
        if (params.startsWith("top_")) {
            List<Map.Entry<UUID, Integer>> top = manager.getTopRanks();
            String[] split = params.split("_");
            
            // Verificamos que el formato sea correcto (ej: top_name_1)
            if (split.length < 3) return "---";
            
            try {
                int index = Integer.parseInt(split[2]) - 1;

                // Si no hay suficientes jugadores en el top, devolvemos "---"
                if (index >= top.size() || index < 0) return "---";

                if (split[1].equalsIgnoreCase("name")) {
                    String name = Bukkit.getOfflinePlayer(top.get(index).getKey()).getName();
                    return name != null ? name : "Desconocido";
                }
                
                if (split[1].equalsIgnoreCase("rank")) {
                    return String.valueOf(top.get(index).getValue());
                }
            } catch (NumberFormatException e) {
                return "---";
            }
        }

        return null; // Si devuelve null, PlaceholderAPI mostrará el texto original
    }
}