package eu.endercentral.crazy_advancements.events;

import eu.endercentral.crazy_advancements.Advancement;
import eu.endercentral.crazy_advancements.manager.AdvancementManager;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class AdvancementRevokeEvent extends Event {

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

    public AdvancementRevokeEvent(AdvancementManager advancementManager_v2, Advancement advancement, Player player) {
        this.manager = advancementManager_v2;
        this.advancement = advancement;
        this.player = player;
    }

    /**
     * @return The Manager this event has been fired from
     */
    public AdvancementManager getManager() {
        return manager;
    }

    /**
     * @return The Advancement that has been revoked
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


}