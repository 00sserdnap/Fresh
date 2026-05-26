//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package cl.pandress.command.player;

import cl.pandress.Etherium;
import cl.pandress.modules.rankup.menus.RankMenu;
import cl.pandress.utils.ChatUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class RankupCommand implements CommandExecutor {
    public RankupCommand() {
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Solo jugadores pueden usar este comando.");
            return true;
        } else {
            boolean isEnabled = Etherium.getInstance().getManagerHandler().getRankManager().getConfig().getBoolean("settings.enabled", true);
            if (!isEnabled) {
                player.sendMessage(ChatUtils.colorize("&cEl sistema de rangos está desactivado actualmente."));
                return true;
            } else {
                RankMenu.open(player);
                return true;
            }
        }
    }
}
