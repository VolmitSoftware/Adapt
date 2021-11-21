package eu.endercentral.crazy_advancements.events;

import eu.endercentral.crazy_advancements.Advancement;
import eu.endercentral.crazy_advancements.manager.AdvancementManager;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class CriteriaGrantEvent extends Event {

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
    private final String[] criteria;
    private final Player player;

    public CriteriaGrantEvent(AdvancementManager manager, Advancement advancement, String[] criteria, Player player) {
        this.manager = manager;
        this.advancement = advancement;
        this.criteria = criteria;
        this.player = player;
    }

    /**
     * @return The Manager this event has been fired from
     */
    public AdvancementManager getManager() {
        return manager;
    }

    /**
     * @return Advancement
     */
    public Advancement getAdvancement() {
        return advancement;
    }

    /**
     * @return Granted Criteria
     */
    public String[] getCriteria() {
        return criteria;
    }

    /**
     * @return Reciever
     */
    public Player getPlayer() {
        return player;
    }


}