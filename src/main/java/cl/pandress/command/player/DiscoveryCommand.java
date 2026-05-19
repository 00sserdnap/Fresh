package cl.pandress.command.player;

import cl.pandress.modules.discoveries.DiscoveryManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class DiscoveryCommand implements CommandExecutor {

    private final DiscoveryManager manager;

    public DiscoveryCommand(DiscoveryManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Este comando solo puede ser ejecutado por un jugador.");
            return true;
        }

        Player player = (Player) sender;
        manager.openMainMenu(player);
        return true;
    }
}