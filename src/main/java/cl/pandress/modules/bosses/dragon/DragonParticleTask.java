package cl.pandress.modules.bosses.dragon;

import cl.pandress.Etherium;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Random;

public class DragonParticleTask extends BukkitRunnable {

    private final DragonEventManager manager;
    private final Random random = new Random();
    private int tick = 0;
    private double orbitAngle = 0;

    public DragonParticleTask(DragonEventManager manager, Etherium plugin) {
        this.manager = manager;
    }

    @Override
    public void run() {
        if (!manager.isEventActive() || manager.getActiveDragon() == null || manager.getActiveDragon().isDead()) {
            this.cancel();
            return;
        }

        tick++;
        orbitAngle += 0.15;
        Location dragonLoc = manager.getActiveDragon().getLocation();
        World w = dragonLoc.getWorld();
        if (w == null) return;

        List<Player> nearby = manager.getNearbyPlayers(100);
        int currentPhase = manager.getCurrentPhase();
        Location spawnLocation = manager.getSpawnLocation();

        // 1. ANILLO DE PARTÍCULAS GIRATORIO (Reemplazo DUST por WITCH)
        if (tick % 2 == 0) {
            for (int i = 0; i < 12; i++) {
                double a = orbitAngle + (i * Math.PI / 6);
                double r = 8;
                Location ringPoint = dragonLoc.clone().add(Math.cos(a) * r, 0, Math.sin(a) * r);
                w.spawnParticle(Particle.WITCH, ringPoint, 1, 0, 0, 0, 0);
            }
        }

        // 2. ESPIRAL DESCENDENTE DE ALIENTO
        // NOTA: Particle.DRAGON_BREATH requiere Float extra en Paper 1.21.11 y causa crash.
        //       Se usa PORTAL + SOUL_FIRE_FLAME como alternativa segura de efecto similar.
        if (tick % 2 == 0) {
            double sa = orbitAngle * 2;
            Location spiralPoint = dragonLoc.clone().add(Math.cos(sa) * 5, -2 + Math.sin(sa) * 3, Math.sin(sa) * 5);
            w.spawnParticle(Particle.PORTAL, spiralPoint, 3, 0.1, 0.1, 0.1, 0.05);
            w.spawnParticle(Particle.SOUL_FIRE_FLAME, spiralPoint, 1, 0.1, 0.1, 0.1, 0.01);
        }

        // 3. PARTÍCULAS DE ALMA FLOTANDO
        if (tick % 3 == 0) {
            double bx = spawnLocation.getX() + (random.nextDouble() - 0.5) * 20;
            double bz = spawnLocation.getZ() + (random.nextDouble() - 0.5) * 20;
            Location groundPoint = new Location(w, bx, spawnLocation.getY(), bz);
            w.spawnParticle(Particle.SOUL, groundPoint, 4, 0.5, 1, 0.5, 0.02);
            w.spawnParticle(Particle.SOUL_FIRE_FLAME, groundPoint, 3, 0.3, 0.5, 0.3, 0.02);
        }

        // 4. CHISPAS ELÉCTRICAS
        if (tick % 2 == 0) w.spawnParticle(Particle.ELECTRIC_SPARK, dragonLoc, 6, 3, 1, 3, 0.1);

        // 5. TOTEM DE VICTORIA
        if (tick % 4 == 0) w.spawnParticle(Particle.TOTEM_OF_UNDYING, dragonLoc.clone().add(0, 3, 0), 3, 2, 2, 2, 0.05);

        // 6. NUBES DE CENIZA
        if (tick % 4 == 0) w.spawnParticle(Particle.ASH, dragonLoc, 15, 8, 3, 8, 0.05);

        // 7. ANILLO DE FUEGO
        if (tick % 5 == 0) {
            double flameAngle = orbitAngle * 0.5;
            for (int i = 0; i < 16; i++) {
                double a = flameAngle + (i * Math.PI / 8);
                Location firePoint = spawnLocation.clone().add(Math.cos(a) * 30, 0.5, Math.sin(a) * 30);
                w.spawnParticle(Particle.FLAME, firePoint, 2, 0.2, 0.5, 0.2, 0.02);
                w.spawnParticle(Particle.LAVA, firePoint, 1, 0, 0, 0, 0);
            }
        }

        // 8. POLVO DE ENCANTAMIENTO
        if (tick % 3 == 0 && currentPhase >= 2) w.spawnParticle(Particle.ENCHANT, dragonLoc, 20, 5, 2, 5, 0.5);

        // 9. VÓRTICE DE PORTALES
        int portalCount = currentPhase == 3 ? 40 : (currentPhase == 2 ? 25 : 12);
        if (tick % 2 == 0) w.spawnParticle(Particle.PORTAL, dragonLoc, portalCount, 3, 1, 3, 0.5);

        // 10. EXPLOSIÓN DE MAGIA (Línea 57 - Corregido a Particle.CRIT para evitar el error del Float)
        if (tick % 10 == 0) {
            for (Player p : nearby) w.spawnParticle(Particle.CRIT, p.getLocation(), 5, 0.3, 0.5, 0.3, 0.1);
        }

        // 11. CORONA DE END_ROD
        if (tick % 4 == 0) {
            double crownAngle = orbitAngle * 1.5;
            for (int i = 0; i < 8; i++) {
                double a = crownAngle + (i * Math.PI / 4);
                Location crown = dragonLoc.clone().add(Math.cos(a) * 4, 4, Math.sin(a) * 4);
                w.spawnParticle(Particle.END_ROD, crown, 1, 0, 0, 0, 0.01);
            }
        }

        // 12. LLUVIA DE ESTRELLAS
        if (tick % 6 == 0 && currentPhase == 1) {
            for (int i = 0; i < 5; i++) {
                Location starLoc = spawnLocation.clone().add((random.nextDouble() - 0.5) * 30, 20, (random.nextDouble() - 0.5) * 30);
                w.spawnParticle(Particle.END_ROD, starLoc, 1, 0, 0, 0, 0.05);
            }
        }

        // 13. EFECTO DRIP
        if (tick % 5 == 0) {
            Location dropLoc = dragonLoc.clone().add((random.nextDouble() - 0.5) * 10, -1, (random.nextDouble() - 0.5) * 10);
            w.spawnParticle(Particle.DRIPPING_LAVA, dropLoc, 3, 1, 0.1, 1, 0);
            w.spawnParticle(Particle.FALLING_LAVA, dropLoc, 3, 1, 0.1, 1, 0);
        }

        // 14. CHISPAS DE MAGIA EN MINIONS
        if (tick % 8 == 0) {
            for (LivingEntity minion : manager.getActiveMinions()) {
                if (!minion.isDead()) w.spawnParticle(Particle.WITCH, minion.getLocation().add(0, 1, 0), 5, 0.5, 0.5, 0.5, 0.05);
            }
        }

        // 15. FASE 2+: NUBE TÓXICA
        if (tick % 3 == 0 && currentPhase >= 2) w.spawnParticle(Particle.HAPPY_VILLAGER, dragonLoc, 20, 6, 3, 6, 0.1);

        // 16. FASE 2+: ESPIRAL MORADA (WITCH)
        if (tick % 4 == 0 && currentPhase >= 2) {
            double skyAngle = orbitAngle * 3;
            for (int i = 0; i < 5; i++) {
                Location skySpiral = dragonLoc.clone().add(Math.cos(skyAngle + i) * (3 + i), i * 2, Math.sin(skyAngle + i) * (3 + i));
                w.spawnParticle(Particle.WITCH, skySpiral, 2, 0, 0, 0, 0);
            }
        }

        // 17. FASE 3: INDICADORES DE DAÑO
        if (currentPhase == 3 && tick % 2 == 0) w.spawnParticle(Particle.DAMAGE_INDICATOR, dragonLoc, 5, 4, 2, 4, 0.1);

        // 18. FASE 3: RAYOS DE PARTÍCULAS (Corregido a Particle.FIREWORK para evitar el error del Float)
        if (tick % 7 == 0 && currentPhase == 3) {
            for (Player p : nearby) {
                Vector dir = p.getLocation().toVector().subtract(dragonLoc.toVector()).normalize();
                for (int step = 1; step <= 10; step++) {
                    Location beam = dragonLoc.clone().add(dir.clone().multiply(step * 1.5));
                    w.spawnParticle(Particle.FIREWORK, beam, 1, 0, 0, 0, 0);
                }
            }
        }

        // 20. EFECTO EXPLOSIÓN TOTEM
        if (tick % 20 == 0) w.spawnParticle(Particle.TOTEM_OF_UNDYING, dragonLoc, 30, 3, 3, 3, 0.2);

        // 21. PARTÍCULAS SONIC BOOM + SWEEP
        // SONIC_BOOM se llama con count=1 y spreads=0 que es la firma segura en 1.21
        if (tick % 15 == 0) {
            w.spawnParticle(Particle.SONIC_BOOM, dragonLoc, 1, 0, 0, 0, 0);
            w.spawnParticle(Particle.SWEEP_ATTACK, dragonLoc, 3, 3, 1, 3, 0.1);
        }

        // 22. EFECTO DE MAGIA EN EL SUELO
        if (tick % 8 == 0) {
            Location groundGlow = spawnLocation.clone().add((random.nextDouble() - 0.5) * 40, 0, (random.nextDouble() - 0.5) * 40);
            w.spawnParticle(Particle.ENCHANT, groundGlow, 10, 1, 2, 1, 0.3);
        }

        // 23. SPIRALES DE NEÓN
        if (tick % 6 == 0) {
            int[] angles = {0, 90, 180, 270};
            for (int i = 0; i < 4; i++) {
                double rad = Math.toRadians(angles[i] + (tick * 3));
                Location neonPoint = spawnLocation.clone().add(Math.cos(rad) * 25, 2 + Math.sin(tick * 0.1 + i) * 3, Math.sin(rad) * 25);
                w.spawnParticle(i % 2 == 0 ? Particle.WITCH : Particle.PORTAL, neonPoint, 3, 0, 0, 0, 0);
            }
        }

        // 24. LLUVIA DE POLVO DORADO
        if (tick % 2 == 0 && currentPhase == 3) {
            Location goldLoc = spawnLocation.clone().add((random.nextDouble() - 0.5) * 50, 15, (random.nextDouble() - 0.5) * 50);
            w.spawnParticle(Particle.TOTEM_OF_UNDYING, goldLoc, 2, 0.5, 0.5, 0.5, 0.01);
        }
    }
}