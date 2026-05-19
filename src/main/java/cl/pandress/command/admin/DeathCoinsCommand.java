package cl.pandress.command.admin;

import cl.pandress.modules.headdeath.HeadDeathManager;
import cl.pandress.modules.headdeath.data.PlayerCosmetics;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class DeathCoinsCommand implements CommandExecutor, TabCompleter {

    private final HeadDeathManager manager;

    public DeathCoinsCommand(HeadDeathManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player p) {
                PlayerCosmetics cosmetics = manager.getCosmetics(p.getUniqueId());
                p.sendMessage(manager.getMsg("commands.balance").replace("{coins}", String.valueOf(cosmetics.getCoins())));
            } else {
                sender.sendMessage(manager.getRawMsg("commands.usage"));
            }
            return true;
        }

        if (!sender.hasPermission("headdeath.admin")) {
            sender.sendMessage(manager.getRawMsg("commands.no-permission"));
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(manager.getRawMsg("commands.usage"));
            return true;
        }

        String action = args[0].toLowerCase();
        Player target = Bukkit.getPlayerExact(args[1]);
        
        if (target == null) {
            sender.sendMessage(manager.getRawMsg("commands.invalid-player"));
            return true;
        }

        int amount;
        try {
            amount = Integer.parseInt(args[2]);
            if (amount < 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            sender.sendMessage(manager.getRawMsg("commands.invalid-number"));
            return true;
        }

        PlayerCosmetics targetCosmetics = manager.getCosmetics(target.getUniqueId());

        switch (action) {
            case "give" -> {
                targetCosmetics.addCoins(amount);
                sender.sendMessage(manager.getMsg("commands.give").replace("{amount}", String.valueOf(amount)).replace("{player}", target.getName()));
                target.sendMessage(manager.getMsg("commands.give-receiver").replace("{amount}", String.valueOf(amount)));
            }
            case "take" -> {
                targetCosmetics.removeCoins(amount);
                sender.sendMessage(manager.getMsg("commands.take").replace("{amount}", String.valueOf(amount)).replace("{player}", target.getName()));
            }
            case "set" -> {
                targetCosmetics.setCoins(amount);
                sender.sendMessage(manager.getMsg("commands.set").replace("{amount}", String.valueOf(amount)).replace("{player}", target.getName()));
            }
            default -> sender.sendMessage(manager.getRawMsg("commands.usage"));
        }

        manager.savePlayerCosmetics();
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> completions = new ArrayList<>();
        if (!sender.hasPermission("headdeath.admin")) return completions;

        if (args.length == 1) {
            completions.add("give");
            completions.add("take");
            completions.add("set");
            completions.add("balance");
        } else if (args.length == 2 && !args[0].equalsIgnoreCase("balance")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                completions.add(p.getName());
            }
        }
        return completions;
    }
}