package cl.pandress.modules.chatbubble;

import cl.pandress.Etherium;
import cl.pandress.utils.ChatUtils;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Sound;
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

    private final Etherium plugin = Etherium.getInstance();
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

        display.setText(ChatUtils.colorize("&f" + text));
        display.setBillboard(Display.Billboard.CENTER);
        display.setDefaultBackground(false);
        display.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
        display.setAlignment(TextDisplay.TextAlignment.CENTER);
        display.setShadowed(true);

        // --- 1. ESTADO INICIAL PARA LA ANIMACIÓN ---
        // Iniciamos la escala en 0 (invisible) para que luego crezca.
        display.setTransformation(new Transformation(
                new Vector3f(0f, 0.8f, 0f),
                new AxisAngle4f(),
                new Vector3f(0f, 0f, 0f),    // Escala inicial: 0
                new AxisAngle4f()
        ));

        player.addPassenger(display);
        activeBubbles.put(uuid, display);

        // --- 2. CONFIGURACIÓN DE LA ANIMACIÓN (INTERPOLACIÓN) ---
        // Le decimos al display que tarde 8 ticks (menos de medio segundo) en cambiar a su nueva forma.
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(8);

        // --- 3. ESTADO FINAL DE LA ANIMACIÓN ---
        // Actualizamos la transformación a su tamaño real (escala 1).
        // El cliente de Minecraft animará automáticamente la transición.
        display.setTransformation(new Transformation(
                new Vector3f(0f, 0.8f, 0f),
                new AxisAngle4f(),
                new Vector3f(1f, 1f, 1f),    // Escala final: 1
                new AxisAngle4f()
        ));

        // --- 4. EFECTO DE SONIDO ---
        // Reproducimos un sonido tipo "Pop" (el de la gallina poniendo un huevo con pitch alto suena excelente para esto)
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_CHICKEN_EGG, 1.0f, 1.5f);

        // Eliminar a los 5 segundos (100 ticks)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (activeBubbles.get(uuid) == display) {
                // Opcional: Podrías hacer una animación de cierre aquí antes de removerlo,
                // pero eliminarlo directamente es más limpio para el servidor.
                display.remove();
                activeBubbles.remove(uuid);
            }
        }, 100L);
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