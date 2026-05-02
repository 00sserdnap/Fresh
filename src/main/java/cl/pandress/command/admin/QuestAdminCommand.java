package cl.pandress.command.admin;

import cl.pandress.Fresh;
import cl.pandress.modules.quests.QuestManager;
import cl.pandress.utils.ChatUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class QuestAdminCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return true;
        
        if (!player.hasPermission("fresh.admin")) {
            player.sendMessage(ChatUtils.colorize("&cNo tienes permiso."));
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("test")) {
            QuestManager manager = Fresh.getInstance().getManagerHandler().getQuestManager();
            // Nivel 11 es el estado para reclamar el bonus
            manager.setPlayerDailyLevel(player.getUniqueId(), 11);
            manager.saveUserData(player.getUniqueId());
            player.sendMessage(ChatUtils.colorize("&aNivel de misión ajustado a 11. Abre el menú para reclamar el bonus."));
            return true;
        }

        player.sendMessage(ChatUtils.colorize("&cUso: /misionesadmin test"));
        return true;
    }
}