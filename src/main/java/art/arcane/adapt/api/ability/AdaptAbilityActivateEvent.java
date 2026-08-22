package art.arcane.adapt.api.ability;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Objects;

public final class AdaptAbilityActivateEvent extends Event implements Cancellable {
  private static final HandlerList HANDLERS = new HandlerList();

  private final AbilityContext context;
  private final AbilityCostContext cost;
  private boolean cancelled;
  private String cancelReason = "";

  public AdaptAbilityActivateEvent(AbilityContext context, AbilityCostContext cost) {
    this.context = Objects.requireNonNull(context, "context");
    this.cost = Objects.requireNonNull(cost, "cost");
  }

  public static HandlerList getHandlerList() {
    return HANDLERS;
  }

  public AbilityContext getContext() {
    return context;
  }

  public AbilityCostContext getCost() {
    return cost;
  }

  public String getCancelReason() {
    return cancelReason;
  }

  public void setCancelReason(String cancelReason) {
    this.cancelReason = AbilityText.sanitize(cancelReason);
  }

  @Override
  public boolean isCancelled() {
    return cancelled;
  }

  @Override
  public void setCancelled(boolean cancel) {
    cancelled = cancel;
  }

  @Override
  public HandlerList getHandlers() {
    return HANDLERS;
  }
}
