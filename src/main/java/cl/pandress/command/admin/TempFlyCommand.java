package cl.pandress.command.admin;

import cl.pandress.Etherium;
import cl.pandress.utils.ChatUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TempFlyCommand implements CommandExecutor, TabCompleter {

    private static class FlyData {
        String executor;
        String durationStr;
        LocalDateTime expiryDate;

        FlyData(String executor, String durationStr, LocalDateTime expiryDate) {
            this.executor = executor;
            this.durationStr = durationStr;
            this.expiryDate = expiryDate;
        }
    }

    private final Map<UUID, FlyData> flyLog = new HashMap<>();
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        
        // 1. Verificamos si tiene AL MENOS uno de los dos permisos para usar el comando base
        if (!sender.hasPermission("eth.admin.tempfly") && !sender.hasPermission("staff.tempfly.verify")) {
            sender.sendMessage(ChatUtils.colorize("&cNo tienes permiso para usar este comando."));
            return true;
        }

        // --- SUBCOMANDO VERIFY ---
        if (args.length >= 1 && args[0].equalsIgnoreCase("verify")) {
            if (!sender.hasPermission("staff.tempfly.verify")) {
                sender.sendMessage(ChatUtils.colorize("&cNo tienes permiso para verificar registros."));
                return true;
            }

            if (args.length < 2) {
                sender.sendMessage(ChatUtils.colorize("&cUso: /tempfly verify <jugador>"));
                return true;
            }

            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(ChatUtils.colorize("&cEl jugador no está en línea."));
                return true;
            }

            FlyData data = flyLog.get(target.getUniqueId());
            if (data == null) {
                sender.sendMessage(ChatUtils.colorize("&cEl jugador &e" + target.getName() + " &cno tiene registros de TempFly."));
            } else {
                boolean isExpired = LocalDateTime.now().isAfter(data.expiryDate);
                String status = isExpired ? "&c&lEXPIRADO" : "&a&lACTIVO";

                sender.sendMessage(ChatUtils.colorize("&8&m---------------------------------------"));
                sender.sendMessage(ChatUtils.colorize("&6&lVerificación de Fly: &e" + target.getName()));
                sender.sendMessage(ChatUtils.colorize("&fEstado: " + status));
                sender.sendMessage(ChatUtils.colorize("&fOtorgado por: &b" + data.executor));
                sender.sendMessage(ChatUtils.colorize("&fExpiración: &e" + data.expiryDate.format(formatter)));
                sender.sendMessage(ChatUtils.colorize("&8&m---------------------------------------"));
            }
            return true;
        }

        // --- SUBCOMANDO CLEAR ---
        if (args.length >= 1 && args[0].equalsIgnoreCase("clear")) {
            if (!sender.hasPermission("eth.admin.tempfly")) {
                sender.sendMessage(ChatUtils.colorize("&cNo tienes permiso para remover el fly."));
                return true;
            }

            if (args.length < 2) {
                sender.sendMessage(ChatUtils.colorize("&cUso: /tempfly clear <jugador>"));
                return true;
            }

            Player target = Bukkit.getPlayer(args[1]);
            if (target != null && flyLog.containsKey(target.getUniqueId())) {
                flyLog.remove(target.getUniqueId());
                Etherium.getInstance().getManagerHandler().getQuestManager().addTempFly(target, 0);
                target.setAllowFlight(false);
                sender.sendMessage(ChatUtils.colorize("&aFly removido a &e" + target.getName()));
            } else {
                sender.sendMessage(ChatUtils.colorize("&cNo hay registros activos para este jugador."));
            }
            return true;
        }

        // --- COMANDO PRINCIPAL (Dar Fly) ---
        // Solo llegamos aquí si no se usó 'verify' o 'clear'
        if (!sender.hasPermission("eth.admin.tempfly")) {
            sender.sendMessage(ChatUtils.colorize("&cNo tienes permiso para dar fly, solo puedes verificar."));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatUtils.colorize("&cUso: /tempfly <jugador> <tiempo>"));
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        long durationMillis = parseTime(args[1]);

        if (target != null && durationMillis > 0) {
            Etherium.getInstance().getManagerHandler().getQuestManager().addTempFly(target, durationMillis);
            LocalDateTime expiryDate = LocalDateTime.now().plusNanos(durationMillis * 1_000_000);
            flyLog.put(target.getUniqueId(), new FlyData(sender.getName(), args[1], expiryDate));
            sender.sendMessage(ChatUtils.colorize("&aFly dado a &e" + target.getName()));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            if (sender.hasPermission("staff.tempfly.verify")) options.add("verify");
            if (sender.hasPermission("eth.admin.tempfly")) {
                options.add("clear");
                for (Player p : Bukkit.getOnlinePlayers()) options.add(p.getName());
            }
            return StringUtil.copyPartialMatches(args[0], options, completions);
        }
        return completions;
    }

    private long parseTime(String timeStr) {
        timeStr = timeStr.toLowerCase();
        if (timeStr.endsWith("h")) return Long.parseLong(timeStr.replace("h", "")) * 3600000L;
        if (timeStr.endsWith("m")) return Long.parseLong(timeStr.replace("m", "")) * 60000L;
        if (timeStr.endsWith("s")) return Long.parseLong(timeStr.replace("s", "")) * 1000L;
        return -1;
    }
}