package pers.yufiria.landguard.claim;

import crypticlib.listener.EventListener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * 玩家进出服会话：确保玩家数据存在、发放初始额度、刷新最后登录。
 */
@EventListener
public enum PlayerSessionListener implements Listener {

    INSTANCE;

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        ClaimService.INSTANCE.ensurePlayer(event.getPlayer().getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        ClaimService.INSTANCE.ensurePlayer(event.getPlayer().getUniqueId(), System.currentTimeMillis());
    }

}
