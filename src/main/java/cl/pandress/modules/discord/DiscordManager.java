package cl.pandress.modules.discord;

import cl.pandress.Etherium;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

public class DiscordManager {

    private final Etherium plugin = Etherium.getInstance();
    private FileConfiguration config, messages;
    private File configFile, messagesFile;

    public DiscordManager() {
        reloadConfig();
    }

    public void reloadConfig() {
        if (configFile == null) configFile = new File(plugin.getDataFolder(), "modules/discord/config.yml");
        if (!configFile.exists()) plugin.saveResource("modules/discord/config.yml", false);
        config = YamlConfiguration.loadConfiguration(configFile);

        if (messagesFile == null) messagesFile = new File(plugin.getDataFolder(), "modules/discord/messages.yml");
        if (!messagesFile.exists()) plugin.saveResource("modules/discord/messages.yml", false);
        messages = YamlConfiguration.loadConfiguration(messagesFile);
    }

    public void saveConfig() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().warning("No se pudo guardar el config.yml de Discord.");
        }
    }

    public FileConfiguration getConfig() { return config; }
    public FileConfiguration getMessages() { return messages; }
}