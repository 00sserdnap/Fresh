package cl.pandress.command.admin;

import cl.pandress.Fresh;
import cl.pandress.utils.ChatUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class FreshCommand implements CommandExecutor, TabCompleter {

    private final Fresh plugin = Fresh.getInstance();

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        
        // Comandos Administrativos base (/fresh ...)
        if (label.equalsIgnoreCase("fresh")) {
            if (!sender.hasPermission("fresh.admin")) {
                sender.sendMessage(ChatUtils.colorize("&cNo tienes permiso para usar comandos de administración."));
                return true;
            }

            if (args.length >= 2 && args[0].equalsIgnoreCase("reload")) {
                
                // Recargar misiones
                if (args[1].equalsIgnoreCase("quests")) {
                    plugin.getManagerHandler().getQuestManager().reloadConfig();
                    sender.sendMessage(ChatUtils.colorize("&b&lFRESH &8» &aMódulo de misiones recargado."));
                    return true;
                }
                
                // Recargar rangos
                if (args[1].equalsIgnoreCase("rankup") || args[1].equalsIgnoreCase("ranks")) {
                    plugin.getManagerHandler().getRankManager().reloadConfig();
                    sender.sendMessage(ChatUtils.colorize("&b&lFRESH &8» &aConfiguración de rangos recargada correctamente."));
                    return true;
                }
            }

            sender.sendMessage(ChatUtils.colorize("&eUso correcto: &f/fresh reload <quests|ranks>"));
            return true;
        }

        return false;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        if (sender.hasPermission("fresh.admin")) {
            if (args.length == 1) {
                completions.add("reload");
            } else if (args.length == 2 && args[0].equalsIgnoreCase("reload")) {
                completions.add("quests");
                completions.add("ranks");
            }
        }
        return completions;
    }
}