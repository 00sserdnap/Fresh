package cl.pandress.modules.customspawners.data;

import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import java.util.UUID;

public class CustomSpawnerData {

    private final UUID id;
    private final Location location;
    private final EntityType entityType;
    private long nextSpawnTime;

    private final UUID ownerId;
    private final String ownerName;

    // FIX: chunkX/chunkZ cacheados con inicialización LAZY.
    // NO se calculan en el constructor porque cuando loadData() reconstruye
    // los spawners desde el YAML, Bukkit puede entregar una Location cuyo
    // World todavía no está registrado (world = null). Si intentamos llamar
    // cualquier método de Location en ese momento obtenemos NPE o valores
    // inválidos.
    //
    // Con lazy init, el cálculo ocurre la primera vez que el task los pide,
    // momento en que el servidor ya está completamente levantado y la
    // Location es válida. A partir de ahí el valor queda cacheado y
    // nunca más se llama getChunk().
    //
    // Usamos Integer (objeto) en lugar de int (primitivo) para poder
    // distinguir "no calculado aún" (null) de "calculado y vale 0".
    private Integer chunkX = null;
    private Integer chunkZ = null;

    public CustomSpawnerData(UUID id, Location location, EntityType entityType,
                              UUID ownerId, String ownerName) {
        this.id            = id;
        this.location      = location;
        this.entityType    = entityType;
        this.nextSpawnTime = System.currentTimeMillis();
        this.ownerId       = ownerId;
        this.ownerName     = ownerName;
    }

    /**
     * Coordenada X del chunk donde está este spawner.
     * Se calcula con bit-shift (blockX >> 4) la primera vez y se cachea.
     * Sin llamadas a getChunk(), sin syncLoad.
     */
    public int getChunkX() {
        if (chunkX == null) chunkX = location.getBlockX() >> 4;
        return chunkX;
    }

    /**
     * Coordenada Z del chunk donde está este spawner.
     * Se calcula con bit-shift (blockZ >> 4) la primera vez y se cachea.
     * Sin llamadas a getChunk(), sin syncLoad.
     */
    public int getChunkZ() {
        if (chunkZ == null) chunkZ = location.getBlockZ() >> 4;
        return chunkZ;
    }

    public UUID getId()                  { return id; }
    public Location getLocation()        { return location; }
    public EntityType getEntityType()    { return entityType; }
    public long getNextSpawnTime()       { return nextSpawnTime; }
    public void setNextSpawnTime(long t) { this.nextSpawnTime = t; }
    public UUID getOwnerId()             { return ownerId; }
    public String getOwnerName()         { return ownerName != null ? ownerName : "Desconocido"; }
}