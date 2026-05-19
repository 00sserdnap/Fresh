package cl.pandress.modules.bosses.dragon.managers;

import cl.pandress.Etherium;
import cl.pandress.modules.bosses.dragon.DragonEventManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.DragonFireball;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public class DragonPowerManager {

    private final DragonEventManager manager;
    private final Etherium plugin;
    private final Random random = new Random();

    // Nuevas variables añadidas para solucionar los errores
    private final Set<UUID> mirrorPlayers = new HashSet<>();
    private boolean healInverted = false;

    public DragonPowerManager(DragonEventManager manager, Etherium plugin) {
        this.manager = manager;
        this.plugin = plugin;
    }

    // Nuevos métodos añadidos para solucionar los errores
    public Set<UUID> getMirrorPlayers() {
        return mirrorPlayers;
    }

    public boolean isHealInverted() {
        return healInverted;
    }

    public void setHealInverted(boolean healInverted) {
        this.healInverted = healInverted;
    }

    public void executeRandomPower() {
        if (manager.getActiveDragon() == null || manager.getActiveDragon().getWorld() == null) return;
        World w = manager.getActiveDragon().getWorld();
        
        List<Player> nearby = new ArrayList<>(manager.getNearbyPlayers(100));
        if (nearby.isEmpty()) return;

        int power = random.nextInt(25);
        int phase = manager.getCurrentPhase();

        switch (power) {
            case 0: // EXPLOSIÓN DE ALIENTO
                for (Player p : nearby) {
                    p.damage(phase == 3 ? 16.0 : 10.0);
                    Vector push = p.getLocation().toVector().subtract(manager.getActiveDragon().getLocation().toVector()).normalize().multiply(2.0);
                    p.setVelocity(push);
                }
                w.playSound(manager.getActiveDragon().getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.5f);
                manager.sendTaunt("&d¡Alejaos de mi presencia o seréis cenizas!");
                break;

            case 1: // GOLPE DE ALMA
                for (Player p : nearby) {
                    p.setHealth(2.0);
                    p.playSound(p.getLocation(), Sound.ENTITY_WITHER_DEATH, 1.0f, 1.0f);
                    w.spawnParticle(Particle.DAMAGE_INDICATOR, p.getLocation(), 30, 1, 2, 1, 0.1);
                }
                manager.broadcastTitle("&4☠", "&c¡El dragón drena el alma de todos!", 5, 40, 10);
                manager.sendTaunt("&4¡Vuestras almas me pertenecen a TODOS!");
                break;

            case 2: // BOMBA DE FIREBALLS
                for (int i = 0; i < 8; i++) {
                    double angle = i * (Math.PI / 4);
                    Vector dir = new Vector(Math.cos(angle), -0.2, Math.sin(angle)).normalize();
                    DragonFireball fb = (DragonFireball) w.spawnEntity(manager.getActiveDragon().getLocation().add(0, 2, 0), EntityType.DRAGON_FIREBALL);
                    fb.setShooter(manager.getActiveDragon());
                    fb.setDirection(dir);
                }
                w.playSound(manager.getActiveDragon().getLocation(), Sound.ENTITY_ENDER_DRAGON_SHOOT, 1.0f, 1.0f);
                manager.sendTaunt("&c¡Arded todos en mi fuego eterno!");
                break;

            case 3: // TORMENTA DE RAYOS
                for (Player p : nearby) p.getWorld().strikeLightning(p.getLocation());
                manager.sendTaunt("&e¡Sientan la ira de los cielos!");
                break;

            case 4: // SUCCIÓN
                for (Player p : nearby) {
                    Vector pull = manager.getActiveDragon().getLocation().toVector().subtract(p.getLocation().toVector()).normalize().multiply(1.8);
                    p.setVelocity(pull);
                }
                manager.sendTaunt("&5¡No podéis escapar de mi gravedad!");
                break;

            case 5: // WITHER MASIVO
                for (Player p : nearby) p.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 20 * 6, phase));
                w.spawnParticle(Particle.LARGE_SMOKE, manager.getActiveDragon().getLocation(), 300, 10, 5, 10, 0.1);
                manager.sendTaunt("&8¡El aliento de la muerte os consume!");
                break;

            case 6: // FIREBALLS DIRIGIDOS
                for (Player t : nearby) {
                    Vector dir = t.getLocation().toVector().subtract(manager.getActiveDragon().getLocation().toVector()).normalize();
                    DragonFireball fb = (DragonFireball) w.spawnEntity(manager.getActiveDragon().getLocation().add(0, 3, 0), EntityType.DRAGON_FIREBALL);
                    fb.setShooter(manager.getActiveDragon());
                    fb.setDirection(dir);
                }
                manager.sendTaunt("&c¡Un fireball para cada uno de vosotros!");
                break;

            case 7: // LEVITACIÓN
                for (Player p : nearby) p.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, phase == 3 ? 100 : 60, 3 + phase));
                manager.broadcastTitle("&b↑", "&7¡El dragón os eleva hacia la muerte!", 5, 40, 10);
                manager.sendTaunt("&b¡Volad todos hacia vuestro fin!");
                break;

            case 8: // PARÁLISIS
                for (Player p : nearby) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 4));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 100, -10));
                    p.playSound(p.getLocation(), Sound.ENTITY_MINECART_RIDING, 1.0f, 0.1f);
                }
                w.spawnParticle(Particle.BLOCK, manager.getSpawnLocation(), 500, 20, 0, 20, Bukkit.createBlockData(org.bukkit.Material.OBSIDIAN));
                manager.sendTaunt("&5¡Quedaos quietos mientras os devoro!");
                break;

            case 9: // ROBO DE VIDA
                double totalHeal = 0;
                for (Player p : nearby) {
                    p.damage(phase == 3 ? 14.0 : 9.0);
                    totalHeal += phase == 3 ? 20.0 : 12.0;
                }
                double finalHeal = Math.min(manager.getActiveDragon().getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue(), manager.getActiveDragon().getHealth() + totalHeal);
                manager.getActiveDragon().setHealth(finalHeal);
                w.spawnParticle(Particle.HEART, manager.getActiveDragon().getLocation(), 50, 4, 3, 4, 0);
                manager.sendTaunt("&c¡Vuestra sangre colectiva me hace INMORTAL!");
                break;

            case 10: // FATIGA
                for (Player p : nearby) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 200, 3));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 120, 1));
                }
                manager.sendTaunt("&7¡Vuestros brazos son míos ahora!");
                break;

            case 11: // ENJAMBRE DE PHANTOMS
                for (int i = 0; i < (phase == 3 ? 5 : 3); i++) manager.spawnSpecificMinion(nearby.get(random.nextInt(nearby.size())), EntityType.PHANTOM);
                manager.sendTaunt("&9¡Mis espectros os atormentarán en sueños y en vigilia!");
                break;

            case 12: // VENENO
                for (Player p : nearby) p.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 160, 2));
                w.spawnParticle(Particle.HAPPY_VILLAGER, manager.getActiveDragon().getLocation(), 300, 10, 5, 10, 0);
                manager.sendTaunt("&2¡Respirad mi aliento podrido!");
                break;

            case 13: // LANZAR AL CIELO
                for (Player p : nearby) p.teleport(p.getLocation().add(0, 30, 0));
                manager.broadcastTitle("&b↑↑", "&f¡El dragón os lanza a todos al vacío!", 5, 40, 10);
                manager.sendTaunt("&f¡Mirad abajo y llorad, TODOS!");
                break;

            case 14: // CARGA DIRECTA
                Player chargeTarget = nearby.get(random.nextInt(nearby.size()));
                manager.getActiveDragon().setTarget(chargeTarget);
                Location chargePos = chargeTarget.getLocation().clone().add(0, 8, 0);
                chargePos.setY(Math.max(chargePos.getY(), manager.getSpawnLocation().getY() + 5));
                manager.getActiveDragon().teleport(chargePos);
                try { manager.getActiveDragon().setPhase(org.bukkit.entity.EnderDragon.Phase.CHARGE_PLAYER); } catch (Exception ignored) {}
                w.playSound(manager.getActiveDragon().getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 2.0f, 0.8f);
                manager.broadcastTitle("&5⚡", "&c¡El dragón CARGA contra " + chargeTarget.getName() + "!", 5, 40, 10);
                break;

            case 15: // TP MASIVO
                for (Player p : nearby) {
                    double angle = random.nextDouble() * 2 * Math.PI;
                    double dist = 10 + random.nextDouble() * 15;
                    Location tpLoc = manager.getSpawnLocation().clone().add(Math.cos(angle) * dist, 1, Math.sin(angle) * dist);
                    p.teleport(tpLoc);
                    w.spawnParticle(Particle.PORTAL, tpLoc, 40, 1, 1, 1, 0.3);
                }
                manager.playWorldSound(manager.getSpawnLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.5f, 0.7f);
                manager.sendTaunt("&5¡Jugad al escondite con la muerte!");
                break;

            case 16: // CEGUERA
                for (Player p : nearby) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 80, 0));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 120, 0));
                }
                manager.sendTaunt("&f¡No podéis ver lo que no podéis comprender!");
                break;

            case 17: // EXPLOSIÓN MASIVA
                w.createExplosion(manager.getActiveDragon().getLocation(), 3.0f, false, false);
                for (Player p : nearby) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 160, 3));
                    p.damage(12.0);
                }
                manager.playWorldSound(manager.getSpawnLocation(), Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.3f);
                manager.sendTaunt("&4&l¡SI MUERO, ARDERÁ TODO EL MUNDO CONMIGO!");
                manager.spawnHeavyMinions();
                break;

            case 18: // ONDA EXPANSIVA
                double shockRadius = phase == 3 ? 35.0 : (phase == 2 ? 28.0 : 22.0);
                double shockDmg = phase == 3 ? 10.0 : (phase == 2 ? 6.0 : 4.0);
                if (manager.getEventListener() != null) manager.getEventListener().launchShockwave(manager.getActiveDragon().getLocation(), shockRadius, shockDmg);
                manager.sendTaunt("&5&l¡SENTID MI FUERZA EXPANSIVA!");
                manager.playWorldSound(manager.getSpawnLocation(), Sound.ENTITY_WITHER_SPAWN, 1.5f, 0.6f);
                break;

            case 19: // LLUVIA ÁCIDA
                manager.sendTaunt("&2&l¡QUE LLUEVA MI IRA SOBRE TODOS VOSOTROS!");
                new BukkitRunnable() {
                    int pulses = 0;
                    @Override public void run() {
                        if (!manager.isEventActive() || pulses++ >= 5) { this.cancel(); return; }
                        for (Player p : w.getPlayers()) {
                            p.damage(phase == 3 ? 4.0 : 2.5);
                            p.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 40, 1));
                            w.spawnParticle(Particle.HAPPY_VILLAGER, p.getLocation().add(0,2,0), 8, 0.4,0.4,0.4, 0);
                        }
                        w.playSound(manager.getActiveDragon().getLocation(), Sound.WEATHER_RAIN, 1.5f, 0.4f);
                    }
                }.runTaskTimer(plugin, 0L, 20L);
                break;

            case 20: // MALDICIÓN OSCURIDAD
                for (Player p : nearby) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 140, 0));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 2));
                    w.spawnParticle(Particle.SOUL, p.getLocation().add(0,1,0), 20, 0.5,1,0.5, 0.05);
                }
                manager.broadcastTitle("&0&l☠ MALDICIÓN ☠", "&8La oscuridad os devora...", 10, 60, 20);
                manager.sendTaunt("&0&l¡LA OSCURIDAD ES MI REINO Y VOSOTROS SUS PRISIONEROS!");
                break;

            case 21: // INVERSIÓN DE GRAVEDAD
                for (Player p : nearby) p.setVelocity(new Vector((random.nextDouble() - 0.5) * 1.5, 2.5 + random.nextDouble(), (random.nextDouble() - 0.5) * 1.5));
                w.spawnParticle(Particle.PORTAL, manager.getActiveDragon().getLocation(), 500, 8,3,8, 0.4);
                manager.sendTaunt("&b¡Las leyes del mundo no aplican cuando estoy yo aquí!");
                break;

            case 22: // TORMENTA DE HIELO
                for (Player p : nearby) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 120, 3));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 100, 2));
                    p.setFreezeTicks(p.getMaxFreezeTicks());
                    w.spawnParticle(Particle.SNOWFLAKE, p.getLocation().add(0,1,0), 30, 0.5,1,0.5, 0.05);
                }
                manager.broadcastTitle("&b❄ CONGELADOS ❄", "&7El frío del dragón os paraliza", 10, 50, 20);
                manager.sendTaunt("&b¡QUE EL FRÍO ETERNO OS CONGELE LAS ENTRAÑAS!");
                break;

            case 23: // INVERSIÓN CONTROLES
                for (Player p : nearby) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 160, 1));
                    Vector chaos = new Vector((random.nextDouble() - 0.5) * 3, 0.8, (random.nextDouble() - 0.5) * 3);
                    p.setVelocity(chaos);
                }
                manager.sendTaunt("&d¡Ya no sabéis ni dónde estáis, pobres insectos!");
                break;

            case 24: // PURGA TOTAL
                for (Player p : nearby) {
                    p.removePotionEffect(PotionEffectType.REGENERATION);
                    p.removePotionEffect(PotionEffectType.STRENGTH);
                    p.removePotionEffect(PotionEffectType.SPEED);
                    p.removePotionEffect(PotionEffectType.FIRE_RESISTANCE);
                    p.removePotionEffect(PotionEffectType.ABSORPTION);
                    p.removePotionEffect(PotionEffectType.RESISTANCE);
                    p.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 100, 1));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 160, 2));
                }
                manager.broadcastTitle("&4&l✦ PURGA ✦", "&cEl dragón os despoja de todo poder", 10, 60, 20);
                manager.sendTaunt("&4&l¡NADA DE LO QUE TENÉIS OS PUEDE SALVAR DE MÍ!");
                break;
        }
    }
}