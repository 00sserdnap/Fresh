package cl.pandress.modules.customspawners;

import cl.pandress.Etherium;
import cl.pandress.modules.customspawners.data.CustomSpawnerData;
import cl.pandress.modules.keepchunk.data.KeepChunkData;
import cl.pandress.modules.keepchunk.KeepChunkManager;
import cl.pandress.modules.keepchunk.KeepChunkType;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Collection;
import java.util.concurrent.ThreadLocalRandom;

public class CustomSpawnerTask extends BukkitRunnable {

    private final CustomSpawnerManager manager;

    // --- OPTIMIZACIÓN: Cache de configuración ---
    // Antes se leían del FileConfiguration en cada tick (muy costoso).
    // Ahora se leen una vez al arrancar y se pueden refrescar si hace falta.
    private int maxNearby;
    private int minDelay;
    private int maxDelay;
    private int mobsPerSpawn;

    public CustomSpawnerTask(CustomSpawnerManager manager) {
        this.manager = manager;
        reloadConfigCache();
    }

    /**
     * Refresca los valores cacheados desde la config.
     * Llamar esto si el servidor hace /reload o si implementas un comando de recarga.
     */
    public void reloadConfigCache() {
        this.maxNearby   = manager.getConfig().getInt("settings.max-nearby-entities", 10);
        this.minDelay    = manager.getConfig().getInt("settings.min-delay-seconds", 10) * 1000;
        this.maxDelay    = manager.getConfig().getInt("settings.max-delay-seconds", 25) * 1000;
        this.mobsPerSpawn = manager.getConfig().getInt("settings.mobs-per-spawn", 3);
    }

    @Override
    public void run() {
        long now = System.currentTimeMillis();

        KeepChunkManager keepChunkManager = Etherium.getInstance().getManagerHandler().getKeepChunkManager();

        for (CustomSpawnerData spawner : manager.getActiveSpawners().values()) {
            Location loc = spawner.getLocation();
            World world = loc.getWorld();
            if (world == null) continue;

            boolean canSpawn = false;

            if (keepChunkManager != null) {
                int spawnerChunkX = loc.getChunk().getX();
                int spawnerChunkZ = loc.getChunk().getZ();

                for (KeepChunkData loader : keepChunkManager.getActiveLoaders().values()) {
                    if (loader.isActive() && loader.getLocation().getWorld().equals(world)) {
                        KeepChunkType type = keepChunkManager.getType(loader.getTypeId());
                        if (type != null) {
                            int radius = type.getRadius();
                            int loaderChunkX = loader.getLocation().getChunk().getX();
                            int loaderChunkZ = loader.getLocation().getChunk().getZ();

                            if (Math.abs(loaderChunkX - spawnerChunkX) <= radius &&
                                Math.abs(loaderChunkZ - spawnerChunkZ) <= radius) {
                                canSpawn = true;
                                break;
                            }
                        }
                    }
                }
            }

            // Segundo check: si el chunk está marcado como force-loaded (por KeepChunk) puede spawnear
            if (!canSpawn && loc.getChunk().isForceLoaded()) {
                canSpawn = true;
            }

            // Fallback: jugador dentro de 64 bloques (distanceSquared 4096)
            if (!canSpawn) {
                for (Player p : world.getPlayers()) {
                    if (p.getGameMode() != GameMode.SPECTATOR &&
                        p.getLocation().distanceSquared(loc) <= 4096) {
                        canSpawn = true;
                        break;
                    }
                }
            }

            if (!canSpawn) continue;

            world.spawnParticle(Particle.FLAME, loc.clone().add(0.5, 0.5, 0.5), 2, 0.2, 0.2, 0.2, 0.02);

            if (now < spawner.getNextSpawnTime()) continue;

            // Radio 16: suficiente para contar todos los mobs que spawneó este spawner en el área
            Collection<Entity> nearby = world.getNearbyEntities(loc, 16, 16, 16,
                    entity -> entity.getType() == spawner.getEntityType());

            if (nearby.size() >= maxNearby) {
                spawner.setNextSpawnTime(now + 5000);
                continue;
            }

            int spawned = 0;
            for (int i = 0; i < mobsPerSpawn; i++) {
                int blockOffsetX = ThreadLocalRandom.current().nextInt(-1, 2);
                int blockOffsetZ = ThreadLocalRandom.current().nextInt(-1, 2);

                if (blockOffsetX == 0 && blockOffsetZ == 0) {
                    if (ThreadLocalRandom.current().nextBoolean()) {
                        blockOffsetX = ThreadLocalRandom.current().nextBoolean() ? 1 : -1;
                    } else {
                        blockOffsetZ = ThreadLocalRandom.current().nextBoolean() ? 1 : -1;
                    }
                }

                double exactX = blockOffsetX + 0.2 + (ThreadLocalRandom.current().nextDouble() * 0.6);
                double exactZ = blockOffsetZ + 0.2 + (ThreadLocalRandom.current().nextDouble() * 0.6);

                Location spawnLoc = loc.clone().add(exactX, 1.0, exactZ);

                if (spawnLoc.getBlock().isPassable() &&
                    spawnLoc.clone().add(0, 1, 0).getBlock().isPassable()) {
                    Entity entity = world.spawnEntity(spawnLoc, spawner.getEntityType());
                    // Evitar despawn natural cuando no hay jugadores cerca
                    // (necesario para que funcionen con el cargador de chunks)
                    entity.setPersistent(true);
                    if (entity instanceof Mob) {
                        ((Mob) entity).setRemoveWhenFarAway(false);
                    }
                    spawned++;
                }
            }

            if (spawned > 0) {
                long nextDelay = ThreadLocalRandom.current().nextLong(minDelay, maxDelay + 1L);
                spawner.setNextSpawnTime(now + nextDelay);
            }
        }
    }
}