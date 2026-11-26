package art.arcane.adapt.content.adaptation.nether;

import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.api.support.PlatformNeed;
import art.arcane.adapt.client.protocol.HudGlyph;
import art.arcane.adapt.client.server.hud.AbilityGlyphs;
import art.arcane.adapt.platform.entity.AttributeOperation;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;

/**
 * Soul Strider's hold on soul sand and soul soil: movement efficiency that cancels the slowdown, plus an optional speed
 * bonus, both kept alive while the player keeps walking there and kept in the owner's storage.
 */
public final class NetherSoulStride {
  public static final PlatformNeed NEED = PlatformNeed.attribute(Attributes.MOVEMENT_EFFICIENCY);
  private static final String SLOT_SOUL = "soul";
  private static final String SLOT_STRIDE = "stride";
  private static final String HOLD_UNTIL = "soulStriderHoldUntil";
  private static final long HOLD_TICKS = 40L;
  private static final long HOLD_REFRESH_MILLIS = 1000L;
  private static final Id SOUL_SAND = Id.minecraft("soul_sand");
  private static final Id SOUL_SOIL = Id.minecraft("soul_soil");

  private NetherSoulStride() {
  }

  public static boolean onSoul(Loc to) {
    return isSoul(to.world().blockAt(to).type()) || isSoul(to.world().blockAt(to.add(0D, -1D, 0D)).type());
  }

  /** Renews the hold when it is close to running out; returns whether the player had no hold before. */
  public static boolean refresh(Adaptation<?> owner, PlayerRef p, boolean immunity, double speedBonus, long now) {
    long holdUntil = owner.getStorageLong(p, HOLD_UNTIL, 0L);
    if (holdUntil - now > HOLD_REFRESH_MILLIS) {
      return false;
    }

    owner.setStorage(p, HOLD_UNTIL, now + (HOLD_TICKS * 50L));
    AbilityGlyphs.duration(p.uuid(), owner, now, HOLD_TICKS * 50L);
    AdaptAttributeService attributes = AdaptAttributeService.get();
    if (immunity) {
      attributes.applyTimed(p, owner.getName(), SLOT_SOUL, Attributes.MOVEMENT_EFFICIENCY, 1.0D, AttributeOperation.ADD_VALUE, HOLD_TICKS);
    }
    if (speedBonus > 0D) {
      attributes.applyTimed(p, owner.getName(), SLOT_STRIDE, Attributes.MOVEMENT_SPEED, speedBonus, AttributeOperation.ADD_MULTIPLIED_TOTAL, HOLD_TICKS);
    }
    return holdUntil == 0L;
  }

  /** Drops the hold; returns whether there was one. */
  public static boolean release(Adaptation<?> owner, PlayerRef p) {
    if (owner.getStorageLong(p, HOLD_UNTIL, 0L) == 0L) {
      return false;
    }

    owner.setStorage(p, HOLD_UNTIL, 0L);
    AbilityGlyphs.clear(p.uuid(), owner, HudGlyph.DURATION);
    AdaptAttributeService attributes = AdaptAttributeService.get();
    attributes.remove(p, owner.getName(), SLOT_SOUL, Attributes.MOVEMENT_EFFICIENCY);
    attributes.remove(p, owner.getName(), SLOT_STRIDE, Attributes.MOVEMENT_SPEED);
    return true;
  }

  private static boolean isSoul(Id type) {
    return type.equals(SOUL_SAND) || type.equals(SOUL_SOIL);
  }
}
