package cl.pandress.modules.chatbubble;

import cl.pandress.Fresh;
import cl.pandress.utils.ChatUtils;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ChatBubbleListener implements Listener {

    private final Fresh plugin = Fresh.getInstance();
    private final Map<UUID, TextDisplay> activeBubbles = new HashMap<>();

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        String message = event.getMessage();
        
        if (message.startsWith("#")) {
            event.setCancelled(true);
            Player player = event.getPlayer();
            String chatMessage = message.substring(1).trim();

            if (chatMessage.isEmpty()) return;

            Bukkit.getScheduler().runTask(plugin, () -> showBubble(player, chatMessage));
        }
    }

    private void showBubble(Player player, String text) {
        UUID uuid = player.getUniqueId();

        if (activeBubbles.containsKey(uuid)) {
            TextDisplay oldBubble = activeBubbles.remove(uuid);
            if (oldBubble != null && oldBubble.isValid()) {
                oldBubble.remove();
            }
        }

        TextDisplay display = player.getWorld().spawn(player.getLocation(), TextDisplay.class);
        
        // --- AQUÍ QUITAMOS EL NOMBRE ---
        // Ahora solo mostrará el texto que el jugador escribió en color blanco (&f)
        display.setText(ChatUtils.colorize("&f" + text));
        
        display.setBillboard(Display.Billboard.CENTER);
        display.setDefaultBackground(false); 
        display.setBackgroundColor(Color.fromARGB(0, 0, 0, 0)); 
        display.setAlignment(TextDisplay.TextAlignment.CENTER);
        display.setShadowed(true); 

        // --- ALTURA AJUSTADA ---
        // Lo dejé en 0.8f para que quede justo arriba de los nametags
        display.setTransformation(new Transformation(
                new Vector3f(0f, 0.8f, 0f),  
                new AxisAngle4f(),           
                new Vector3f(1f, 1f, 1f),    
                new AxisAngle4f()            
        ));

        player.addPassenger(display);
        activeBubbles.put(uuid, display);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (activeBubbles.get(uuid) == display) {
                display.remove();
                activeBubbles.remove(uuid);
            }
        }, 100L); // Se borra a los 5 segundos
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        if (activeBubbles.containsKey(uuid)) {
            TextDisplay display = activeBubbles.remove(uuid);
            if (display != null && display.isValid()) {
                display.remove();
            }
        }
    }
}