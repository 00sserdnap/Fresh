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

            // Si el mundo todavía no está listo (puede pasar justo al arrancar
            // el server antes de que todos los mundos terminen de registrarse),
            // simplemente saltamos este tick para este spawner.
            if (world == null) continue;

            boolean canSpawn = false;

            // ── Check 1: algún loader de KeepChunk cubre este spawner ────────
            // FIX: usamos spawner.getChunkX() / loader.getChunkX() que son
            // enteros precalculados con blockX >> 4 (lazy, primera llamada).
            // Antes se llamaba loc.getChunk().getX() en cada tick, lo que
            // podía forzar ServerChunkCache.syncLoad() → bloqueo del server thread.
            if (keepChunkManager != null) {
                int spawnerCX = spawner.getChunkX();
                int spawnerCZ = spawner.getChunkZ();

                for (KeepChunkData loader : keepChunkManager.getActiveLoaders().values()) {
                    if (!loader.isActive()) continue;

                    // Comparar mundo por nombre para evitar problemas de instancias
                    // cuando el mundo se recarga o hay múltiples referencias.
                    Location loaderLoc = loader.getLocation();
                    if (loaderLoc.getWorld() == null) continue;
                    if (!loaderLoc.getWorld().getName().equals(world.getName())) continue;

                    KeepChunkType type = keepChunkManager.getType(loader.getTypeId());
                    if (type == null) continue;

                    int radius = type.getRadius();

                    // loader.getChunkX() / getChunkZ() — enteros cacheados,
                    // sin getChunk(), sin syncLoad.
                    if (Math.abs(loader.getChunkX() - spawnerCX) <= radius &&
                        Math.abs(loader.getChunkZ() - spawnerCZ) <= radius) {
                        canSpawn = true;
                        break;
                    }
                }
            }

            // ── Check 2: el chunk está force-loaded ──────────────────────────
            // Restauramos la lógica original (isForceLoaded) pero la protegemos
            // con isChunkLoaded() primero para evitar que getChunkAt() dispare
            // un syncLoad cuando el chunk NO está en memoria.
            // Si el chunk ya está cargado (porque KeepChunk lo forzó u otro
            // motivo), getChunkAt() lo obtiene del cache interno en O(1).
            if (!canSpawn) {
                int cx = spawner.getChunkX();
                int cz = spawner.getChunkZ();
                if (world.isChunkLoaded(cx, cz) && world.getChunkAt(cx, cz).isForceLoaded()) {
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

            // Partículas solo cuando hay razón para spawnear
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