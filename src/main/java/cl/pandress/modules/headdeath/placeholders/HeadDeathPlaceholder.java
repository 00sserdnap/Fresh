package cl.pandress.modules.headdeath.placeholders;

import cl.pandress.Etherium;
import cl.pandress.modules.headdeath.data.PlayerCosmetics;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

public class HeadDeathPlaceholder extends PlaceholderExpansion {

    private final Etherium plugin;

    public HeadDeathPlaceholder(Etherium plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "etherium";
    }

    @Override
    public @NotNull String getAuthor() {
        return plugin.getDescription().getAuthors().toString();
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) return "";

        PlayerCosmetics cosmetics = plugin.getManagerHandler().getHeadDeathManager().getCosmetics(player.getUniqueId());

        switch (params.toLowerCase()) {
            case "deathcoins":
                return String.valueOf(cosmetics.getCoins());
            case "death_grave":
                return cosmetics.getSelectedGrave();
            case "death_effect":
                return cosmetics.getSelectedEffect();
            default:
                return null;
        }
    }
}