package cl.pandress.command.player;

import cl.pandress.Etherium;
import cl.pandress.modules.essentials.SpawnManager;
import cl.pandress.utils.ChatUtils;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SpawnCommand implements CommandExecutor {
    
    private final SpawnManager spawnManager;
    private final Map<UUID, BukkitRunnable> activeTeleports;
    private final Map<UUID, Long> cooldowns; // Registro de cooldowns

    public SpawnCommand(SpawnManager spawnManager) {
        this.spawnManager = spawnManager;
        this.activeTeleports = new HashMap<>();
        this.cooldowns = new HashMap<>();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        FileConfiguration config = Etherium.getInstance().getConfig();

        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatUtils.colorize(config.getString("spawn.messages.not-player", "&cSolo los jugadores pueden usar esto.")));
            return true;
        }

        Player player = (Player) sender;
        UUID playerId = player.getUniqueId();

        // 1. Verificación de Cooldown
        int cooldownTime = config.getInt("spawn.cooldown", 0);
        if (cooldownTime > 0 && cooldowns.containsKey(playerId)) {
            long timeLeftMillis = cooldowns.get(playerId) - System.currentTimeMillis();
            if (timeLeftMillis > 0) {
                long timeLeftSeconds = timeLeftMillis / 1000;
                String cdMsg = config.getString("spawn.messages.cooldown-active", "&cEspera &e%time% &csegundos.")
                                     .replace("%time%", String.valueOf(timeLeftSeconds));
                player.sendMessage(ChatUtils.colorize(cdMsg));
                return true;
            }
        }

        // 2. Verificación de si ya se está teletransportando
        if (activeTeleports.containsKey(playerId)) {
            player.sendMessage(ChatUtils.colorize(config.getString("spawn.messages.already-teleporting", "&cYa tienes un teletransporte en curso.")));
            return true;
        }

        // 3. Obtener ubicación del Spawn
        Location spawnLoc = spawnManager.getSpawn();
        if (spawnLoc == null) {
            player.sendMessage(ChatUtils.colorize(config.getString("spawn.messages.spawn-not-set", "&cSpawn no configurado.")));
            return true;
        }

        // 4. Iniciar Teletransporte
        int delay = config.getInt("spawn.delay", 5);
        String startMsg = config.getString("spawn.messages.teleport-start", "&eTeletransporte en &c%time% &esegundos.")
                                .replace("%time%", String.valueOf(delay));
        player.sendMessage(ChatUtils.colorize(startMsg));

        final Location startLoc = player.getLocation();

        BukkitRunnable teleportTask = new BukkitRunnable() {
            int timeLeft = delay;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    activeTeleports.remove(playerId);
                    cancel();
                    return;
                }

                Location currentLoc = player.getLocation();
                if (startLoc.getBlockX() != currentLoc.getBlockX() || 
                    startLoc.getBlockY() != currentLoc.getBlockY() || 
                    startLoc.getBlockZ() != currentLoc.getBlockZ()) {
                    
                    player.sendMessage(ChatUtils.colorize(config.getString("spawn.messages.teleport-cancel", "&cCancelado por movimiento.")));
                    sendActionBar(player, config.getString("spawn.messages.teleport-cancel-actionbar", "&c¡Cancelado!"));
                    
                    activeTeleports.remove(playerId);
                    cancel();
                    return;
                }

                if (timeLeft <= 0) {
                    player.teleport(spawnLoc);
                    player.sendMessage(ChatUtils.colorize(config.getString("spawn.messages.teleport-success", "&aLlegaste al Spawn.")));
                    sendActionBar(player, config.getString("spawn.messages.teleport-success-actionbar", "&a¡Llegaste!"));
                    
                    // Aplicar el cooldown solo si el TP fue exitoso
                    if (cooldownTime > 0) {
                        cooldowns.put(playerId, System.currentTimeMillis() + (cooldownTime * 1000L));
                    }
                    
                    activeTeleports.remove(playerId);
                    cancel();
                    return;
                }

                String abMsg = config.getString("spawn.messages.teleport-actionbar", "&eEn &c%time% &esegundos...")
                                     .replace("%time%", String.valueOf(timeLeft));
                sendActionBar(player, abMsg);
                timeLeft--;
            }
        };

        activeTeleports.put(playerId, teleportTask);
        teleportTask.runTaskTimer(Etherium.getInstance(), 0L, 20L);

        return true;
    }

    private void sendActionBar(Player player, String message) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatUtils.colorize(message)));
    }
}