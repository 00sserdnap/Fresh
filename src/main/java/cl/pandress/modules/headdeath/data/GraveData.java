package cl.pandress.modules.headdeath.data;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.Inventory;

import java.util.UUID;

public class GraveData {
    private final UUID victim;
    private final String victimName;
    private final UUID owner;
    private final long deathTimeMillis;
    private long expireTimeMillis;
    private final Inventory inventory;
    private TextDisplay hologram;
    private boolean isEmptying;
    private final Location location;
    private final Material graveMaterial;

    public GraveData(UUID victim, String victimName, UUID owner, long deathTimeMillis, long expireTimeMillis, Inventory inventory, TextDisplay hologram, Location location, Material graveMaterial) {
        this.victim = victim;
        this.victimName = victimName;
        this.owner = owner;
        this.deathTimeMillis = deathTimeMillis;
        this.expireTimeMillis = expireTimeMillis;
        this.inventory = inventory;
        this.hologram = hologram;
        this.location = location;
        this.graveMaterial = graveMaterial;
        this.isEmptying = false;
    }

    public UUID getVictim() { return victim; }
    public String getVictimName() { return victimName; }
    public UUID getKiller() { return owner; }
    public long getDeathTimeMillis() { return deathTimeMillis; }
    public long getExpireTimeMillis() { return expireTimeMillis; }
    public void setExpireTimeMillis(long expireTimeMillis) { this.expireTimeMillis = expireTimeMillis; }
    public Inventory getInventory() { return inventory; }
    public TextDisplay getHologram() { return hologram; }
    public void setHologram(TextDisplay hologram) { this.hologram = hologram; }
    public boolean isEmptying() { return isEmptying; }
    public void setEmptying(boolean emptying) { this.isEmptying = emptying; }
    public Location getLocation() { return location; }
    public Material getGraveMaterial() { return graveMaterial; }
}