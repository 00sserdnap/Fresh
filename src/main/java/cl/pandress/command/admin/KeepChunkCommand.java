package cl.pandress.command.admin;

import cl.pandress.Etherium;
import cl.pandress.modules.keepchunk.KeepChunkManager;
import cl.pandress.modules.keepchunk.KeepChunkType;
import cl.pandress.modules.keepchunk.menus.KeepChunkAdminMenu;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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

public class KeepChunkCommand implements CommandExecutor, TabCompleter {
    private final KeepChunkManager manager;

    public KeepChunkCommand(KeepChunkManager manager) {
        this.manager = manager;
    }

    /**
     * Convierte un string de tiempo flexible a minutos.
     * Formatos soportados:
     *   30s  / 30seg / 30segundos  -> segundos (mínimo 1 minuto)
     *   10m  / 10min / 10minutos   -> minutos
     *   2h   / 2hr  / 2horas       -> horas  (* 60)
     *   1d   / 1dia / 1dias        -> días   (* 1440)
     *   60   (solo número)          -> minutos por defecto
     */
    private int parseTimeToMinutes(String input) {
        if (input == null || input.isEmpty()) return 60;
        String lower = input.toLowerCase().trim();
        String numStr = lower.replaceAll("[^0-9]", "");
        if (numStr.isEmpty()) return 60;
        int value;
        try { value = Integer.parseInt(numStr); } catch (Exception e) { return 60; }
        String unit = lower.replaceAll("[0-9]", "").trim();

        if (unit.startsWith("s")) {
            return Math.max(1, (int) Math.ceil(value / 60.0));
        } else if (unit.startsWith("h")) {
            return value * 60;
        } else if (unit.startsWith("d")) {
            return value * 1440;
        } else {
            // Sin unidad o "m..." -> minutos
            return Math.max(1, value);
        }
    }

    /**
     * Formatea minutos a string legible para el nombre del ítem.
     * Ejemplos: 1->"1m", 60->"1h", 90->"1h 30m", 1440->"1d", 1500->"1d 1h"
     */
    private String formatMinutes(int minutes) {
        if (minutes < 60) {
            return minutes + "m";
        } else if (minutes < 1440) {
            int h = minutes / 60;
            int m = minutes % 60;
            return m > 0 ? h + "h " + m + "m" : h + "h";
        } else {
            int d = minutes / 1440;
            int rem = minutes % 1440;
            int h = rem / 60;
            int m = rem % 60;
            StringBuilder sb = new StringBuilder(d + "d");
            if (h > 0) sb.append(" ").append(h).append("h");
            if (m > 0) sb.append(" ").append(m).append("m");
            return sb.toString();
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String[] args) {
        if (!sender.hasPermission("keepchunk.admin")) {
            sender.sendMessage(manager.getMsg("no-permission"));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(manager.getMsg("admin.invalid-args"));
            return true;
        }

        String sub = args[0].toLowerCase();

        // 1. MENÚ DE ADMINISTRACIÓN
        if (sub.equals("adminmenu")) {
            if (sender instanceof Player) {
                KeepChunkAdminMenu.openMainMenu((Player) sender, manager);
            } else {
                sender.sendMessage("Solo jugadores pueden abrir el panel.");
            }
            return true;
        }

        // 2. LIMPIEZA DE NPCs BUGEADOS
        if (sub.equals("cleanup")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("§cEste comando solo puede ser usado por jugadores.");
                return true;
            }
            Player p = (Player) sender;
            int radius = 5;
            if (args.length >= 2) {
                try { radius = Integer.parseInt(args[1]); }
                catch (Exception ignored) {}
            }

            int count = 0;
            for (org.bukkit.entity.Entity e : p.getWorld().getNearbyEntities(p.getLocation(), radius, radius, radius)) {
                if (e instanceof org.bukkit.entity.Villager && e.getPersistentDataContainer().has(manager.getChunkIdKey(), org.bukkit.persistence.PersistentDataType.STRING)) {
                    e.remove();
                    count++;
                }
            }
            p.sendMessage("§a🧹 Limpieza completada: §f" + count + " §acargadores atascados/huérfanos eliminados en un radio de " + radius + " bloques.");
            return true;
        }

        // PARA LOS SIGUIENTES COMANDOS SE REQUIEREN AL MENOS 3 ARGUMENTOS
        if (args.length < 3) {
            sender.sendMessage("§eUso: /ethloadchunk <give|givetemp|recharge> <jugador> <id> [cantidad] [tiempo]");
            sender.sendMessage("§7Unidades de tiempo: §f30s §8(seg) §f10m §8(min) §f2h §8(horas) §f1d §8(días)");
            return true;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(manager.getMsg("admin.player-not-found"));
            return true;
        }

        String id = args[2].toLowerCase();

        // 3. DAR CARGADOR PERMANENTE/NORMAL
        if (sub.equals("give")) {
            int amount = 1;
            if (args.length >= 4) {
                try { amount = Integer.parseInt(args[3]); }
                catch (Exception ignored) {}
            }
            ItemStack item = manager.createLoaderItem(id, -1, null, null, false);
            if (item == null) {
                sender.sendMessage("§cLoader inválido o no existe en la config: " + id);
                return true;
            }
            item.setAmount(amount);
            target.getInventory().addItem(item);

            String msg = manager.getMsg("admin.give-success")
                    .replace("{amount}", String.valueOf(amount))
                    .replace("{item}", item.getItemMeta().getDisplayName())
                    .replace("{player}", target.getName());
            sender.sendMessage(msg);

            String adminName = sender instanceof Player ? sender.getName() : "Consola";
            String cleanName = ChatColor.stripColor(item.getItemMeta().getDisplayName());
            KeepChunkType type = manager.getType(id);
            int finalAmount = amount;
            Bukkit.getScheduler().runTaskAsynchronously(Etherium.getInstance(), () -> {
                manager.getDiscordHook().logGiveLoader(adminName, target, type, finalAmount, cleanName, false);
            });
        }

        // 4. DAR CARGADOR TEMPORAL
        // Uso: /ethloadchunk givetemp <jugador> <id> <cantidad> <tiempo>
        // Tiempo: 30s | 10m | 2h | 1d
        else if (sub.equals("givetemp")) {
            int amount = 1;
            int minutes = 60; // Default: 1 hora

            if (args.length >= 4) {
                try { amount = Integer.parseInt(args[3]); }
                catch (Exception ignored) {}
            }
            if (args.length >= 5) {
                minutes = parseTimeToMinutes(args[4]);
            }

            String timeLabel = formatMinutes(minutes);
            ItemStack item = manager.createLoaderItem(id, minutes, "§c⏳ Cargador Temporal §7(" + timeLabel + ")", null, true);
            if (item == null) {
                sender.sendMessage("§cLoader inválido o no existe en la config: " + id);
                return true;
            }
            item.setAmount(amount);
            target.getInventory().addItem(item);

            String msg = manager.getMsg("admin.give-success")
                    .replace("{amount}", String.valueOf(amount))
                    .replace("{item}", item.getItemMeta().getDisplayName())
                    .replace("{player}", target.getName());
            sender.sendMessage(msg);

            String adminName = sender instanceof Player ? sender.getName() : "Consola";
            String cleanName = ChatColor.stripColor(item.getItemMeta().getDisplayName());
            KeepChunkType type = manager.getType(id);
            int finalAmount = amount;
            Bukkit.getScheduler().runTaskAsynchronously(Etherium.getInstance(), () -> {
                manager.getDiscordHook().logGiveLoader(adminName, target, type, finalAmount, cleanName, true);
            });
        }

        // 5. DAR NÚCLEO DE RECARGA
        else if (sub.equals("recharge")) {
            int amount = 1;
            if (args.length >= 4) {
                try { amount = Integer.parseInt(args[3]); }
                catch (Exception ignored) {}
            }
            ItemStack item = manager.createCore(id);
            if (item == null) {
                sender.sendMessage("§cNúcleo inválido o no existe en la config: " + id);
                return true;
            }
            item.setAmount(amount);
            target.getInventory().addItem(item);

            String msg = manager.getMsg("admin.give-success")
                    .replace("{amount}", String.valueOf(amount))
                    .replace("{item}", item.getItemMeta().getDisplayName())
                    .replace("{player}", target.getName());
            sender.sendMessage(msg);

            String adminName = sender instanceof Player ? sender.getName() : "Consola";
            String cleanName = ChatColor.stripColor(item.getItemMeta().getDisplayName());
            int finalAmount = amount;
            Bukkit.getScheduler().runTaskAsynchronously(Etherium.getInstance(), () -> {
                manager.getDiscordHook().logGiveCore(adminName, target, finalAmount, cleanName);
            });
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        if (!sender.hasPermission("keepchunk.admin")) return new ArrayList<>();

        if (args.length == 1) {
            return Arrays.asList("give", "givetemp", "recharge", "adminmenu", "cleanup").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("adminmenu")) return new ArrayList<>();
            if (args[0].equalsIgnoreCase("cleanup")) return Arrays.asList("5", "10", "20");
            return null; // Autocompleta jugadores en línea
        }

        if (args.length == 3) {
            if (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("givetemp")) {
                return new ArrayList<>(manager.getLoadedTypes().keySet()).stream()
                        .filter(i -> i.startsWith(args[2].toLowerCase()))
                        .collect(Collectors.toList());
            } else if (args[0].equalsIgnoreCase("recharge")) {
                if (manager.getConfig().contains("cores")) {
                    return manager.getConfig().getConfigurationSection("cores").getKeys(false).stream()
                            .filter(i -> i.startsWith(args[2].toLowerCase()))
                            .collect(Collectors.toList());
                }
            }
        }

        // Argumento 4: Cantidad
        if (args.length == 4) {
            return Arrays.asList("1", "16", "32", "64");
        }

        // Argumento 5: Tiempo (solo para givetemp) — una sugerencia por unidad
        if (args.length == 5 && args[0].equalsIgnoreCase("givetemp")) {
            return Arrays.asList(
                "30s", "60s",        // segundos
                "10m", "30m", "60m", // minutos
                "2h",  "6h", "12h",  // horas
                "1d",  "3d",  "7d"   // días
            );
        }

        return new ArrayList<>();
    }
}