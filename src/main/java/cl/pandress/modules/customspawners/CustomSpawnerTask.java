//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

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
        KeepChunkManager keepChunkManager = Etherium.getInstance().getManagerHandler().getKeepChunkManager();

        for(CustomSpawnerData spawner : this.manager.getActiveSpawners().values()) {
            Location loc = spawner.getLocation();
            World world = loc.getWorld();
            if (world != null) {
                boolean canSpawn = false;
                if (keepChunkManager != null) {
                    int spawnerChunkX = loc.getChunk().getX();
                    int spawnerChunkZ = loc.getChunk().getZ();

                    for(KeepChunkData loader : keepChunkManager.getActiveLoaders().values()) {
                        if (loader.isActive() && loader.getLocation().getWorld().equals(world)) {
                            KeepChunkType type = keepChunkManager.getType(loader.getTypeId());
                            if (type != null) {
                                int radius = type.getRadius();
                                int loaderChunkX = loader.getLocation().getChunk().getX();
                                int loaderChunkZ = loader.getLocation().getChunk().getZ();
                                if (Math.abs(loaderChunkX - spawnerChunkX) <= radius && Math.abs(loaderChunkZ - spawnerChunkZ) <= radius) {
                                    canSpawn = true;
                                    break;
                                }
                            }
                        }
                    }
                }

                if (!canSpawn && loc.getChunk().isForceLoaded()) {
                    canSpawn = true;
                }

                if (!canSpawn) {
                    for(Player p : world.getPlayers()) {
                        if (p.getGameMode() != GameMode.SPECTATOR && p.getLocation().distanceSquared(loc) <= (double)4096.0F) {
                            canSpawn = true;
                            break;
                        }
                    }
                }

                if (canSpawn) {
                    world.spawnParticle(Particle.FLAME, loc.clone().add((double)0.5F, (double)0.5F, (double)0.5F), 2, 0.2, 0.2, 0.2, 0.02);
                    if (now >= spawner.getNextSpawnTime()) {
                        Collection<Entity> nearby = world.getNearbyEntities(loc, (double)16.0F, (double)16.0F, (double)16.0F, (entityx) -> entityx.getType() == spawner.getEntityType());
                        if (nearby.size() >= this.maxNearby) {
                            spawner.setNextSpawnTime(now + 5000L);
                        } else {
                            int spawned = 0;

                            for(int i = 0; i < this.mobsPerSpawn; ++i) {
                                int blockOffsetX = ThreadLocalRandom.current().nextInt(-1, 2);
                                int blockOffsetZ = ThreadLocalRandom.current().nextInt(-1, 2);
                                if (blockOffsetX == 0 && blockOffsetZ == 0) {
                                    if (ThreadLocalRandom.current().nextBoolean()) {
                                        blockOffsetX = ThreadLocalRandom.current().nextBoolean() ? 1 : -1;
                                    } else {
                                        blockOffsetZ = ThreadLocalRandom.current().nextBoolean() ? 1 : -1;
                                    }
                                }

                                double exactX = (double)blockOffsetX + 0.2 + ThreadLocalRandom.current().nextDouble() * 0.6;
                                double exactZ = (double)blockOffsetZ + 0.2 + ThreadLocalRandom.current().nextDouble() * 0.6;
                                Location spawnLoc = loc.clone().add(exactX, (double)1.0F, exactZ);
                                if (spawnLoc.getBlock().isPassable() && spawnLoc.clone().add((double)0.0F, (double)1.0F, (double)0.0F).getBlock().isPassable()) {
                                    Entity entity = world.spawnEntity(spawnLoc, spawner.getEntityType());
                                    entity.setPersistent(true);
                                    if (entity instanceof Mob) {
                                        ((Mob)entity).setRemoveWhenFarAway(false);
                                    }

                                    ++spawned;
                                }
                            }

                            if (spawned > 0) {
                                long nextDelay = ThreadLocalRandom.current().nextLong((long)this.minDelay, (long)this.maxDelay + 1L);
                                spawner.setNextSpawnTime(now + nextDelay);
                            }
                        }
                    }
                }
            }
        }

    }
}
