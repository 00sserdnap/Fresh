package cl.pandress.modules.customspawners.data;

import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import java.util.UUID;

public class CustomSpawnerData {

    private final UUID id;
    private final Location location;
    private final EntityType entityType;
    private long nextSpawnTime;

    // Campos de propiedad
    private final UUID ownerId;
    private final String ownerName;

    // FIX: coordenadas de chunk cacheadas al construir el objeto.
    // Antes el task llamaba loc.getChunk().getX() / getZ() en cada tick,
    // lo que forzaba a Minecraft a cargar el chunk síncronamente si no
    // estaba en memoria (ServerChunkCache.syncLoad → bloqueo del server thread).
    //
    // La conversión bloque→chunk es pura aritmética de bits:
    //   chunkCoord = blockCoord >> 4   (equivale a blockCoord / 16)
    // No toca el mundo, no carga nada, coste O(1) instantáneo.
    private final int chunkX;
    private final int chunkZ;

    public CustomSpawnerData(UUID id, Location location, EntityType entityType,
                              UUID ownerId, String ownerName) {
        this.id            = id;
        this.location      = location;
        this.entityType    = entityType;
        this.nextSpawnTime = System.currentTimeMillis();
        this.ownerId       = ownerId;
        this.ownerName     = ownerName;

        // Cachear una sola vez al crear — nunca más se necesita llamar getChunk()
        this.chunkX = location.getBlockX() >> 4;
        this.chunkZ = location.getBlockZ() >> 4;
    }

    public UUID getId()                  { return id; }
    public Location getLocation()        { return location; }
    public EntityType getEntityType()    { return entityType; }
    public long getNextSpawnTime()       { return nextSpawnTime; }
    public void setNextSpawnTime(long t) { this.nextSpawnTime = t; }
    public UUID getOwnerId()             { return ownerId; }
    public String getOwnerName()         { return ownerName != null ? ownerName : "Desconocido"; }

    /** Coordenada X del chunk donde está este spawner. Sin llamadas al mundo. */
    public int getChunkX() { return chunkX; }

    /** Coordenada Z del chunk donde está este spawner. Sin llamadas al mundo. */
    public int getChunkZ() { return chunkZ; }
}