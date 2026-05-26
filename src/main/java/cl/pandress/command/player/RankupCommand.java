package cl.pandress.command.player;

import cl.pandress.Etherium;
import cl.pandress.modules.rankup.menus.RankMenu;
import cl.pandress.utils.ChatUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class RankupCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Solo jugadores pueden usar este comando.");
            return true;
        }

        boolean isEnabled = Etherium.getInstance()
                .getManagerHandler()
                .getRankManager()
                .getConfig()
                .getBoolean("settings.enabled", true);

        if (!isEnabled) {
            player.sendMessage(ChatUtils.colorize("&cEl sistema de rangos está desactivado actualmente."));
            return true;
        }

        RankMenu.open(player);
        return true;
    }
}