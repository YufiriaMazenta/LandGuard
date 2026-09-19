package pers.yufiria.landguard.api.event;

import org.bukkit.Bukkit;
import org.bukkit.event.Event;

public final class EventCaller {

    private EventCaller() {
    }

    public static <T extends Event> T call(T event) {
        Bukkit.getPluginManager().callEvent(event);
        return event;
    }

}
