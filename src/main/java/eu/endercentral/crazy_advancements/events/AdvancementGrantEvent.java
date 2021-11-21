package eu.endercentral.crazy_advancements.events;

import eu.endercentral.crazy_advancements.Advancement;
import eu.endercentral.crazy_advancements.manager.AdvancementManager;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class AdvancementGrantEvent extends Event {

    public static final HandlerList handlers = new HandlerList();

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }


    private final AdvancementManager manager;
    private final Advancement advancement;
    private final Player player;
    private boolean displayMessage;

    public AdvancementGrantEvent(AdvancementManager manager, Advancement advancement, Player player, boolean displayMessage) {
        this.manager = manager;
        this.advancement = advancement;
        this.player = player;
        this.displayMessage = displayMessage;
    }

    /**
     * @return The Manager this event has been fired from
     */
    public AdvancementManager getManager() {
        return manager;
    }

    /**
     * @return The Advancement that has been granted
     */
    public Advancement getAdvancement() {
        return advancement;
    }

    /**
     * @return Reciever
     */
    public Player getPlayer() {
        return player;
    }

    /**
     * @return true if a message will be displayed
     */
    public boolean isDisplayMessage() {
        return displayMessage;
    }

    /**
     * Sets if a message will be displayed
     */
    public void setDisplayMessage(boolean displayMessage) {
        this.displayMessage = displayMessage;
    }


}