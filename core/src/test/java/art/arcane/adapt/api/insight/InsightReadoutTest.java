package art.arcane.adapt.api.insight;

import art.arcane.adapt.api.attribute.Attributes;
import art.arcane.adapt.client.view.InsightBond;
import art.arcane.adapt.client.view.InsightEffect;
import art.arcane.adapt.client.view.InsightKind;
import art.arcane.adapt.client.view.InsightMark;
import art.arcane.adapt.client.view.InsightStat;
import art.arcane.adapt.client.view.InsightStatKind;
import art.arcane.adapt.client.view.InsightView;
import art.arcane.adapt.platform.entity.EffectInstance;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InsightReadoutTest {
  private static final UUID VIEWER = new UUID(0L, 1L);
  private static final UUID OTHER = new UUID(0L, 2L);
  private static final Id POISON = Id.minecraft("poison");
  private static final Id SPEED = Id.minecraft("speed");
  private static final Id HIDDEN = Id.minecraft("glowing");

  @Test
  void tiersFollowTheLevelRangeAndTheFullDetailsPreference() {
    assertThat(InsightTier.of(0.2D, true)).isEqualTo(InsightTier.BASE);
    assertThat(InsightTier.of(0.4D, true)).isEqualTo(new InsightTier(true, false, false, false));
    assertThat(InsightTier.of(0.6D, true)).isEqualTo(new InsightTier(true, true, false, false));
    assertThat(InsightTier.of(0.8D, true)).isEqualTo(new InsightTier(true, true, true, false));
    assertThat(InsightTier.of(1D, true)).isEqualTo(new InsightTier(true, true, true, true));
    assertThat(InsightTier.of(1D, false)).isEqualTo(InsightTier.BASE);
    assertThat(InsightTier.of(0D, true)).isEqualTo(InsightTier.BASE);
    assertThat(InsightTier.of(Double.NaN, true)).isEqualTo(InsightTier.BASE);
  }

  @Test
  void levelOneRevealsHealthArmorAttackAndOwnershipOnly() {
    LivingRef wolf = wolf(VIEWER);

    InsightView view = read(wolf, InsightTier.of(0.2D, true));

    assertThat(view.entityId()).isEqualTo(17);
    assertThat(view.typeId()).isEqualTo("minecraft:wolf");
    assertThat(view.kind()).isEqualTo(InsightKind.PASSIVE);
    assertThat(view.health()).isEqualTo(12D);
    assertThat(view.maxHealth()).isEqualTo(20D);
    assertThat(view.armor()).isEqualTo(3D);
    assertThat(view.bond()).isEqualTo(InsightBond.YOURS);
    assertThat(view.stats()).containsExactly(new InsightStat(InsightStatKind.ATTACK, 4D));
    assertThat(view.effects()).isEmpty();
  }

  @Test
  void maxLevelAddsEffectsMovementAndDefensesInAStableOrder() {
    InsightView view = read(wolf(VIEWER), InsightTier.of(1D, true));

    assertThat(view.stats()).extracting(InsightStat::kind).containsExactly(InsightStatKind.ATTACK,
        InsightStatKind.SPEED, InsightStatKind.TOUGHNESS, InsightStatKind.KNOCKBACK_RESISTANCE,
        InsightStatKind.FOLLOW_RANGE);
    assertThat(view.effects()).containsExactly(new InsightEffect("minecraft:poison", 0, 100, true),
        new InsightEffect("minecraft:speed", 1, InsightEffect.INFINITE, false));
  }

  @Test
  void ownershipNamesAnotherOwnerAndMarksWildTameables() {
    LivingRef owned = wolf(OTHER);
    assertThat(read(owned, InsightTier.BASE).bond()).isEqualTo(InsightBond.OWNED);
    assertThat(read(owned, InsightTier.BASE).ownerName()).isEqualTo("Alex");

    LivingRef wild = wolf(null);
    assertThat(read(wild, InsightTier.BASE).bond()).isEqualTo(InsightBond.WILD);
    assertThat(read(wild, InsightTier.BASE).ownerName()).isEmpty();

    LivingRef zombie = mock(LivingRef.class);
    when(zombie.type()).thenReturn(Id.minecraft("zombie"));
    when(zombie.has(EntityTrait.ENEMY)).thenReturn(true);
    InsightView view = read(zombie, InsightTier.of(1D, true));
    assertThat(view.bond()).isEqualTo(InsightBond.NONE);
    assertThat(view.kind()).isEqualTo(InsightKind.HOSTILE);
    assertThat(view.name()).isEmpty();
    assertThat(view.stats()).isEmpty();
  }

  @Test
  void playersAreNamedAndMarksCarryAHealthFraction() {
    PlayerRef player = mock(PlayerRef.class);
    when(player.type()).thenReturn(Id.minecraft("player"));
    when(player.name()).thenReturn("Notch");
    when(player.entityId()).thenReturn(9);
    when(player.health()).thenReturn(5D);
    when(player.maxHealth()).thenReturn(20D);

    assertThat(read(player, InsightTier.BASE).name()).isEqualTo("Notch");
    assertThat(read(player, InsightTier.BASE).kind()).isEqualTo(InsightKind.PLAYER);
    assertThat(InsightReadout.mark(player)).isEqualTo(new InsightMark(9, InsightKind.PLAYER, 0.25D));
  }

  private static InsightView read(LivingRef target, InsightTier tier) {
    return InsightReadout.read(target, VIEWER, tier, false, Map.of(OTHER, "Alex")::get, (Id effect) -> effect.equals(POISON));
  }

  private static LivingRef wolf(UUID owner) {
    LivingRef wolf = mock(LivingRef.class);
    when(wolf.entityId()).thenReturn(17);
    when(wolf.type()).thenReturn(Id.minecraft("wolf"));
    when(wolf.has(EntityTrait.TAMEABLE)).thenReturn(true);
    when(wolf.tameOwner()).thenReturn(owner);
    when(wolf.health()).thenReturn(12D);
    when(wolf.maxHealth()).thenReturn(20D);
    when(wolf.hasAttribute(any(Id.class))).thenAnswer(call -> !call.getArgument(0).equals(Attributes.JUMP_STRENGTH));
    when(wolf.attributeValue(Attributes.ARMOR)).thenReturn(3D);
    when(wolf.attributeValue(Attributes.ATTACK_DAMAGE)).thenReturn(4D);
    when(wolf.attributeValue(Attributes.MOVEMENT_SPEED)).thenReturn(0.3D);
    when(wolf.attributeValue(Attributes.ARMOR_TOUGHNESS)).thenReturn(0D);
    when(wolf.attributeValue(Attributes.KNOCKBACK_RESISTANCE)).thenReturn(0D);
    when(wolf.attributeValue(Attributes.FOLLOW_RANGE)).thenReturn(16D);
    when(wolf.effects()).thenReturn(List.of(new EffectInstance(SPEED, -1, 1, false, true, true),
        EffectInstance.of(POISON, 100, 0), new EffectInstance(HIDDEN, 100, 0, false, false, false)));
    return wolf;
  }
}
