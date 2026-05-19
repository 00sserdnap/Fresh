package cl.pandress.command.admin;

import cl.pandress.modules.areatools.menus.AdminMenu;
import cl.pandress.modules.areatools.AreaToolManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class AreaToolCommand implements CommandExecutor, TabCompleter {

    private final AreaToolManager manager;

    public AreaToolCommand(AreaToolManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!sender.isOp()) {
            sender.sendMessage("§cNo tienes permiso para usar este comando.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        if (subCommand.equals("menu")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("§cSolo jugadores pueden abrir el menú.");
                return true;
            }
            AdminMenu.open((Player) sender, manager);
            return true;
        }

        if (subCommand.equals("give")) {
            // Uso: /ethtools give <user> <tool/battery> <id> <amount> <permanent/temporal>
            if (args.length < 4) {
                sender.sendMessage("§eUso: §f/ethtools give <usuario> <tool/battery> <id> <cantidad> [permanente/temporal]");
                return true;
            }

            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage("§cJugador no encontrado.");
                return true;
            }

            String category = args[2].toLowerCase();
            String id = args[3].toLowerCase();
            
            int amount = 1;
            if (args.length >= 5) {
                try {
                    amount = Integer.parseInt(args[4]);
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cLa cantidad debe ser un número válido.");
                    return true;
                }
            }

            if (category.equals("battery")) {
                ItemStack battery = manager.createBattery(id);
                if (battery == null) {
                    sender.sendMessage("§cLa batería '" + id + "' no existe en config.yml.");
                    return true;
                }
                battery.setAmount(amount);
                target.getInventory().addItem(battery);
                sender.sendMessage("§aEntregado §f" + amount + "x §r" + battery.getItemMeta().getDisplayName() + " §aa §e" + target.getName());
                return true;

            } else if (category.equals("tool")) {
                boolean isTemp = false;
                if (args.length >= 6 && args[5].equalsIgnoreCase("temporal")) {
                    isTemp = true;
                }

                ItemStack tool = manager.createTool(id, isTemp);
                if (tool == null) {
                    sender.sendMessage("§cLa herramienta '" + id + "' no existe en config.yml.");
                    return true;
                }
                tool.setAmount(amount);
                target.getInventory().addItem(tool);
                sender.sendMessage("§aEntregado §f" + amount + "x §r" + tool.getItemMeta().getDisplayName() + " §aa §e" + target.getName() + " §8(" + (isTemp ? "Temporal" : "Permanente") + ")");
                return true;
            } else {
                sender.sendMessage("§cCategoría inválida. Usa 'tool' o 'battery'.");
                return true;
            }
        }

        sendHelp(sender);
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§8§m----------------------------------------");
        sender.sendMessage("§c§lEtherium Tools Admin");
        sender.sendMessage("§e/ethtools menu §7- Abre el menú de administración.");
        sender.sendMessage("§e/ethtools give <jugador> battery <id> <cantidad> §7- Da baterías.");
        sender.sendMessage("§e/ethtools give <jugador> tool <id> <cantidad> <permanente/temporal> §7- Da herramientas.");
        sender.sendMessage("§8§m----------------------------------------");
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        if (!sender.isOp()) return new ArrayList<>();

        if (args.length == 1) {
            return Arrays.asList("give", "menu").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args[0].equalsIgnoreCase("give")) {
            if (args.length == 2) {
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
            if (args.length == 3) {
                return Arrays.asList("tool", "battery").stream()
                        .filter(s -> s.startsWith(args[2].toLowerCase()))
                        .collect(Collectors.toList());
            }
            if (args.length == 4) {
                if (args[2].equalsIgnoreCase("battery")) {
                    return manager.getAvailableBatteries().stream()
                            .filter(id -> id.startsWith(args[3].toLowerCase()))
                            .collect(Collectors.toList());
                } else if (args[2].equalsIgnoreCase("tool")) {
                    return manager.getAvailableTools().stream()
                            .filter(id -> id.startsWith(args[3].toLowerCase()))
                            .collect(Collectors.toList());
                }
            }
            if (args.length == 5) {
                return Arrays.asList("1", "16", "32", "64");
            }
            if (args.length == 6 && args[2].equalsIgnoreCase("tool")) {
                return Arrays.asList("permanente", "temporal").stream()
                        .filter(s -> s.startsWith(args[5].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        return new ArrayList<>();
    }
}