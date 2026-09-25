package com.server.textures;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.lenis0012.bukkit.loginsecurity.events.AuthActionEvent;
import com.lenis0012.bukkit.loginsecurity.session.AuthActionType;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class TexturesEventListener implements Listener {
    private final MojangTexturesInjector plugin;

    public TexturesEventListener(MojangTexturesInjector plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String name = player.getName();

        // Preload/prefetch profile in background asynchronously
        plugin.preloadProfile(name);

        if (plugin.isInjectOnJoin() || !plugin.hasLoginSecurity()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    plugin.applyTextures(player, false);
                }
            }, 10L); // 10 ticks delay to let initial join handshake finish
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLoginSecurityAuth(AuthActionEvent event) {
        AuthActionType type = event.getType();
        if (type == AuthActionType.LOGIN || type == AuthActionType.REGISTER || type == AuthActionType.BYPASS) {
            Player player = event.getPlayer();
            if (player != null && player.isOnline()) {
                // Run next tick so LoginSecurity session completes
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        plugin.applyTextures(player, false);
                    }
                });
            }
        }
    }
}
