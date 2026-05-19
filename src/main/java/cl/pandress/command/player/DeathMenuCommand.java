package cl.pandress.command.player;

import cl.pandress.modules.headdeath.gui.CosmeticsGUI;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class DeathMenuCommand implements CommandExecutor {

    private final CosmeticsGUI gui;

    public DeathMenuCommand(CosmeticsGUI gui) {
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§cEste comando solo puede ser usado por jugadores.");
            return true;
        }

        gui.openMainMenu(p);
        return true;
    }
}