package cl.pandress.modules.battlepass;

import cl.pandress.Fresh;
import cl.pandress.utils.ChatUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
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

public class BattlePassManager {

    private final Fresh plugin = Fresh.getInstance();
    private FileConfiguration config, menuMain, menuCategories, menuMissions, menuRewards, dataConfig, messages;
    private File configFile, dataFile, messagesFile;

    private final Map<UUID, Integer> playerXp = new HashMap<>();
    private final Map<UUID, Integer> playerLevel = new HashMap<>();
    private final Map<UUID, List<Integer>> claimedFree = new HashMap<>();
    private final Map<UUID, List<Integer>> claimedPremium = new HashMap<>();
    private final Map<UUID, Map<String, Integer>> missionProgress = new HashMap<>();

    public BattlePassManager() {
        reloadConfig();
        setupDataFile();
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
        String path = "players." + uuid.toString();
        dataConfig.set(path + ".xp", getXp(uuid));
        dataConfig.set(path + ".level", getLevel(uuid));
        dataConfig.set(path + ".claimed-free", claimedFree.getOrDefault(uuid, new ArrayList<>()));
        dataConfig.set(path + ".claimed-premium", claimedPremium.getOrDefault(uuid, new ArrayList<>()));
        
        Map<String, Integer> prog = missionProgress.getOrDefault(uuid, new HashMap<>());
        for (Map.Entry<String, Integer> entry : prog.entrySet()) {
            dataConfig.set(path + ".missions." + entry.getKey(), entry.getValue());
        }
        try { dataConfig.save(dataFile); } catch (IOException e) { e.printStackTrace(); }
    }

    public FileConfiguration getConfig() { return config; }
    public FileConfiguration getMessages() { return messages; }
    public FileConfiguration getMenuMain() { return menuMain; }
    public FileConfiguration getMenuCategories() { return menuCategories; }
    public FileConfiguration getMenuMissions() { return menuMissions; }
    public FileConfiguration getMenuRewards() { return menuRewards; }
    
    public int getXp(UUID uuid) { return playerXp.getOrDefault(uuid, 0); }
    public int getLevel(UUID uuid) { return playerLevel.getOrDefault(uuid, 1); }
    public boolean hasPremium(Player player) { return player.hasPermission("fresh.battlepass"); }
    public int getMissionProgress(UUID uuid, String missionKey) { return missionProgress.getOrDefault(uuid, new HashMap<>()).getOrDefault(missionKey, 0); }

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

    // --- LOGS DETALLADOS (WEBHOOK) ---
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
                connection.setRequestProperty("User-Agent", "FreshBattlePass-Logger");
                connection.setDoOutput(true);

                String safeDetails = details.replace("\"", "\\\"");
                String safeExtra = extraData != null ? extraData.replace("\"", "\\\"").replace("\n", "\\n") : "";

                StringBuilder fields = new StringBuilder();
                fields.append("{\"name\": \"\uD83D\uDC64 Jugador\", \"value\": \"`").append(player.getName()).append("`\\n").append(player.getUniqueId().toString()).append("\", \"inline\": true},");
                fields.append("{\"name\": \"\uD83D\uDCCA Estado del Pase\", \"value\": \"Nivel: **").append(currentLevel).append("**\\nXP Total: **").append(currentXp).append("**\", \"inline\": true},");
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
                        + "\"footer\": {\"text\": \"Fresh Security Logger\"},"
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
        // Ahora lee desde un archivo separado asumiendo que lo fusionamos o leemos todo,
        // pero manteniendo la compatibilidad si la configuracion sigue en config.yml.
        if (config.getConfigurationSection("missions-pool") == null) return new ArrayList<>();
        return new ArrayList<>(config.getConfigurationSection("missions-pool").getKeys(false));
    }

    public List<String> getMissionsByCategory(String difficulty) {
        List<String> list = new ArrayList<>();
        for (String key : getAllMissions()) {
            if (config.getString("missions-pool." + key + ".difficulty", "facil").equalsIgnoreCase(difficulty)) {
                list.add(key);
            }
        }
        return list;
    }

    public boolean isClaimed(UUID uuid, int level, boolean isPremium) {
        if (isPremium) return claimedPremium.getOrDefault(uuid, new ArrayList<>()).contains(level);
        return claimedFree.getOrDefault(uuid, new ArrayList<>()).contains(level);
    }

    public void addProgress(Player player, String actionType, String target, int amount) {
        UUID uuid = player.getUniqueId();
        Map<String, Integer> prog = missionProgress.computeIfAbsent(uuid, k -> new HashMap<>());

        for (String missionKey : getAllMissions()) {
            String path = "missions-pool." + missionKey;
            if (config.getString(path + ".action-type").equalsIgnoreCase(actionType) &&
                config.getString(path + ".action-target").equalsIgnoreCase(target)) {
                
                int current = prog.getOrDefault(missionKey, 0);
                int required = config.getInt(path + ".action-amount");
                
                if (current < required) {
                    prog.put(missionKey, current + amount);
                    if (current + amount >= required) {
                        int xpReward = config.getInt(path + ".xp-reward", 100);
                        addXp(player, xpReward);
                        
                        // Título en pantalla y sonido
                        String titleStr = messages.getString("titles.mission-completed.title", "&d&l¡MISIÓN COMPLETADA!");
                        String subStr = messages.getString("titles.mission-completed.subtitle", "&fHas ganado &d%xp% XP").replace("%xp%", String.valueOf(xpReward));
                        
                        player.sendTitle(ChatUtils.colorize(titleStr), ChatUtils.colorize(subStr), 10, 50, 15);
                        playSound(player, "sounds.mission-complete");

                        // WEBHOOK
                        String cleanName = ChatColor.stripColor(ChatUtils.colorize(config.getString(path + ".name", missionKey)));
                        String type = config.getString(path + ".action-type", "N/A");
                        String targetBlock = config.getString(path + ".action-target", "N/A");
                        String extra = "ID: " + missionKey + "\nObjetivo: " + type + " -> " + targetBlock + " (x" + required + ")\nRecompensa: +" + xpReward + " XP";
                        
                        sendWebhookLog(player, "Misión Completada", "Completó exitosamente la misión: **" + cleanName + "**", extra);
                    }
                    savePlayerData(uuid);
                }
            }
        }
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
                String msg = messages.getString("level-up", "").replace("%prefix%", prefix).replace("%level%", String.valueOf(currentLevel));
                player.sendMessage(ChatUtils.colorize(msg));

                // WEBHOOK
                sendWebhookLog(player, "Subida de Nivel", "Ha acumulado XP suficiente para subir de nivel.", "Nivel Anterior: " + (currentLevel - 1) + "\nNuevo Nivel: " + currentLevel);
            } else {
                break;
            }
        }

        playerXp.put(uuid, currentXp);
        playerLevel.put(uuid, currentLevel);
        savePlayerData(uuid);
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
        
        // --- PARCHE DE SEGURIDAD (NIVELES INTERCALADOS GRATIS) ---
        // Si no hay comandos configurados para este nivel (porque no hay premio), no hace nada.
        if (commands == null || commands.isEmpty()) return;

        StringBuilder executedCmds = new StringBuilder();

        for (String cmd : commands) {
            String parsedCmd = cmd.replace("%player%", player.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsedCmd);
            executedCmds.append("/").append(parsedCmd).append("\n");
        }

        if (isPremium) claimedPremium.computeIfAbsent(uuid, k -> new ArrayList<>()).add(level);
        else claimedFree.computeIfAbsent(uuid, k -> new ArrayList<>()).add(level);
        
        savePlayerData(uuid);
        playSound(player, "sounds.level-up");
        
        String prefix = messages.getString("prefix", "");
        String msg = messages.getString("reward-claimed", "").replace("%prefix%", prefix).replace("%level%", String.valueOf(level));
        player.sendMessage(ChatUtils.colorize(msg));

        // WEBHOOK
        String type = isPremium ? "Premium" : "Gratis";
        sendWebhookLog(player, "Premio Reclamado", "Reclamó las recompensas del **Nivel " + level + "** (Pase **" + type + "**).", executedCmds.toString());
    }
}