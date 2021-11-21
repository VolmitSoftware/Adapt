package eu.endercentral.crazy_advancements.events;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class AdvancementScreenCloseEvent extends Event {

    public static final HandlerList handlers = new HandlerList();

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }


    private final Player player;

    public AdvancementScreenCloseEvent(Player player) {
        super(true);
        this.player = player;
    }

    /**
     * @return Player closing his advancement screen
     */
    public Player getPlayer() {
        return player;
    }

    /**
     * @return Information about this event
     */
    public String getInformationString() {
        return "tab_action=close;player=" + player.getName();
    }

}