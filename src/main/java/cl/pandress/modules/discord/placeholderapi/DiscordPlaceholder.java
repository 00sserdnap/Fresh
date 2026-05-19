package cl.pandress.modules.discord.placeholderapi;

import cl.pandress.Etherium;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.dv8tion.jda.api.entities.Guild;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

public class DiscordPlaceholder extends PlaceholderExpansion {

    private final Etherium plugin;

    public DiscordPlaceholder(Etherium plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "eth";
    }

    @Override
    public @NotNull String getAuthor() {
        return "pandress";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        try {
            // 1. Evitar NullPointerException si el plugin o sus managers aún no terminan de cargar
            if (plugin == null || plugin.getManagerHandler() == null || plugin.getManagerHandler().getDiscordManager() == null) {
                return "Cargando...";
            }

            boolean isEnabled = plugin.getManagerHandler().getDiscordManager().getConfig().getBoolean("discord.enabled", true);
            if (!isEnabled) {
                return "Desactivado";
            }

            // %eth_discord_members%
            if (params.equalsIgnoreCase("discord_members")) {
                if (plugin.getJda() != null) {
                    String guildId = plugin.getManagerHandler().getDiscordManager().getConfig().getString("discord.guild-id", "");
                    Guild guild = null;

                    // 2. Buscar guild de forma segura verificando que no esté vacío ni en default
                    if (guildId != null && !guildId.isEmpty() && !guildId.equals("AQUI_PEGA_TU_ID_DE_SERVIDOR")) {
                        guild = plugin.getJda().getGuildById(guildId);
                    }

                    // Si no lo encuentra por ID, intentar sacar el primer servidor en el que esté el bot
                    if (guild == null && !plugin.getJda().getGuilds().isEmpty()) {
                        guild = plugin.getJda().getGuilds().get(0);
                    }

                    if (guild != null) {
                        return String.valueOf(guild.getMemberCount());
                    }
                }
                return "0"; // Si JDA aún no se conecta
            }

            // %eth_discord_goal%
            if (params.equalsIgnoreCase("discord_goal")) {
                return String.valueOf(plugin.getManagerHandler().getDiscordManager().getConfig().getInt("discord.member-goal", 1000));
            }

        } catch (Exception e) {
            // 3. Si algo explota (un error raro de JDA), que no rompa la consola entera, solo avise.
            plugin.getLogger().warning("Error cargando Placeholder (eth_" + params + "): " + e.getMessage());
            return "Error";
        }

        return null; // El placeholder solicitado no existe
    }
}