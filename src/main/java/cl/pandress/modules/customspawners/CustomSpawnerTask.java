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
            // KeepChunkManager aún no inicializado
        }

        for (CustomSpawnerData spawner : this.manager.getActiveSpawners().values()) {
            Location loc = spawner.getLocation();
            World world = loc.getWorld();

            // Seguro saltar si es nulo, porque CustomSpawnerManager ya corrigió la carga
            if (world == null) continue;

            boolean canSpawn = false;
            int bX = loc.getBlockX();
            int bZ = loc.getBlockZ();

            // ═══ CHECK 1: jugador cercano (Comportamiento natural 64 bloques) ═══
            for (Player p : world.getPlayers()) {
                if (p.getGameMode() != GameMode.SPECTATOR && p.getLocation().distanceSquared(loc) <= 4096.0) {
                    canSpawn = true;
                    break;
                }
            }

            // ═══ CHECK 2: Cargador de Chunks de Etherium ═══
            if (!canSpawn && keepChunkManager != null) {
                int spawnerChunkX = bX >> 4;
                int spawnerChunkZ = bZ >> 4;

                for (KeepChunkData loader : keepChunkManager.getActiveLoaders().values()) {
                    if (!loader.isActive()) continue;

                    Location loaderLoc = loader.getLocation();
                    World loaderWorld = loaderLoc.getWorld();
                    if (loaderWorld == null || !loaderWorld.equals(world)) continue;

                    KeepChunkType type = keepChunkManager.getType(loader.getTypeId());
                    if (type == null) continue;

                    int radius = type.getRadius();
                    int loaderChunkX = loaderLoc.getBlockX() >> 4;
                    int loaderChunkZ = loaderLoc.getBlockZ() >> 4;

                    // Si el spawner está dentro del radio del cargador activo
                    if (Math.abs(loaderChunkX - spawnerChunkX) <= radius && Math.abs(loaderChunkZ - spawnerChunkZ) <= radius) {
                        canSpawn = true;
                        break;
                    }
                }
            }

            // ═══ SPAWN LÓGICA ═══
            if (canSpawn) {
                // Previene errores: NUNCA intentamos spawnear si el chunk está descargado por Minecraft
                if (!world.isChunkLoaded(bX >> 4, bZ >> 4)) continue;

                // Partículas indicando que el spawner está activo
                world.spawnParticle(Particle.FLAME, loc.clone().add(0.5, 0.5, 0.5), 2, 0.2, 0.2, 0.2, 0.02);

                if (now >= spawner.getNextSpawnTime()) {
                    Collection<Entity> nearby = world.getNearbyEntities(
                            loc, 16.0, 16.0, 16.0,
                            (entity) -> entity.getType() == spawner.getEntityType()
                    );

                    if (nearby.size() >= this.maxNearby) {
                        spawner.setNextSpawnTime(now + 5000L);
                    } else {
                        int spawned = 0;

                        for (int i = 0; i < this.mobsPerSpawn; i++) {
                            int blockOffsetX = ThreadLocalRandom.current().nextInt(-1, 2);
                            int blockOffsetZ = ThreadLocalRandom.current().nextInt(-1, 2);

                            if (blockOffsetX == 0 && blockOffsetZ == 0) {
                                if (ThreadLocalRandom.current().nextBoolean()) {
                                    blockOffsetX = ThreadLocalRandom.current().nextBoolean() ? 1 : -1;
                                } else {
                                    blockOffsetZ = ThreadLocalRandom.current().nextBoolean() ? 1 : -1;
                                }
                            }

                            double exactX = blockOffsetX + 0.2 + ThreadLocalRandom.current().nextDouble() * 0.6;
                            double exactZ = blockOffsetZ + 0.2 + ThreadLocalRandom.current().nextDouble() * 0.6;
                            Location spawnLoc = loc.clone().add(exactX, 1.0, exactZ);

                            if (spawnLoc.getBlock().isPassable() && spawnLoc.clone().add(0.0, 1.0, 0.0).getBlock().isPassable()) {
                                Entity entity = world.spawnEntity(spawnLoc, spawner.getEntityType());
                                if (entity != null) {
                                    entity.setPersistent(true);
                                    if (entity instanceof Mob) {
                                        ((Mob) entity).setRemoveWhenFarAway(false);
                                    }
                                    spawned++;
                                }
                            }
                        }

                        if (spawned > 0) {
                            long nextDelay = ThreadLocalRandom.current().nextLong(this.minDelay, (long) this.maxDelay + 1L);
                            spawner.setNextSpawnTime(now + nextDelay);
                        }
                    }
                }
            }
        }
    }
}