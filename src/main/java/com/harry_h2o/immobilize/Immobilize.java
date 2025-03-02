package com.harry_h2o.immobilize;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.command.TabCompleter;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class Immobilize extends JavaPlugin implements Listener {

    private final Map<UUID, Long> immobilizedEntities = new ConcurrentHashMap<>();
    private final Map<UUID, List<String>> entityReasons = new ConcurrentHashMap<>();
    private FileConfiguration lang;

    @Override
    public void onEnable() {
        printColoredTitle();
        getServer().getPluginManager().registerEvents(this, this);
        saveDefaultConfig();
        reloadLang();
        startExpirationCheckTask();

        // 确保默认配置写入
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();

        if (getCommand("ib") != null) {
            Objects.requireNonNull(getCommand("ib")).setExecutor(this);
            Objects.requireNonNull(getCommand("ib")).setTabCompleter(new ImmobilizeTabCompleter());
        }

        if (Bukkit.getPluginManager().isPluginEnabled("ProtocolLib")) {
            ProtocolLibrary.getProtocolManager().addPacketListener(new PacketAdapter(this, PacketType.Play.Client.CHAT) {
                @Override
                public void onPacketReceiving(PacketEvent event) {
                    Player player = event.getPlayer();
                    if (isImmobilized(player) && !getConfig().getBoolean("disabled_events.command", false)) {
                        event.setCancelled(true);
                        player.sendTitle("", colorize(lang.getString("messages.cannot_open_chat")), 0, 40, 10);
                    }
                }
            });
        }
    }

    @Override
    public void onDisable() {
        Bukkit.getConsoleSender().sendMessage(ChatColor.GOLD + "[Immobilize] Plugin unloaded safely");
    }

    private void printColoredTitle() {
        String title =
                "§bImmobilize V1.0.2 正在检查文件完整性...\n" +
                        "§6 _____     Immobilize V1.0.2     _     _ _ _        \n" +
                        "§6|_   _|   By Harry_H2O 已启用!  | |   (_) (_)       \n" +
                        "§6  | |  _ __ ___  _ __ ___   ___ | |__  _| |_ _______\n" +
                        "§6  | | | '_ ` _ \\| '_ ` _ \\ / _ \\| '_ \\| | | |_  / _ \\\n" +
                        "§6 _| |_| | | | | | | | | | | (_) | |_) | | | |/ /  __/\n" +
                        "§6|_____|_| |_| |_|_| |_| |_|\\___/|_.__/|_|_|_/___\\___|\n";
        Bukkit.getConsoleSender().sendMessage(title);
    }

    private void reloadLang() {
        File langFile = new File(getDataFolder(), "lang.yml");
        if (!langFile.exists()) saveResource("lang.yml", false);
        lang = YamlConfiguration.loadConfiguration(langFile);
    }

    private void startExpirationCheckTask() {
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            Iterator<Map.Entry<UUID, Long>> iterator = immobilizedEntities.entrySet().iterator();
            long current = System.currentTimeMillis();

            while (iterator.hasNext()) {
                Map.Entry<UUID, Long> entry = iterator.next();
                Entity entity = Bukkit.getEntity(entry.getKey());
                if (entity instanceof Player) {
                    Player player = (Player) entity;
                    long remaining = entry.getValue() == Long.MAX_VALUE ? -1 : (entry.getValue() - current) / 1000;
                    String reason = entityReasons.getOrDefault(player.getUniqueId(), Collections.emptyList()).stream().findFirst().orElse("无");
                    if (remaining > 0) {
                        player.sendTitle(
                                colorize(lang.getString("messages.immobilized_title")),
                                colorize("§c原因: " + reason + " §7| §a剩余时间: " + remaining + "s"),
                                0, 40, 20
                        );
                    } else if (remaining == -1) {
                        player.sendTitle(
                                colorize(lang.getString("messages.immobilized_title")),
                                colorize("§c原因: " + reason + " §7| §a剩余时间: 无限"),
                                0, 40, 20
                        );
                    }
                }
                if (entry.getValue() != Long.MAX_VALUE && entry.getValue() <= current) {
                    iterator.remove();
                    entityReasons.remove(entry.getKey());
                    if (entity instanceof Player) {
                        Player player = (Player) entity;
                        player.sendTitle("", colorize(lang.getString("messages.time_expired")), 10, 70, 20);
                        removeResistanceEffect(player); // 确保移除抗性效果
                    }
                }
            }
        }, 0L, 20L);
    }

    // 事件监听部分
    @EventHandler
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        if (isImmobilized(event.getPlayer()) && !getConfig().getBoolean("disabled_events.entity_interact", false)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!isImmobilized(player)) return; // 快速跳出非定身玩家

        if (!getConfig().getBoolean("disabled_events.block_break", false)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (isImmobilized(event.getPlayer()) && !getConfig().getBoolean("disabled_events.block_place", false)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onItemConsume(PlayerItemConsumeEvent event) {
        if (isImmobilized(event.getPlayer()) && !getConfig().getBoolean("disabled_events.item_consume", false)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (isImmobilized(event.getPlayer()) && !getConfig().getBoolean("disabled_events.interact_entity", false)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        if (isImmobilized(event.getPlayer()) && !getConfig().getBoolean("disabled_events.gamemode_change", false)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onToggleSneak(PlayerToggleSneakEvent event) {
        if (isImmobilized(event.getPlayer()) && !getConfig().getBoolean("disabled_events.toggle_sneak", false)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        if (isImmobilized(event.getPlayer()) && !getConfig().getBoolean("disabled_events.toggle_flight", false)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onItemHeld(PlayerItemHeldEvent event) {
        if (isImmobilized(event.getPlayer()) && !getConfig().getBoolean("disabled_events.item_held", false)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (isImmobilized(event.getPlayer()) && !getConfig().getBoolean("disabled_events.move", false)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (isImmobilized(event.getPlayer()) && !getConfig().getBoolean("disabled_events.interact", false)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        if (isImmobilized(event.getPlayer()) && !getConfig().getBoolean("disabled_events.teleport", false)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player) {
            Player damager = (Player) event.getDamager();
            if (isImmobilized(damager) && !getConfig().getBoolean("disabled_events.damage", false)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onItemDrop(PlayerDropItemEvent event) {
        if (isImmobilized(event.getPlayer()) && !getConfig().getBoolean("disabled_events.drop_item", false)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onItemPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            if (isImmobilized(player) && !getConfig().getBoolean("disabled_events.pickup_item", false)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            if (isImmobilized(player) && !getConfig().getBoolean("disabled_events.entity_damage", false)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent event) {
        if (isImmobilized(event.getPlayer()) && !getConfig().getBoolean("disabled_events.swap_hand", false)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player) {
            Player player = (Player) event.getPlayer();
            if (isImmobilized(player) && !getConfig().getBoolean("disabled_events.inventory_open", false)) {
                event.setCancelled(true);
                player.sendTitle("", colorize(lang.getString("messages.cannot_open_inventory")), 0, 40, 10);
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player) {
            Player player = (Player) event.getWhoClicked();
            if (isImmobilized(player) && !getConfig().getBoolean("disabled_events.inventory_click", false)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (isImmobilized(player) && !getConfig().getBoolean("disabled_events.command", false)) {
            String command = event.getMessage().split(" ")[0].substring(1);
            if (!command.equalsIgnoreCase("ib")) {
                event.setCancelled(true);
                player.sendTitle("", colorize(lang.getString("messages.cannot_open_chat")), 0, 40, 10);
            }
        }
    }

     // 指令处理部分
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        if (!cmd.getName().equalsIgnoreCase("ib")) return false;

        // 处理 /ib false
        if (args.length > 0 && args[0].equalsIgnoreCase("false")) {
            List<Entity> targets = parseTargets(sender, args.length > 1 ? args[1] : "@s");
            if (targets.isEmpty()) {
                sender.sendMessage(colorize(lang.getString("messages.invalid_entity")));
                return true;
            }
            applyImmobilize(targets, false, -1, Collections.emptyList());
            return true;
        }

        // 处理 /ib admin
        if (args.length > 0 && args[0].equalsIgnoreCase("admin")) {
            if (args.length > 1 && args[1].equalsIgnoreCase("reload")) {
                reloadLang();
                sender.sendMessage(colorize(lang.getString("messages.config_reloaded")));
            } else {
                // 如果只有 /ib admin，显示用法提示
                sender.sendMessage(colorize(lang.getString("messages.invalid_args")));
            }
            return true;
        }

        // 权限检查
        if (!sender.hasPermission("immobilize.command")) {
            sender.sendMessage(colorize(lang.getString("messages.no_permission")));
            return true;
        }

        // 参数校验
        if (args.length < 1) {
            sender.sendMessage(colorize(lang.getString("messages.invalid_args")));
            return true;
        }

        // 其他逻辑保持不变
        boolean enable = parseBooleanSafe(args[0]);
        List<Entity> targets = parseTargets(sender, args.length > 1 ? args[1] : "@s");
        if (targets.isEmpty()) {
            sender.sendMessage(colorize(lang.getString("messages.invalid_entity")));
            return true;
        }

        int parsedTime = -1; // 默认值为 -1，表示无限时间
        List<Map.Entry<String, Boolean>> parsedReasons = new ArrayList<>();

        // 参数解析
        for (String arg : args) {
            if (arg.startsWith("-t:")) {
                try {
                    parsedTime = Integer.parseInt(arg.substring(3));
                    if (parsedTime <= 0) throw new NumberFormatException();
                } catch (NumberFormatException e) {
                    sender.sendMessage(colorize(lang.getString("messages.invalid_time")));
                    return true;
                }
            } else if (arg.startsWith("-r:")) {
                // 解析原因和广播标志
                String[] parts = arg.substring(3).split("\\.", 2);
                String reason = parts[0]; // 原因内容
                boolean broadcast = parts.length > 1 && Boolean.parseBoolean(parts[1]); // 是否广播
                parsedReasons.add(new AbstractMap.SimpleEntry<>(reason, broadcast));
            }
        }

        // 应用定身逻辑
        applyImmobilize(targets, enable, parsedTime, parsedReasons);
        return true;
    }

    private boolean parseBooleanSafe(String value) {
        return value.equalsIgnoreCase("true");
    }

    private void applyImmobilize(List<Entity> targets, boolean enable, int duration, List<Map.Entry<String, Boolean>> reasons) {
        for (Entity entity : targets) {
            if (enable) {
                // 应用抗性效果（仅玩家）
                if (entity instanceof Player) {
                    applyResistanceEffect((Player) entity);
                }

                // 计算定身结束时间
                long expireTime = (duration == -1) ? Long.MAX_VALUE : System.currentTimeMillis() + duration * 1000L;
                immobilizedEntities.put(entity.getUniqueId(), expireTime);

                // 构建原因列表
                List<String> reasonList = reasons.stream()
                        .map(Map.Entry::getKey)
                        .filter(Objects::nonNull) // 过滤空值
                        .collect(Collectors.toList());

                // 存储原因
                entityReasons.put(entity.getUniqueId(), reasonList);

                // 广播原因
                reasons.stream()
                        .filter(Map.Entry::getValue) // 只广播需要广播的原因
                        .forEach(reason -> Bukkit.broadcastMessage(colorize(lang.getString("messages.broadcast_reason"))
                                .replace("%player%", entity.getName())
                                .replace("%reason%", reason.getKey())));

                // 显示原因给被定身的玩家
                if (entity instanceof Player) {
                    Player player = (Player) entity;
                    String reasonText = reasonList.isEmpty() ? "无" : String.join(", ", reasonList); // 多个原因用逗号分隔
                    player.sendTitle(
                            colorize(lang.getString("messages.immobilized_title")),
                            colorize("§c原因: " + reasonText + (duration == -1 ? "" : " §7| §a剩余时间: " + duration + "s")),
                            10, 70, 20
                    );
                }
            } else {
                // 移除抗性效果（仅玩家）
                if (entity instanceof Player) {
                    removeResistanceEffect((Player) entity);
                }

                // 解除定身
                immobilizedEntities.remove(entity.getUniqueId());
                entityReasons.remove(entity.getUniqueId());
                if (entity instanceof Player) {
                    ((Player) entity).sendTitle("", colorize(lang.getString("messages.time_expired")), 10, 70, 20);
                }
            }
        }
    }

    private List<Entity> parseTargets(CommandSender sender, String selector) {
        if (selector == null || selector.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return Bukkit.selectEntities(sender, selector);
        } catch (IllegalArgumentException e) {
            return Collections.emptyList();
        }
    }

    private String colorize(String message) {
        if (message == null) return "";
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    private boolean isImmobilized(Entity entity) {
        return immobilizedEntities.containsKey(entity.getUniqueId()) &&
                immobilizedEntities.get(entity.getUniqueId()) > System.currentTimeMillis();
    }

    public static class ImmobilizeTabCompleter implements TabCompleter {
        @Override
        public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
            List<String> completions = new ArrayList<>();
            if (args.length == 1) {
                completions.addAll(Arrays.asList("true", "false", "admin"));
            } else if (args.length == 2 && args[0].equalsIgnoreCase("admin")) {
                completions.add("reload");
            } else if (args.length == 2) {
                completions.addAll(Arrays.asList("@s", "@a", "@p", "@r"));
            } else if (args.length >= 3) {
                completions.addAll(Arrays.asList("-t:", "-r:"));
            }
            return completions;
        }
    }

    private void applyResistanceEffect(Player player) {
        if (getConfig().getBoolean("apply_resistance", true)) {
            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.DAMAGE_RESISTANCE,
                    Integer.MAX_VALUE,
                    0xFE,
                    false,
                    false
            ));
        }
    }

    // 解除定身逻辑
    private void removeResistanceEffect(Player player) {
        if (getConfig().getBoolean("apply_resistance", true)) {
            player.removePotionEffect(PotionEffectType.DAMAGE_RESISTANCE);
            // 强制客户端同步
            player.addPotionEffect(new PotionEffect(
                    PotionEffectType.DAMAGE_RESISTANCE,
                    1,
                    0,
                    false,
                    false
            ));
        }
    }
}