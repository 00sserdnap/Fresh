package cl.pandress.command.player;

import cl.pandress.Fresh;
import cl.pandress.modules.battlepass.menus.PassMainMenu;
import cl.pandress.utils.ChatUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class BattlePassCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            // Lee el mensaje de error directamente desde messages.yml
            String msg = Fresh.getInstance().getManagerHandler().getBattlePassManager().getMessages().getString("only-players", "&cSolo los jugadores pueden abrir el Pase de Batalla.");
            sender.sendMessage(ChatUtils.colorize(msg));
            return true;
        }

        PassMainMenu.open(player);
        return true;
    }
}