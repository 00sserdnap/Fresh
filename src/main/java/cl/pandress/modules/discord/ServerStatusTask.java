package cl.pandress.modules.discord;

import cl.pandress.Etherium;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.scheduler.BukkitRunnable;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

public class ServerStatusTask extends BukkitRunnable {

    private final Etherium plugin;
    private final JDA jda;
    private final DiscordManager discordManager;

    public ServerStatusTask(Etherium plugin, JDA jda) {
        this.plugin = plugin;
        this.jda = jda;
        this.discordManager = plugin.getManagerHandler().getDiscordManager();
    }

    @Override
    public void run() {
        FileConfiguration config = discordManager.getConfig();
        FileConfiguration msgs = discordManager.getMessages();
        
        if (jda == null || !config.getBoolean("status-monitor.enabled", false)) return;

        String channelId = config.getString("status-monitor.channel-id", "");
        String messageId = config.getString("status-monitor.message-id", "");

        if (channelId.isEmpty() || channelId.equals("AQUI_ID_DEL_CANAL")) return;

        TextChannel channel = jda.getTextChannelById(channelId);
        if (channel == null) {
            plugin.log(msgs.getString("logs.channel-not-found", ""));
            return;
        }

        if (messageId.isEmpty() || messageId.equals("AQUI_ID_DEL_MENSAJE")) {
            createNewMessage(channel);
            return;
        }

        channel.retrieveMessageById(messageId).queue(message -> {
            List<Button> buttons = getActiveButtons();
            if (buttons.isEmpty()) {
                message.editMessageEmbeds(buildStatusEmbed(true).build()).setComponents().queue();
            } else {
                message.editMessageEmbeds(buildStatusEmbed(true).build()).setComponents(ActionRow.of(buttons)).queue();
            }
        }, failure -> {
            plugin.log(msgs.getString("logs.message-not-found", ""));
            createNewMessage(channel);
        });
    }

    private void createNewMessage(TextChannel channel) {
        List<Button> buttons = getActiveButtons();
        
        // Verificamos si hay botones activos para añadirlos a la creación del mensaje
        if (buttons.isEmpty()) {
            channel.sendMessageEmbeds(buildStatusEmbed(true).build()).queue(this::saveMessageId);
        } else {
            channel.sendMessageEmbeds(buildStatusEmbed(true).build()).setComponents(ActionRow.of(buttons)).queue(this::saveMessageId);
        }
    }

    private void saveMessageId(net.dv8tion.jda.api.entities.Message message) {
        String newId = message.getId();
        discordManager.getConfig().set("status-monitor.message-id", newId);
        discordManager.saveConfig(); 
        
        String logMsg = discordManager.getMessages().getString("logs.panel-created", "").replace("%id%", newId);
        plugin.log(logMsg);
    }

    // --- NUEVO MÉTODO PARA CARGAR LOS BOTONES ---
    private List<Button> getActiveButtons() {
        List<Button> buttons = new ArrayList<>();
        FileConfiguration config = discordManager.getConfig();
        
        if (config.getBoolean("status-monitor.buttons.store.enabled", true)) {
            String label = config.getString("status-monitor.buttons.store.label", "🔗 Tienda / Web");
            String url = config.getString("status-monitor.buttons.store.url", "https://tienda.almamc.net");
            buttons.add(Button.link(url, label));
        }
        
        return buttons;
    }

    private int[] getPlayersData(String ip, boolean useGlobal) {
        if (useGlobal) {
            try {
                URL url = new URL("https://api.mcstatus.io/v2/status/java/" + ip);
                HttpURLConnection con = (HttpURLConnection) url.openConnection();
                con.setRequestMethod("GET");
                con.setConnectTimeout(3000);
                con.setReadTimeout(3000);

                StringBuilder response = new StringBuilder();
                try (BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()))) {
                    String inputLine;
                    while ((inputLine = in.readLine()) != null) {
                        response.append(inputLine);
                    }
                }

                JsonObject json = JsonParser.parseString(response.toString()).getAsJsonObject();
                if (json.get("online").getAsBoolean()) {
                    JsonObject players = json.getAsJsonObject("players");
                    return new int[]{players.get("online").getAsInt(), players.get("max").getAsInt()};
                }
            } catch (Exception e) {}
        }
        return new int[]{Bukkit.getOnlinePlayers().size(), Bukkit.getMaxPlayers()};
    }

    public EmbedBuilder buildStatusEmbed(boolean isOnline) {
        FileConfiguration config = discordManager.getConfig();
        FileConfiguration msgs = discordManager.getMessages();
        EmbedBuilder eb = new EmbedBuilder();

        eb.setColor(Color.decode(isOnline ? config.getString("status-monitor.color-online", "#43b581") : config.getString("status-monitor.color-offline", "#f04747")));
        eb.setTitle(config.getString("status-monitor.title", "Etherium Server"));
        eb.setDescription(config.getString("status-monitor.description", "Minecraft Server"));

        if (isOnline) {
            eb.addField(msgs.getString("embed.fields.status.name"), msgs.getString("embed.fields.status.online"), true);
            
            String ip = config.getString("status-monitor.ip", "mc.tuservidor.com");
            boolean useGlobal = config.getBoolean("status-monitor.global-players", false);
            int[] playersData = getPlayersData(ip, useGlobal);

            String playersFormat = msgs.getString("embed.fields.players.format")
                    .replace("%online%", String.valueOf(playersData[0]))
                    .replace("%max%", String.valueOf(playersData[1]));
            eb.addField(msgs.getString("embed.fields.players.name"), playersFormat, true);

            ZoneId zoneId = ZoneId.of("America/Santiago");
            ZonedDateTime now = ZonedDateTime.now(zoneId);
            String restartTimeStr = config.getString("status-monitor.restart-time", "06:00");
            int restartHour = Integer.parseInt(restartTimeStr.split(":")[0]);
            int restartMinute = Integer.parseInt(restartTimeStr.split(":")[1]);
            
            ZonedDateTime nextRestart = now.withHour(restartHour).withMinute(restartMinute).withSecond(0);
            if (now.isAfter(nextRestart) || now.isEqual(nextRestart)) nextRestart = nextRestart.plusDays(1);
            Duration untilRestart = Duration.between(now, nextRestart);
            
            String restartFormat = msgs.getString("embed.fields.restart.format")
                    .replace("%hours%", String.valueOf(untilRestart.toHours()))
                    .replace("%minutes%", String.valueOf(untilRestart.toMinutesPart()));
            eb.addField(msgs.getString("embed.fields.restart.name"), restartFormat, true);

            long uptimeMillis = ManagementFactory.getRuntimeMXBean().getUptime();
            Duration uptime = Duration.ofMillis(uptimeMillis);
            String uptimeFormat = msgs.getString("embed.fields.uptime.format")
                    .replace("%hours%", String.valueOf(uptime.toHours()))
                    .replace("%minutes%", String.valueOf(uptime.toMinutesPart()));
            eb.addField(msgs.getString("embed.fields.uptime.name"), uptimeFormat, true);

            String connectFormat = msgs.getString("embed.fields.connect.format")
                    .replace("%ip%", ip);
            eb.addField(msgs.getString("embed.fields.connect.name"), connectFormat, false);

        } else {
            eb.addField(msgs.getString("embed.fields.status.name"), msgs.getString("embed.fields.status.offline"), true);
            eb.addField(msgs.getString("embed.fields.players.name"), msgs.getString("embed.fields.players.offline"), true);
            eb.addField(msgs.getString("embed.fields.restart.name"), msgs.getString("embed.fields.restart.offline"), true);
            eb.addField(msgs.getString("embed.fields.uptime.name"), msgs.getString("embed.fields.uptime.offline"), true);
            eb.addField(msgs.getString("embed.fields.connect.name"), msgs.getString("embed.fields.connect.offline"), false);
        }

        eb.setFooter(msgs.getString("embed.footer.text", ""), jda.getSelfUser().getAvatarUrl());
        eb.setTimestamp(Instant.now());

        return eb;
    }
}