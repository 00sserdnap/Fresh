package cl.pandress.modules.quests;

import cl.pandress.Fresh;
import cl.pandress.utils.ChatUtils;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

public class QuestManager {

    private final Fresh plugin = Fresh.getInstance();
    private FileConfiguration questConfig;
    private File questFile;
    
    private FileConfiguration dataConfig;
    private File dataFile;
    
    private FileConfiguration userConfig;
    private File userFile;

    private final Map<UUID, Integer> dailyLevel = new HashMap<>();
    private final Map<UUID, Integer> questProgress = new HashMap<>();
    private final Map<UUID, Integer> weeklyProgress = new HashMap<>();
    private final Map<UUID, Integer> globalMilestones = new HashMap<>();
    
    // NUEVO: Mapa para guardar la expiración del Fly
    private final Map<UUID, Long> flyExpiry = new HashMap<>();

    private List<String> currentActiveQuests = new ArrayList<>();

    public QuestManager() {
        reloadConfig();
        setupDataFile();
        setupUserFile();
        Bukkit.getScheduler().runTaskTimer(plugin, this::checkDailyReset, 20L * 60 * 5, 20L * 60 * 5);
        startFlyCheckTask(); // Iniciar revisor de Fly
    }

    // Tarea que corre cada 5 segundos para verificar si el Fly expiró
    private void startFlyCheckTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            for (Player p : Bukkit.getOnlinePlayers()) {
                long expiry = flyExpiry.getOrDefault(p.getUniqueId(), 0L);
                if (expiry > 0) {
                    if (now >= expiry) {
                        // El tiempo expiró
                        flyExpiry.remove(p.getUniqueId());
                        saveUserData(p.getUniqueId());
                        if (p.getGameMode() != GameMode.CREATIVE && p.getGameMode() != GameMode.SPECTATOR) {
                            p.setAllowFlight(false);
                            p.setFlying(false);
                        }
                        p.sendMessage(ChatUtils.colorize("&c&l¡Tu Fly temporal ha expirado!"));
                    } else {
                        // Aún tiene tiempo, asegurarnos de que puede volar
                        if (!p.getAllowFlight()) {
                            p.setAllowFlight(true);
                        }
                    }
                }
            }
        }, 20L, 100L); 
    }

    public void reloadConfig() {
        if (questFile == null) questFile = new File(plugin.getDataFolder(), "modules/quests/config.yml");
        if (!questFile.exists()) plugin.saveResource("modules/quests/config.yml", false);

        questConfig = YamlConfiguration.loadConfiguration(questFile);

        if (dataFile != null) dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        if (userFile != null) userConfig = YamlConfiguration.loadConfiguration(userFile);

        plugin.log("&a[Fresh] Configuración y datos de misiones recargados.");
    }

    private void setupDataFile() {
        if (dataFile == null) dataFile = new File(plugin.getDataFolder(), "modules/quests/data.yml");
        if (!dataFile.exists()) {
            try {
                dataFile.getParentFile().mkdirs();
                dataFile.createNewFile();
            } catch (IOException e) { e.printStackTrace(); }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        checkDailyReset();
    }

    private void setupUserFile() {
        if (userFile == null) userFile = new File(plugin.getDataFolder(), "modules/quests/userdata.yml");
        if (!userFile.exists()) {
            try { userFile.createNewFile(); } catch (IOException e) { e.printStackTrace(); }
        }
        userConfig = YamlConfiguration.loadConfiguration(userFile);
        loadAllUserData();
    }

    private void checkDailyReset() {
        String today = LocalDate.now(ZoneId.of("GMT-4")).toString();
        String lastReset = dataConfig.getString("last-reset", "");

        if (!today.equals(lastReset)) {
            rotateDailyQuests(today);
        } else {
            currentActiveQuests = dataConfig.getStringList("active-quests");
            if (currentActiveQuests.isEmpty()) rotateDailyQuests(today);
        }
    }

    private void rotateDailyQuests(String date) {
        if (questConfig.getConfigurationSection("quest-pool") == null) return;

        List<String> pool = new ArrayList<>(questConfig.getConfigurationSection("quest-pool").getKeys(false));
        Collections.shuffle(pool);
        int amountToPick = Math.min(questConfig.getInt("settings.quests-per-day", 10), pool.size());
        currentActiveQuests = pool.subList(0, amountToPick);

        dataConfig.set("last-reset", date);
        dataConfig.set("active-quests", currentActiveQuests);
        try { dataConfig.save(dataFile); } catch (IOException e) { e.printStackTrace(); }

        dailyLevel.clear();
        questProgress.clear();
        userConfig.set("players", null);
        saveUserFile();

        plugin.log("&a[Fresh] Misiones rotadas y progreso reiniciado (GMT-4).");
    }

    public void saveUserData(UUID uuid) {
        String path = "players." + uuid.toString();
        userConfig.set(path + ".level", getPlayerDailyLevel(uuid));
        userConfig.set(path + ".progress", getProgress(uuid));
        userConfig.set(path + ".weekly", weeklyProgress.getOrDefault(uuid, 0));
        userConfig.set(path + ".global", globalMilestones.getOrDefault(uuid, 0));
        // Guardar el tiempo de expiración
        userConfig.set(path + ".fly-expiry", flyExpiry.getOrDefault(uuid, 0L));
        saveUserFile();
    }

    private void loadAllUserData() {
        if (userConfig.getConfigurationSection("players") == null) return;
        for (String key : userConfig.getConfigurationSection("players").getKeys(false)) {
            UUID uuid = UUID.fromString(key);
            dailyLevel.put(uuid, userConfig.getInt("players." + key + ".level", 1));
            questProgress.put(uuid, userConfig.getInt("players." + key + ".progress", 0));
            weeklyProgress.put(uuid, userConfig.getInt("players." + key + ".weekly", 0));
            globalMilestones.put(uuid, userConfig.getInt("players." + key + ".global", 0));
            // Cargar el tiempo de expiración
            flyExpiry.put(uuid, userConfig.getLong("players." + key + ".fly-expiry", 0L));
        }
    }

    private void saveUserFile() {
        try { userConfig.save(userFile); } catch (IOException e) { e.printStackTrace(); }
    }

    public String getActiveQuestKey(int level) {
        if (level > currentActiveQuests.size() || level < 1) return null;
        return currentActiveQuests.get(level - 1);
    }

    public FileConfiguration getConfig() { return questConfig; }
    public int getPlayerDailyLevel(UUID uuid) { return dailyLevel.getOrDefault(uuid, 1); }
    public void setPlayerDailyLevel(UUID uuid, int level) { dailyLevel.put(uuid, level); }
    public int getProgress(UUID uuid) { return questProgress.getOrDefault(uuid, 0); }
    public long getFlyExpiry(UUID uuid) { return flyExpiry.getOrDefault(uuid, 0L); }

    public int getGlobalCompleted(UUID uuid) {
        return globalMilestones.getOrDefault(uuid, 0);
    }

    public List<Map.Entry<UUID, Integer>> getTopGlobalMissions() {
        List<Map.Entry<UUID, Integer>> list = new ArrayList<>(globalMilestones.entrySet());
        list.sort((e1, e2) -> e2.getValue().compareTo(e1.getValue())); 
        return list;
    }

    public void addProgress(UUID uuid, int amount) {
        questProgress.put(uuid, getProgress(uuid) + amount);
        saveUserData(uuid);
    }

    public void resetProgress(UUID uuid) {
        questProgress.put(uuid, 0);
        saveUserData(uuid);
    }

    public void completeQuest(Player player, int level) {
        String questKey = getActiveQuestKey(level);
        if (questKey == null) return;

        List<String> commands = questConfig.getStringList("quest-pool." + questKey + ".rewards");
        for (String cmd : commands) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%player%", player.getName()));
        }

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        player.sendMessage(ChatUtils.colorize("&b&lMISIONES &8» &a¡Has reclamado tus recompensas!"));

        globalMilestones.put(player.getUniqueId(), getGlobalCompleted(player.getUniqueId()) + 1);

        int nextLevel = level + 1;
        setPlayerDailyLevel(player.getUniqueId(), nextLevel);
        resetProgress(player.getUniqueId());
        saveUserData(player.getUniqueId());

        if (nextLevel == 11) {
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            player.sendMessage(ChatUtils.colorize("&b&lMISIONES &8» &e¡Felicidades! Completaste todas las misiones. ¡Reclama tu BONUS en el menú!"));
        }
    }

    // NUEVA LÓGICA DE BONUS (Fly Propio y Permisos)
    public void claimDailyBonus(Player player) {
        FileConfiguration config = getConfig();

        // 1. Revisar si tiene el permiso VIP
        if (player.hasPermission(config.getString("bonus.ranked.permission", "quest.rank.rewards"))) {
            
            // Recompensas VIP
            List<String> commands = config.getStringList("bonus.ranked.commands");
            for (String cmd : commands) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%player%", player.getName()));
            }
            for (String msg : config.getStringList("bonus.ranked.messages")) {
                player.sendMessage(ChatUtils.colorize(msg));
            }
            
        } else {
            // 2. Recompensas de Usuario Normal (Fly Temporal)
            int hours = config.getInt("bonus.default.fly-hours", 2);
            long expiry = System.currentTimeMillis() + (hours * 3600000L); // Convertir horas a milisegundos
            
            flyExpiry.put(player.getUniqueId(), expiry);
            player.setAllowFlight(true);
            
            List<String> commands = config.getStringList("bonus.default.commands");
            for (String cmd : commands) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%player%", player.getName()));
            }
            for (String msg : config.getStringList("bonus.default.messages")) {
                player.sendMessage(ChatUtils.colorize(msg));
            }
        }

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);

        // Avanzar al nivel 12 para registrar que ya lo reclamó
        setPlayerDailyLevel(player.getUniqueId(), 12);
        saveUserData(player.getUniqueId());
    }

    public void resetWeeklyProgress() { weeklyProgress.clear(); saveUserFile(); }
    public void resetMilestonesProgress() { globalMilestones.clear(); saveUserFile(); }
}