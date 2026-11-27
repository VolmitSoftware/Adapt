package art.arcane.adapt.content.adaptation.agility;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.hud.HudStatus;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Loc;

import java.util.Map;
import java.util.UUID;

/** The landing roll: a short swimming pose and the dust and thud of the landing. The prone map belongs to the owner. */
public final class AgilityRoll {
  private final Adaptation<?> owner;
  private final Map<UUID, Long> proneUntilMillis;

  public AgilityRoll(Adaptation<?> owner, Map<UUID, Long> proneUntilMillis) {
    this.owner = owner;
    this.proneUntilMillis = proneUntilMillis;
  }

  /** Plays the landing for {@code absorbed} prevented damage and holds the roll pose for {@code proneTicks}. */
  public void roll(PlayerRef p, int proneTicks, double absorbed) {
    pose(p, proneTicks);
    int dust = (int) Math.max(4, Math.min(12, 4 + Math.round(absorbed)));
    Loc location = p.location();
    AgilityRollLanding.rollFx(owner, location, dust);
  }

  private void pose(PlayerRef p, int proneTicks) {
    long now = System.currentTimeMillis();
    long until = now + (proneTicks * 50L);
    UUID id = p.uuid();
    proneUntilMillis.put(id, until);
    HudStatus.duration(id, owner, now, proneTicks * 50L);
    p.setSwimming(true);

    AgilityRollLanding.rollTrailFx(owner, p, proneTicks);

    AdaptApi.scheduler().runEntity(p, () -> {
      if (!p.isOnline() || p.isDead()) {
        proneUntilMillis.remove(id);
        return;
      }

      long expectedUntil = proneUntilMillis.getOrDefault(id, 0L);
      if (expectedUntil > System.currentTimeMillis()) {
        return;
      }

      proneUntilMillis.remove(id);
      if (!p.isInWater()) {
        p.setSwimming(false);
      }
    }, proneTicks);
  }
}
