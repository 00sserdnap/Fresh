package cl.pandress.utils;

import cl.pandress.Fresh;
import cl.pandress.utils.ChatUtils;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.RayTraceResult;

public class MobHealthListener implements Listener {

    @EventHandler
    public void onPlayerLook(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        
        double distance = Fresh.getInstance().getConfig().getDouble("modules.mobboard.distance", 10.0);

        RayTraceResult result = player.getWorld().rayTraceEntities(
                player.getEyeLocation(),
                player.getEyeLocation().getDirection(),
                distance,
                entity -> entity instanceof LivingEntity && !entity.equals(player)
        );

        if (result != null && result.getHitEntity() instanceof LivingEntity target) {
            sendHealthBar(player, target);
        }
    }

    private void sendHealthBar(Player player, LivingEntity target) {
        double health = target.getHealth();
        double maxHealth = target.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
        
        // Reemplazo dinámico de colores legacy[cite: 1]
        String color = (health > maxHealth / 2) ? "&a" : (health > maxHealth / 4) ? "&e" : "&c";
        String name = target.getCustomName() != null ? target.getCustomName() : target.getName();
        
        String format = Fresh.getInstance().getConfig().getString("modules.mobboard.format", "&8<name>: <color><hp>&f/&c<max_hp> HP");
        
        String finalMsg = format
                .replace("<name>", name)
                .replace("<hp>", String.format("%.1f", health))
                .replace("<max_hp>", String.valueOf((int)maxHealth))
                .replace("<color>", color);

        // Enviamos al action bar usando tus utilidades de color[cite: 1, 2]
        player.sendActionBar(ChatUtils.colorize(finalMsg));
    }
}