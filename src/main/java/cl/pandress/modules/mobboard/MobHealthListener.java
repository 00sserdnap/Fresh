package cl.pandress.modules.mobboard;

import cl.pandress.Fresh;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.RayTraceResult;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Módulo MobBoard: Muestra una barra de salud pura al apuntar a entidades.
 * Minimalista: Sin nombres ni números, solo la barra de progreso.
 * Developed by: pandress
 */
public class MobHealthListener implements Listener {

    private final Map<UUID, BossBar> activeBars = new HashMap<>();

    @EventHandler
    public void onPlayerLook(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        
        // Carga de filtros desde el archivo config.yml
        boolean showPlayers = Fresh.getInstance().getConfig().getBoolean("modules.mobboard.show-players", true);
        boolean showMobs = Fresh.getInstance().getConfig().getBoolean("modules.mobboard.show-mobs", true);
        double distance = Fresh.getInstance().getConfig().getDouble("modules.mobboard.distance", 10.0);

        // Detección de la entidad mediante RayTrace
        RayTraceResult result = player.getWorld().rayTraceEntities(
                player.getEyeLocation(),
                player.getEyeLocation().getDirection(),
                distance,
                entity -> {
                    if (!(entity instanceof LivingEntity) || entity.equals(player)) return false;
                    
                    // Filtrado por tipo de entidad
                    if (entity instanceof Player) return showPlayers;
                    return showMobs;
                }
        );

        if (result != null && result.getHitEntity() instanceof LivingEntity target) {
            updateBossBar(player, target);
        } else {
            removeBossBar(player);
        }
    }

    /**
     * Actualiza o crea la BossBar para el jugador.
     */
    private void updateBossBar(Player player, LivingEntity target) {
        double health = target.getHealth();
        double maxHealth = target.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
        
        // Cálculo del progreso de la barra (0.0 a 1.0)
        double progress = Math.clamp(health / maxHealth, 0.0, 1.0);

        // Selección de color dinámico según la salud restante
        BarColor barColor = (progress > 0.5) ? BarColor.GREEN : (progress > 0.25) ? BarColor.YELLOW : BarColor.RED;

        // Se crea la barra con título vacío (" ") para ocultar texto innecesario
        BossBar bossBar = activeBars.computeIfAbsent(player.getUniqueId(), k -> 
            Bukkit.createBossBar(" ", barColor, BarStyle.SOLID));

        bossBar.setProgress(progress);
        bossBar.setColor(barColor);
        
        // Asegurar que el jugador vea la barra
        if (!bossBar.getPlayers().contains(player)) {
            bossBar.addPlayer(player);
        }
    }

    /**
     * Elimina la BossBar de la pantalla del jugador.
     */
    private void removeBossBar(Player player) {
        BossBar bossBar = activeBars.remove(player.getUniqueId());
        if (bossBar != null) {
            bossBar.removeAll();
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        removeBossBar(event.getPlayer());
    }
}