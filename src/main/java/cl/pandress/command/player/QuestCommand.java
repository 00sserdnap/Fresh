package cl.pandress.command.player;

import cl.pandress.menus.QuestMenu;
import cl.pandress.utils.ChatUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class QuestCommand implements CommandExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        
        // Verificamos que sea un jugador y no la consola
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatUtils.colorize("&cEste comando solo puede ser usado por jugadores dentro del servidor."));
            return true;
        }

        // Abrimos el menú de Misiones Diarias
        QuestMenu.open(player);
        return true;
    }
}