package art.arcane.adapt.engine.adaptation;

import art.arcane.adapt.content.adaptation.rift.RiftEnderTaglock;
import art.arcane.adapt.content.item.BoundEyeOfEnder;
import art.arcane.adapt.content.item.BoundRedstoneTorch;
import art.arcane.adapt.content.item.ChronoTimeBombItem;
import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the isolation contract: an Adapt item on cooldown must gray out and
 * block only itself, never the vanilla item it is built from.
 */
class ItemCooldownWiringTest {
  private static final List<Id> DECLARED_GROUPS = List.of(
      BoundEyeOfEnder.ITEM.cooldownGroup().orElseThrow(),
      BoundRedstoneTorch.ITEM.cooldownGroup().orElseThrow(),
      ChronoTimeBombItem.ITEM.cooldownGroup().orElseThrow(),
      RiftEnderTaglock.COOLDOWN_GROUP
  );

  @Test
  void everyCustomItemCooldownGroupLivesInTheAdaptNamespaceNotMinecraft() {
    for (Id group : DECLARED_GROUPS) {
      assertThat(group.namespace())
          .as(group + " must not collide with a vanilla item cooldown group")
          .isEqualTo(Id.ADAPT)
          .isNotEqualTo(Id.MINECRAFT);
    }
  }

  @Test
  void noTwoCustomItemsShareACooldownGroup() {
    Set<Id> unique = new LinkedHashSet<>(DECLARED_GROUPS);

    assertThat(unique).hasSameSizeAs(DECLARED_GROUPS);
  }

  @Test
  void theTaglockGroupIsNotThePlainEnderPearlGroup() {
    assertThat(RiftEnderTaglock.COOLDOWN_GROUP.toString()).isEqualTo("adapt:item_rift_ender_taglock");
    assertThat(RiftEnderTaglock.COOLDOWN_GROUP).isNotEqualTo(Id.minecraft("ender_pearl"));
  }

  @Test
  void theBoundEyeGroupIsNotThePlainEnderEyeGroup() {
    assertThat(BoundEyeOfEnder.ITEM.cooldownGroup()).isNotEqualTo(Optional.of(Id.minecraft("ender_eye")));
  }

  @Test
  void theBoundTorchGroupIsNotThePlainRedstoneTorchGroup() {
    assertThat(BoundRedstoneTorch.ITEM.cooldownGroup()).isNotEqualTo(Optional.of(Id.minecraft("redstone_torch")));
  }
}
