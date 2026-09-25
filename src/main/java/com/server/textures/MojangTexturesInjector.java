package com.server.textures;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class MojangTexturesInjector extends JavaPlugin implements CommandExecutor, TabCompleter {
    private MojangApiClient apiClient;
    private ProfileCacheManager cacheManager;
    private boolean injectOnJoin;
    private boolean notifyPlayer;
    private boolean hasLoginSecurity;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfiguration();

        this.hasLoginSecurity = Bukkit.getPluginManager().getPlugin("LoginSecurity") != null;
        if (hasLoginSecurity) {
            getLogger().info("Found LoginSecurity, texture injection will trigger upon successful login/register.");
        } else {
            getLogger().info("LoginSecurity not found, falling back to PlayerJoin injection.");
        }

        Bukkit.getPluginManager().registerEvents(new TexturesEventListener(this), this);

        if (getCommand("mojangtextures") != null) {
            getCommand("mojangtextures").setExecutor(this);
            getCommand("mojangtextures").setTabCompleter(this);
        }

        getLogger().info("MojangTexturesInjector enabled successfully!");
    }

    public void loadConfiguration() {
        reloadConfig();
        FileConfiguration config = getConfig();

        String proxyHost = config.getString("proxy.host", "");
        int proxyPort = config.getInt("proxy.port", 0);
        boolean proxyEnabled = config.getBoolean("proxy.enabled", false);
        int timeout = config.getInt("network.timeout-seconds", 10);
        long cacheTtl = config.getLong("cache.ttl-hours", 24);
        this.injectOnJoin = config.getBoolean("settings.inject-on-join", false);
        this.notifyPlayer = config.getBoolean("settings.notify-player", true);

        this.apiClient = new MojangApiClient(getLogger(), proxyEnabled ? proxyHost : null, proxyPort, timeout);
        this.cacheManager = new ProfileCacheManager(getDataFolder(), getLogger(), cacheTtl);
    }

    public boolean hasLoginSecurity() {
        return hasLoginSecurity;
    }

    public boolean isInjectOnJoin() {
        return injectOnJoin;
    }

    /**
     * Preloads profile into cache if not present.
     */
    public void preloadProfile(String username) {
        if (cacheManager.get(username) != null) {
            return;
        }
        apiClient.fetchProfileAsync(username).thenAccept(profile -> {
            if (profile != null) {
                cacheManager.put(username, profile);
                getLogger().info("[MojangTexturesInjector] Preloaded Mojang textures for " + username +
                        (profile.getTextures().hasCape() ? " (with Cape)" : ""));
            }
        });
    }

    /**
     * Applies textures to a player.
     */
    public void applyTextures(Player player, boolean force) {
        String username = player.getName();
        MojangProfile cached = cacheManager.get(username);

        if (cached != null && !force) {
            injectToPlayer(player, cached);
            return;
        }

        // Fetch from API
        apiClient.fetchProfileAsync(username).thenAccept(profile -> {
            if (profile != null) {
                cacheManager.put(username, profile);
                Bukkit.getScheduler().runTask(this, () -> {
                    if (player.isOnline()) {
                        injectToPlayer(player, profile);
                    }
                });
            } else {
                getLogger().fine("[MojangTexturesInjector] " + username + " is not a premium Mojang account or fetch failed.");
            }
        });
    }

    private void injectToPlayer(Player player, MojangProfile profile) {
        TexturesProperty tp = profile.getTextures();
        try {
            PlayerProfile paperProfile = player.getPlayerProfile();
            paperProfile.removeProperty("textures");
            paperProfile.setProperty(new ProfileProperty("textures", tp.getValue(), tp.getSignature()));
            player.setPlayerProfile(paperProfile);

            if (notifyPlayer) {
                if (tp.hasCape()) {
                    player.sendMessage("§a[Skin & Cape] 成功为您加载正版皮肤与官方披风！");
                } else {
                    player.sendMessage("§a[Skin] 成功为您加载正版皮肤！");
                }
            }

            getLogger().info("[MojangTexturesInjector] Injected signed textures for " + player.getName() +
                    (tp.hasCape() ? " (Cape included)" : ""));
        } catch (Exception e) {
            getLogger().warning("[MojangTexturesInjector] Failed injecting textures for " + player.getName() + ": " + e.getMessage());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§6=== MojangTexturesInjector ===");
            sender.sendMessage("§e/" + label + " refresh [player] §7- 刷新正版皮肤与披风");
            if (sender.hasPermission("mojangtextures.admin")) {
                sender.sendMessage("§e/" + label + " reload §7- 重载配置文件");
            }
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if ("reload".equals(sub)) {
            if (!sender.hasPermission("mojangtextures.admin")) {
                sender.sendMessage("§c您没有权限执行此命令。");
                return true;
            }
            loadConfiguration();
            sender.sendMessage("§a[MojangTexturesInjector] 配置文件已重载！");
            return true;
        }

        if ("refresh".equals(sub)) {
            Player target;
            if (args.length > 1) {
                if (!sender.hasPermission("mojangtextures.admin")) {
                    sender.sendMessage("§c您没有权限刷新其他玩家的皮肤。");
                    return true;
                }
                target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage("§c玩家 " + args[1] + " 不在线。");
                    return true;
                }
            } else {
                if (!(sender instanceof Player)) {
                    sender.sendMessage("§c控制台请指定玩家名：/" + label + " refresh <player>");
                    return true;
                }
                target = (Player) sender;
            }

            sender.sendMessage("§e[MojangTexturesInjector] 正在重新从 Mojang 获取 " + target.getName() + " 的皮肤与披风...");
            applyTextures(target, true);
            return true;
        }

        sender.sendMessage("§c未知子命令，请输入 /" + label + " 查看帮助。");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> list = new ArrayList<>();
            if ("refresh".startsWith(args[0].toLowerCase())) list.add("refresh");
            if (sender.hasPermission("mojangtextures.admin") && "reload".startsWith(args[0].toLowerCase())) list.add("reload");
            return list;
        }
        if (args.length == 2 && "refresh".equalsIgnoreCase(args[0]) && sender.hasPermission("mojangtextures.admin")) {
            List<String> list = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                    list.add(p.getName());
                }
            }
            return list;
        }
        return List.of();
    }
}
