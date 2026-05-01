package cl.pandress.utils;

import org.bukkit.ChatColor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatUtils {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([0-9a-fA-F]{6})|#([0-9a-fA-F]{6})");

    /**
     * Traduce códigos de color tradicionales (&) y hexadecimales (&#RRGGBB).
     */
    public static String colorize(String message) {
        if (message == null || message.isEmpty()) {
            return "";
        }

        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuilder buffer = new StringBuilder(message.length());

        while (matcher.find()) {
            String hexColor = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            if (hexColor != null) {
                StringBuilder fancyHex = new StringBuilder(ChatColor.COLOR_CHAR + "x");
                for (char c : hexColor.toCharArray()) {
                    fancyHex.append(ChatColor.COLOR_CHAR).append(c);
                }
                matcher.appendReplacement(new StringBuffer(buffer), fancyHex.toString());
            }
        }
        message = matcher.appendTail(new StringBuffer(buffer)).toString();
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    private ChatUtils() {}
}