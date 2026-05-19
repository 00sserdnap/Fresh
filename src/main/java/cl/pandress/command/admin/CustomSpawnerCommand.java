package cl.pandress.command.admin;

import cl.pandress.modules.customspawners.CustomSpawnerManager;
import cl.pandress.modules.customspawners.menus.CustomSpawnerMenu;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class CustomSpawnerCommand implements CommandExecutor, TabCompleter {

    private final CustomSpawnerManager manager;
    private final CustomSpawnerMenu menuHandler;

    public CustomSpawnerCommand(CustomSpawnerManager manager, CustomSpawnerMenu menuHandler) {
        this.manager = manager;
        this.menuHandler = menuHandler;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("customspawner.admin")) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', manager.getConfig().getString("messages.no-permission", "&cNo tienes permisos.")));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + "Uso: /" + label + " give <jugador> <tipo> [cantidad]");
            sender.sendMessage(ChatColor.YELLOW + "Uso: /" + label + " menu");
            return true;
        }

        // COMANDO: /ethspawners menu
        if (args[0].equalsIgnoreCase("menu")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "Solo jugadores pueden abrir el menú.");
                return true;
            }
            menuHandler.openMainMenu((Player) sender);
            return true;
        }

        // COMANDO: /ethspawners give <jugador> <tipo> [cantidad]
        if (args[0].equalsIgnoreCase("give")) {
            if (args.length < 3) {
                sender.sendMessage(ChatColor.RED + "Uso correcto: /" + label + " give <jugador> <tipo> [cantidad]");
                return true;
            }

            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', manager.getConfig().getString("messages.invalid-player", "&cJugador no encontrado.")));
                return true;
            }

            EntityType type;
            try {
                type = EntityType.valueOf(args[2].toUpperCase());
            } catch (IllegalArgumentException e) {
                sender.sendMessage(ChatColor.translateAlternateColorCodes('&', manager.getConfig().getString("messages.invalid-type", "&cTipo de entidad inválido.")));
                return true;
            }

            int amount = 1; // Cantidad por defecto si no escriben el número
            if (args.length >= 4) {
                try {
                    amount = Integer.parseInt(args[3]);
                } catch (NumberFormatException e) {
                    sender.sendMessage(ChatColor.RED + "La cantidad debe ser un número válido.");
                    return true;
                }
            }

            ItemStack spawnerItem = manager.createSpawnerItem(type);
            spawnerItem.setAmount(amount);
            target.getInventory().addItem(spawnerItem);

            String giveMsg = manager.getConfig().getString("messages.give", "&aHas dado {amount} Spawners de {type} a {player}.")
                    .replace("{amount}", String.valueOf(amount))
                    .replace("{type}", type.name())
                    .replace("{player}", target.getName());
            
            String receiveMsg = manager.getConfig().getString("messages.receive", "&aHas recibido {amount} Spawners de {type}.")
                    .replace("{amount}", String.valueOf(amount))
                    .replace("{type}", type.name());

            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', giveMsg));
            target.sendMessage(ChatColor.translateAlternateColorCodes('&', receiveMsg));

            sendWebhookLog(sender.getName(), target.getName(), amount, type.name(), target.getWorld().getName());
            
            return true;
        }

        sender.sendMessage(ChatColor.RED + "Uso incorrecto. Usa /" + label + " give o menu.");
        return true;
    }

    // --- SISTEMA DE AUTOCOMPLETADO (TABCOMPLETER) ---
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (!sender.hasPermission("customspawner.admin")) {
            return completions;
        }

        if (args.length == 1) {
            if ("give".startsWith(args[0].toLowerCase())) completions.add("give");
            if ("menu".startsWith(args[0].toLowerCase())) completions.add("menu");
        } 
        else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                    completions.add(p.getName());
                }
            }
        } 
        else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            for (EntityType type : EntityType.values()) {
                if (type.isAlive() && type.isSpawnable() && type.name().toLowerCase().startsWith(args[2].toLowerCase())) {
                    completions.add(type.name());
                }
            }
        } 
        else if (args.length == 4 && args[0].equalsIgnoreCase("give")) {
            completions.add("1");
            completions.add("16");
            completions.add("32");
            completions.add("64");
        }

        return completions;
    }

    private void sendWebhookLog(String admin, String target, int amount, String type, String worldName) {
        if (!manager.getConfig().getBoolean("webhook.enabled")) return;
        
        String urlString = manager.getConfig().getString("webhook.url");
        if (urlString == null || urlString.isEmpty() || urlString.equals("TU_WEBHOOK_AQUI")) return;

        Bukkit.getScheduler().runTaskAsynchronously(manager.getPlugin(), () -> {
            try {
                String json = "{\"embeds\": [{" 
                        + "\"title\": \"\uD83D\uDCE6 Log de Custom Spawners\"," 
                        + "\"description\": \"Se han entregado nuevos spawners custom.\"," 
                        + "\"color\": 5814783," 
                        + "\"fields\": [" 
                        + "{\"name\": \"Administrador\", \"value\": \"" + admin + "\", \"inline\": true}," 
                        + "{\"name\": \"Receptor\", \"value\": \"" + target + "\", \"inline\": true}," 
                        + "{\"name\": \"Cantidad\", \"value\": \"" + amount + "\", \"inline\": true}," 
                        + "{\"name\": \"Tipo de Entidad\", \"value\": \"" + type + "\", \"inline\": true}," 
                        + "{\"name\": \"Mundo\", \"value\": \"" + worldName + "\", \"inline\": true}" 
                        + "]" 
                        + "}]}";

                URL url = new URL(urlString);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("User-Agent", "Java-DiscordWebhook");
                connection.setDoOutput(true);

                try (OutputStream os = connection.getOutputStream()) {
                    byte[] input = json.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                connection.getInputStream().close();
                connection.disconnect();

            } catch (Exception e) {
                manager.getPlugin().getLogger().warning("[CustomSpawners] No se pudo enviar el Webhook de Discord: " + e.getMessage());
            }
        });
    }
}