package cl.pandress.modules.rankup;

import cl.pandress.Fresh;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class RankManager {

    private final Fresh plugin = Fresh.getInstance();
    private FileConfiguration rankConfig;
    private File rankFile;
    private FileConfiguration dataConfig;
    private File dataFile;

    private Economy econ = null;

    private final Map<UUID, Integer> playerRanks = new HashMap<>();
    // UUID -> "categoria:tipo" -> progreso (Ej: "blocks_mine:STONE" -> 50)
    private final Map<UUID, Map<String, Integer>> playerProgress = new HashMap<>();

    public RankManager() {
        setupConfigs();
        setupEconomy();
        loadData();
    }

    private boolean setupEconomy() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) return false;
        RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        econ = rsp.getProvider();
        return econ != null;
    }

    public Economy getEconomy() { return econ; }

    private void setupConfigs() {
        rankFile = new File(plugin.getDataFolder(), "modules/ranks/config.yml");
        if (!rankFile.exists()) plugin.saveResource("modules/ranks/config.yml", false);
        rankConfig = YamlConfiguration.loadConfiguration(rankFile);

        dataFile = new File(plugin.getDataFolder(), "modules/ranks/rankdata.yml");
        if (!dataFile.exists()) {
            try { dataFile.createNewFile(); } catch (IOException e) { e.printStackTrace(); }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

    // --- MÉTODO PARA RECARGAR LA CONFIGURACIÓN ---
    public void reloadConfig() {
        if (rankFile == null) rankFile = new File(plugin.getDataFolder(), "modules/ranks/config.yml");
        if (!rankFile.exists()) plugin.saveResource("modules/ranks/config.yml", false);
        rankConfig = YamlConfiguration.loadConfiguration(rankFile);

        if (dataFile == null) dataFile = new File(plugin.getDataFolder(), "modules/ranks/rankdata.yml");
        if (!dataFile.exists()) {
            try { dataFile.createNewFile(); } catch (IOException e) { e.printStackTrace(); }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        
        // Volvemos a cargar los datos en memoria por si se editaron a mano en el yml
        playerRanks.clear();
        playerProgress.clear();
        loadData();
        
        plugin.log("&a[Fresh] Configuración y datos de rangos recargados.");
    }

    public void loadData() {
        if (dataConfig.getConfigurationSection("ranks") != null) {
            for (String key : dataConfig.getConfigurationSection("ranks").getKeys(false)) {
                playerRanks.put(UUID.fromString(key), dataConfig.getInt("ranks." + key));
            }
        }
        if (dataConfig.getConfigurationSection("progress") != null) {
            for (String uuidStr : dataConfig.getConfigurationSection("progress").getKeys(false)) {
                UUID uuid = UUID.fromString(uuidStr);
                Map<String, Integer> prog = new HashMap<>();
                for (String key : dataConfig.getConfigurationSection("progress." + uuidStr).getKeys(false)) {
                    prog.put(key, dataConfig.getInt("progress." + uuidStr + "." + key));
                }
                playerProgress.put(uuid, prog);
            }
        }
    }

    public void savePlayerData(UUID uuid) {
        dataConfig.set("ranks." + uuid.toString(), getPlayerRank(uuid));
        if (playerProgress.containsKey(uuid)) {
            for (Map.Entry<String, Integer> entry : playerProgress.get(uuid).entrySet()) {
                dataConfig.set("progress." + uuid.toString() + "." + entry.getKey(), entry.getValue());
            }
        } else {
            dataConfig.set("progress." + uuid.toString(), null);
        }
        try { dataConfig.save(dataFile); } catch (IOException e) { e.printStackTrace(); }
    }

    public int getPlayerRank(UUID uuid) { return playerRanks.getOrDefault(uuid, 0); }
    
    public void setPlayerRank(UUID uuid, int rank) {
        playerRanks.put(uuid, rank);
        playerProgress.remove(uuid); // Al subir de rango, se resetea el progreso acumulado
        dataConfig.set("progress." + uuid.toString(), null); // Limpiar del config
        savePlayerData(uuid);
    }

    public int getProgress(UUID uuid, String category, String type) {
        String key = category + ":" + type;
        return playerProgress.getOrDefault(uuid, new HashMap<>()).getOrDefault(key, 0);
    }

    public void addProgress(UUID uuid, String category, String type, int amount) {
        String key = category + ":" + type;
        playerProgress.putIfAbsent(uuid, new HashMap<>());
        Map<String, Integer> prog = playerProgress.get(uuid);
        prog.put(key, prog.getOrDefault(key, 0) + amount);
        savePlayerData(uuid); // Guarda progreso en vivo
    }

    public List<Map.Entry<UUID, Integer>> getTopRanks() {
        return playerRanks.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .limit(10).collect(Collectors.toList());
    }

    public FileConfiguration getConfig() { return rankConfig; }

    // --- MÉTODOS DE ADMINISTRACIÓN ---

    public void resetPlayerRank(UUID uuid) {
        playerRanks.remove(uuid);
        playerProgress.remove(uuid);
        dataConfig.set("ranks." + uuid.toString(), null);
        dataConfig.set("progress." + uuid.toString(), null);
        try { dataConfig.save(dataFile); } catch (IOException e) { e.printStackTrace(); }
    }

    public void resetAllRanks() {
        playerRanks.clear();
        playerProgress.clear();
        dataConfig.set("ranks", null);
        dataConfig.set("progress", null);
        try { dataConfig.save(dataFile); } catch (IOException e) { e.printStackTrace(); }
    }
}