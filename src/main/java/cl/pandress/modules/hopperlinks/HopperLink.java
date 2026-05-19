package cl.pandress.modules.hopperlinks;

import org.bukkit.Location;
import java.util.List;
import java.util.UUID;

public class HopperLink {
    private String id;
    private UUID owner;
    private Location chestLocation;
    private List<Location> hoppers;

    public HopperLink(String id, UUID owner, Location chestLocation, List<Location> hoppers) {
        this.id = id;
        this.owner = owner;
        this.chestLocation = chestLocation;
        this.hoppers = hoppers;
    }

    public String getId() { return id; }
    public UUID getOwner() { return owner; }
    public Location getChestLocation() { return chestLocation; }
    public List<Location> getHoppers() { return hoppers; }
}