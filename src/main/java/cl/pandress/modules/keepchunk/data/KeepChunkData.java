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

    // FIX: igual que CustomSpawnerData — lazy init para evitar NPE
    // cuando loadData() reconstruye los loaders desde YAML y el mundo
    // todavía puede no estar completamente registrado.
    private Integer chunkX = null;
    private Integer chunkZ = null;

    public KeepChunkData(UUID id, UUID owner, String typeId, Location location,
                          int fuel, boolean isTemporary) {
        this.id            = id;
        this.owner         = owner;
        this.typeId        = typeId;
        this.location      = location;
        this.fuel          = fuel;
        this.isTemporary   = isTemporary;
        this.active        = false;
        this.showParticles = true;
        this.activeSince   = 0;
    }

    /**
     * Coordenada X del chunk donde está este loader.
     * Calculado con blockX >> 4 la primera vez y cacheado.
     * Sin llamadas a getChunk(), sin syncLoad.
     */
    public int getChunkX() {
        if (chunkX == null) chunkX = location.getBlockX() >> 4;
        return chunkX;
    }

    /**
     * Coordenada Z del chunk donde está este loader.
     * Calculado con blockZ >> 4 la primera vez y cacheado.
     * Sin llamadas a getChunk(), sin syncLoad.
     */
    public int getChunkZ() {
        if (chunkZ == null) chunkZ = location.getBlockZ() >> 4;
        return chunkZ;
    }

    public UUID getId()                           { return id; }
    public UUID getOwner()                        { return owner; }
    public String getTypeId()                     { return typeId; }
    public Location getLocation()                 { return location; }

    public int getFuel()                          { return fuel; }
    public void setFuel(int fuel)                 { this.fuel = Math.max(0, fuel); }

    public boolean isActive()                     { return active; }
    public void setActive(boolean active)         { this.active = active; }

    public String getCustomName()                 { return customName; }
    public void setCustomName(String name)        { this.customName = name; }

    public boolean isShowParticles()                        { return showParticles; }
    public void setShowParticles(boolean showParticles)     { this.showParticles = showParticles; }

    public long getActiveSince()                  { return activeSince; }
    public void setActiveSince(long t)            { this.activeSince = t; }

    public boolean isTemporary()                  { return isTemporary; }
    public void setTemporary(boolean t)           { this.isTemporary = t; }
}