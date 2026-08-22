package art.arcane.adapt.api.ability;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Objects;

public final class AdaptAbilityActivatedEvent extends Event {
  private static final HandlerList HANDLERS = new HandlerList();

  private final AbilityContext context;
  private final AbilityCostContext cost;
  private final AbilityCharge charge;

  public AdaptAbilityActivatedEvent(AbilityContext context, AbilityCostContext cost, AbilityCharge charge) {
    this.context = Objects.requireNonNull(context, "context");
    this.cost = Objects.requireNonNull(cost, "cost");
    this.charge = Objects.requireNonNull(charge, "charge");
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

  public AbilityCharge getCharge() {
    return charge;
  }

  @Override
  public HandlerList getHandlers() {
    return HANDLERS;
  }
}
