//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package cl.pandress.modules.customspawners.data;

import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;

public class CustomSpawnerData {
    private final UUID id;
    private final Location location;
    private final EntityType entityType;
    private long nextSpawnTime;
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

    public UUID getId() {
        return this.id;
    }

    public Location getLocation() {
        return this.location;
    }

    public EntityType getEntityType() {
        return this.entityType;
    }

    public long getNextSpawnTime() {
        return this.nextSpawnTime;
    }

    public void setNextSpawnTime(long nextSpawnTime) {
        this.nextSpawnTime = nextSpawnTime;
    }

    public UUID getOwnerId() {
        return this.ownerId;
    }

    public String getOwnerName() {
        return this.ownerName != null ? this.ownerName : "Desconocido";
    }
}
