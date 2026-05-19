package cl.pandress.command.player;

import cl.pandress.Etherium;
import cl.pandress.modules.quests.menus.QuestMenu;
import cl.pandress.utils.ChatUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class QuestCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Solo jugadores pueden usar este comando.");
            return true;
        }

        Player player = (Player) sender;
        
        // --- VERIFICACIÓN DE MÓDULO APAGADO ---
        boolean isEnabled = Etherium.getInstance().getManagerHandler().getQuestManager().getConfig().getBoolean("settings.enabled", true);
        
        if (!isEnabled) {
            player.sendMessage(ChatUtils.colorize("&cEl sistema de misiones diarias está desactivado actualmente."));
            return true;
        }

        QuestMenu.open(player);
        return true;
    }
}