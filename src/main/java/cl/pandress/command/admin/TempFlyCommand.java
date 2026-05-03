package cl.pandress.command.admin;

import cl.pandress.Fresh;
import cl.pandress.utils.ChatUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class TempFlyCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("fresh.admin.tempfly")) {
            sender.sendMessage(ChatUtils.colorize("&cNo tienes permiso para dar fly temporal."));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatUtils.colorize("&cUso: /tempfly <jugador> <tiempo> (Ej: 2h, 30m, 1d)"));
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(ChatUtils.colorize("&cEl jugador no está en línea."));
            return true;
        }

        long durationMillis = parseTime(args[1]);
        if (durationMillis <= 0) {
            sender.sendMessage(ChatUtils.colorize("&cFormato de tiempo inválido. Usa 's' (segundos), 'm' (minutos), 'h' (horas), o 'd' (días)."));
            return true;
        }

        // Llamamos al método que añadiremos al QuestManager para agregar el tiempo
        Fresh.getInstance().getManagerHandler().getQuestManager().addTempFly(target, durationMillis);
        
        sender.sendMessage(ChatUtils.colorize("&aHas dado &e" + args[1] + " &ade Fly temporal a &e" + target.getName() + "&a."));
        return true;
    }

    /**
     * Convierte un string como "2h" o "30m" a milisegundos.
     */
    private long parseTime(String timeStr) {
        timeStr = timeStr.toLowerCase();
        long multiplier = 0;
        
        if (timeStr.endsWith("h")) multiplier = 3600000L;
        else if (timeStr.endsWith("m")) multiplier = 60000L;
        else if (timeStr.endsWith("d")) multiplier = 86400000L;
        else if (timeStr.endsWith("s")) multiplier = 1000L;
        else return -1;

        try {
            long value = Long.parseLong(timeStr.substring(0, timeStr.length() - 1));
            return value * multiplier;
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}