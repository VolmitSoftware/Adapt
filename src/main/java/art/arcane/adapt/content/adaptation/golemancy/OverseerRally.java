package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.api.golem.overseer.OverseerSignal;
import art.arcane.adapt.content.skill.golemancy.GolemXpCooldowns;

import java.util.Objects;
import java.util.UUID;

final class OverseerRally implements OverseerSignal.Rally {
  private final GolemXpCooldowns<UUID> cooldowns;
  private final UUID owner;
  private final long nowMillis;
  private final long cooldownMillis;
  private Boolean granted;

  OverseerRally(GolemXpCooldowns<UUID> cooldowns, UUID owner, long nowMillis, long cooldownMillis) {
    this.cooldowns = Objects.requireNonNull(cooldowns);
    this.owner = Objects.requireNonNull(owner);
    this.nowMillis = nowMillis;
    this.cooldownMillis = cooldownMillis;
  }

  @Override
  public synchronized boolean claim() {
    if (granted == null) {
      granted = cooldowns.tryClaim(owner, nowMillis, cooldownMillis);
    }
    return granted;
  }
}
