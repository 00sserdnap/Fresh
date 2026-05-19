package cl.pandress.modules.keepchunk.discord;

import cl.pandress.Etherium;
import cl.pandress.modules.keepchunk.KeepChunkManager;
import cl.pandress.modules.keepchunk.KeepChunkType;
import cl.pandress.modules.keepchunk.data.KeepChunkData;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.awt.Color;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class KeepChunkDiscord {
    private final KeepChunkManager manager;

    public KeepChunkDiscord(KeepChunkManager manager) {
        this.manager = manager;
    }

    private boolean isValidSnowflake(String id) {
        return id != null && !id.isEmpty() && id.matches("\\d{17,20}");
    }

    public void logGiveLoader(String adminName, Player target, KeepChunkType type, int amount, String itemName, boolean isTemp) {
        JDA jda = Etherium.getInstance().getJda();
        if (jda == null) return;

        String channelId = manager.getConfig().getString("settings.discord.log-channel-id", "");
        if (!isValidSnowflake(channelId)) return;

        TextChannel channel = jda.getTextChannelById(channelId);
        if (channel == null) return;

        int owned = amount;
        for (KeepChunkData data : manager.getActiveLoaders().values()) {
            if (data.getOwner().equals(target.getUniqueId())) owned++;
        }
        
        int radius = type != null ? type.getRadius() : 0;
        int chunksPorLado = (radius * 2) + 1;
        int totalChunks = chunksPorLado * chunksPorLado;
        String sizeText = chunksPorLado + "x" + chunksPorLado + " (" + totalChunks + " Chunks a la vez)";

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle(isTemp ? "⏳ Registro: Entrega de Cargador TEMPORAL" : "📦 Registro: Entrega de Cargador");
        embed.setColor(isTemp ? new Color(230, 126, 34) : new Color(46, 204, 113)); 
        
        embed.addField("🛠️ Administrador", adminName, true);
        embed.addField("👤 Jugador Destino", target.getName() + "\n*(Total en posesión: " + owned + ")*", true);
        embed.addField("🎁 Ítem Entregado", amount + "x " + itemName, false);
        
        embed.addField("⚙️ Especificaciones Técnicas", 
                "**Tamaño de Carga:** " + sizeText + "\n" +
                "**Carga Vertical (Y):** Infinita (De Bedrock a Límite de Cielo)\n" +
                "*(El motor de Minecraft siempre carga la altura completa del chunk)*", false);
        
        embed.setTimestamp(Instant.now());
        embed.setFooter("Etherium Logs | /ethloadchunk", null);

        channel.sendMessageEmbeds(embed.build()).queue();
    }

    public void logGiveCore(String adminName, Player target, int amount, String itemName) {
        JDA jda = Etherium.getInstance().getJda();
        if (jda == null) return;

        String channelId = manager.getConfig().getString("settings.discord.log-channel-id", "");
        if (!isValidSnowflake(channelId)) return;

        TextChannel channel = jda.getTextChannelById(channelId);
        if (channel == null) return;

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("🔋 Registro: Entrega de Núcleo");
        embed.setColor(new Color(241, 196, 15)); 
        
        embed.addField("🛠️ Administrador", adminName, true);
        embed.addField("👤 Jugador Destino", target.getName(), true);
        embed.addField("🎁 Combustible Entregado", amount + "x " + itemName, false);
        
        embed.setTimestamp(Instant.now());
        embed.setFooter("Etherium Logs | /ethloadchunk", null);

        channel.sendMessageEmbeds(embed.build()).queue();
    }

    public void updateStatusEmbed() {
        JDA jda = Etherium.getInstance().getJda();
        if (jda == null) return;

        String channelId = manager.getConfig().getString("settings.discord.status-channel-id", "");
        String messageId = manager.getConfig().getString("settings.discord.status-message-id", "");

        if (!isValidSnowflake(channelId) || !isValidSnowflake(messageId)) return; 

        TextChannel channel = jda.getTextChannelById(channelId);
        if (channel == null) return;

        Map<UUID, Map<KeepChunkType, Integer>> playerStats = new HashMap<>();
        int totalNPCs = 0;
        int totalChunksReales = 0;
        
        for (KeepChunkData data : manager.getActiveLoaders().values()) {
            if (!data.isActive()) continue;
            
            UUID owner = data.getOwner();
            KeepChunkType type = manager.getType(data.getTypeId());
            if (type == null) continue;
            
            playerStats.putIfAbsent(owner, new HashMap<>());
            Map<KeepChunkType, Integer> counts = playerStats.get(owner);
            counts.put(type, counts.getOrDefault(type, 0) + 1);
            
            int radius = type.getRadius();
            int side = (radius * 2) + 1;
            totalChunksReales += (side * side);
            totalNPCs++;
        }

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("⚡ Monitor Global: Cargadores de Chunks");
        embed.setColor(new Color(155, 89, 182));
        embed.setDescription("Panel de monitoreo en tiempo real de los chunks forzados en el servidor.\n\n" +
                "**Nota Técnica:** La carga es **Infinita en Vertical** (Desde la Bedrock hasta el límite del cielo).");

        if (playerStats.isEmpty()) {
            embed.addField("Sin Actividad", "Actualmente ningún jugador tiene cargadores activos.", false);
        } else {
            StringBuilder sb = new StringBuilder();
            
            for (Map.Entry<UUID, Map<KeepChunkType, Integer>> entry : playerStats.entrySet()) {
                OfflinePlayer p = Bukkit.getOfflinePlayer(entry.getKey());
                String name = p.getName() != null ? p.getName() : "Desconocido";
                
                sb.append("👤 **").append(name).append("**\n");
                
                for (Map.Entry<KeepChunkType, Integer> typeEntry : entry.getValue().entrySet()) {
                    KeepChunkType t = typeEntry.getKey();
                    int count = typeEntry.getValue();
                    int size = (t.getRadius() * 2) + 1;
                    
                    sb.append("└ `").append(count).append("x` Cargador ").append(size).append("x").append(size).append("\n");
                }
                sb.append("\n");
            }
            
            embed.addField("📋 Desglose por Jugador", sb.toString(), false);
            embed.addField("📊 Estadísticas Globales de Rendimiento", 
                    "• Total de NPCs Cargadores activos: **" + totalNPCs + "**\n" +
                    "• Total de Chunks individuales cargados: **" + totalChunksReales + "**", false);
        }

        embed.setTimestamp(Instant.now());
        embed.setFooter("Sistema automatizado Etherium KeepChunk", null);

        channel.editMessageEmbedsById(messageId, embed.build()).queue(
                success -> {}, 
                error -> Etherium.getInstance().log("&cNo se pudo actualizar el panel de Cargadores (Logs) en Discord. Revisa el message-id en tu config de keepchunk.")
        );
    }
}