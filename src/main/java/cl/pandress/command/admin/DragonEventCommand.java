package cl.pandress.command.admin;

import cl.pandress.Etherium;
import cl.pandress.modules.bosses.dragon.DragonEventManager;
import cl.pandress.modules.bosses.dragon.managers.DragonScheduleManager;
import cl.pandress.utils.ChatUtils;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class DragonEventCommand implements CommandExecutor, TabCompleter {

    private final DragonEventManager manager;
    private final DragonScheduleManager scheduleManager;
    private final Etherium plugin;

    public DragonEventCommand(DragonEventManager manager, DragonScheduleManager scheduleManager, Etherium plugin) {
        this.manager = manager;
        this.scheduleManager = scheduleManager;
        this.plugin = plugin;
    }

    public long parseTimeToSeconds(String timeStr) {
        long totalSeconds = 0;
        StringBuilder num = new StringBuilder();
        for (char c : timeStr.toCharArray()) {
            if (Character.isDigit(c)) {
                num.append(c);
            } else {
                if (num.length() == 0) continue;
                long val = Long.parseLong(num.toString());
                switch (Character.toLowerCase(c)) {
                    case 's': totalSeconds += val; break;
                    case 'm': totalSeconds += val * 60; break;
                    case 'h': totalSeconds += val * 3600; break;
                    case 'd': totalSeconds += val * 86400; break;
                }
                num.setLength(0);
            }
        }
        return totalSeconds;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("etherium.admin.dragon")) {
            sender.sendMessage(ChatUtils.colorize("&cNo tienes permisos para usar este comando."));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(ChatUtils.colorize("&eUso: /ethdragon <start|stop|set>"));
            return true;
        }

        if (args[0].equalsIgnoreCase("start")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatUtils.colorize("&cSolo un jugador puede iniciar el evento para usar su ubicación."));
                return true;
            }

            if (manager.isEventActive()) {
                sender.sendMessage(ChatUtils.colorize("&cYa hay un evento en curso."));
                return true;
            }

            Player p = (Player) sender;
            scheduleManager.forceStart(p.getLocation());
            sender.sendMessage(ChatUtils.colorize("&aEvento de Dragón forzado en tu posición."));
            return true;
        }

        if (args[0].equalsIgnoreCase("stop")) {
            if (!manager.isEventActive()) {
                sender.sendMessage(ChatUtils.colorize("&cNo hay ningún evento activo."));
                return true;
            }

            scheduleManager.forceStop();
            sender.sendMessage(ChatUtils.colorize("&aEvento de dragón detenido forzosamente."));
            return true;
        }

        if (args[0].equalsIgnoreCase("set")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatUtils.colorize("&cSolo un jugador puede setear el evento para usar su ubicación."));
                return true;
            }

            if (args.length < 3) {
                sender.sendMessage(ChatUtils.colorize("&eUso: /ethdragon set <intervalo> <duracion> (Ej: 5h 30m)"));
                return true;
            }

            Player player = (Player) sender;
            String intervalStr = args[1];
            String durationStr = args[2];
            
            long intervalSecs = parseTimeToSeconds(intervalStr);
            long durationSecs = parseTimeToSeconds(durationStr);
            
            if (intervalSecs == 0 || durationSecs == 0) {
                player.sendMessage(ChatUtils.colorize("&cFormato inválido. Usa s, m, h, d (Ej: 5h, 30m)"));
                return true;
            }
            
            Location loc = player.getLocation();
            scheduleManager.setupSchedule(loc, intervalSecs, durationSecs);
            
            // Calculamos el tiempo real absoluto (crash-proof)
            long targetTimestamp = System.currentTimeMillis() + (intervalSecs * 1000);
            
            plugin.getConfig().set("dragon-event.world", loc.getWorld().getName());
            plugin.getConfig().set("dragon-event.x", loc.getX());
            plugin.getConfig().set("dragon-event.y", loc.getY());
            plugin.getConfig().set("dragon-event.z", loc.getZ());
            plugin.getConfig().set("dragon-event.yaw", loc.getYaw());
            plugin.getConfig().set("dragon-event.pitch", loc.getPitch());
            plugin.getConfig().set("dragon-event.interval", intervalSecs);
            plugin.getConfig().set("dragon-event.duration", durationSecs);
            plugin.getConfig().set("dragon-event.target-timestamp", targetTimestamp);
            plugin.saveConfig();

            player.sendMessage(ChatUtils.colorize("&8[&5&l☠ Dragón &8] &aTemporizador configurado y en marcha en tu posición actual."));
            player.sendMessage(ChatUtils.colorize("&8[&5&l☠ Dragón &8] &7Aparición cada: &e" + intervalStr + " &8| &7Duración máxima: &e" + durationStr));
            return true;
        }

        sender.sendMessage(ChatUtils.colorize("&eUso: /ethdragon <start|stop|set>"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (sender.hasPermission("etherium.admin.dragon")) {
            if (args.length == 1) {
                completions.add("start");
                completions.add("stop");
                completions.add("set");
            } else if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
                completions.add("5h");
                completions.add("1d");
            } else if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
                completions.add("30m");
                completions.add("1h");
            }
        }
        return completions;
    }
}