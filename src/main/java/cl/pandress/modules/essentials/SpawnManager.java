package cl.pandress.modules.essentials;

import cl.pandress.Etherium;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;

public class SpawnManager {
    
    private final Etherium plugin;

    public SpawnManager(Etherium plugin) {
        this.plugin = plugin;
    }

    public void setSpawn(Location loc) {
        FileConfiguration config = plugin.getConfig();
        config.set("spawn.world", loc.getWorld().getName());
        config.set("spawn.x", loc.getX());
        config.set("spawn.y", loc.getY());
        config.set("spawn.z", loc.getZ());
        config.set("spawn.yaw", loc.getYaw());
        config.set("spawn.pitch", loc.getPitch());
        
        plugin.saveConfig();
    }

    public Location getSpawn() {
        FileConfiguration config = plugin.getConfig();
        
        // Verificamos si existe el mundo en la config
        if (!config.contains("spawn.world")) {
            return null;
        }

        World world = Bukkit.getWorld(config.getString("spawn.world"));
        if (world == null) return null;

        double x = config.getDouble("spawn.x");
        double y = config.getDouble("spawn.y");
        double z = config.getDouble("spawn.z");
        float yaw = (float) config.getDouble("spawn.yaw");
        float pitch = (float) config.getDouble("spawn.pitch");

        return new Location(world, x, y, z, yaw, pitch);
    }
}