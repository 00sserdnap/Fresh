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

    // Cache de configuración — leídos una vez, nunca en el tick loop.
    private int maxNearby;
    private int minDelay;
    private int maxDelay;
    private int mobsPerSpawn;

    public CustomSpawnerTask(CustomSpawnerManager manager) {
        this.manager = manager;
        reloadConfigCache();
    }

    public void reloadConfigCache() {
        this.maxNearby    = manager.getConfig().getInt("settings.max-nearby-entities", 10);
        this.minDelay     = manager.getConfig().getInt("settings.min-delay-seconds", 10) * 1000;
        this.maxDelay     = manager.getConfig().getInt("settings.max-delay-seconds", 25) * 1000;
        this.mobsPerSpawn = manager.getConfig().getInt("settings.mobs-per-spawn", 3);
    }

    @Override
    public void run() {
        long now = System.currentTimeMillis();

        KeepChunkManager keepChunkManager = Etherium.getInstance()
                .getManagerHandler().getKeepChunkManager();

        for (CustomSpawnerData spawner : manager.getActiveSpawners().values()) {
            Location loc   = spawner.getLocation();
            World    world = loc.getWorld();
            if (world == null) continue;

            boolean canSpawn = false;

            // ── Check 1: algún loader de KeepChunk cubre este spawner ──────────
            // FIX: antes se llamaba loc.getChunk().getX() y
            //      loader.getLocation().getChunk().getX() en cada iteración.
            // Ambas llamadas pueden forzar ServerChunkCache.syncLoad() si el
            // chunk no está en memoria, bloqueando el server thread.
            //
            // Ahora usamos spawner.getChunkX() / loader.getChunkX() que son
            // enteros precalculados con blockX >> 4 — aritmética pura, O(1),
            // sin tocar el mundo ni el chunk system.
            if (keepChunkManager != null) {
                int spawnerCX = spawner.getChunkX();
                int spawnerCZ = spawner.getChunkZ();

                for (KeepChunkData loader : keepChunkManager.getActiveLoaders().values()) {
                    if (!loader.isActive()) continue;
                    if (!loader.getLocation().getWorld().equals(world)) continue;

                    KeepChunkType type = keepChunkManager.getType(loader.getTypeId());
                    if (type == null) continue;

                    int radius = type.getRadius();

                    // FIX: loader.getChunkX() / getChunkZ() en lugar de
                    //      loader.getLocation().getChunk().getX() / getZ()
                    if (Math.abs(loader.getChunkX() - spawnerCX) <= radius &&
                        Math.abs(loader.getChunkZ() - spawnerCZ) <= radius) {
                        canSpawn = true;
                        break;
                    }
                }
            }

            // ── Check 2: el chunk está force-loaded ───────────────────────────
            // FIX: isForceLoaded() también llama getChunk() internamente en
            //      algunas versiones de Paper/CraftBukkit, que puede derivar
            //      en syncLoad. Lo reemplazamos verificando primero isChunkLoaded()
            //      que solo mira si está en memoria sin cargarlo.
            // Si ya está en memoria, getChunkAt() es O(1) desde el cache interno.
            if (!canSpawn && world.isChunkLoaded(spawner.getChunkX(), spawner.getChunkZ())) {
                if (world.getChunkAt(spawner.getChunkX(), spawner.getChunkZ()).isForceLoaded()) {
                    canSpawn = true;
                }
            }

            // ── Check 3 (fallback): jugador dentro de 64 bloques ─────────────
            if (!canSpawn) {
                for (Player p : world.getPlayers()) {
                    if (p.getGameMode() != GameMode.SPECTATOR &&
                        p.getLocation().distanceSquared(loc) <= 4096) {
                        canSpawn = true;
                        break;
                    }
                }
            }

            // Las partículas solo si hay razón para spawnear
            if (!canSpawn) continue;

            world.spawnParticle(Particle.FLAME,
                    loc.clone().add(0.5, 0.5, 0.5), 2, 0.2, 0.2, 0.2, 0.02);

            if (now < spawner.getNextSpawnTime()) continue;

            // ── Contar mobs cercanos ──────────────────────────────────────────
            Collection<Entity> nearby = world.getNearbyEntities(loc, 16, 16, 16,
                    entity -> entity.getType() == spawner.getEntityType());

            if (nearby.size() >= maxNearby) {
                spawner.setNextSpawnTime(now + 5000);
                continue;
            }

            // ── Spawnear mobs ─────────────────────────────────────────────────
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
                    entity.setPersistent(true);
                    if (entity instanceof Mob mob) {
                        mob.setRemoveWhenFarAway(false);
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