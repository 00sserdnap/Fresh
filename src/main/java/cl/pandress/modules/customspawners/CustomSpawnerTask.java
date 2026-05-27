package cl.pandress.modules.customspawners;

import cl.pandress.Etherium;
import cl.pandress.modules.customspawners.data.CustomSpawnerData;
import cl.pandress.modules.keepchunk.KeepChunkManager;
import cl.pandress.modules.keepchunk.KeepChunkType;
import cl.pandress.modules.keepchunk.data.KeepChunkData;
import java.util.Collection;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class CustomSpawnerTask extends BukkitRunnable {
    private final CustomSpawnerManager manager;
    private int maxNearby;
    private int minDelay;
    private int maxDelay;
    private int mobsPerSpawn;

    public CustomSpawnerTask(CustomSpawnerManager manager) {
        this.manager = manager;
        this.reloadConfigCache();
    }

    public void reloadConfigCache() {
        this.maxNearby = this.manager.getConfig().getInt("settings.max-nearby-entities", 10);
        this.minDelay = this.manager.getConfig().getInt("settings.min-delay-seconds", 10) * 1000;
        this.maxDelay = this.manager.getConfig().getInt("settings.max-delay-seconds", 25) * 1000;
        this.mobsPerSpawn = this.manager.getConfig().getInt("settings.mobs-per-spawn", 3);
    }

    public void run() {
        long now = System.currentTimeMillis();
        KeepChunkManager keepChunkManager = null;

        try {
            keepChunkManager = Etherium.getInstance().getManagerHandler().getKeepChunkManager();
        } catch (Exception ignored) {
            // Si el manager no está listo, no pasa nada.
        }

        for (CustomSpawnerData spawner : this.manager.getActiveSpawners().values()) {
            Location loc = spawner.getLocation();
            World world = loc.getWorld();

            if (world == null) continue;

            boolean canSpawn = false;

            // 1. COMPROBAR SI HAY JUGADOR CERCA (Dentro de 64 bloques)
            for (Player p : world.getPlayers()) {
                if (p.getGameMode() != GameMode.SPECTATOR && p.getLocation().distanceSquared(loc) <= 4096.0) {
                    canSpawn = true;
                    break;
                }
            }

            // 2. COMPROBAR SI EL CARGADOR DE CHUNKS LO ESTÁ CUBRIENDO (Solo si no hay jugador)
            if (!canSpawn && keepChunkManager != null) {
                int spawnerChunkX = loc.getBlockX() >> 4;
                int spawnerChunkZ = loc.getBlockZ() >> 4;

                for (KeepChunkData loader : keepChunkManager.getActiveLoaders().values()) {
                    if (!loader.isActive()) continue; // Si está desactivado, saltamos.

                    World loaderWorld = loader.getLocation().getWorld();
                    if (loaderWorld == null || !loaderWorld.equals(world)) continue;

                    KeepChunkType type = keepChunkManager.getType(loader.getTypeId());
                    if (type == null) continue;

                    int radius = type.getRadius();
                    int loaderChunkX = loader.getLocation().getBlockX() >> 4;
                    int loaderChunkZ = loader.getLocation().getBlockZ() >> 4;

                    // Comprobación matemática (no carga chunks accidentalmente)
                    if (Math.abs(loaderChunkX - spawnerChunkX) <= radius && Math.abs(loaderChunkZ - spawnerChunkZ) <= radius) {
                        canSpawn = true;
                        break;
                    }
                }
            }

            // 3. GENERAR EL MOB (Solo si pasó alguna de las pruebas anteriores)
            if (canSpawn) {
                // Última comprobación de seguridad: asegurarse de que el juego tiene el chunk cargado
                if (!world.isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) continue;

                // Partículas indicando que la tarea está intentando funcionar en este spawner
                world.spawnParticle(Particle.FLAME, loc.clone().add(0.5, 0.5, 0.5), 2, 0.2, 0.2, 0.2, 0.02);

                if (now >= spawner.getNextSpawnTime()) {
                    Collection<Entity> nearby = world.getNearbyEntities(
                            loc, 16.0, 16.0, 16.0,
                            (entity) -> entity.getType() == spawner.getEntityType()
                    );

                    if (nearby.size() >= this.maxNearby) {
                        spawner.setNextSpawnTime(now + 5000L);
                    } else {
                        int spawnsToAttempt = Math.max(1, this.mobsPerSpawn);

                        for (int i = 0; i < spawnsToAttempt; i++) {
                            int blockOffsetX = ThreadLocalRandom.current().nextInt(-1, 2);
                            int blockOffsetZ = ThreadLocalRandom.current().nextInt(-1, 2);

                            if (blockOffsetX == 0 && blockOffsetZ == 0) {
                                if (ThreadLocalRandom.current().nextBoolean()) {
                                    blockOffsetX = ThreadLocalRandom.current().nextBoolean() ? 1 : -1;
                                } else {
                                    blockOffsetZ = ThreadLocalRandom.current().nextBoolean() ? 1 : -1;
                                }
                            }

                            Location spawnLoc = new Location(world, loc.getBlockX() + blockOffsetX + 0.5, loc.getBlockY() + 1.0, loc.getBlockZ() + blockOffsetZ + 0.5);

                            if (spawnLoc.getBlock().isPassable() && spawnLoc.clone().add(0.0, 1.0, 0.0).getBlock().isPassable()) {
                                Entity entity = world.spawnEntity(spawnLoc, spawner.getEntityType());
                                if (entity != null) {
                                    entity.setPersistent(true);
                                    if (entity instanceof Mob) {
                                        ((Mob) entity).setRemoveWhenFarAway(false);
                                    }
                                }
                            }
                        }

                        long nextDelay = ThreadLocalRandom.current().nextLong((long) this.minDelay, (long) this.maxDelay + 1L);
                        spawner.setNextSpawnTime(now + nextDelay);
                    }
                }
            }
        }
    }
}