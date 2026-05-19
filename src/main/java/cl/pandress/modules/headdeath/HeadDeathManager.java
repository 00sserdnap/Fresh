package cl.pandress.modules.headdeath;

import cl.pandress.modules.headdeath.data.GraveData;
import cl.pandress.modules.headdeath.data.PlayerCosmetics;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Campfire;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class HeadDeathManager {

    private final JavaPlugin plugin;
    private final Map<Location, GraveData> graves = new HashMap<>();
    private final Map<UUID, PlayerCosmetics> playerCosmetics = new HashMap<>();
    
    private FileConfiguration config;
    private FileConfiguration messages;
    private FileConfiguration cosmeticsConfig;
    private FileConfiguration playerData;
    private File playerDataFile;

    public HeadDeathManager(JavaPlugin plugin) {
        this.plugin = plugin;
        loadConfig();
        startTasks();
    }

    public boolean isEnabled() {
        return config != null && config.getBoolean("settings.enabled", true);
    }

    public void loadConfig() {
        File configFile = new File(plugin.getDataFolder(), "modules/headdeath/config.yml");
        if (!configFile.exists()) {
            plugin.saveResource("modules/headdeath/config.yml", false);
        }
        this.config = YamlConfiguration.loadConfiguration(configFile);

        File msgFile = new File(plugin.getDataFolder(), "modules/headdeath/messages.yml");
        if (!msgFile.exists()) {
            plugin.saveResource("modules/headdeath/messages.yml", false);
        }
        this.messages = YamlConfiguration.loadConfiguration(msgFile);

        File cosFile = new File(plugin.getDataFolder(), "modules/headdeath/cosmetics.yml");
        if (!cosFile.exists()) {
            plugin.saveResource("modules/headdeath/cosmetics.yml", false);
        }
        this.cosmeticsConfig = YamlConfiguration.loadConfiguration(cosFile);

        playerDataFile = new File(plugin.getDataFolder(), "modules/headdeath/players.yml");
        if (!playerDataFile.exists()) {
            try {
                playerDataFile.getParentFile().mkdirs();
                playerDataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("No se pudo crear players.yml");
            }
        }
        this.playerData = YamlConfiguration.loadConfiguration(playerDataFile);
        loadPlayerCosmetics();

        if (isEnabled()) {
            loadGraves();
        }
    }

    public void reloadConfig() {
        cleanup();
        savePlayerCosmetics();
        loadConfig();
    }

    public void cleanup() {
        if (!graves.isEmpty()) {
            saveGraves();
            plugin.getLogger().info("Limpiando y guardando " + graves.size() + " tumbas...");
            Iterator<Map.Entry<Location, GraveData>> it = graves.entrySet().iterator();

            while (it.hasNext()) {
                Map.Entry<Location, GraveData> entry = it.next();
                GraveData data = entry.getValue();

                if (data.getHologram() != null) {
                    data.getHologram().remove();
                }
                it.remove();
            }
        }
        savePlayerCosmetics();
    }

    private void loadPlayerCosmetics() {
        playerCosmetics.clear();
        if (!playerData.contains("players")) return;
        
        for (String uuidStr : playerData.getConfigurationSection("players").getKeys(false)) {
            UUID uuid = UUID.fromString(uuidStr);
            PlayerCosmetics pc = new PlayerCosmetics(uuid);
            
            String path = "players." + uuidStr + ".";
            pc.setCoins(playerData.getInt(path + "coins", 0));
            pc.setSelectedGrave(playerData.getString(path + "selected-grave", "SOUL_CAMPFIRE"));
            pc.setSelectedEffect(playerData.getString(path + "selected-effect", "NONE"));
            
            List<String> unlockedG = playerData.getStringList(path + "unlocked-graves");
            for (String g : unlockedG) pc.addUnlockedGrave(g);
            
            List<String> unlockedE = playerData.getStringList(path + "unlocked-effects");
            for (String e : unlockedE) pc.addUnlockedEffect(e);
            
            playerCosmetics.put(uuid, pc);
        }
    }

    public void savePlayerCosmetics() {
        for (Map.Entry<UUID, PlayerCosmetics> entry : playerCosmetics.entrySet()) {
            String path = "players." + entry.getKey().toString() + ".";
            PlayerCosmetics pc = entry.getValue();
            
            playerData.set(path + "coins", pc.getCoins());
            playerData.set(path + "selected-grave", pc.getSelectedGrave());
            playerData.set(path + "selected-effect", pc.getSelectedEffect());
            playerData.set(path + "unlocked-graves", new ArrayList<>(pc.getUnlockedGraves()));
            playerData.set(path + "unlocked-effects", new ArrayList<>(pc.getUnlockedEffects()));
        }
        try {
            playerData.save(playerDataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Error guardando players.yml: " + e.getMessage());
        }
    }

    public PlayerCosmetics getCosmetics(UUID uuid) {
        return playerCosmetics.computeIfAbsent(uuid, PlayerCosmetics::new);
    }

    public void playDeathEffect(Location loc, String effectName) {
        World w = loc.getWorld();
        if (w == null || "NONE".equalsIgnoreCase(effectName)) return;

        switch (effectName.toUpperCase()) {
            case "EXPLOSION":
                w.spawnParticle(Particle.EXPLOSION, loc, 3, 0.5, 0.5, 0.5, 0.1);
                w.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);
                break;
            case "LIGHTNING":
                w.strikeLightningEffect(loc);
                break;
            case "BLOOD":
                w.spawnParticle(Particle.BLOCK, loc, 50, 0.5, 0.5, 0.5, Bukkit.createBlockData(Material.REDSTONE_BLOCK));
                w.playSound(loc, Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 0.5f, 0.5f);
                break;
            case "SOULS":
                w.spawnParticle(Particle.SOUL, loc.clone().add(0, 1, 0), 25, 0.5, 1.0, 0.5, 0.05);
                w.playSound(loc, Sound.PARTICLE_SOUL_ESCAPE, 1.0f, 1.0f);
                break;
            case "FLAME":
                w.spawnParticle(Particle.FLAME, loc.clone().add(0, 0.5, 0), 40, 0.5, 0.5, 0.5, 0.05);
                w.playSound(loc, Sound.ITEM_FIRECHARGE_USE, 1.0f, 1.0f);
                break;
            case "VOID":
                w.spawnParticle(Particle.PORTAL, loc.clone().add(0, 1, 0), 50, 0.5, 0.5, 0.5, 0.5);
                w.playSound(loc, Sound.BLOCK_END_PORTAL_SPAWN, 0.5f, 1.5f);
                break;
            case "MUSIC":
                w.spawnParticle(Particle.NOTE, loc.clone().add(0, 1, 0), 15, 0.5, 0.5, 0.5, 1);
                w.playSound(loc, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
                break;
            case "FIREWORK":
                w.spawnParticle(Particle.FIREWORK, loc.clone().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.1);
                w.playSound(loc, Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 1.0f, 1.0f);
                break;
            case "LOVE":
                w.spawnParticle(Particle.HEART, loc.clone().add(0, 1, 0), 15, 0.5, 0.5, 0.5, 1);
                w.playSound(loc, Sound.ENTITY_ALLAY_HURT, 1.0f, 0.5f); 
                break;
            case "SNOWBALL":
                w.spawnParticle(Particle.SNOWFLAKE, loc.clone().add(0, 1, 0), 50, 0.5, 0.5, 0.5, 0.05);
                w.playSound(loc, Sound.ENTITY_SNOW_GOLEM_DEATH, 1.0f, 1.0f);
                break;
            case "SLIME":
                w.spawnParticle(Particle.ITEM_SLIME, loc.clone().add(0, 0.5, 0), 60, 0.5, 0.5, 0.5, 0.1);
                w.playSound(loc, Sound.ENTITY_SLIME_DEATH, 1.0f, 0.5f);
                break;
            case "ENCHANT":
                w.spawnParticle(Particle.ENCHANT, loc.clone().add(0, 1, 0), 100, 0.5, 1.0, 0.5, 1);
                w.playSound(loc, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 0.8f);
                break;
            case "TOTEM":
                w.spawnParticle(Particle.TOTEM_OF_UNDYING, loc.clone().add(0, 1, 0), 80, 0.5, 0.5, 0.5, 0.5);
                w.playSound(loc, Sound.ITEM_TOTEM_USE, 1.0f, 1.0f);
                break;
            case "CHERRY":
                w.spawnParticle(Particle.CHERRY_LEAVES, loc.clone().add(0, 1, 0), 60, 0.5, 0.5, 0.5, 0.05);
                w.playSound(loc, Sound.BLOCK_CHERRY_LEAVES_BREAK, 1.0f, 0.8f);
                break;
            case "SCULK":
                w.spawnParticle(Particle.SCULK_SOUL, loc.clone().add(0, 1, 0), 40, 0.5, 0.5, 0.5, 0.1);
                w.playSound(loc, Sound.ENTITY_WARDEN_DEATH, 1.0f, 1.0f);
                break;
            case "DRAGON_BREATH":
                w.spawnParticle(Particle.DRAGON_BREATH, loc.clone().add(0, 1, 0), 80, 0.5, 0.5, 0.5, 0.1);
                w.playSound(loc, Sound.ENTITY_ENDER_DRAGON_SHOOT, 1.0f, 0.5f);
                break;
            case "GUST":
                w.spawnParticle(Particle.GUST, loc.clone().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.2);
                w.playSound(loc, Sound.ENTITY_BREEZE_DEATH, 1.0f, 1.0f);
                break;
        }
    }

    private void saveGraves() {
        File file = new File(plugin.getDataFolder(), "modules/headdeath/graves.yml");
        YamlConfiguration data = new YamlConfiguration();
        int i = 0;
        for (GraveData gd : graves.values()) {
            String path = "graves." + i + ".";
            data.set(path + "location", gd.getLocation());
            data.set(path + "victim", gd.getVictim().toString());
            data.set(path + "victimName", gd.getVictimName());
            data.set(path + "owner", gd.getKiller() != null ? gd.getKiller().toString() : null);
            data.set(path + "deathTime", gd.getDeathTimeMillis());
            data.set(path + "expireTime", gd.getExpireTimeMillis());
            data.set(path + "isEmptying", gd.isEmptying());
            data.set(path + "graveMaterial", gd.getGraveMaterial().name());
            data.set(path + "inventory", gd.getInventory().getContents());
            i++;
        }
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Error guardando tumbas: " + e.getMessage());
        }
    }

    private void loadGraves() {
        File file = new File(plugin.getDataFolder(), "modules/headdeath/graves.yml");
        if (!file.exists()) return;

        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
        if (!data.contains("graves")) return;

        for (String key : data.getConfigurationSection("graves").getKeys(false)) {
            String path = "graves." + key + ".";
            Location loc = data.getLocation(path + "location");
            if (loc == null) continue;

            UUID victim = UUID.fromString(Objects.requireNonNull(data.getString(path + "victim")));
            String victimName = data.getString(path + "victimName");
            String ownerStr = data.getString(path + "owner");
            UUID owner = ownerStr != null ? UUID.fromString(ownerStr) : null;
            long deathTime = data.getLong(path + "deathTime");
            long expireTime = data.getLong(path + "expireTime");
            boolean isEmptying = data.getBoolean(path + "isEmptying");
            
            String matStr = data.getString(path + "graveMaterial", "SOUL_CAMPFIRE");
            Material graveMat = Material.matchMaterial(matStr);
            if (graveMat == null) graveMat = Material.SOUL_CAMPFIRE;

            List<?> items = data.getList(path + "inventory");
            int size = ((items.size() / 9) + 1) * 9;
            if (size > 54) size = 54;
            Inventory inv = Bukkit.createInventory(null, size, "§8Tumba de " + victimName);
            
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i) instanceof ItemStack item) {
                    inv.setItem(i, item);
                }
            }

            GraveData gd = new GraveData(victim, victimName, owner, deathTime, expireTime, inv, null, loc, graveMat);
            gd.setEmptying(isEmptying);
            graves.put(loc, gd);
            
            spawnHologramFor(gd);
        }
        file.delete(); 
    }

    public void clearHologramsAt(Location loc) {
        if (!loc.getChunk().isLoaded()) return;
        for (Entity e : loc.getWorld().getNearbyEntities(loc.clone().add(0.5, 1.2, 0.5), 1, 3, 1)) {
            if (e instanceof TextDisplay && e.getScoreboardTags().contains("etherium_grave")) {
                e.remove();
            }
        }
    }

    private void spawnHologramFor(GraveData data) {
        if (!config.getBoolean("hologram.enabled", true)) return;
        
        Location loc = data.getLocation();
        clearHologramsAt(loc);

        double offset = config.getDouble("hologram.height-offset", 1.2);
        Location spawnLoc = loc.clone().add(0.5, offset, 0.5);
        
        TextDisplay display = loc.getWorld().spawn(spawnLoc, TextDisplay.class, ent -> {
            ent.setBillboard(TextDisplay.Billboard.CENTER);
            ent.setDefaultBackground(false);
            ent.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            ent.setShadowed(true);
            ent.addScoreboardTag("etherium_grave");
        });
        
        data.setHologram(display);
        updateHologram(data);
    }

    public void createGrave(Player victim, Player owner, List<ItemStack> items, Location loc, Material graveMaterial) {
        if (items.isEmpty()) return;

        Block block = loc.getBlock();
        block.setType(graveMaterial);
        
        if (graveMaterial == Material.PLAYER_HEAD || graveMaterial == Material.SKELETON_SKULL || graveMaterial == Material.WITHER_SKELETON_SKULL) {
            org.bukkit.block.BlockState state = block.getState();
            if (state instanceof org.bukkit.block.Skull skull && graveMaterial == Material.PLAYER_HEAD) {
                skull.setOwningPlayer(Bukkit.getOfflinePlayer(victim.getUniqueId()));
                skull.update();
            }
        } else if (block.getBlockData() instanceof Campfire campfire) {
            campfire.setWaterlogged(false);
            block.setBlockData(campfire);
        }

        int size = ((items.size() / 9) + 1) * 9;
        if (size > 54) size = 54;
        Inventory inv = Bukkit.createInventory(null, size, "§8Tumba de " + victim.getName());
        for (ItemStack item : items) {
            if (item != null && item.getType() != Material.AIR) inv.addItem(item);
        }

        long now = System.currentTimeMillis();
        long expireTime = now + (config.getInt("settings.grave-despawn-seconds", 180) * 1000L);
        UUID ownerId = owner != null ? owner.getUniqueId() : null;

        GraveData data = new GraveData(victim.getUniqueId(), victim.getName(), ownerId, now, expireTime, inv, null, block.getLocation(), graveMaterial);
        graves.put(block.getLocation(), data);

        spawnHologramFor(data);

        String killerName = (owner != null && !owner.getUniqueId().equals(victim.getUniqueId())) ? owner.getName() : "Entorno/Monstruo";
        logDeathToDiscord(victim.getName(), killerName, loc, items);
    }

    private void startTasks() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!isEnabled() || graves.isEmpty()) return;
                
                long now = System.currentTimeMillis();
                boolean spawnParticles = config.getBoolean("particles.enabled", true);
                List<String> effects = config.getStringList("particles.effects");
                
                Iterator<Map.Entry<Location, GraveData>> it = graves.entrySet().iterator();

                while (it.hasNext()) {
                    Map.Entry<Location, GraveData> entry = it.next();
                    GraveData grave = entry.getValue();
                    Location loc = entry.getKey();

                    if (now >= grave.getExpireTimeMillis()) {
                        expireGraveInternal(loc, grave);
                        it.remove();
                    } else {
                        if (grave.getHologram() != null) {
                            updateHologram(grave);
                        }
                        if (spawnParticles && loc.getChunk().isLoaded()) {
                            spawnGraveParticles(loc, effects);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void spawnGraveParticles(Location loc, List<String> effects) {
        Location pLoc = loc.clone().add(0.5, 0.5, 0.5);
        for (String effectData : effects) {
            try {
                String[] split = effectData.split(":");
                Particle particle = Particle.valueOf(split[0]);
                int count = split.length > 1 ? Integer.parseInt(split[1]) : 5;
                pLoc.getWorld().spawnParticle(particle, pLoc, count, 0.3, 0.3, 0.3, 0.05);
            } catch (Exception ignored) { }
        }
    }

    private void updateHologram(GraveData grave) {
        if (grave.getHologram() == null) return;

        long remainingSecs = (grave.getExpireTimeMillis() - System.currentTimeMillis()) / 1000;
        if (remainingSecs < 0) remainingSecs = 0;

        List<String> lines = config.getStringList("hologram.lines");
        StringBuilder builder = new StringBuilder();
        for (String line : lines) {
            builder.append(ChatColor.translateAlternateColorCodes('&', line
                    .replace("{victim}", grave.getVictimName())
                    .replace("{time}", String.valueOf(remainingSecs)))).append("\n");
        }
        grave.getHologram().setText(builder.toString().trim());
    }

    private void expireGraveInternal(Location loc, GraveData data) {
        if (config.getBoolean("settings.drop-items-on-expire", true)) {
            for (ItemStack item : data.getInventory().getContents()) {
                if (item != null && item.getType() != Material.AIR) {
                    loc.getWorld().dropItemNaturally(loc, item);
                }
            }
        }
        if (data.getHologram() != null) data.getHologram().remove();
        loc.getBlock().setType(Material.AIR);
    }

    public void setEmptying(GraveData grave) {
        if (grave.isEmptying()) return;
        
        grave.setEmptying(true);
        Block block = grave.getLocation().getBlock();
        
        if (block.getType() == Material.SOUL_CAMPFIRE) {
            block.setType(Material.CAMPFIRE);
            if (block.getBlockData() instanceof Campfire c) {
                c.setWaterlogged(false);
                block.setBlockData(c);
            }
        }
        
        long now = System.currentTimeMillis();
        long emptySecs = config.getInt("settings.empty-grave-seconds", 30);
        long newExpire = now + (emptySecs * 1000L);
        if (grave.getExpireTimeMillis() > newExpire) {
            grave.setExpireTimeMillis(newExpire);
        }
    }

    public boolean canLoot(Player player, GraveData grave) {
        if (grave.getKiller() == null || player.getUniqueId().equals(grave.getKiller())) return true;
        long passed = (System.currentTimeMillis() - grave.getDeathTimeMillis()) / 1000;
        return passed > config.getInt("settings.protection-seconds", 30);
    }

    public GraveData getGraveByInventory(Inventory inv) {
        for (GraveData data : graves.values()) {
            if (data.getInventory().equals(inv)) return data;
        }
        return null;
    }

    public String getMsg(String path) {
        String prefix = messages.getString("prefix", "&8[&4&l☠&8] &f");
        return ChatColor.translateAlternateColorCodes('&', prefix + messages.getString(path, ""));
    }

    public String getRawMsg(String path) {
        return ChatColor.translateAlternateColorCodes('&', messages.getString(path, ""));
    }

    public void logDeathToDiscord(String victimName, String killerName, Location loc, List<ItemStack> items) {
        if (!config.getBoolean("discord-logs.enabled", false)) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String pasteUrl = uploadToPaste(generatePasteContent(victimName, items));
            String description = "☠ **Muerte Detectada en el Servidor**";
            String fields = "[" +
                    "{\"name\":\"👤 Víctima\",\"value\":\"" + victimName + "\",\"inline\":true}," +
                    "{\"name\":\"⚔ Asesino\",\"value\":\"" + killerName + "\",\"inline\":true}," +
                    "{\"name\":\"📍 Ubicación\",\"value\":\"Mundo: " + loc.getWorld().getName() + "\\nX: " + loc.getBlockX() + ", Y: " + loc.getBlockY() + ", Z: " + loc.getBlockZ() + "\",\"inline\":false}," +
                    "{\"name\":\"🎒 Inventario Perdido\",\"value\":\"[Ver inventario completo](" + pasteUrl + ")\",\"inline\":false}" +
                    "]";
            sendWebhookEmbedRaw(description, fields, java.awt.Color.RED);
        });
    }

    public void logLootToDiscord(String looterName, String victimName, Location loc, Inventory inventory) {
        if (!config.getBoolean("discord-logs.enabled", false)) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<ItemStack> items = new ArrayList<>();
            for (ItemStack i : inventory.getContents()) {
                if (i != null && i.getType() != Material.AIR) items.add(i);
            }
            String pasteUrl = uploadToPaste(generatePasteContent(victimName + " (Loteada por " + looterName + ")", items));
            String description = "🔍 **Tumba Abierta para Loteo**";
            String fields = "[" +
                    "{\"name\":\"👤 Loteador\",\"value\":\"" + looterName + "\",\"inline\":true}," +
                    "{\"name\":\"👤 Dueño de Tumba\",\"value\":\"" + victimName + "\",\"inline\":true}," +
                    "{\"name\":\"📍 Ubicación\",\"value\":\"Mundo: " + loc.getWorld().getName() + "\\nX: " + loc.getBlockX() + ", Y: " + loc.getBlockY() + ", Z: " + loc.getBlockZ() + "\",\"inline\":false}," +
                    "{\"name\":\"🎒 Contenido Actual\",\"value\":\"[Ver ítems en la tumba](" + pasteUrl + ")\",\"inline\":false}" +
                    "]";
            sendWebhookEmbedRaw(description, fields, java.awt.Color.ORANGE);
        });
    }

    private String generatePasteContent(String title, List<ItemStack> items) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Inventario de ").append(title).append(" ===\n\n");
        if (items == null || items.isEmpty()) return sb.append("El inventario estaba vacío.\n").toString();
        List<ItemStack> armor = new ArrayList<>(), main = new ArrayList<>();
        for (ItemStack item : items) {
            if (item == null || item.getType() == Material.AIR) continue;
            String typeName = item.getType().name();
            if (typeName.endsWith("_HELMET") || typeName.endsWith("_CHESTPLATE") || typeName.endsWith("_LEGGINGS") || typeName.endsWith("_BOOTS")) {
                armor.add(item);
            } else {
                main.add(item);
            }
        }
        if (!armor.isEmpty()) {
            sb.append("[ ARMADURA ]\n");
            for (ItemStack item : armor) {
                sb.append("- ").append(item.getAmount()).append("x ").append(item.getType().name());
                if (item.hasItemMeta() && item.getItemMeta().hasEnchants()) sb.append(" [Encantado]");
                sb.append("\n");
            }
            sb.append("\n");
        }
        if (!main.isEmpty()) {
            sb.append("[ INVENTARIO PRINCIPAL ]\n");
            for (ItemStack item : main) {
                sb.append("- ").append(item.getAmount()).append("x ").append(item.getType().name());
                if (item.hasItemMeta() && item.getItemMeta().hasEnchants()) sb.append(" [Encantado]");
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    private String uploadToPaste(String text) {
        try {
            URL url = new URL("https://paste.helpch.at/documents");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("User-Agent", "Etherium-Plugin");
            connection.setRequestProperty("Content-Type", "text/plain; charset=UTF-8");
            connection.setDoOutput(true);
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = text.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            if (connection.getResponseCode() == 200) {
                try (Scanner scanner = new Scanner(connection.getInputStream(), StandardCharsets.UTF_8.name())) {
                    String response = scanner.useDelimiter("\\A").next();
                    String key = response.split("\"key\":\"")[1].split("\"")[0];
                    return "https://paste.helpch.at/" + key;
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error subiendo a Pastebin: " + e.getMessage());
        }
        return "No se pudo generar el enlace.";
    }

    private void sendWebhookEmbedRaw(String description, String fieldsJson, java.awt.Color color) {
        String webhookUrl = config.getString("discord-logs.webhook-url", "");
        if (webhookUrl.isEmpty() || webhookUrl.equals("AQUI_PEGA_TU_ENLACE_DE_WEBHOOK")) return;
        try {
            URL url = new URL(webhookUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("User-Agent", "Etherium-Plugin");
            connection.setDoOutput(true);
            int colorInt = color.getRGB() & 0xFFFFFF;
            String time = ZonedDateTime.now(ZoneId.of("GMT-4")).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
            String botName = config.getString("discord-logs.bot-name", "Etherium Logs");
            String botAvatar = config.getString("discord-logs.bot-avatar", "");

            StringBuilder jsonBuilder = new StringBuilder("{");
            jsonBuilder.append("\"username\":\"").append(botName).append("\",");
            if (!botAvatar.isEmpty()) jsonBuilder.append("\"avatar_url\":\"").append(botAvatar).append("\",");
            jsonBuilder.append("\"embeds\":[{")
                       .append("\"description\":\"").append(description).append("\",")
                       .append("\"color\":").append(colorInt).append(",")
                       .append("\"fields\":").append(fieldsJson).append(",")
                       .append("\"footer\":{\"text\":\"Etherium Logs • GMT-4 • ").append(time).append("\"}")
                       .append("}]}");

            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = jsonBuilder.toString().getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }
            connection.getResponseCode();
            connection.disconnect();
        } catch (Exception ignored) {}
    }

    public Map<Location, GraveData> getGraves() { return graves; }
    public FileConfiguration getConfig() { return config; }
    public FileConfiguration getCosmeticsConfig() { return cosmeticsConfig; }

    public boolean isWorldEnabled(String worldName) {
        if (!config.contains("settings.enabled-worlds")) return true;
        return config.getStringList("settings.enabled-worlds").contains(worldName);
    }
}