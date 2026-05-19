package cl.pandress.modules.areatools.types;

public enum AreaToolType {
    PICKAXE_3x3("pickaxe_3x3", 3),
    PICKAXE_5x5("pickaxe_5x5", 5),
    PICKAXE_7x7("pickaxe_7x7", 7),
    SHOVEL_3x3("shovel_3x3", 3),
    SHOVEL_5x5("shovel_5x5", 5),
    SHOVEL_7x7("shovel_7x7", 7);

    private final String nbtKey;
    private final int size;

    AreaToolType(String nbtKey, int size) {
        this.nbtKey = nbtKey;
        this.size = size;
    }

    public String getNbtKey() { return nbtKey; }
    public int getSize()      { return size; }
    public boolean isPickaxe() { return this.name().startsWith("PICKAXE"); }
    public boolean isShovel()  { return this.name().startsWith("SHOVEL"); }
}