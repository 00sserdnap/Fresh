package cl.pandress.command.admin;

import cl.pandress.Etherium;
import cl.pandress.modules.essentials.SpawnManager;
import cl.pandress.utils.ChatUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

public class SetSpawnCommand implements CommandExecutor {
    
    private final SpawnManager spawnManager;

    public SetSpawnCommand(SpawnManager spawnManager) {
        this.spawnManager = spawnManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        FileConfiguration config = Etherium.getInstance().getConfig();

        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatUtils.colorize(config.getString("spawn.messages.not-player", "&cSolo jugadores.")));
            return true;
        }

        Player player = (Player) sender;
        
        if (!player.hasPermission("etherium.admin.setspawn")) {
            player.sendMessage(ChatUtils.colorize(config.getString("spawn.messages.no-permission", "&cSin permisos.")));
            return true;
        }

        spawnManager.setSpawn(player.getLocation());
        player.sendMessage(ChatUtils.colorize(config.getString("spawn.messages.set-success", "&aSpawn configurado.")));
        return true;
    }
}