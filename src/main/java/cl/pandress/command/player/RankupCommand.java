package cl.pandress.command.player;

import cl.pandress.Fresh;
import cl.pandress.modules.rankup.menus.RankMenu;
import cl.pandress.modules.rankup.menus.RankTopMenu;
import cl.pandress.modules.rankup.RankManager;
import cl.pandress.utils.ChatUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class RankupCommand implements CommandExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        
        RankManager manager = Fresh.getInstance().getManagerHandler().getRankManager();

        // 1. Comando base: /rankup
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatUtils.colorize("&cSolo jugadores pueden abrir el menú."));
                return true;
            }
            RankMenu.open(player);
            return true;
        }

        // 2. Menú Top: /rankup top
        if (args[0].equalsIgnoreCase("top")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatUtils.colorize("&cSolo jugadores pueden ver el podio."));
                return true;
            }
            RankTopMenu.open(player);
            return true;
        }

        // 3. COMANDOS DE ADMINISTRADOR
        if (!sender.hasPermission("fresh.admin")) {
            sender.sendMessage(ChatUtils.colorize("&cNo tienes permiso para usar comandos de administración."));
            return true;
        }

        if (args[0].equalsIgnoreCase("resetall")) {
            manager.resetAllRanks();
            sender.sendMessage(ChatUtils.colorize("&a&lFRESH &8» &fHas reseteado los rangos y progreso de todos los jugadores."));
            return true;
        }

        if (args.length >= 2) {
            if (args[0].equalsIgnoreCase("reset")) {
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                manager.resetPlayerRank(target.getUniqueId());
                sender.sendMessage(ChatUtils.colorize("&a&lFRESH &8» &fHas reseteado el rango de &e" + target.getName()));
                return true;
            }

            if (args[0].equalsIgnoreCase("set")) {
                if (args.length < 3) {
                    sender.sendMessage(ChatUtils.colorize("&cUso correcto: /rankup set <jugador> <rango>"));
                    return true;
                }
                try {
                    int newRank = Integer.parseInt(args[2]);
                    OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                    
                    // setPlayerRank ya se encarga de limpiar el progreso de requisitos viejo automáticamente
                    manager.setPlayerRank(target.getUniqueId(), newRank);
                    sender.sendMessage(ChatUtils.colorize("&a&lFRESH &8» &fHas establecido el rango de &e" + target.getName() + " &fen &e" + newRank));
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatUtils.colorize("&cError: El rango debe ser un número válido."));
                }
                return true;
            }
        }

        sender.sendMessage(ChatUtils.colorize("&eUso: /rankup [top|reset|set|resetall]"));
        return true;
    }
}