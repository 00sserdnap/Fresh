package cl.pandress.modules.keepchunk.data;

import org.bukkit.Location;
import java.util.UUID;

public class KeepChunkData {

    private final UUID id;
    private final UUID owner;
    private String typeId;
    private Location location;
    private int fuel;
    private boolean active;
    private String customName;
    private boolean showParticles;
    private long activeSince;
    private boolean isTemporary;

    // FIX: coordenadas de chunk cacheadas al construir el objeto.
    // CustomSpawnerTask iteraba todos los loaders activos y por cada uno
    // llamaba loader.getLocation().getChunk().getX() / getZ() en el
    // hilo principal, forzando syncLoad si el chunk no estaba en memoria.
    //
    // Con este cache la comparación de chunks en el tick loop es aritmética
    // pura, sin tocar el mundo ni cargar nada.
    private final int chunkX;
    private final int chunkZ;

    public KeepChunkData(UUID id, UUID owner, String typeId, Location location,
                          int fuel, boolean isTemporary) {
        this.id          = id;
        this.owner       = owner;
        this.typeId      = typeId;
        this.location    = location;
        this.fuel        = fuel;
        this.isTemporary = isTemporary;
        this.active      = false;
        this.showParticles = true;
        this.activeSince = 0;

        // Cachear una sola vez al crear — nunca más se necesita llamar getChunk()
        this.chunkX = location.getBlockX() >> 4;
        this.chunkZ = location.getBlockZ() >> 4;
    }

    public UUID getId()                      { return id; }
    public UUID getOwner()                   { return owner; }
    public String getTypeId()                { return typeId; }
    public Location getLocation()            { return location; }

    public int getFuel()                     { return fuel; }
    public void setFuel(int fuel)            { this.fuel = Math.max(0, fuel); }

    public boolean isActive()                { return active; }
    public void setActive(boolean active)    { this.active = active; }

    public String getCustomName()            { return customName; }
    public void setCustomName(String name)   { this.customName = name; }

    public boolean isShowParticles()                   { return showParticles; }
    public void setShowParticles(boolean showParticles){ this.showParticles = showParticles; }

    public long getActiveSince()             { return activeSince; }
    public void setActiveSince(long t)       { this.activeSince = t; }

    public boolean isTemporary()             { return isTemporary; }
    public void setTemporary(boolean t)      { this.isTemporary = t; }

    /** Coordenada X del chunk donde está este loader. Sin llamadas al mundo. */
    public int getChunkX() { return chunkX; }

    /** Coordenada Z del chunk donde está este loader. Sin llamadas al mundo. */
    public int getChunkZ() { return chunkZ; }
}