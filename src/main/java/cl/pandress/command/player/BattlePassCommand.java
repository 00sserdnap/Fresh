package cl.pandress.command.player;

import cl.pandress.Etherium;
import cl.pandress.modules.battlepass.BattlePassManager;
import cl.pandress.modules.battlepass.menus.PassMainMenu;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class BattlePassCommand implements CommandExecutor {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Solo los jugadores pueden usar este comando.");
            return true;
        }

        BattlePassManager bp = Etherium.getInstance().getManagerHandler().getBattlePassManager();

        if (!bp.getConfig().getBoolean("settings.enabled", true)) {
            player.sendMessage("§cEl Pase de Batalla se encuentra desactivado.");
            return true;
        }

        if (args.length > 0) {
            if (args[0].equalsIgnoreCase("bossbar")) {
                // Activar/Desactivar el BossBar
                bp.toggleBossBar(player);
                return true;
            }
        }

        // Si solo escribe /bp, abre el menú principal
        PassMainMenu.open(player);
        return true;
    }
}