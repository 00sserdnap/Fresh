package cl.pandress.modules.battlepass;

import cl.pandress.Etherium;
import cl.pandress.utils.ChatUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BattlePassManager {

    private final Etherium plugin = Etherium.getInstance();
    private FileConfiguration config, menuMain, menuCategories, menuMissions, menuRewards, dataConfig, messages;
    private File configFile, dataFile, messagesFile;

    private final Map<UUID, Integer> playerXp = new HashMap<>();
    private final Map<UUID, Integer> playerLevel = new HashMap<>();
    private final Map<UUID, List<Integer>> claimedFree = new HashMap<>();
    private final Map<UUID, List<Integer>> claimedPremium = new HashMap<>();
    private final Map<UUID, Map<String, Integer>> missionProgress = new HashMap<>();

    // Mapas para el BossBar
    private final Map<UUID, BossBar> activeBossBars = new HashMap<>();
    private final Map<UUID, Boolean> bossBarToggled = new HashMap<>();

    private final Map<String, Map<String, List<MissionData>>> missionIndex = new HashMap<>();
    private final Map<String, List<String>> missionsByDifficultyCache = new HashMap<>();

    private final Set<UUID> dirtyPlayers = ConcurrentHashMap.newKeySet();
    private int saveTaskId = -1;

    private static class MissionData {
        final String key;
        final int required;
        final int xpReward;
        final String name;

        MissionData(String key, int required, int xpReward, String name) {
            this.key = key;
            this.required = required;
            this.xpReward = xpReward;
            this.name = name;
        }
    }

    public BattlePassManager() {
        reloadConfig();
        setupDataFile();
        startAsyncSaveTask();
    }

    private void startAsyncSaveTask() {
        if (saveTaskId != -1) Bukkit.getScheduler().cancelTask(saveTaskId);
        saveTaskId = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            Set<UUID> toSave = new HashSet<>(dirtyPlayers);
            dirtyPlayers.clear();
            for (UUID uuid : toSave) {
                savePlayerDataInternal(uuid);
            }
        }, 1200L, 1200L).getTaskId();
    }

    public void shutdown() {
        if (saveTaskId != -1) Bukkit.getScheduler().cancelTask(saveTaskId);
        for (UUID uuid : dirtyPlayers) savePlayerDataInternal(uuid);
        dirtyPlayers.clear();
        
        // Limpiar las BossBars de memoria
        for (BossBar bar : activeBossBars.values()) {
            bar.removeAll();
        }
        activeBossBars.clear();
    }

    public void reloadConfig() {
        if (configFile == null) configFile = new File(plugin.getDataFolder(), "modules/battlepass/config.yml");
        if (!configFile.exists()) plugin.saveResource("modules/battlepass/config.yml", false);
        config = YamlConfiguration.loadConfiguration(configFile);

        if (messagesFile == null) messagesFile = new File(plugin.getDataFolder(), "modules/battlepass/messages.yml");
        if (!messagesFile.exists()) plugin.saveResource("modules/battlepass/messages.yml", false);
        messages = YamlConfiguration.loadConfiguration(messagesFile);

        menuMain = loadMenuConfig("main.yml");
        menuCategories = loadMenuConfig("categories.yml");
        menuMissions = loadMenuConfig("missions.yml");
        menuRewards = loadMenuConfig("rewards.yml");

        buildMissionCache();
    }

    private void buildMissionCache() {
        missionIndex.clear();
        missionsByDifficultyCache.clear();

        if (config.getConfigurationSection("missions-pool") == null) return;

        for (String key : config.getConfigurationSection("missions-pool").getKeys(false)) {
            String path = "missions-pool." + key;
            String actionType   = config.getString(path + ".action-type",   "").toUpperCase();
            String actionTarget = config.getString(path + ".action-target",  "").toUpperCase();
            String difficulty   = config.getString(path + ".difficulty", "facil").toLowerCase();
            int required        = config.getInt(path + ".action-amount", 1);
            int xpReward        = config.getInt(path + ".xp-reward", 100);
            String name         = config.getString(path + ".name", key);

            missionIndex
                .computeIfAbsent(actionType, t -> new HashMap<>())
                .computeIfAbsent(actionTarget, t -> new ArrayList<>())
                .add(new MissionData(key, required, xpReward, name));

            missionsByDifficultyCache
                .computeIfAbsent(difficulty, d -> new ArrayList<>())
                .add(key);
        }
    }

    private FileConfiguration loadMenuConfig(String fileName) {
        File file = new File(plugin.getDataFolder(), "modules/battlepass/menus/" + fileName);
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            plugin.saveResource("modules/battlepass/menus/" + fileName, false);
        }
        return YamlConfiguration.loadConfiguration(file);
    }

    private void setupDataFile() {
        if (dataFile == null) dataFile = new File(plugin.getDataFolder(), "modules/battlepass/data.yml");
        if (!dataFile.exists()) {
            try { dataFile.getParentFile().mkdirs(); dataFile.createNewFile(); } catch (IOException e) { e.printStackTrace(); }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        loadAllData();
    }

    private void loadAllData() {
        if (dataConfig.getConfigurationSection("players") == null) return;
        for (String key : dataConfig.getConfigurationSection("players").getKeys(false)) {
            UUID uuid = UUID.fromString(key);
            playerXp.put(uuid, dataConfig.getInt("players." + key + ".xp", 0));
            playerLevel.put(uuid, dataConfig.getInt("players." + key + ".level", 1));
            claimedFree.put(uuid, dataConfig.getIntegerList("players." + key + ".claimed-free"));
            claimedPremium.put(uuid, dataConfig.getIntegerList("players." + key + ".claimed-premium"));
            
            // Cargar preferencia del BossBar, FALSE por defecto
            bossBarToggled.put(uuid, dataConfig.getBoolean("players." + key + ".bossbar-enabled", false));

            Map<String, Integer> prog = new HashMap<>();
            if (dataConfig.getConfigurationSection("players." + key + ".missions") != null) {
                for (String mKey : dataConfig.getConfigurationSection("players." + key + ".missions").getKeys(false)) {
                    prog.put(mKey, dataConfig.getInt("players." + key + ".missions." + mKey));
                }
            }
            missionProgress.put(uuid, prog);
        }
    }

    public void savePlayerData(UUID uuid) {
        dirtyPlayers.add(uuid);
    }

    private synchronized void savePlayerDataInternal(UUID uuid) {
        String path = "players." + uuid.toString();
        dataConfig.set(path + ".xp", getXp(uuid));
        dataConfig.set(path + ".level", getLevel(uuid));
        dataConfig.set(path + ".claimed-free", claimedFree.getOrDefault(uuid, new ArrayList<>()));
        dataConfig.set(path + ".claimed-premium", claimedPremium.getOrDefault(uuid, new ArrayList<>()));
        
        // Guardar preferencia del BossBar
        dataConfig.set(path + ".bossbar-enabled", bossBarToggled.getOrDefault(uuid, false));

        Map<String, Integer> prog = missionProgress.getOrDefault(uuid, new HashMap<>());
        for (Map.Entry<String, Integer> entry : prog.entrySet()) {
            dataConfig.set(path + ".missions." + entry.getKey(), entry.getValue());
        }
        try { dataConfig.save(dataFile); } catch (IOException e) { e.printStackTrace(); }
    }

    public void savePlayerDataNow(UUID uuid) {
        dirtyPlayers.remove(uuid);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> savePlayerDataInternal(uuid));
    }

    public FileConfiguration getConfig() { return config; }
    public FileConfiguration getMessages() { return messages; }
    public FileConfiguration getMenuMain() { return menuMain; }
    public FileConfiguration getMenuCategories() { return menuCategories; }
    public FileConfiguration getMenuMissions() { return menuMissions; }
    public FileConfiguration getMenuRewards() { return menuRewards; }

    public int getXp(UUID uuid) { return playerXp.getOrDefault(uuid, 0); }
    public int getLevel(UUID uuid) { return playerLevel.getOrDefault(uuid, 1); }
    public boolean hasPremium(Player player) { return player.hasPermission("eth.battlepass"); }
    public int getMissionProgress(UUID uuid, String missionKey) {
        return missionProgress.getOrDefault(uuid, Collections.emptyMap()).getOrDefault(missionKey, 0);
    }

    public void sendMessage(Player player, String path) {
        String prefix = messages.getString("prefix", "&d&lBATTLEPASS &8» ");
        String msg = messages.getString(path);
        if (msg != null && !msg.isEmpty()) {
            player.sendMessage(ChatUtils.colorize(msg.replace("%prefix%", prefix)));
        }
    }

    public void playSound(Player player, String path) {
        try {
            Sound sound = Sound.valueOf(messages.getString(path, ""));
            player.playSound(player.getLocation(), sound, 1f, 1f);
        } catch (IllegalArgumentException | NullPointerException ignored) {}
    }

    private void sendWebhookLog(Player player, String action, String details, String extraData) {
        if (config == null || !config.getBoolean("webhook.enabled", false)) return;

        String webhookUrl = config.getString("webhook.url", "");
        if (webhookUrl == null || webhookUrl.isEmpty() || webhookUrl.equals("AQUI_TU_URL_DEL_WEBHOOK")) return;

        int currentLevel = getLevel(player.getUniqueId());
        int currentXp = getXp(player.getUniqueId());
        String loc = player.getWorld().getName() + " [" + player.getLocation().getBlockX() + ", " + player.getLocation().getBlockY() + ", " + player.getLocation().getBlockZ() + "]";

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                URL url = new URL(webhookUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("User-Agent", "EthBattlePass-Logger");
                connection.setDoOutput(true);

                String safeDetails = details.replace("\"", "\\\"");
                String safeExtra = extraData != null ? extraData.replace("\"", "\\\"").replace("\n", "\\n") : "";

                StringBuilder fields = new StringBuilder();
                fields.append("{\"name\": \"\uD83D\uDC64 Jugador\", \"value\": \"`").append(player.getName()).append("`\\n").append(player.getUniqueId().toString()).append("\", \"inline\": true},");
                fields.append("{\"name\": \"\uD83D\uDCCA Nivel/XP\", \"value\": \"Nv. `").append(currentLevel).append("` | `").append(currentXp).append(" XP`\", \"inline\": true},");
                fields.append("{\"name\": \"\uD83D\uDDFA Ubicación\", \"value\": \"`").append(loc).append("`\", \"inline\": true},");
                fields.append("{\"name\": \"\uD83D\uDCDC Acción: ").append(action).append("\", \"value\": \"").append(safeDetails).append("\", \"inline\": false}");

                if (extraData != null && !extraData.isEmpty()) {
                    fields.append(",{\"name\": \"\uD83D\uDCCB Datos Extra / Comandos\", \"value\": \"```yaml\\n").append(safeExtra).append("\\n```\", \"inline\": false}");
                }

                String json = "{"
                        + "\"embeds\": [{"
                        + "\"title\": \"\uD83D\uDEE1 Auditoría de BattlePass\","
                        + "\"color\": 10181046,"
                        + "\"fields\": [" + fields.toString() + "],"
                        + "\"footer\": {\"text\": \"Etherium Security Logger\"},"
                        + "\"timestamp\": \"" + Instant.now().toString() + "\""
                        + "}]"
                        + "}";

                try (OutputStream os = connection.getOutputStream()) {
                    os.write(json.getBytes(StandardCharsets.UTF_8));
                }
                connection.getResponseCode();
            } catch (Exception e) {
                plugin.getLogger().warning("No se pudo enviar el webhook detallado (BattlePass): " + e.getMessage());
            }
        });
    }

    public List<String> getAllMissions() {
        if (config.getConfigurationSection("missions-pool") == null) return new ArrayList<>();
        return new ArrayList<>(config.getConfigurationSection("missions-pool").getKeys(false));
    }

    public List<String> getMissionsByCategory(String difficulty) {
        return missionsByDifficultyCache.getOrDefault(difficulty.toLowerCase(), Collections.emptyList());
    }

    public boolean isClaimed(UUID uuid, int level, boolean isPremium) {
        if (isPremium) return claimedPremium.getOrDefault(uuid, Collections.emptyList()).contains(level);
        return claimedFree.getOrDefault(uuid, Collections.emptyList()).contains(level);
    }

    public void addProgress(Player player, String actionType, String target, int amount) {
        UUID uuid = player.getUniqueId();
        Map<String, Integer> prog = missionProgress.computeIfAbsent(uuid, k -> new HashMap<>());

        Map<String, List<MissionData>> byTarget = missionIndex.get(actionType.toUpperCase());
        if (byTarget == null) return;

        List<MissionData> matching = byTarget.get(target.toUpperCase());
        if (matching == null || matching.isEmpty()) return;

        boolean anyProgress = false;

        for (MissionData mission : matching) {
            int current = prog.getOrDefault(mission.key, 0);
            if (current >= mission.required) continue;

            int newProgress = current + amount;
            prog.put(mission.key, newProgress);
            anyProgress = true;

            if (newProgress >= mission.required) {
                addXp(player, mission.xpReward);

                String titleStr = messages.getString("titles.mission-completed.title", "&d&l¡MISIÓN COMPLETADA!");
                String subStr   = messages.getString("titles.mission-completed.subtitle", "&fHas ganado &d%xp% XP")
                                          .replace("%xp%", String.valueOf(mission.xpReward));
                player.sendTitle(ChatUtils.colorize(titleStr), ChatUtils.colorize(subStr), 10, 50, 15);
                playSound(player, "sounds.mission-complete");

                String mPath = "missions-pool." + mission.key;
                String mType   = config.getString(mPath + ".action-type",   "N/A");
                String mTarget = config.getString(mPath + ".action-target",  "N/A");
                String extra   = "ID: " + mission.key + "\nObjetivo: " + mType + " -> " + mTarget
                               + " (x" + mission.required + ")\nRecompensa: +" + mission.xpReward + " XP";
                sendWebhookLog(player, "Misión Completada",
                        "Completó exitosamente la misión: **" + ChatColor.stripColor(ChatUtils.colorize(mission.name)) + "**", extra);
            }
        }

        if (anyProgress) savePlayerData(uuid);
    }

    public void addXp(Player player, int amount) {
        UUID uuid = player.getUniqueId();
        int currentXp = getXp(uuid) + amount;
        int currentLevel = getLevel(uuid);
        int maxLevel = config.getInt("settings.max-level", 54);

        while (currentLevel < maxLevel) {
            int xpRequired = config.getInt("levels." + (currentLevel + 1) + ".xp-required", 500);
            if (currentXp >= xpRequired) {
                currentXp -= xpRequired;
                currentLevel++;

                String prefix = messages.getString("prefix", "");
                String msg = messages.getString("level-up", "")
                                     .replace("%prefix%", prefix)
                                     .replace("%level%", String.valueOf(currentLevel));
                player.sendMessage(ChatUtils.colorize(msg));

                sendWebhookLog(player, "Subida de Nivel",
                        "Ha acumulado XP suficiente para subir de nivel.",
                        "Nivel Anterior: " + (currentLevel - 1) + "\nNuevo Nivel: " + currentLevel);
            } else {
                break;
            }
        }

        playerXp.put(uuid, currentXp);
        playerLevel.put(uuid, currentLevel);
        savePlayerData(uuid); 
        
        // Actualizar el BossBar visualmente al ganar XP
        updateBossBar(player);
    }

    public void claimReward(Player player, int level, boolean isPremium) {
        UUID uuid = player.getUniqueId();
        if (getLevel(uuid) < level || isClaimed(uuid, level, isPremium)) return;

        if (isPremium && !hasPremium(player)) {
            sendMessage(player, "no-premium");
            return;
        }

        String path = "levels." + level + (isPremium ? ".premium" : ".free") + ".commands";
        List<String> commands = config.getStringList(path);

        if (commands == null || commands.isEmpty()) return;

        StringBuilder executedCmds = new StringBuilder();
        for (String cmd : commands) {
            String parsedCmd = cmd.replace("%player%", player.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsedCmd);
            executedCmds.append("/").append(parsedCmd).append("\n");
        }

        if (isPremium) claimedPremium.computeIfAbsent(uuid, k -> new ArrayList<>()).add(level);
        else           claimedFree.computeIfAbsent(uuid, k -> new ArrayList<>()).add(level);

        savePlayerDataNow(uuid);
        playSound(player, "sounds.level-up");

        String prefix = messages.getString("prefix", "");
        String msg = messages.getString("reward-claimed", "")
                             .replace("%prefix%", prefix)
                             .replace("%level%", String.valueOf(level));
        player.sendMessage(ChatUtils.colorize(msg));

        String type = isPremium ? "Premium" : "Gratis";
        sendWebhookLog(player, "Premio Reclamado",
                "Reclamó las recompensas del **Nivel " + level + "** (Pase **" + type + "**).",
                executedCmds.toString());
    }

    // ── MÉTODOS DEL BOSSBAR ────────────────────────────────────────────────────
    
    public void toggleBossBar(Player player) {
        UUID uuid = player.getUniqueId();
        boolean current = bossBarToggled.getOrDefault(uuid, false);
        boolean newState = !current;
        bossBarToggled.put(uuid, newState); 
        savePlayerData(uuid); 

        if (newState) {
            updateBossBar(player);
            sendMessage(player, "bossbar-enabled");
        } else {
            removeBossBar(player);
            sendMessage(player, "bossbar-disabled");
        }
    }

    public void updateBossBar(Player player) {
        if (!config.getBoolean("settings.enabled", true)) return;
        UUID uuid = player.getUniqueId();
        if (!bossBarToggled.getOrDefault(uuid, false)) return; // No mostrar si está desactivado

        int currentLvl = getLevel(uuid);
        int currentXp = getXp(uuid);
        int reqXp = config.getInt("levels." + (currentLvl + 1) + ".xp-required", 500);

        double progress = (double) currentXp / reqXp;
        if (progress > 1.0) progress = 1.0;
        if (progress < 0.0) progress = 0.0;

        String title = config.getString("settings.bossbar.title", "&b&lPase de Batalla &8» &fNivel &e%level% &8| &b%xp%&8/&b%req_xp% XP")
                .replace("%level%", String.valueOf(currentLvl))
                .replace("%xp%", String.valueOf(currentXp))
                .replace("%req_xp%", String.valueOf(reqXp));

        BossBar bossBar = activeBossBars.get(uuid);
        if (bossBar == null) {
            bossBar = Bukkit.createBossBar(ChatUtils.colorize(title), BarColor.BLUE, BarStyle.SOLID);
            bossBar.addPlayer(player);
            activeBossBars.put(uuid, bossBar);
        } else {
            bossBar.setTitle(ChatUtils.colorize(title));
        }
        bossBar.setProgress(progress);
    }

    public void removeBossBar(Player player) {
        BossBar bossBar = activeBossBars.remove(player.getUniqueId());
        if (bossBar != null) {
            bossBar.removeAll(); 
        }
    }
}