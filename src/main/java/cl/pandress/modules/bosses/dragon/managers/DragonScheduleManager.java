package cl.pandress.modules.bosses.dragon.managers;

import cl.pandress.Etherium;
import cl.pandress.modules.bosses.dragon.DragonEventManager;
import org.bukkit.Location;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

public class DragonScheduleManager {

    private final Etherium plugin;
    private final DragonEventManager manager;

    private Location spawnLocation;
    private long intervalSeconds = 0;
    private long durationSeconds = 0;

    private long timeUntilNext = 0;
    private long timeLeftInEvent = 0;

    private boolean wasActive = false;
    private BukkitTask task;

    public DragonScheduleManager(Etherium plugin, DragonEventManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    public void setupSchedule(Location loc, long intervalSecs, long durationSecs) {
        this.spawnLocation = loc;
        this.intervalSeconds = intervalSecs;
        this.durationSeconds = durationSecs;
        this.timeUntilNext = intervalSecs; 
        
        startTimer();
    }

    // Nuevo método para que Etherium pueda restaurar el tiempo tras un reinicio
    public void setTimeUntilNext(long timeUntilNext) {
        this.timeUntilNext = timeUntilNext;
    }

    public void forceStart(Location loc) {
        if (manager.isEventActive()) return;
        this.spawnLocation = loc;
        manager.startEvent(loc);
        this.timeLeftInEvent = durationSeconds > 0 ? durationSeconds : 1800; 
        this.wasActive = true;
        if (task == null) startTimer();
    }

    public void forceStop() {
        if (!manager.isEventActive()) return;
        manager.cleanup();
        this.timeLeftInEvent = 0;
        this.timeUntilNext = intervalSeconds > 0 ? intervalSeconds : 18000;
        this.wasActive = false;
        
        // Guardar el nuevo tiempo absoluto en la config para que no se pierda al reiniciar
        long targetTimestamp = System.currentTimeMillis() + (this.timeUntilNext * 1000);
        plugin.getConfig().set("dragon-event.target-timestamp", targetTimestamp);
        plugin.saveConfig();
    }

    private void startTimer() {
        if (task != null) task.cancel();
        
        task = new BukkitRunnable() {
            @Override
            public void run() {
                if (spawnLocation == null) return;
                
                boolean currentlyActive = manager.isEventActive();

                // Si el evento acaba de terminar (el boss murió o fallaron)
                if (wasActive && !currentlyActive) {
                    timeUntilNext = intervalSeconds;
                    
                    // Guardar la hora exacta en la que toca el próximo (Crash-proof)
                    long targetTimestamp = System.currentTimeMillis() + (intervalSeconds * 1000);
                    plugin.getConfig().set("dragon-event.target-timestamp", targetTimestamp);
                    plugin.saveConfig();
                }
                
                wasActive = currentlyActive;

                if (!currentlyActive) {
                    if (timeUntilNext > 0) {
                        timeUntilNext--;
                    } else {
                        manager.startEvent(spawnLocation);
                        timeLeftInEvent = durationSeconds;
                        wasActive = true; 
                    }
                } else {
                    if (timeLeftInEvent > 0) {
                        timeLeftInEvent--;
                    } else {
                        manager.endEventFailure();
                        // El guardado del próximo tiempo se hará en el siguiente tick automáticamente
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    public String getFormattedTimeUntilNext() {
        if (manager.isEventActive()) return "&a¡Evento en curso!";
        if (spawnLocation == null) return "&cNo configurado";
        return formatSeconds(timeUntilNext);
    }

    public String getFormattedTimeLeft() {
        if (!manager.isEventActive()) return "&cEn espera...";
        return formatSeconds(timeLeftInEvent);
    }

    private String formatSeconds(long totalSecs) {
        long d = totalSecs / 86400;
        long h = (totalSecs % 86400) / 3600;
        long m = ((totalSecs % 86400) % 3600) / 60;
        long s = totalSecs % 60;
        
        StringBuilder sb = new StringBuilder();
        if (d > 0) sb.append(d).append("d ");
        if (h > 0) sb.append(h).append("h ");
        if (m > 0) sb.append(m).append("m ");
        sb.append(s).append("s");
        return sb.toString().trim();
    }
}