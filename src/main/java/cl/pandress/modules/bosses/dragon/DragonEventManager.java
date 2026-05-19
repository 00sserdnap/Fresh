package cl.pandress.modules.bosses.dragon;

import cl.pandress.Etherium;
import cl.pandress.modules.bosses.dragon.listeners.DragonEventListener;
import cl.pandress.modules.bosses.dragon.managers.DragonPowerManager;
import cl.pandress.utils.ChatUtils;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.*;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.stream.Collectors;

public class DragonEventManager {

    private final Etherium plugin;
    private EnderDragon activeDragon;
    private boolean eventActive = false;
    private final List<LivingEntity> activeMinions = new ArrayList<>();
    private final Random random = new Random();

    private Location spawnLocation;
    private BossBar bossBar;
    private DragonEventListener eventListener;

    private BukkitTask mainTask;
    private BukkitTask musicTask;
    private BukkitTask minionScaleTask;
    private DragonParticleTask particleTask;
    private DragonPowerManager powerManager;

    private int currentPhase = 1;
    private final Map<UUID, Double> damageMap = new HashMap<>();
    private final Map<UUID, Integer> hitMap = new HashMap<>();
    private double minionDamageMultiplier = 1.0;
    private int eventSeconds = 0;

    public static final String EVENT_WORLD = "event_end";

    private static final String[][] TAUNT_MESSAGES = {
        {
            "&d¿En serio? ¿Eso es todo lo que tenéis?",
            "&dMe aburro más viendo crecer el pasto...",
            "&dHe dormido mil años para &n¿esto?",
            "&d¡Mis escamas son más duras que vuestros cráneos!",
            "&dMi abuela dragona os haría más daño que vosotros a mí."
        },
        {
            "&c¡Empezáis a molestarme! ¡Arrepentíos!",
            "&c¡Osáis herirme! ¡Pagaréis con sangre!",
            "&c¡Ya no me divierto! ¡DESTRUCCIÓN!",
            "&c¡Mis criaturas, DESPERTAD con más fuerza!",
            "&c¡Sentid mi verdadera ira!"
        },
        {
            "&4&lIMPOSIBLE... ¡NO PUEDO MORIR!",
            "&4&l¡SI YO CAIGO, CAÉIS TODOS CONMIGO!",
            "&4&l¡MALDITOS SEÁIS POR MIL GENERACIONES!",
            "&4&l¡EL MUNDO ARDERÁ CON MIS ÚLTIMAS LLAMAS!",
            "&4&l¡NO... NO... ESTO NO PUEDE SER...!"
        }
    };

    public DragonEventManager(Etherium plugin) {
        this.plugin = plugin;
        this.powerManager = new DragonPowerManager(this, plugin);
    }

    public World getEventWorld() {
        World w = Bukkit.getWorld(EVENT_WORLD);
        if (w == null) throw new IllegalStateException("El mundo '" + EVENT_WORLD + "' no existe o no está cargado.");
        return w;
    }

    public void registerDamage(UUID playerUUID, double damage) {
        damageMap.merge(playerUUID, damage, Double::sum);
        hitMap.merge(playerUUID, 1, Integer::sum);
    }

    public void startEvent(Location spawnLoc) {
        if (eventActive) {
            plugin.log("&cYa hay un evento de dragón activo.");
            return;
        }

        World eventWorld;
        try {
            eventWorld = getEventWorld();
        } catch (IllegalStateException e) {
            plugin.log("&c[EVENTO] " + e.getMessage());
            return;
        }

        // ====== CAMBIO DE DIFICULTAD A HARD AL INICIAR ======
        eventWorld.setDifficulty(Difficulty.HARD);

        this.spawnLocation = new Location(eventWorld, spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ(), spawnLoc.getYaw(), spawnLoc.getPitch());
        eventActive = true;
        currentPhase = 1;
        damageMap.clear();
        hitMap.clear();
        minionDamageMultiplier = 1.0;
        eventSeconds = 0;

        broadcastTitle("&5&l☠ DRAGÓN ANCESTRAL ☠", "&7Prepárate... algo despierta", 10, 60, 20);
        playWorldSound(this.spawnLocation, Sound.ENTITY_ENDER_DRAGON_GROWL, 2.0f, 0.6f);

        spawnCountdownParticles(this.spawnLocation);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!eventActive) return; 
                
                spawnDragon(spawnLocation);
            }
        }.runTaskLater(plugin, 60L);
    }

    private void spawnCountdownParticles(Location loc) {
        World w = loc.getWorld();
        if (w == null) return;
        new BukkitRunnable() {
            double angle = 0;
            int count = 0;
            @Override
            public void run() {
                if (count++ > 40) { this.cancel(); return; }
                angle += Math.PI / 8;
                double r = 8 + Math.sin(angle) * 4;
                Location ring = loc.clone().add(Math.cos(angle) * r, 2 + Math.sin(angle * 2) * 3, Math.sin(angle) * r);
                w.spawnParticle(Particle.PORTAL, ring, 8, 0.3, 0.3, 0.3, 0.2);
            }
        }.runTaskTimer(plugin, 0L, 3L);
    }

    private void spawnDragon(Location spawnLoc) {
        World eventWorld;
        try { eventWorld = getEventWorld(); }
        catch (IllegalStateException e) { plugin.log("&c" + e.getMessage()); cleanup(); return; }

        try {
            activeDragon = (EnderDragon) eventWorld.spawnEntity(spawnLoc, EntityType.ENDER_DRAGON);
            if (activeDragon == null) {
                plugin.log("&c[CRÍTICO] El servidor canceló la aparición del dragón.");
                worldBroadcast("&8[&5&lEVENTO&8] &cError: No se pudo invocar al dragón.");
                eventActive = false;
                return;
            }

            if (activeDragon.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null) {
                activeDragon.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(1000.0);
                activeDragon.setHealth(1000.0);
            }

            activeDragon.setCustomName(ChatUtils.colorize("&5&lDragón Ancestral &8[&cJEFE&8]"));
            activeDragon.setCustomNameVisible(true);
            activeDragon.setPhase(EnderDragon.Phase.HOVER);

            bossBar = Bukkit.createBossBar(ChatUtils.colorize("&5&l☠ Dragón Ancestral &8[&cJEFE&8] ☠"), BarColor.PURPLE, BarStyle.SEGMENTED_20);

        } catch (Exception e) {
            plugin.log("&cError interno al crear el dragón: " + e.getMessage());
            e.printStackTrace();
            cleanup();
            return;
        }

        worldBroadcast("");
        worldBroadcast("&5&m══════════════════════════════════");
        worldBroadcast("  &5&l☠ &d&lEL DRAGÓN ANCESTRAL HA DESPERTADO &5&l☠");
        worldBroadcast("  &7¡Defendeos o seréis devorados!");
        worldBroadcast("&5&m══════════════════════════════════");
        worldBroadcast("");

        broadcastTitle("&5&l☠ DRAGÓN ANCESTRAL ☠", "&d¡El terror ha comenzado!", 10, 80, 20);

        spawnInitialWave();
        startMusicLoop();
        startMainTask();
        startMinionScaleTask();

        particleTask = new DragonParticleTask(this, plugin);
        particleTask.runTaskTimer(plugin, 0L, 2L);
    }

    private void spawnInitialWave() {
        worldBroadcast("&8[&5&l☠ Dragón ☠&8] &c¡LEVANTAOS, SIERVOS MÍOS! ¡DEVORADLES!");

        EntityType[] initialTypes = {
            EntityType.RAVAGER, EntityType.RAVAGER,
            EntityType.EVOKER, EntityType.EVOKER, EntityType.EVOKER,
            EntityType.VINDICATOR, EntityType.VINDICATOR, EntityType.VINDICATOR,
            EntityType.PHANTOM, EntityType.PHANTOM
        };

        List<Player> nearbyPlayers = getNearbyPlayers(100);
        World w = spawnLocation.getWorld();
        if (w == null) return;

        for (EntityType type : initialTypes) {
            double angle = random.nextDouble() * 2 * Math.PI;
            double radius = 8 + random.nextDouble() * 12;
            Location loc = spawnLocation.clone().add(Math.cos(angle) * radius, 1, Math.sin(angle) * radius);
            try {
                Mob minion = (Mob) w.spawnEntity(loc, type);
                if (minion != null) {
                    setupMinion(minion, type, nearbyPlayers.isEmpty() ? null : nearbyPlayers.get(random.nextInt(nearbyPlayers.size())));
                    w.spawnParticle(Particle.PORTAL, loc.clone().add(0, 1, 0), 60, 1, 1, 1, 0.3);
                }
            } catch (Exception ignored) {}
        }

        playWorldSound(spawnLocation, Sound.ENTITY_ENDER_DRAGON_FLAP, 2.0f, 0.5f);
        playWorldSound(spawnLocation, Sound.ENTITY_WITHER_SPAWN, 1.5f, 1.2f);
    }

    private void startMusicLoop() {
        final Sound[] ambientSounds = {
            Sound.ENTITY_ENDER_DRAGON_AMBIENT, Sound.ENTITY_ENDER_DRAGON_FLAP,
            Sound.ENTITY_ENDER_DRAGON_GROWL, Sound.AMBIENT_CAVE, Sound.MUSIC_DISC_11
        };
        final int[] soundIndex = {0};

        musicTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!eventActive || activeDragon == null) { this.cancel(); return; }
                List<Player> nearby = getNearbyPlayers(100);
                Sound current = ambientSounds[soundIndex[0] % ambientSounds.length];
                soundIndex[0]++;
                for (Player p : nearby) {
                    p.playSound(p.getLocation(), current, 0.8f, 0.7f + (random.nextFloat() * 0.4f));
                    if (currentPhase == 3) {
                        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_AMBIENT, 0.5f, 0.3f);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 100L);
    }

    private void startMinionScaleTask() {
        minionScaleTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!eventActive) { this.cancel(); return; }
                eventSeconds += 30;
                minionDamageMultiplier = Math.min(3.5, 1.0 + (eventSeconds / 30) * 0.15);

                if (eventSeconds > 60 && minionDamageMultiplier > 1.0) {
                    int pct = (int) ((minionDamageMultiplier - 1.0) * 100);
                    worldBroadcast("&8[&5&l☠ Dragón ☠&8] &c¡Mis siervos se fortalecen! &7(+&c" + pct + "%&7 daño)");
                    for (LivingEntity m : activeMinions) {
                        if (!m.isDead()) {
                            m.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, m.getLocation().add(0, 1, 0), 20, 0.5, 1, 0.5, 0.2);
                            int strengthAmp = Math.min(4, (int)((minionDamageMultiplier - 1.0) * 4));
                            m.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 20 * 35, strengthAmp, false, true));
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 600L, 600L);
    }

    private void startMainTask() {
        mainTask = new BukkitRunnable() {
            int ticks = 0;
            boolean doublePowerMode = false;

            @Override
            public void run() {
                if (!eventActive || activeDragon == null || activeDragon.isDead()) {
                    this.cancel();
                    return;
                }
                ticks += 20;

                updateBossBar();
                checkPhaseTransition();
                constrainDragonToArena();

                if (ticks % (20 * 8) == 0) forceDragonDown();
                if (ticks % (20 * 15) == 0) sendRandomTaunt();
                if (ticks % (20 * 20) == 0) spawnHeavyMinions();

                int powerInterval = currentPhase == 3 ? 20 * 4 : (currentPhase == 2 ? 20 * 5 : 20 * 7);
                if (ticks % powerInterval == 0) {
                    powerManager.executeRandomPower();
                    if (doublePowerMode) {
                        new BukkitRunnable() {
                            @Override public void run() {
                                if (isEventActive()) powerManager.executeRandomPower();
                                this.cancel();
                            }
                        }.runTaskLater(plugin, 40L);
                    }
                }

                if (ticks % (20 * 45) == 0) executeSpecialMechanic();

                if (ticks == 20 * 60 * 10 && !doublePowerMode) {
                    doublePowerMode = true;
                    broadcastTitle("&4&l⚡ FURIA TOTAL ⚡", "&c¡El dragón desata su poder al máximo!", 10, 80, 20);
                    worldBroadcast("&8[&5&lEVENTO&8] &4&l¡El dragón lleva 10 minutos vivo! ¡Sus poderes se duplican!");
                    playWorldSound(spawnLocation, Sound.ENTITY_ENDER_DRAGON_GROWL, 2.0f, 0.4f);
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void checkPhaseTransition() {
        double maxHealth = activeDragon.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
        double hp = activeDragon.getHealth();
        double pct = hp / maxHealth;

        if (pct <= 0.20 && currentPhase < 3) {
            enterPhase3();
        } else if (pct <= 0.50 && currentPhase < 2) {
            enterPhase2();
        }
    }

    private void enterPhase2() {
        currentPhase = 2;
        bossBar.setColor(BarColor.RED);
        bossBar.setTitle(ChatUtils.colorize("&4&l☠ Dragón Ancestral &8[&c¡ENOJADO!&8] ☠"));
        broadcastTitle("&c&l⚠ FASE 2 ⚠", "&4¡El Dragón está furioso!", 10, 60, 20);
        worldBroadcast("&8[&5&lEVENTO&8] &4&l¡EL DRAGÓN HA ENTRADO EN FASE DE FURIA!");
        playWorldSound(spawnLocation, Sound.ENTITY_ENDER_DRAGON_GROWL, 2.0f, 0.5f);
        playWorldSound(spawnLocation, Sound.ENTITY_WITHER_HURT, 1.5f, 0.7f);

        if (activeDragon.getWorld() != null) {
            activeDragon.getWorld().spawnParticle(Particle.EXPLOSION, activeDragon.getLocation(), 5, 3, 3, 3, 0);
        }
        spawnHeavyMinions();
        spawnHeavyMinions();
    }

    private void enterPhase3() {
        currentPhase = 3;
        bossBar.setColor(BarColor.RED);
        bossBar.setStyle(BarStyle.SOLID);
        bossBar.setTitle(ChatUtils.colorize("&4&l☠ DRAGÓN ANCESTRAL ☠ &c&l[¡AGONIZANTE!]"));
        broadcastTitle("&4&l☠ FASE FINAL ☠", "&c¡LO ESTÁN MATANDO! ¡EL DRAGÓN ENLOQUECE!", 10, 80, 20);
        worldBroadcast("&8[&5&lEVENTO&8] &4&l¡¡¡FASE FINAL!!! ¡EL DRAGÓN LUCHA POR SU VIDA!");

        for (Player p : getNearbyPlayers(100)) {
            for (int i = 0; i < 5; i++) {
                Location lightLoc = p.getLocation().clone().add((random.nextDouble() - 0.5) * 20, 0, (random.nextDouble() - 0.5) * 20);
                p.getWorld().strikeLightningEffect(lightLoc);
            }
        }

        if (activeDragon.getWorld() != null) {
            World w = activeDragon.getWorld();
            Location dl = activeDragon.getLocation();
            w.spawnParticle(Particle.EXPLOSION, dl, 10, 5, 5, 5, 0);
            w.spawnParticle(Particle.SONIC_BOOM, dl, 3, 2, 2, 2, 0);
        }

        playWorldSound(spawnLocation, Sound.ENTITY_WITHER_DEATH, 2.0f, 0.4f);
        spawnHeavyMinions();
        spawnHeavyMinions();
        spawnHeavyMinions();
    }

    public void spawnHeavyMinions() {
        activeMinions.removeIf(LivingEntity::isDead);

        int maxMinions = currentPhase == 3 ? 25 : (currentPhase == 2 ? 20 : 15);
        if (activeMinions.size() >= maxMinions) return;

        List<Player> nearbyPlayers = getNearbyPlayers(80);
        if (nearbyPlayers.isEmpty()) return;

        int mobsToSpawn = Math.min(currentPhase == 3 ? 7 : 5, maxMinions - activeMinions.size());

        EntityType[] phase1Types = {EntityType.RAVAGER, EntityType.EVOKER, EntityType.VINDICATOR};
        EntityType[] phase2Types = {EntityType.RAVAGER, EntityType.EVOKER, EntityType.VINDICATOR, EntityType.PHANTOM, EntityType.WITCH};
        EntityType[] phase3Types = {EntityType.RAVAGER, EntityType.EVOKER, EntityType.WARDEN, EntityType.PHANTOM, EntityType.WITCH, EntityType.WITCH};
        EntityType[] pool = currentPhase == 3 ? phase3Types : (currentPhase == 2 ? phase2Types : phase1Types);

        String[] waveMessages = {"&c¡Levantaos, mis bestias!", "&c¡Acabad con estos insectos!", "&c¡Más carne para mis siervos!", "&c¡Nunca dejaré de invocarlos!"};
        sendTaunt(waveMessages[random.nextInt(waveMessages.length)]);

        for (int i = 0; i < mobsToSpawn; i++) {
            Player targetPlayer = nearbyPlayers.get(random.nextInt(nearbyPlayers.size()));
            EntityType mobType = pool[random.nextInt(pool.length)];
            spawnSpecificMinion(targetPlayer, mobType);
        }
    }

    public void spawnSpecificMinion(Player target, EntityType type) {
        World eventWorld;
        try { eventWorld = getEventWorld(); } catch (IllegalStateException e) { return; }

        Location spawnLoc = target.getLocation().clone().add((random.nextDouble() - 0.5) * 8, 1, (random.nextDouble() - 0.5) * 8);
        spawnLoc = new Location(eventWorld, spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ());

        try {
            Mob minion = (Mob) eventWorld.spawnEntity(spawnLoc, type);
            if (minion != null) {
                setupMinion(minion, type, target);
                eventWorld.spawnParticle(Particle.PORTAL, spawnLoc.clone().add(0, 1, 0), 40, 0.5, 1, 0.5, 0.2);
                eventWorld.playSound(spawnLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 0.6f);
            }
        } catch (Exception ignored) {}
    }

    private void setupMinion(Mob minion, EntityType type, Player target) {
        if (type == EntityType.PHANTOM) minion.setCustomName(ChatUtils.colorize("&7&lEspectro Ancestral"));
        else if (type == EntityType.WARDEN) minion.setCustomName(ChatUtils.colorize("&0&lGuardián de las Sombras"));
        else minion.setCustomName(ChatUtils.colorize("&c&lProtector Ancestral"));
        minion.setCustomNameVisible(true);

        double baseHealth = (type == EntityType.WARDEN) ? 200.0 : (type == EntityType.RAVAGER) ? 150.0 : 100.0;
        if (minion.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null) {
            minion.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(baseHealth);
            minion.setHealth(baseHealth);
        }
        if (minion.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED) != null) {
            minion.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0.35);
        }

        int strengthAmp = Math.max(0, Math.min(4, (int)((minionDamageMultiplier - 1.0) * 4)));
        if (strengthAmp > 0) {
            minion.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, Integer.MAX_VALUE, strengthAmp, false, true));
        }

        if (target != null) minion.setTarget(target);
        activeMinions.add(minion);
    }

    private void updateBossBar() {
        if (bossBar == null || activeDragon == null) return;
        double maxHealth = activeDragon.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
        bossBar.setProgress(Math.max(0.0, Math.min(1.0, activeDragon.getHealth() / maxHealth)));

        World eventWorld;
        try {
            eventWorld = getEventWorld();
        } catch (IllegalStateException e) {
            return;
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getWorld().equals(eventWorld)) {
                if (!bossBar.getPlayers().contains(p)) {
                    bossBar.addPlayer(p);
                }
            } else {
                if (bossBar.getPlayers().contains(p)) {
                    bossBar.removePlayer(p);
                }
            }
        }
    }

    private void constrainDragonToArena() {
        if (activeDragon == null) return;
        Location loc = activeDragon.getLocation();
        if (loc.getY() - spawnLocation.getY() > 8 || loc.distance(spawnLocation) > 50) {
            forceDragonDown();
        }
    }

    private void forceDragonDown() {
        if (activeDragon == null) return;
        List<Player> nearby = getNearbyPlayers(60);
        double targetY = spawnLocation.getY() + 5;
        Location safeCenter;
        
        if (!nearby.isEmpty()) {
            Player tgt = nearby.get(random.nextInt(nearby.size()));
            safeCenter = tgt.getLocation().clone();
            safeCenter.setY(targetY);
            safeCenter.setDirection(tgt.getLocation().toVector().subtract(safeCenter.toVector()));
            activeDragon.teleport(safeCenter);
            activeDragon.setTarget(tgt);
            try { activeDragon.setPhase(EnderDragon.Phase.CHARGE_PLAYER); } catch (Exception ignored) {}
        } else {
            safeCenter = spawnLocation.clone();
            safeCenter.setY(targetY);
            activeDragon.teleport(safeCenter);
            try { activeDragon.setPhase(EnderDragon.Phase.HOVER); } catch (Exception ignored) {}
        }
    }

    public void sendRandomTaunt() {
        String[] pool = TAUNT_MESSAGES[currentPhase - 1];
        sendTaunt(pool[random.nextInt(pool.length)]);
    }

    public void sendTaunt(String msg) {
        worldBroadcast("&8[&5&l☠ Dragón ☠&8] &r" + msg);
    }

    public void worldBroadcast(String msg) {
        try {
            String colored = ChatUtils.colorize(msg);
            getEventWorld().getPlayers().forEach(p -> p.sendMessage(colored));
        } catch (IllegalStateException e) { /* Ignore */ }
    }

    public void broadcastTitle(String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        try {
            getEventWorld().getPlayers().forEach(p -> p.sendTitle(ChatUtils.colorize(title), ChatUtils.colorize(subtitle), fadeIn, stay, fadeOut));
        } catch (IllegalStateException e) { /* Ignore */ }
    }

    public void playWorldSound(Location loc, Sound sound, float volume, float pitch) {
        if (loc.getWorld() != null) loc.getWorld().playSound(loc, sound, volume, pitch);
    }

    public List<Player> getNearbyPlayers(double radius) {
        if (spawnLocation == null || spawnLocation.getWorld() == null) return new ArrayList<>();
        return new ArrayList<>(spawnLocation.getWorld().getNearbyPlayers(spawnLocation, radius));
    }

    private void broadcastDeathStats(Player killer) {
        String S = "&5&m══════════════════════════════════";
        String S2 = "&8&m  ————————————————————————————  ";

        List<Map.Entry<UUID, Double>> topDmg = damageMap.entrySet().stream().sorted(Map.Entry.<UUID, Double>comparingByValue().reversed()).limit(5).collect(Collectors.toList());
        List<Map.Entry<UUID, Integer>> topHits = hitMap.entrySet().stream().sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed()).limit(5).collect(Collectors.toList());
        String[] pos = {"&6⬛ &e#1", "&7⬛ &f#2", "&c⬛ &f#3", "&7 &f#4", "&7 &f#5"};

        worldBroadcast(""); worldBroadcast(S);
        worldBroadcast("  &5&l✦ &d&lESTADÍSTICAS DEL DRAGÓN ANCESTRAL &5&l✦");
        worldBroadcast(S); worldBroadcast("");

        String killerDisplay = (killer != null) ? "&e&l" + killer.getName() : "&7Desconocido";
        worldBroadcast("  &c&l⚔ GOLPE FINAL: &r" + killerDisplay);
        worldBroadcast(""); worldBroadcast(S2);

        worldBroadcast("  &4&l● MAYOR DAÑO AL DRAGÓN &8(top 5)");
        if (topDmg.isEmpty()) worldBroadcast("    &8Sin datos.");
        else {
            for (int i = 0; i < topDmg.size(); i++) {
                worldBroadcast("  " + pos[i] + " &r&f" + getPlayerName(topDmg.get(i).getKey()) + " &8» &c" + String.format("%.1f", topDmg.get(i).getValue()) + " &7dmg");
            }
        }
        worldBroadcast(""); worldBroadcast(S2);

        worldBroadcast("  &9&l● MÁS GOLPES AL DRAGÓN &8(top 5)");
        if (topHits.isEmpty()) worldBroadcast("    &8Sin datos.");
        else {
            for (int i = 0; i < topHits.size(); i++) {
                worldBroadcast("  " + pos[i] + " &r&f" + getPlayerName(topHits.get(i).getKey()) + " &8» &b" + topHits.get(i).getValue() + " &7golpes");
            }
        }
        worldBroadcast(""); worldBroadcast(S); worldBroadcast("");
    }

    private String getPlayerName(UUID uuid) {
        Player p = Bukkit.getPlayer(uuid);
        if (p != null) return p.getName();
        return Bukkit.getOfflinePlayer(uuid).getName() != null ? Bukkit.getOfflinePlayer(uuid).getName() : uuid.toString().substring(0, 8);
    }

    public void cleanup() {
        if (mainTask != null) { mainTask.cancel(); mainTask = null; }
        if (musicTask != null) { musicTask.cancel(); musicTask = null; }
        if (particleTask != null) { particleTask.cancel(); particleTask = null; }
        if (minionScaleTask != null) { minionScaleTask.cancel(); minionScaleTask = null; }

        if (bossBar != null) { bossBar.removeAll(); bossBar = null; }
        if (activeDragon != null && !activeDragon.isDead()) activeDragon.remove();

        activeMinions.removeIf(LivingEntity::isDead);
        for (LivingEntity minion : activeMinions) minion.remove();
        activeMinions.clear();

        damageMap.clear(); hitMap.clear();
        eventActive = false; activeDragon = null; currentPhase = 1;
        minionDamageMultiplier = 1.0; eventSeconds = 0;
        if (eventListener != null) eventListener.resetDeathCounts();

        // ====== CAMBIO DE DIFICULTAD A PEACEFUL AL LIMPIAR ======
        try {
            if (getEventWorld() != null) {
                getEventWorld().setDifficulty(Difficulty.PEACEFUL);
            }
        } catch (Exception ignored) {}
    }

    public void endEventFailure() {
        cleanup();
        worldBroadcast("");
        worldBroadcast("&8[&5&lEVENTO&8] &c¡El Dragón Ancestral se ha saciado y ha escapado... por ahora.");
        worldBroadcast("&8[&5&lEVENTO&8] &7¡Fallasteis en detenerlo!");
        worldBroadcast("");
        broadcastTitle("&c&l☠ DERROTA ☠", "&7El Dragón escapó victorioso...", 10, 80, 20);
        if (spawnLocation != null) playWorldSound(spawnLocation, Sound.ENTITY_WITHER_DEATH, 1.5f, 0.6f);
    }

    public void endEventSuccess(Player killer) {
        if (mainTask != null) mainTask.cancel();
        if (musicTask != null) musicTask.cancel();
        if (particleTask != null) particleTask.cancel();
        if (minionScaleTask != null) minionScaleTask.cancel();
        if (bossBar != null) bossBar.removeAll();

        eventActive = false; currentPhase = 1;

        activeMinions.removeIf(LivingEntity::isDead);
        for (LivingEntity minion : activeMinions) minion.setHealth(0);
        activeMinions.clear();

        // ====== CAMBIO DE DIFICULTAD A PEACEFUL AL GANAR ======
        try {
            if (getEventWorld() != null) {
                getEventWorld().setDifficulty(Difficulty.PEACEFUL);
            }
        } catch (Exception ignored) {}

        String killerName = (killer != null) ? killer.getName() : "los valientes";
        worldBroadcast("");
        worldBroadcast("&5&m══════════════════════════════════");
        worldBroadcast("  &a&l¡¡¡ EL DRAGÓN ANCESTRAL HA CAÍDO !!!  ");
        worldBroadcast("  &7Derrotado por: &e&l" + killerName);
        worldBroadcast("&5&m══════════════════════════════════");
        worldBroadcast("");

        broadcastTitle("&a&l★ VICTORIA ★", "&e¡" + killerName + " ha matado al Dragón Ancestral!", 10, 100, 20);
        if (spawnLocation != null) {
            playWorldSound(spawnLocation, Sound.UI_TOAST_CHALLENGE_COMPLETE, 2.0f, 1.0f);
            playWorldSound(spawnLocation, Sound.ENTITY_ENDER_DRAGON_DEATH, 2.0f, 1.0f);
        }

        broadcastDeathStats(killer);
        damageMap.clear(); hitMap.clear(); activeDragon = null;
    }

    // =========================================================
    //  MECÁNICAS ESPECIALES (cada 45 seg)
    // =========================================================
    private int specialMechanicIndex = 0;

    private void executeSpecialMechanic() {
        int mechanic = specialMechanicIndex % 4;
        specialMechanicIndex++;

        World w;
        try { w = getEventWorld(); } catch (IllegalStateException e) { return; }

        switch (mechanic) {
            case 0: // FASE DE ESCUDO
                broadcastTitle("&6&l🛡 ESCUDO ACTIVADO 🛡", "&e¡Matad los minions dorados para romperlo!", 10, 80, 20);
                worldBroadcast("&8[&5&lEVENTO&8] &6&l¡El dragón activa su escudo! ¡Matad los 3 minions dorados en 20 seg!");
                sendTaunt("&6&l¡MI ESCUDO ES INVENCIBLE! ¡Os reto a que lo rompáis!");
                playWorldSound(spawnLocation, Sound.BLOCK_BEACON_ACTIVATE, 2.0f, 0.5f);

                List<LivingEntity> shieldMinions = new ArrayList<>();
                List<LivingEntity> aliveMinions = new ArrayList<>();
                for (LivingEntity m : activeMinions) { if (!m.isDead()) aliveMinions.add(m); }

                int toMark = Math.min(3, aliveMinions.size());
                java.util.Collections.shuffle(aliveMinions, random);
                for (int i = 0; i < toMark; i++) {
                    LivingEntity target = aliveMinions.get(i);
                    target.setCustomName(ChatUtils.colorize("&6&l★ OBJETIVO SAGRADO ★"));
                    target.setCustomNameVisible(true);
                    shieldMinions.add(target);
                }

                final boolean[] shieldActive = {true};
                new BukkitRunnable() {
                    int elapsed = 0;
                    @Override public void run() {
                        if (!isEventActive() || elapsed++ >= 20) {
                            shieldActive[0] = false;
                            if (isEventActive()) {
                                long surviving = shieldMinions.stream().filter(m -> !m.isDead()).count();
                                if (surviving > 0) {
                                    for (Player p : getNearbyPlayers(100)) p.damage(18.0);
                                    broadcastTitle("&4&l✦ ESCUDO ROTO POR TIEMPO ✦", "&c¡No lo lograron! ¡Todos sufren!", 5, 50, 10);
                                } else {
                                    broadcastTitle("&a&l✦ ESCUDO ROTO ✦", "&a¡Lo consiguieron! ¡El dragón está expuesto!", 5, 60, 10);
                                    sendTaunt("&7...Bien jugado. Esta ronda.");
                                }
                                for (LivingEntity m : shieldMinions) {
                                    if (!m.isDead()) m.setCustomName(ChatUtils.colorize("&c&lProtector Ancestral"));
                                }
                            }
                            this.cancel(); return;
                        }
                        for (LivingEntity m : shieldMinions) {
                            if (!m.isDead()) w.spawnParticle(Particle.TOTEM_OF_UNDYING, m.getLocation().add(0, 2, 0), 5, 0.3, 0.5, 0.3, 0.05);
                        }
                        if (elapsed % 5 == 0) {
                            long remaining = shieldMinions.stream().filter(m -> !m.isDead()).count();
                            worldBroadcast("&8[&5&l☠ Dragón ☠&8] &6Minions sagrados restantes: &e" + remaining + "/3 &8— &7" + (20 - elapsed) + "s");
                            if (remaining == 0) {
                                shieldActive[0] = false;
                                broadcastTitle("&a&l★ ESCUDO ROTO ★", "&a¡El dragón queda expuesto!", 5, 60, 10);
                                sendTaunt("&7¡Imposible! ¡¿Cómo...?!");
                                this.cancel();
                            }
                        }
                    }
                }.runTaskTimer(plugin, 0L, 20L);
                break;

            case 1: // CÍRCULO DE FUEGO
                broadcastTitle("&c&l🔥 CÍRCULO DE FUEGO 🔥", "&e¡Buscad las zonas verdes seguras! ¡15 seg!", 10, 60, 20);
                worldBroadcast("&8[&5&lEVENTO&8] &c&l¡El suelo arde! ¡Id a las zonas VERDES o recibiréis daño continuo!");
                sendTaunt("&c&l¡BAILAD SOBRE LAS LLAMAS O ENCONTRAD MI GRACIA! ¡ELEGID!");
                playWorldSound(spawnLocation, Sound.ENTITY_BLAZE_SHOOT, 2.0f, 0.5f);

                List<Location> safeZones = new ArrayList<>();
                for (int i = 0; i < 4; i++) {
                    double a = (Math.PI / 2) * i + random.nextDouble() * 0.8;
                    double r = 15 + random.nextDouble() * 10;
                    safeZones.add(spawnLocation.clone().add(Math.cos(a) * r, 0, Math.sin(a) * r));
                }

                new BukkitRunnable() {
                    int elapsed = 0;
                    @Override public void run() {
                        if (!isEventActive() || elapsed++ >= 15) { this.cancel(); return; }

                        for (Location safe : safeZones) {
                            for (int i = 0; i < 8; i++) {
                                double a = (Math.PI / 4) * i;
                                w.spawnParticle(Particle.HAPPY_VILLAGER, safe.clone().add(Math.cos(a) * 3, 0.1, Math.sin(a) * 3), 1, 0, 0, 0, 0);
                            }
                        }
                        for (int i = 0; i < 12; i++) {
                            double a = random.nextDouble() * 2 * Math.PI;
                            double r = random.nextDouble() * 35;
                            Location fl = spawnLocation.clone().add(Math.cos(a) * r, 0.1, Math.sin(a) * r);
                            w.spawnParticle(Particle.FLAME, fl, 2, 0.3, 0.1, 0.3, 0.02);
                        }
                        for (Player p : getNearbyPlayers(100)) {
                            boolean inSafe = false;
                            for (Location safe : safeZones) {
                                if (p.getLocation().distance(safe) <= 4.5) { inSafe = true; break; }
                            }
                            if (!inSafe) {
                                p.damage(3.0);
                                p.sendMessage(ChatUtils.colorize("&8[&5&l☠ Dragón ☠&8] &c¡El fuego te quema! ¡Ve a la zona verde!"));
                            }
                        }
                    }
                }.runTaskTimer(plugin, 0L, 20L);
                break;

            case 2: // TORMENTA DE ALMAS
                broadcastTitle("&0&l☠ TORMENTA DE ALMAS ☠", "&7¡Llegad al punto de spawn en 12 seg!", 10, 60, 20);
                worldBroadcast("&8[&5&lEVENTO&8] &0&l¡TORMENTA! &7¡Ceguera total! ¡Llegad al punto de invocación del dragón en 12 seg o morid!");
                sendTaunt("&0&l¡OSCURIDAD! ¡NADA PODÉIS VER! ¡NADA PODÉIS HACER!");
                playWorldSound(spawnLocation, Sound.ENTITY_WITHER_AMBIENT, 2.0f, 0.3f);

                for (Player p : getNearbyPlayers(100)) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20 * 14, 0));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20 * 14, 1));
                    p.sendMessage(ChatUtils.colorize("&8[&5&l☠ Dragón ☠&8] &7¡Llega al centro del arena en 12 segundos!"));
                }

                new BukkitRunnable() {
                    @Override public void run() {
                        if (!isEventActive()) { this.cancel(); return; }
                        for (Player p : getNearbyPlayers(100)) {
                            if (p.getLocation().distance(spawnLocation) > 20) {
                                p.damage(activeDragon != null && getCurrentPhase() == 3 ? 20.0 : 14.0);
                                p.sendMessage(ChatUtils.colorize("&8[&5&l☠ Dragón ☠&8] &4¡No llegaste a tiempo!"));
                            } else {
                                p.sendMessage(ChatUtils.colorize("&8[&5&l☠ Dragón ☠&8] &a¡Sobreviviste la tormenta!"));
                            }
                        }
                        broadcastTitle("&a&l★ TORMENTA PASADA ★", "&7El dragón pierde concentración.", 5, 40, 10);
                        this.cancel();
                    }
                }.runTaskLater(plugin, 20L * 12);
                break;

            case 3: // INVERSIÓN TOTAL
                broadcastTitle("&b&l⚡ INVERSIÓN TOTAL ⚡", "&7¡20 seg! ¡El dragón recibe el doble de daño, pero vosotros también!", 10, 70, 20);
                worldBroadcast("&8[&5&lEVENTO&8] &b&l¡INVERSIÓN TOTAL! &7¡Es vuestro momento de all-in! ¡20 seg!");
                sendTaunt("&b¿Os atrevéis a darme todo de una vez? ¡VAMOS!");
                playWorldSound(spawnLocation, Sound.BLOCK_BEACON_POWER_SELECT, 2.0f, 1.5f);

                final boolean[] inversionActive = {true};

                new BukkitRunnable() {
                    int elapsed = 0;
                    @Override public void run() {
                        if (!isEventActive() || elapsed++ >= 20) {
                            inversionActive[0] = false;
                            if (isEventActive()) {
                                broadcastTitle("&7✦ INVERSIÓN TERMINADA ✦", "&8El dragón vuelve a la normalidad.", 5, 40, 10);
                                sendTaunt("&7...El momento ha pasado.");
                            }
                            this.cancel(); return;
                        }
                        if (elapsed % 5 == 0) worldBroadcast("&8[&5&l☠ Dragón ☠&8] &b⚡ Inversión: &e" + (20 - elapsed) + "s restantes");
                        if (activeDragon != null) w.spawnParticle(Particle.ENCHANT, activeDragon.getLocation(), 15, 3, 2, 3, 0.3);
                    }
                }.runTaskTimer(plugin, 0L, 20L);
                break;
        }
    }

    public DragonPowerManager getPowerManager() { return powerManager; }

    public boolean isEventActive() { return eventActive; }
    public EnderDragon getActiveDragon() { return activeDragon; }
    public List<LivingEntity> getActiveMinions() { return activeMinions; }
    public int getCurrentPhase() { return currentPhase; }
    public Location getSpawnLocation() { return spawnLocation; }
    public void setEventListener(DragonEventListener listener) {
        this.eventListener = listener;
        if (listener != null) listener.setPowerManager(this.powerManager);
    }
    public DragonEventListener getEventListener() { return eventListener; }
    public double getMinionDamageMultiplier() { return minionDamageMultiplier; }
}