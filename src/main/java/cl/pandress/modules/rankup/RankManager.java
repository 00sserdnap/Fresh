package cl.pandress.modules.rankup;

import cl.pandress.Etherium;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class RankManager {

    private final Etherium plugin = Etherium.getInstance();

    private FileConfiguration rankConfig;
    private File rankFile;

    private FileConfiguration dataConfig;
    private File dataFile;

    private Economy econ = null;

    // ── Datos en memoria ─────────────────────────────────────────────────────
    private final Map<UUID, Integer> playerRanks    = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Integer>> playerProgress = new ConcurrentHashMap<>();

    // ── Patrón dirty-flag (igual que QuestManager / BattlePassManager) ───────
    // Thread-safe: los eventos de Bukkit pueden venir de distintos hilos async.
    private final Set<UUID> dirtyPlayers = ConcurrentHashMap.newKeySet();
    private int saveTaskId = -1;

    public RankManager() {
        setupConfigs();
        setupEconomy();
        loadData();
        startAsyncSaveTask();
    }

    // =========================================================
    //  TAREA ASÍNCRONA DE GUARDADO
    // =========================================================

    /**
     * Lanza una tarea periódica (cada 60 s) que guarda únicamente los jugadores
     * marcados como "dirty" desde la última pasada.
     * Idéntico al patrón de QuestManager.
     */
    private void startAsyncSaveTask() {
        if (saveTaskId != -1) Bukkit.getScheduler().cancelTask(saveTaskId);

        saveTaskId = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            // Drenamos el set ANTES de iterar para no perder marcas que lleguen
            // mientras estamos guardando.
            Set<UUID> toSave = new HashSet<>(dirtyPlayers);
            dirtyPlayers.removeAll(toSave);

            for (UUID uuid : toSave) {
                savePlayerDataInternal(uuid);
            }
        }, 1200L, 1200L).getTaskId(); // 1200 ticks = 60 segundos
    }

    /**
     * Llamar en onDisable. Cancela la tarea periódica y vacía síncronamente
     * todo lo que quedara pendiente en el dirty-set.
     */
    public void shutdown() {
        if (saveTaskId != -1) {
            Bukkit.getScheduler().cancelTask(saveTaskId);
            saveTaskId = -1;
        }
        // Vaciado final síncrono — en onDisable está permitido bloquear.
        for (UUID uuid : dirtyPlayers) {
            savePlayerDataInternal(uuid);
        }
        dirtyPlayers.clear();
    }

    // =========================================================
    //  MÉTODOS DE GUARDADO INTERNOS
    // =========================================================

    /**
     * Marca a un jugador para ser guardado en la próxima pasada asíncrona.
     * Reemplaza el antiguo savePlayerData() que era síncrono.
     * Coste: añadir un UUID a un HashSet → O(1), cero I/O.
     */
    public void markDirty(UUID uuid) {
        dirtyPlayers.add(uuid);
    }

    /**
     * Escritura real al YAML. Siempre se llama desde un hilo async
     * (tarea periódica) o desde onDisable. Nunca desde el main thread.
     */
    private synchronized void savePlayerDataInternal(UUID uuid) {
        String rankPath = "ranks." + uuid.toString();
        String progPath = "progress." + uuid.toString();

        dataConfig.set(rankPath, getPlayerRank(uuid));

        Map<String, Integer> prog = playerProgress.get(uuid);
        if (prog != null && !prog.isEmpty()) {
            for (Map.Entry<String, Integer> entry : prog.entrySet()) {
                dataConfig.set(progPath + "." + entry.getKey(), entry.getValue());
            }
        } else {
            dataConfig.set(progPath, null);
        }

        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("[RankManager] Error al guardar datos de " + uuid + ": " + e.getMessage());
        }
    }

    /**
     * Guardado inmediato asíncrono para casos críticos (rankup, reset).
     * Quita al jugador del dirty-set y lanza su guardado ahora mismo
     * en un hilo async dedicado.
     */
    public void savePlayerDataNow(UUID uuid) {
        dirtyPlayers.remove(uuid);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> savePlayerDataInternal(uuid));
    }

    // =========================================================
    //  CONFIGURACIÓN Y CARGA
    // =========================================================

    private void setupConfigs() {
        rankFile = new File(plugin.getDataFolder(), "modules/rankup/config.yml");
        if (!rankFile.exists()) {
            plugin.saveResource("modules/rankup/config.yml", false);
        }
        rankConfig = YamlConfiguration.loadConfiguration(rankFile);

        dataFile = new File(plugin.getDataFolder(), "modules/rankup/rankdata.yml");
        if (!dataFile.exists()) {
            try {
                dataFile.getParentFile().mkdirs();
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("[RankManager] No se pudo crear rankdata.yml: " + e.getMessage());
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

    private boolean setupEconomy() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) return false;
        RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        econ = rsp.getProvider();
        return econ != null;
    }

    public void reloadConfig() {
        if (rankFile == null) {
            rankFile = new File(plugin.getDataFolder(), "modules/rankup/config.yml");
        }
        if (!rankFile.exists()) {
            plugin.saveResource("modules/rankup/config.yml", false);
        }
        rankConfig = YamlConfiguration.loadConfiguration(rankFile);

        if (dataFile == null) {
            dataFile = new File(plugin.getDataFolder(), "modules/rankup/rankdata.yml");
        }
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("[RankManager] Error recreando rankdata.yml: " + e.getMessage());
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);

        playerRanks.clear();
        playerProgress.clear();
        loadData();

        plugin.log("&8[&eEtherium&8] &fConfiguración y datos de rangos recargados.");
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
                if (dataConfig.getConfigurationSection("progress." + uuidStr) != null) {
                    for (String key : dataConfig.getConfigurationSection("progress." + uuidStr).getKeys(false)) {
                        prog.put(key, dataConfig.getInt("progress." + uuidStr + "." + key));
                    }
                }
                playerProgress.put(uuid, prog);
            }
        }
    }

    // =========================================================
    //  API PÚBLICA
    // =========================================================

    public int getPlayerRank(UUID uuid) {
        return playerRanks.getOrDefault(uuid, 0);
    }

    /**
     * Sube el rango del jugador. Limpia el progreso del rango anterior
     * y guarda INMEDIATAMENTE (acción crítica — no puede perderse).
     */
    public void setPlayerRank(UUID uuid, int rank) {
        playerRanks.put(uuid, rank);
        playerProgress.remove(uuid);
        dataConfig.set("progress." + uuid.toString(), null);
        // Guardado inmediato async — el rankup no debe perderse en un crash.
        savePlayerDataNow(uuid);
    }

    public int getProgress(UUID uuid, String category, String type) {
        String key = category + ":" + type;
        Map<String, Integer> prog = playerProgress.get(uuid);
        if (prog == null) return 0;
        return prog.getOrDefault(key, 0);
    }

    /**
     * Añade progreso a un requisito. Solo marca dirty — cero I/O en el
     * main thread. Antes era: prog.put + dataConfig.save (síncrono).
     */
    public void addProgress(UUID uuid, String category, String type, int amount) {
        String key = category + ":" + type;
        playerProgress.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>())
                      .merge(key, amount, Integer::sum);
        // Solo marcar dirty. El guardado real ocurre en el hilo async.
        markDirty(uuid);
    }

    public List<Map.Entry<UUID, Integer>> getTopRanks() {
        return playerRanks.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .limit(10)
                .collect(Collectors.toList());
    }

    public FileConfiguration getConfig() {
        return rankConfig;
    }

    public Economy getEconomy() {
        return econ;
    }

    /**
     * Resetea a un jugador individual. Guardado inmediato async
     * porque es una acción administrativa explícita.
     */
    public void resetPlayerRank(UUID uuid) {
        playerRanks.remove(uuid);
        playerProgress.remove(uuid);
        dataConfig.set("ranks." + uuid.toString(), null);
        dataConfig.set("progress." + uuid.toString(), null);
        savePlayerDataNow(uuid);
    }

    /**
     * Resetea TODOS los rangos. Guardado síncrono completo porque afecta
     * a todo el archivo de datos — se hace en el hilo que llama.
     */
    public void resetAllRanks() {
        playerRanks.clear();
        playerProgress.clear();
        dirtyPlayers.clear();
        dataConfig.set("ranks", null);
        dataConfig.set("progress", null);
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("[RankManager] Error en resetAllRanks: " + e.getMessage());
        }
    }
}