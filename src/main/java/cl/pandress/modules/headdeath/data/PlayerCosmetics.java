package cl.pandress.modules.headdeath.data;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class PlayerCosmetics {
    private final UUID uuid;
    private int coins;
    private String selectedGrave;
    private String selectedEffect;
    private final Set<String> unlockedGraves;
    private final Set<String> unlockedEffects;

    public PlayerCosmetics(UUID uuid) {
        this.uuid = uuid;
        this.coins = 0;
        this.selectedGrave = "SOUL_CAMPFIRE"; // Tumba por defecto
        this.selectedEffect = "NONE";         // Sin efecto por defecto
        this.unlockedGraves = new HashSet<>();
        this.unlockedEffects = new HashSet<>();
        
        // Desbloqueos por defecto
        this.unlockedGraves.add("SOUL_CAMPFIRE");
        this.unlockedGraves.add("CAMPFIRE");
        this.unlockedEffects.add("NONE");
    }

    public UUID getUuid() { return uuid; }
    
    public int getCoins() { return coins; }
    public void setCoins(int coins) { this.coins = Math.max(0, coins); }
    public void addCoins(int amount) { this.coins += amount; }
    public void removeCoins(int amount) { this.coins = Math.max(0, this.coins - amount); }

    public String getSelectedGrave() { return selectedGrave; }
    public void setSelectedGrave(String selectedGrave) { this.selectedGrave = selectedGrave; }

    public String getSelectedEffect() { return selectedEffect; }
    public void setSelectedEffect(String selectedEffect) { this.selectedEffect = selectedEffect; }

    public Set<String> getUnlockedGraves() { return unlockedGraves; }
    public void addUnlockedGrave(String grave) { this.unlockedGraves.add(grave); }
    public boolean hasGraveUnlocked(String grave) { return unlockedGraves.contains(grave); }

    public Set<String> getUnlockedEffects() { return unlockedEffects; }
    public void addUnlockedEffect(String effect) { this.unlockedEffects.add(effect); }
    public boolean hasEffectUnlocked(String effect) { return unlockedEffects.contains(effect); }
}