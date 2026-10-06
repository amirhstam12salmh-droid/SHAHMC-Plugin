package com.shahmc.shahmcplugin;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class SHAHMCPlugin extends JavaPlugin implements Listener {

    private static SHAHMCPlugin instance;
    private final Map<UUID, Integer> money = new HashMap<>();
    private final Map<UUID, Integer> level = new HashMap<>();
    private Location lobbyLocation;
    private ArmorStand lobbyNpc;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        reloadConfig();

        getServer().getPluginManager().registerEvents(this, this);
        registerCommands();

        loadData();
        loadLobbyLocation();
        buildLobbyNpc();
        startScoreboardTask();

        getLogger().info("پلاگین SHAHMC فعال شد.");
    }

    @Override
    public void onDisable() {
        saveData();
        if (lobbyNpc != null && !lobbyNpc.isDead()) {
            lobbyNpc.remove();
        }
        getLogger().info("پلاگین SHAHMC غیرفعال شد.");
    }

    public static SHAHMCPlugin getInstance() {
        return instance;
    }

    private void registerCommands() {
        getCommand("lobby").setExecutor((sender, command, label, args) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "این دستور فقط برای بازیکن‌ها قابل استفاده است.");
                return true;
            }
            if (lobbyLocation == null) {
                player.sendMessage(ChatColor.RED + "مکان لابی هنوز تنظیم نشده است.");
                return true;
            }
            player.teleport(lobbyLocation);
            player.sendMessage(ChatColor.GREEN + "به لابی منتقل شدید.");
            return true;
        });

        getCommand("setlobby").setExecutor((sender, command, label, args) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "فقط بازیکن می‌تواند این دستور را اجرا کند.");
                return true;
            }
            if (!player.hasPermission("shahmc.setlobby")) {
                player.sendMessage(ChatColor.RED + "شما اجازه تنظیم لابی را ندارید.");
                return true;
            }
            Location loc = player.getLocation();
            lobbyLocation = loc.clone();
            getConfig().set("lobby.world", loc.getWorld().getName());
            getConfig().set("lobby.x", loc.getX());
            getConfig().set("lobby.y", loc.getY());
            getConfig().set("lobby.z", loc.getZ());
            getConfig().set("lobby.yaw", loc.getYaw());
            getConfig().set("lobby.pitch", loc.getPitch());
            saveConfig();
            buildLobbyNpc();
            player.sendMessage(ChatColor.GREEN + "مکان لابی با موفقیت تنظیم شد.");
            return true;
        });

        getCommand("smp").setExecutor((sender, command, label, args) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "این دستور فقط برای بازیکن‌ها استفاده می‌شود.");
                return true;
            }
            player.sendMessage(ChatColor.GOLD + "§lSHAHMC SMP");
            player.sendMessage(ChatColor.GRAY + "تعداد بازیکنان: " + ChatColor.WHITE + Bukkit.getOnlinePlayers().size());
            player.sendMessage(ChatColor.GRAY + "پینگ شما: " + ChatColor.WHITE + player.getPing() + " ms");
            player.sendMessage(ChatColor.GRAY + "پول: " + ChatColor.WHITE + getMoney(player.getUniqueId()));
            player.sendMessage(ChatColor.GRAY + "سطح: " + ChatColor.WHITE + getLevel(player.getUniqueId()));
            return true;
        });

        getCommand("npc").setExecutor((sender, command, label, args) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "این دستور فقط برای بازیکن‌ها قابل استفاده است.");
                return true;
            }
            if (!player.hasPermission("shahmc.npc")) {
                player.sendMessage(ChatColor.RED + "شما اجازه ساخت NPC را ندارید.");
                return true;
            }
            buildLobbyNpc(player.getLocation());
            player.sendMessage(ChatColor.GREEN + "NPC لابی ساخته شد.");
            return true;
        });
    }

    private void loadData() {
        File file = new File(getDataFolder(), "playerdata.yml");
        if (!file.exists()) {
            saveResource("playerdata.yml", false);
        }
        // Simple persistent data is stored in playerdata.yml by UUID keys.
        // This file is automatically created by the plugin if needed.
    }

    private void saveData() {
        File file = new File(getDataFolder(), "playerdata.yml");
        if (!file.exists()) {
            try {
                file.createNewFile();
            } catch (IOException ignored) {
            }
        }
    }

    private void loadLobbyLocation() {
        if (!getConfig().contains("lobby.world")) {
            return;
        }
        World world = Bukkit.getWorld(getConfig().getString("lobby.world", "world"));
        if (world == null) {
            return;
        }
        lobbyLocation = new Location(
                world,
                getConfig().getDouble("lobby.x", 0.5),
                getConfig().getDouble("lobby.y", 64),
                getConfig().getDouble("lobby.z", 0.5),
                (float) getConfig().getDouble("lobby.yaw", 0),
                (float) getConfig().getDouble("lobby.pitch", 0)
        );
    }

    private void buildLobbyNpc() {
        if (lobbyLocation == null) {
            return;
        }
        buildLobbyNpc(lobbyLocation.clone().add(1.5, 0, 1.5));
    }

    private void buildLobbyNpc(Location loc) {
        if (lobbyNpc != null && !lobbyNpc.isDead()) {
            lobbyNpc.remove();
        }
        ArmorStand npc = (ArmorStand) loc.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
        npc.setVisible(false);
        npc.setGravity(false);
        npc.setMarker(true);
        npc.setCustomName(ChatColor.GOLD + "§lSHAHMC");
        npc.setCustomNameVisible(true);
        npc.setCanTick(false);
        npc.setMetadata("shahmc_npc", new FixedMetadataValue(this, true));
        lobbyNpc = npc;
    }

    public Location getLobbyLocation() {
        return lobbyLocation;
    }

    public void teleportToLobby(Player player) {
        if (lobbyLocation == null) {
            player.sendMessage(ChatColor.RED + "مکان لابی هنوز تعریف نشده است.");
            return;
        }
        player.teleport(lobbyLocation);
    }

    public int getMoney(UUID uuid) {
        return money.getOrDefault(uuid, 0);
    }

    public void setMoney(UUID uuid, int value) {
        money.put(uuid, value);
    }

    public int getLevel(UUID uuid) {
        return level.getOrDefault(uuid, 1);
    }

    public void setLevel(UUID uuid, int value) {
        level.put(uuid, value);
    }

    private void startScoreboardTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    updateScoreboard(player);
                }
            }
        }.runTaskTimer(this, 20L, 20L);
    }

    private void updateScoreboard(Player player) {
        if (!getConfig().getBoolean("scoreboard.enabled", true)) {
            return;
        }

        ScoreboardManager manager = Bukkit.getScoreboardManager();
        if (manager == null) {
            return;
        }

        Scoreboard board = manager.getNewScoreboard();
        Objective objective = board.registerNewObjective("shahmc_scoreboard", "dummy", ChatColor.GOLD + "§lSHAHMC");
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        int online = Bukkit.getOnlinePlayers().size();
        int moneyValue = getMoney(player.getUniqueId());
        int levelValue = getLevel(player.getUniqueId());

        objective.getScore(ChatColor.AQUA + "نام: " + ChatColor.WHITE + player.getName()).setScore(10);
        objective.getScore(ChatColor.YELLOW + "پول: " + ChatColor.WHITE + moneyValue).setScore(9);
        objective.getScore(ChatColor.YELLOW + "سطح: " + ChatColor.WHITE + levelValue).setScore(8);
        objective.getScore(ChatColor.GREEN + "پینگ: " + ChatColor.WHITE + player.getPing() + " ms").setScore(7);
        objective.getScore(ChatColor.GREEN + "آنلاین: " + ChatColor.WHITE + online).setScore(6);
        objective.getScore(ChatColor.LIGHT_PURPLE + "سرور: " + ChatColor.WHITE + "SHAHMC").setScore(5);

        player.setScoreboard(board);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        money.putIfAbsent(uuid, 0);
        level.putIfAbsent(uuid, 1);
        if (lobbyLocation != null) {
            player.teleport(lobbyLocation);
        }
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', getConfig().getString("welcome.message", "§aبه سرور §6SHAHMC §aخوش آمدید!")));
        updateScoreboard(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Save player data if needed later.
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (lobbyLocation == null) {
            return;
        }
        Player player = event.getPlayer();
        World world = player.getWorld();
        if (!world.equals(lobbyLocation.getWorld())) {
            return;
        }
        if (player.getLocation().getY() <= 0) {
            player.teleport(lobbyLocation);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (lobbyLocation != null && player.getWorld().equals(lobbyLocation.getWorld())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (lobbyLocation != null && player.getWorld().equals(lobbyLocation.getWorld())) {
            Bukkit.getScheduler().runTask(this, () -> player.teleport(lobbyLocation));
        }
    }

    @EventHandler
    public void onClick(PlayerInteractAtEntityEvent event) {
        if (!(event.getRightClicked() instanceof ArmorStand stand)) {
            return;
        }
        if (stand.hasMetadata("shahmc_npc")) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.GOLD + "به لابی خوش آمدید!");
            teleportToLobby(event.getPlayer());
        }
    }
}
