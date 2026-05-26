//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package cl.pandress.modules.keepchunk.data;

import java.util.UUID;
import org.bukkit.Location;

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

    public KeepChunkData(UUID id, UUID owner, String typeId, Location location, int fuel, boolean isTemporary) {
        this.id = id;
        this.owner = owner;
        this.typeId = typeId;
        this.location = location;
        this.fuel = fuel;
        this.isTemporary = isTemporary;
        this.active = false;
        this.showParticles = true;
        this.activeSince = 0L;
    }

    public UUID getId() {
        return this.id;
    }

    public UUID getOwner() {
        return this.owner;
    }

    public String getTypeId() {
        return this.typeId;
    }

    public Location getLocation() {
        return this.location;
    }

    public int getFuel() {
        return this.fuel;
    }

    public void setFuel(int fuel) {
        this.fuel = Math.max(0, fuel);
    }

    public boolean isActive() {
        return this.active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getCustomName() {
        return this.customName;
    }

    public void setCustomName(String name) {
        this.customName = name;
    }

    public boolean isShowParticles() {
        return this.showParticles;
    }

    public void setShowParticles(boolean showParticles) {
        this.showParticles = showParticles;
    }

    public long getActiveSince() {
        return this.activeSince;
    }

    public void setActiveSince(long activeSince) {
        this.activeSince = activeSince;
    }

    public boolean isTemporary() {
        return this.isTemporary;
    }

    public void setTemporary(boolean temporary) {
        this.isTemporary = temporary;
    }
}
