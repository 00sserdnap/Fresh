package cl.pandress.core;

import cl.pandress.Etherium;
import cl.pandress.modules.battlepass.placeholderapi.BattlePassPlaceholder;
import cl.pandress.modules.bosses.dragon.placeholders.DragonPlaceholders;
import cl.pandress.modules.headdeath.placeholders.HeadDeathPlaceholder;
import cl.pandress.modules.rankup.placeholderapi.RankPlaceholder;
import org.bukkit.Bukkit;

/**
 * Registra todas las expansiones de PlaceholderAPI.
 * El registro se hace con un delay de 1 tick para garantizar
 * que PAPI ya terminó su propio onEnable antes de intentar
 * registrar expansiones.
 *
 * Placeholders disponibles tras el registro:
 *   %battlepass_level%       — nivel actual del pase
 *   %battlepass_xp%          — XP actual
 *   %battlepass_badge%       — distintivo Premium si aplica
 *   %ethdragon_next%         — tiempo hasta el próximo evento de dragón
 *   %ethdragon_left%         — tiempo restante del evento activo
 *   %etherium_deathcoins%    — DeathCoins del jugador
 *   %etherium_death_grave%   — tumba seleccionada
 *   %etherium_death_effect%  — efecto de muerte seleccionado
 *   %eth_rank%               — número de rango actual
 *   %eth_rank_prefix%        — prefijo del rango según config
 *   %eth_top_name_N%         — nombre del jugador en posición N del top
 *   %eth_top_rank_N%         — rango del jugador en posición N del top
 */
public class PlaceholderRegistry {

    private final Etherium plugin;
    private final ManagerHandler managers;

    public PlaceholderRegistry(Etherium plugin, ManagerHandler managers) {
        this.plugin   = plugin;
        this.managers = managers;
    }

    /**
     * Registra las expansiones con 1 tick de delay.
     * Llamar desde Etherium.onEnable() solo si PlaceholderAPI está presente.
     */
    public void registerAll() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            plugin.log("&ePlaceholderAPI no encontrado — placeholders no registrados.");
            return;
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            new BattlePassPlaceholder().register();
            new DragonPlaceholders(plugin, managers.getDragonScheduleManager()).register();
            new HeadDeathPlaceholder(plugin).register();
            new RankPlaceholder().register();

            plugin.log("&aMódulo interno de Placeholders anclado correctamente a PlaceholderAPI.");
        }, 1L);
    }
}