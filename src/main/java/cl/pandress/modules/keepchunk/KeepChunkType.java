package cl.pandress.modules.keepchunk;

import java.util.List;

public class KeepChunkType {
    private final String id;
    private final String name;
    private final int radius;
    private final boolean permanent;
    private final int maxFuel;
    private final List<String> lore;

    public KeepChunkType(String id, String name, int radius, boolean permanent, int maxFuel, List<String> lore) {
        this.id = id;
        this.name = name;
        this.radius = radius;
        this.permanent = permanent;
        this.maxFuel = maxFuel;
        this.lore = lore;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public int getRadius() { return radius; }
    public boolean isPermanent() { return permanent; }
    public int getMaxFuel() { return maxFuel; }
    public List<String> getLore() { return lore; }
}