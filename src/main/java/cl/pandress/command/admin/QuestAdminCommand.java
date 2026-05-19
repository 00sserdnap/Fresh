package cl.pandress.command.admin;

import cl.pandress.Etherium;
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
        
        if (!player.hasPermission("eth.admin")) {
            player.sendMessage(ChatUtils.colorize("&cNo tienes permiso."));
            return true;
        }

        if (args.length > 0) {
            QuestManager manager = Etherium.getInstance().getManagerHandler().getQuestManager();
            
            if (args[0].equalsIgnoreCase("test")) {
                manager.setPlayerDailyLevel(player.getUniqueId(), 11);
                manager.saveUserData(player.getUniqueId());
                player.sendMessage(ChatUtils.colorize("&aNivel de misión ajustado a 11. Abre el menú para reclamar el bonus."));
                return true;
            }
            
            // Subcomando: /misionesadmin reset (Para desatascar el día de hoy)
            if (args[0].equalsIgnoreCase("reset")) {
                manager.forceDailyReset();
                player.sendMessage(ChatUtils.colorize("&a¡Has forzado la rotación de las misiones diarias manualmente!"));
                return true;
            }
        }

        player.sendMessage(ChatUtils.colorize("&cUso: /misionesadmin <test|reset>"));
        return true;
    }
}