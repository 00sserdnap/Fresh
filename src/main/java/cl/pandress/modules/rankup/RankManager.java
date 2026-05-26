//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package cl.pandress.modules.rankup;

import cl.pandress.Etherium;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.RegisteredServiceProvider;

public class RankManager {
    private final Etherium plugin = Etherium.getInstance();
    private FileConfiguration rankConfig;
    private File rankFile;
    private FileConfiguration dataConfig;
    private File dataFile;
    private Economy econ = null;
    private final Map<UUID, Integer> playerRanks = new HashMap();
    private final Map<UUID, Map<String, Integer>> playerProgress = new HashMap();

    public RankManager() {
        this.setupConfigs();
        this.setupEconomy();
        this.loadData();
    }

    private boolean setupEconomy() {
        if (this.plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        } else {
            RegisteredServiceProvider<Economy> rsp = this.plugin.getServer().getServicesManager().getRegistration(Economy.class);
            if (rsp == null) {
                return false;
            } else {
                this.econ = (Economy)rsp.getProvider();
                return this.econ != null;
            }
        }
    }

    public Economy getEconomy() {
        return this.econ;
    }

    private void setupConfigs() {
        this.rankFile = new File(this.plugin.getDataFolder(), "modules/rankup/config.yml");
        if (!this.rankFile.exists()) {
            this.plugin.saveResource("modules/rankup/config.yml", false);
        }

        this.rankConfig = YamlConfiguration.loadConfiguration(this.rankFile);
        this.dataFile = new File(this.plugin.getDataFolder(), "modules/rankup/rankdata.yml");
        if (!this.dataFile.exists()) {
            try {
                this.dataFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        this.dataConfig = YamlConfiguration.loadConfiguration(this.dataFile);
    }

    public void reloadConfig() {
        if (this.rankFile == null) {
            this.rankFile = new File(this.plugin.getDataFolder(), "modules/rankup/config.yml");
        }

        if (!this.rankFile.exists()) {
            this.plugin.saveResource("modules/rankup/config.yml", false);
        }

        this.rankConfig = YamlConfiguration.loadConfiguration(this.rankFile);
        if (this.dataFile == null) {
            this.dataFile = new File(this.plugin.getDataFolder(), "modules/rankup/rankdata.yml");
        }

        if (!this.dataFile.exists()) {
            try {
                this.dataFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        this.dataConfig = YamlConfiguration.loadConfiguration(this.dataFile);
        this.playerRanks.clear();
        this.playerProgress.clear();
        this.loadData();
        this.plugin.log("&8[&eEtherium&8] &f Configuración y datos de rangos recargados.");
    }

    public void loadData() {
        if (this.dataConfig.getConfigurationSection("ranks") != null) {
            for(String key : this.dataConfig.getConfigurationSection("ranks").getKeys(false)) {
                this.playerRanks.put(UUID.fromString(key), this.dataConfig.getInt("ranks." + key));
            }
        }

        if (this.dataConfig.getConfigurationSection("progress") != null) {
            for(String uuidStr : this.dataConfig.getConfigurationSection("progress").getKeys(false)) {
                UUID uuid = UUID.fromString(uuidStr);
                Map<String, Integer> prog = new HashMap();

                for(String key : this.dataConfig.getConfigurationSection("progress." + uuidStr).getKeys(false)) {
                    prog.put(key, this.dataConfig.getInt("progress." + uuidStr + "." + key));
                }

                this.playerProgress.put(uuid, prog);
            }
        }

    }

    public void savePlayerData(UUID uuid) {
        this.dataConfig.set("ranks." + uuid.toString(), this.getPlayerRank(uuid));
        if (this.playerProgress.containsKey(uuid)) {
            for(Map.Entry<String, Integer> entry : this.playerProgress.get(uuid).entrySet()) {
                this.dataConfig.set("progress." + uuid.toString() + "." + (String)entry.getKey(), entry.getValue());
            }
        } else {
            this.dataConfig.set("progress." + uuid.toString(), (Object)null);
        }

        try {
            this.dataConfig.save(this.dataFile);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public int getPlayerRank(UUID uuid) {
        return (Integer)this.playerRanks.getOrDefault(uuid, 0);
    }

    public void setPlayerRank(UUID uuid, int rank) {
        this.playerRanks.put(uuid, rank);
        this.playerProgress.remove(uuid);
        this.dataConfig.set("progress." + uuid.toString(), (Object)null);
        this.savePlayerData(uuid);
    }

    public int getProgress(UUID uuid, String category, String type) {
        String key = category + ":" + type;
        return (Integer)((Map)this.playerProgress.getOrDefault(uuid, new HashMap())).getOrDefault(key, 0);
    }

    public void addProgress(UUID uuid, String category, String type, int amount) {
        String key = category + ":" + type;
        this.playerProgress.putIfAbsent(uuid, new HashMap());
        Map<String, Integer> prog = (Map)this.playerProgress.get(uuid);
        prog.put(key, (Integer)prog.getOrDefault(key, 0) + amount);
        this.savePlayerData(uuid);
    }

    public List<Map.Entry<UUID, Integer>> getTopRanks() {
        return (List)this.playerRanks.entrySet().stream().sorted((e1, e2) -> ((Integer)e2.getValue()).compareTo((Integer)e1.getValue())).limit(10L).collect(Collectors.toList());
    }

    public FileConfiguration getConfig() {
        return this.rankConfig;
    }

    public void resetPlayerRank(UUID uuid) {
        this.playerRanks.remove(uuid);
        this.playerProgress.remove(uuid);
        this.dataConfig.set("ranks." + uuid.toString(), (Object)null);
        this.dataConfig.set("progress." + uuid.toString(), (Object)null);

        try {
            this.dataConfig.save(this.dataFile);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public void resetAllRanks() {
        this.playerRanks.clear();
        this.playerProgress.clear();
        this.dataConfig.set("ranks", (Object)null);
        this.dataConfig.set("progress", (Object)null);

        try {
            this.dataConfig.save(this.dataFile);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }
}
