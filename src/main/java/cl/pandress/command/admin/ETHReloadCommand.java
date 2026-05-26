package cl.pandress.command.admin;

import cl.pandress.Etherium;
import cl.pandress.utils.ChatUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class ETHReloadCommand implements CommandExecutor, TabCompleter {

    private final Etherium plugin = Etherium.getInstance();

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {

        if (label.equalsIgnoreCase("eth")) {
            if (!sender.hasPermission("eth.admin")) {
                sender.sendMessage(ChatUtils.colorize("&cⓘ No tienes permiso para usar comandos de administración."));
                return true;
            }

            if (args.length >= 2 && args[0].equalsIgnoreCase("reload")) {
                String sub = args[1].toLowerCase();

                switch (sub) {
                    case "quests":
                        plugin.getManagerHandler().getQuestManager().reloadConfig();
                        sender.sendMessage(ChatUtils.colorize("&cⓘ &fConfiguración de Quests recargada."));
                        return true;
                    case "ranks":
                    case "battlepass":
                    case "bp":
                        plugin.getManagerHandler().getBattlePassManager().reloadConfig();
                        sender.sendMessage(ChatUtils.colorize("&cⓘ &fConfiguración de BattlePass recargada."));
                        return true;
                    case "headdeath":
                    case "cosmetics":
                        plugin.getManagerHandler().getHeadDeathManager().reloadConfig();
                        plugin.getCosmeticsGUI().loadCosmeticsFromConfig(); // <-- Refresca la GUI en vivo
                        sender.sendMessage(ChatUtils.colorize("&cⓘ &fHologramas limpios, config y cosméticos recargados."));
                        return true;
                    case "discoveries":
                    case "descubrimientos":
                        plugin.getManagerHandler().getDiscoveryManager().loadConfigs();
                        sender.sendMessage(ChatUtils.colorize("&cⓘ &fConfiguración de Descubrimientos recargada correctamente."));
                        return true;
                    // --- Módulo de Tolvas (HopperLinks) ---
                    case "hopperlinks":
                        plugin.getManagerHandler().getHopperLinkManager().loadConfigs();
                        sender.sendMessage(ChatUtils.colorize("&cⓘ &fConfiguración de HopperLinks recargada correctamente."));
                        return true;
                }
            }

            sender.sendMessage(ChatUtils.colorize("&eUso correcto: &f/eth reload <quests|ranks|bp|discord|headdeath|cosmetics|discoveries|hopperlinks>"));
            return true;
        }
        return false;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();
        if (sender.hasPermission("eth.admin")) {
            if (args.length == 1) {
                completions.add("reload");
            } else if (args.length == 2 && args[0].equalsIgnoreCase("reload")) {
                completions.add("quests");
                completions.add("ranks");
                completions.add("battlepass");
                completions.add("discord");
                completions.add("headdeath");
                completions.add("cosmetics"); // <-- Añadido al TabCompleter
                completions.add("discoveries");
                completions.add("hopperlinks");
            }
        }
        return completions;
    }
}