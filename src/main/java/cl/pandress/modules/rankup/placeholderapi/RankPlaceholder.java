package cl.pandress.modules.rankup.placeholderapi;

import cl.pandress.Etherium;
import cl.pandress.modules.rankup.RankManager;
import cl.pandress.utils.ChatUtils;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;

public class RankPlaceholder extends PlaceholderExpansion {
    private final Etherium plugin = Etherium.getInstance();

    public RankPlaceholder() {
    }

    public @NotNull String getIdentifier() {
        return "eth";
    }

    public @NotNull String getAuthor() {
        return "pandress";
    }

    public @NotNull String getVersion() {
        return "1.0";
    }

    public boolean persist() {
        return true;
    }

    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) return "";
        RankManager manager = this.plugin.getManagerHandler().getRankManager();

        // Devuelve el número del rango
        if (params.equalsIgnoreCase("rank")) {
            return String.valueOf(manager.getPlayerRank(player.getUniqueId()));
        } 
        // NUEVO: Devuelve el prefijo del rango
        else if (params.equalsIgnoreCase("rank_prefix")) {
            int rank = manager.getPlayerRank(player.getUniqueId());
            FileConfiguration config = manager.getConfig();
            
            // Si el jugador es rango 0 (no ha subido nada), mostramos el prefijo por defecto
            if (rank == 0) {
                return ChatUtils.colorize(config.getString("settings.default_prefix", "&7[Usuario]"));
            }
            
            // Si tiene rango, busca su prefijo en la config
            String path = "ranks." + rank + ".prefix";
            return ChatUtils.colorize(config.getString(path, "&7[Rango " + rank + "]"));
        } 
        // Lógica del Top
        else if (params.startsWith("top_")) {
            List<Map.Entry<UUID, Integer>> top = manager.getTopRanks();
            String[] split = params.split("_");
            if (split.length < 3) {
                return "---";
            }

            try {
                int index = Integer.parseInt(split[2]) - 1;
                if (index >= top.size() || index < 0) {
                    return "---";
                }

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

        return null;
    }
}