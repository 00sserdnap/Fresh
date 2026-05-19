package cl.pandress.command.admin;

import cl.pandress.modules.hopperlinks.HopperLinkManager;
import cl.pandress.utils.ChatUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class HopperLinkCommand implements CommandExecutor, TabCompleter {

    private final HopperLinkManager manager;

    public HopperLinkCommand(HopperLinkManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {

        // Permiso de administrador para usar el comando
        if (!sender.hasPermission("eth.hopperlinks.admin")) {
            String prefix = manager.getMessages().getString("prefix", "&8[&c!&8] ");
            sender.sendMessage(ChatUtils.colorize(prefix + manager.getMessages().getString("errors.no-permission", "&cNo tienes permisos.")));
            return true;
        }

        // Comando: /ethhopperlinks give <jugador>
        if (args.length >= 2 && args[0].equalsIgnoreCase("give")) {
            Player target = Bukkit.getPlayer(args[1]);
            
            if (target == null) {
                sender.sendMessage(ChatUtils.colorize("&c&lⓘ &7El jugador &c" + args[1] + " &7no está conectado o no existe."));
                return true;
            }

            // Obtiene el ítem de la varita generado desde la configuración
            ItemStack wand = manager.getWandItem();
            
            // Si el inventario está lleno, el ítem cae al suelo
            HashMap<Integer, ItemStack> leftover = target.getInventory().addItem(wand);
            if (!leftover.isEmpty()) {
                for (ItemStack drop : leftover.values()) {
                    target.getWorld().dropItemNaturally(target.getLocation(), drop);
                }
                sender.sendMessage(ChatUtils.colorize("&e&lⓘ &7El inventario de &e" + target.getName() + " &7estaba lleno. La varita cayó al suelo."));
            } else {
                sender.sendMessage(ChatUtils.colorize("&a&lⓘ &7Has entregado la &6Varita de Conexión &7a &a" + target.getName() + "&7."));
            }

            target.sendMessage(ChatUtils.colorize("&a&lⓘ &7¡Has recibido una &6Varita de Conexión&7!"));
            return true;
        }

        // Si escriben mal el comando
        sender.sendMessage(ChatUtils.colorize("&c&lⓘ &7Uso correcto: &f/ethhopperlinks give <jugador>"));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (sender.hasPermission("eth.hopperlinks.admin")) {
            if (args.length == 1) {
                completions.add("give");
            } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
                // Autocompleta con los nombres de los jugadores online
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                        completions.add(p.getName());
                    }
                }
            }
        }
        return completions;
    }
}