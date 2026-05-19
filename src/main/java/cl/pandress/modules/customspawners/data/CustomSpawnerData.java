package cl.pandress.modules.customspawners.data;

import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import java.util.UUID;

public class CustomSpawnerData {
    private final UUID id;
    private final Location location;
    private final EntityType entityType;
    private long nextSpawnTime;
    
    // Nuevos campos de propiedad
    private final UUID ownerId;
    private final String ownerName;

    public CustomSpawnerData(UUID id, Location location, EntityType entityType, UUID ownerId, String ownerName) {
        this.id = id;
        this.location = location;
        this.entityType = entityType;
        this.nextSpawnTime = System.currentTimeMillis();
        this.ownerId = ownerId;
        this.ownerName = ownerName;
    }

    public UUID getId() { return id; }
    public Location getLocation() { return location; }
    public EntityType getEntityType() { return entityType; }
    public long getNextSpawnTime() { return nextSpawnTime; }
    public void setNextSpawnTime(long nextSpawnTime) { this.nextSpawnTime = nextSpawnTime; }
    public UUID getOwnerId() { return ownerId; }
    public String getOwnerName() { return ownerName != null ? ownerName : "Desconocido"; }
}