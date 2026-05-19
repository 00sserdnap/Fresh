package cl.pandress.modules.bosses.dragon.listeners;

import cl.pandress.modules.bosses.dragon.DragonEventManager;
import cl.pandress.modules.bosses.dragon.managers.DragonPowerManager;
import cl.pandress.utils.ChatUtils;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DragonEventListener implements Listener {

    private final DragonEventManager manager;
    private final cl.pandress.Etherium plugin;
    private static final int MAX_DEATHS = 3;
    private static final double ARENA_RADIUS = 60.0;
    private final Map<UUID, Integer> deathCount = new HashMap<>();

    private DragonPowerManager powerManager;

    public DragonEventListener(DragonEventManager manager, cl.pandress.Etherium plugin) {
        this.manager = manager;
        this.plugin = plugin;
    }

    public void setPowerManager(DragonPowerManager powerManager) {
        this.powerManager = powerManager;
    }

    public void resetDeathCounts() { deathCount.clear(); }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDragonDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof EnderDragon)) return;
        if (!event.getEntity().getWorld().getName().equals(DragonEventManager.EVENT_WORLD)) return;

        EnderDragon damagedDragon = (EnderDragon) event.getEntity();
        if (!manager.isEventActive() || manager.getActiveDragon() == null) return;
        if (!damagedDragon.getUniqueId().equals(manager.getActiveDragon().getUniqueId())) return;

        double factorDeVida = manager.getCurrentPhase() == 3 ? 1.2 : (manager.getCurrentPhase() == 2 ? 2.0 : 3.0);
        double damageFinal = event.getDamage() / factorDeVida;
        event.setDamage(damageFinal);

        if (event instanceof EntityDamageByEntityEvent edbe) {
            Player attacker = null;
            if (edbe.getDamager() instanceof Player) attacker = (Player) edbe.getDamager();
            else if (edbe.getDamager() instanceof org.bukkit.entity.Projectile proj && proj.getShooter() instanceof Player) {
                attacker = (Player) proj.getShooter();
            }
            if (attacker != null) {
                manager.registerDamage(attacker.getUniqueId(), damageFinal);

                if (manager.getPowerManager() != null && manager.getPowerManager().getMirrorPlayers().contains(attacker.getUniqueId())) {
                    final Player mirrorAttacker = attacker;
                    final double reflectDmg = damageFinal * 0.5;
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (mirrorAttacker.isOnline() && !mirrorAttacker.isDead()) {
                            mirrorAttacker.damage(reflectDmg);
                            mirrorAttacker.sendMessage(ChatUtils.colorize("&8[&5&l☠ Dragón ☠&8] &b¡El espejo te devuelve &e" + String.format("%.1f", reflectDmg) + " &bdaño!"));
                        }
                    });
                }
            }
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity deadEntity = event.getEntity();
        if (!deadEntity.getWorld().getName().equals(DragonEventManager.EVENT_WORLD)) return;

        if (deadEntity instanceof EnderDragon) {
            if (manager.isEventActive() && manager.getActiveDragon() != null && deadEntity.getUniqueId().equals(manager.getActiveDragon().getUniqueId())) {
                event.setDroppedExp(event.getDroppedExp() * 5);
                resetDeathCounts();
                manager.endEventSuccess(deadEntity.getKiller());
            }
            return;
        }

        if (manager.isEventActive() && manager.getActiveMinions().contains(deadEntity)) {
            event.getDrops().clear();
            event.setDroppedExp(0);
        }

        if (deadEntity instanceof Player dead && manager.isEventActive()) {
            int deaths = deathCount.merge(dead.getUniqueId(), 1, Integer::sum);
            int remaining = MAX_DEATHS - deaths;
            if (remaining > 0) dead.sendMessage(ChatUtils.colorize("&8[&5&l☠ Dragón ☠&8] &cHas muerto. Te quedan &e" + remaining + " &cintento(s)."));
            else dead.sendMessage(ChatUtils.colorize("&8[&5&l☠ Dragón ☠&8] &4&l¡Has agotado tus 3 intentos! Ya no puedes volver a la arena."));
        }
    }

    private boolean isExhausted(Player player) {
        return manager.isEventActive() && deathCount.getOrDefault(player.getUniqueId(), 0) >= MAX_DEATHS;
    }

    // =========================================================
    //  CONTROL DE ACCESO
    // =========================================================

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (event.getTo() != null && event.getTo().getWorld().getName().equals(DragonEventManager.EVENT_WORLD)) {
            if (isExhausted(event.getPlayer())) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(ChatUtils.colorize("&8[&5&l☠ Dragón ☠&8] &4¡Ya no puedes entrar a la arena! Agotaste todos tus intentos."));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (event.getRespawnLocation().getWorld().getName().equals(DragonEventManager.EVENT_WORLD) && isExhausted(event.getPlayer())) {
            World mainWorld = Bukkit.getWorlds().get(0);
            event.setRespawnLocation(mainWorld.getSpawnLocation());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player.getWorld().getName().equals(DragonEventManager.EVENT_WORLD) && isExhausted(player)) {
            World mainWorld = Bukkit.getWorlds().get(0);
            player.teleport(mainWorld.getSpawnLocation());
            player.sendMessage(ChatUtils.colorize("&8[&5&l☠ Dragón ☠&8] &4Fuiste retirado de la arena porque no te quedan intentos."));
        }
    }

    // =========================================================
    //  PROTECCIÓN DEL MUNDO (ANTI-GRIEFING ABSOLUTO)
    // =========================================================

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.getBlock().getWorld().getName().equals(DragonEventManager.EVENT_WORLD)) {
            if (event.getPlayer().getGameMode() != GameMode.CREATIVE) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.getBlock().getWorld().getName().equals(DragonEventManager.EVENT_WORLD)) {
            if (event.getPlayer().getGameMode() != GameMode.CREATIVE) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (event.getBlock().getWorld().getName().equals(DragonEventManager.EVENT_WORLD)) {
            if (event.getPlayer().getGameMode() != GameMode.CREATIVE) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (event.getBlock().getWorld().getName().equals(DragonEventManager.EVENT_WORLD)) {
            if (event.getPlayer().getGameMode() != GameMode.CREATIVE) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(org.bukkit.event.player.PlayerInteractEvent event) {
        if (event.getPlayer().getWorld().getName().equals(DragonEventManager.EVENT_WORLD)) {
            if (event.getPlayer().getGameMode() != GameMode.CREATIVE) {
                // Evita pisar cultivos, usar mecheros o lanzar bolas de fuego
                if (event.getAction() == org.bukkit.event.block.Action.PHYSICAL) {
                    event.setCancelled(true);
                } else if (event.hasItem() && (event.getItem().getType() == Material.FLINT_AND_STEEL || event.getItem().getType() == Material.FIRE_CHARGE)) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityExplode(EntityExplodeEvent event) {
        // Evita que CUALQUIER explosión rompa el mapa (creepers, fireballs, cristales, dragón, wither)
        if (event.getLocation().getWorld().getName().equals(DragonEventManager.EVENT_WORLD)) {
            event.blockList().clear();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockExplode(org.bukkit.event.block.BlockExplodeEvent event) {
        // Evita que explosiones originadas por bloques (camas, respawn anchors) rompan el mapa
        if (event.getBlock().getWorld().getName().equals(DragonEventManager.EVENT_WORLD)) {
            event.blockList().clear();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDragonBreakBlock(EntityChangeBlockEvent event) {
        // Evita que entidades (EnderDragon al volar sobre bloques, Endermans, Wither) cambien o rompan bloques
        if (event.getEntity().getWorld().getName().equals(DragonEventManager.EVENT_WORLD)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        // Bloquea Endermans naturales
        if (event.getLocation().getWorld().getName().equals(DragonEventManager.EVENT_WORLD)) {
            if (event.getEntity().getType() == org.bukkit.entity.EntityType.ENDERMAN) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHangingBreak(org.bukkit.event.hanging.HangingBreakByEntityEvent event) {
        // Evita que se rompan cuadros o item frames por flechas o golpes
        if (event.getEntity().getWorld().getName().equals(DragonEventManager.EVENT_WORLD)) {
            if (event.getRemover() instanceof Player && ((Player) event.getRemover()).getGameMode() == GameMode.CREATIVE) return;
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onArmorStandManipulate(org.bukkit.event.player.PlayerArmorStandManipulateEvent event) {
        // Evita que los jugadores roben armaduras de Armor Stands
        if (event.getPlayer().getWorld().getName().equals(DragonEventManager.EVENT_WORLD)) {
            if (event.getPlayer().getGameMode() != GameMode.CREATIVE) {
                event.setCancelled(true);
            }
        }
    }

    // =========================================================
    //  ONDA EXPANSIVA
    // =========================================================
    public void launchShockwave(Location center, double maxRadius, double damage) {
        World world = center.getWorld();
        if (world == null) return;

        new BukkitRunnable() {
            double radius = 0;
            final double step = 1.5;

            @Override
            public void run() {
                if (radius > maxRadius) { this.cancel(); return; }

                for (int i = 0; i < 24; i++) {
                    double angle = (2 * Math.PI / 24) * i;
                    Location ground = new Location(world, center.getX() + Math.cos(angle) * radius, center.getY() + 0.1, center.getZ() + Math.sin(angle) * radius);
                    
                    world.spawnParticle(Particle.ENCHANTED_HIT, ground, 1, 0, 0, 0, 0);
                    if (radius % 3 < step) world.spawnParticle(Particle.SONIC_BOOM, ground, 1, 0, 0, 0, 0);
                }

                world.playSound(center, Sound.ENTITY_WITHER_HURT, 0.4f, 1.8f - (float)(radius / maxRadius));

                for (Player p : world.getPlayers()) {
                    double dist = p.getLocation().distance(center);
                    if (dist >= radius - step && dist <= radius + step / 2.0) applyShockwaveHit(p, center, damage);
                }
                radius += step;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    private void applyShockwaveHit(Player player, Location center, double damage) {
        if (damage > 0) player.damage(damage);
        Vector push = player.getLocation().toVector().subtract(center.toVector());
        push.setY(0); if (push.lengthSquared() > 0) push.normalize();
        player.setVelocity(push.multiply(1.5).setY(0.5));

        World w = player.getWorld();
        w.spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0, 1, 0), 40, 0.6, 1.0, 0.6, 0.15);

        boolean hasTotem = false;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == org.bukkit.Material.TOTEM_OF_UNDYING) {
                item.setAmount(item.getAmount() - 1);
                hasTotem = true; break;
            }
        }

        String msg = hasTotem ? "&8[&5&l☠ Dragón ☠&8] &d¡La onda te golpeó! Tu tótem absorbió parte del impacto." : "&8[&5&l☠ Dragón ☠&8] &c¡La onda expansiva te barrió!";
        player.sendMessage(ChatUtils.colorize(msg));
        player.playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 0.6f, 1.2f);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!player.getWorld().getName().equals(DragonEventManager.EVENT_WORLD) || player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;
        String cmd = event.getMessage().toLowerCase();
        if (cmd.startsWith("/fly") || cmd.startsWith("/efly") || cmd.startsWith("/cmi fly")) {
            event.setCancelled(true); player.setAllowFlight(false); player.setFlying(false);
            player.sendMessage(ChatUtils.colorize("&8[&5&l☠ Dragón ☠&8] &c¡La densa magia bloquea el vuelo!"));
        }
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (!player.getWorld().getName().equals(DragonEventManager.EVENT_WORLD) || player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;
        if (player.getAllowFlight() || player.isFlying()) {
            player.setAllowFlight(false); player.setFlying(false);
            player.sendMessage(ChatUtils.colorize("&8[&5&l☠ Dragón ☠&8] &c¡Las corrientes anulan tu vuelo!"));
        }
    }

    @EventHandler
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (!player.getWorld().getName().equals(DragonEventManager.EVENT_WORLD) || player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return;
        event.setCancelled(true); player.setAllowFlight(false); player.setFlying(false);
    }

    // =========================================================
    //  INVERSIÓN DE CURACIÓN
    // =========================================================
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPotionConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        if (!player.getWorld().getName().equals(DragonEventManager.EVENT_WORLD)) return;
        if (!manager.isEventActive() || manager.getPowerManager() == null) return;
        if (!manager.getPowerManager().isHealInverted()) return;

        ItemStack item = event.getItem();
        if (item.getType() != Material.POTION) return;
        if (!(item.getItemMeta() instanceof PotionMeta meta)) return;

        boolean isHealPotion = meta.getCustomEffects().stream().anyMatch(e ->
            e.getType().equals(org.bukkit.potion.PotionEffectType.REGENERATION) ||
            e.getType().equals(org.bukkit.potion.PotionEffectType.INSTANT_HEALTH)
        );

        if (!isHealPotion) {
            String potionKey = meta.getBasePotionType() != null ? meta.getBasePotionType().name() : "";
            isHealPotion = potionKey.contains("HEALING") || potionKey.contains("REGENERATION");
        }

        if (!isHealPotion) return;

        event.setCancelled(true);

        if (manager.getActiveDragon() != null && !manager.getActiveDragon().isDead()) {
            double healAmount = 6.0;
            double maxHp = manager.getActiveDragon().getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
            double newHp = Math.min(maxHp, manager.getActiveDragon().getHealth() + healAmount);
            manager.getActiveDragon().setHealth(newHp);

            player.sendMessage(ChatUtils.colorize("&8[&5&l☠ Dragón ☠&8] &4¡Tu poción curó al dragón! (&c+" + healAmount + "❤&4)"));
            player.getWorld().spawnParticle(Particle.HEART, manager.getActiveDragon().getLocation().add(0, 3, 0), 10, 2, 2, 2, 0);

            ItemStack bottle = new ItemStack(Material.GLASS_BOTTLE);
            if (player.getInventory().getItemInMainHand().isSimilar(item)) {
                ItemStack hand = player.getInventory().getItemInMainHand().clone();
                hand.setAmount(hand.getAmount() - 1);
                player.getInventory().setItemInMainHand(hand.getAmount() > 0 ? hand : null);
                player.getInventory().addItem(bottle);
            } else {
                ItemStack off = player.getInventory().getItemInOffHand().clone();
                off.setAmount(off.getAmount() - 1);
                player.getInventory().setItemInOffHand(off.getAmount() > 0 ? off : null);
                player.getInventory().addItem(bottle);
            }
        }
    }
}