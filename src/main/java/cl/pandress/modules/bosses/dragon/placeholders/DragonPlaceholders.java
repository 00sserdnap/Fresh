package cl.pandress.modules.bosses.dragon.placeholders;

import cl.pandress.Etherium;
import cl.pandress.modules.bosses.dragon.managers.DragonScheduleManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class DragonPlaceholders extends PlaceholderExpansion {

    private final Etherium plugin;
    private final DragonScheduleManager scheduleManager;

    public DragonPlaceholders(Etherium plugin, DragonScheduleManager scheduleManager) {
        this.plugin = plugin;
        this.scheduleManager = scheduleManager;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "ethdragon";
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
    public String onPlaceholderRequest(Player player, @NotNull String params) {
        if (params.equalsIgnoreCase("next")) {
            return scheduleManager.getFormattedTimeUntilNext();
        }
        if (params.equalsIgnoreCase("left")) {
            return scheduleManager.getFormattedTimeLeft();
        }
        return null;
    }
}