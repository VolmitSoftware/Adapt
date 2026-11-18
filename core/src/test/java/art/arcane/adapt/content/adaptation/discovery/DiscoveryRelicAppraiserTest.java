package art.arcane.adapt.content.adaptation.discovery;

import art.arcane.adapt.api.golem.item.GolemItems;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.platform.entity.ItemEntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockDropItemEvent;
import art.arcane.adapt.platform.event.block.BlockPlaceEvent;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DiscoveryRelicAppraiserTest {
  @Test
  void identifiesEveryPlaceableRelicCategoryWithoutTreatingOtherRelicsAsBlocks() {
    assertThat(DiscoveryRelicAppraiser.isAppraisableBlock(Id.minecraft("player_head"))).isTrue();
    assertThat(DiscoveryRelicAppraiser.isAppraisableBlock(Id.minecraft("creeper_head"))).isTrue();
    assertThat(DiscoveryRelicAppraiser.isAppraisableBlock(Id.minecraft("skeleton_skull"))).isTrue();
    assertThat(DiscoveryRelicAppraiser.isAppraisableBlock(Id.minecraft("player_wall_head"))).isTrue();
    assertThat(DiscoveryRelicAppraiser.isAppraisableBlock(Id.minecraft("music_disc_13"))).isFalse();
    assertThat(DiscoveryRelicAppraiser.isAppraisableBlock(Id.minecraft("wayfinder_armor_trim_smithing_template"))).isFalse();
    assertThat(DiscoveryRelicAppraiser.isAppraisableBlock(Id.minecraft("angler_pottery_sherd"))).isFalse();
  }

  @Test
  void potterySherdsAreAppraisableUnlessTheyAreGolemancyItems() {
    ItemRef sherd = mock(ItemRef.class);
    ItemRef rune = mock(ItemRef.class);
    when(sherd.type()).thenReturn(Id.minecraft("guster_pottery_sherd"));
    when(rune.type()).thenReturn(Id.minecraft("guster_pottery_sherd"));

    try (MockedStatic<GolemItems> items = mockStatic(GolemItems.class)) {
      items.when(() -> GolemItems.isGolemItem(rune)).thenReturn(true);

      assertThat(DiscoveryRelicAppraiser.isAppraisable(sherd)).isTrue();
      assertThat(DiscoveryRelicAppraiser.isAppraisable(rune)).isFalse();
    }
  }

  @Test
  void restoresTheSerializedRelicOnlyOntoItsMatchingCommittedDrop() {
    ItemRef snapshot = mock(ItemRef.class);
    ItemRef unrelatedStack = mock(ItemRef.class);
    ItemRef matchingStack = mock(ItemRef.class);
    ItemEntityRef unrelated = mock(ItemEntityRef.class);
    ItemEntityRef matching = mock(ItemEntityRef.class);
    when(snapshot.type()).thenReturn(Id.minecraft("player_head"));
    when(unrelatedStack.type()).thenReturn(Id.minecraft("stone"));
    when(matchingStack.type()).thenReturn(Id.minecraft("player_head"));
    when(matchingStack.amount()).thenReturn(1);
    when(unrelated.item()).thenReturn(unrelatedStack);
    when(matching.item()).thenReturn(matchingStack);

    assertThat(DiscoveryRelicAppraiser.restoreAppraisedDrop(List.of(unrelated, matching), snapshot)).isTrue();

    verify(snapshot).setAmount(1);
    verify(matching).setItem(snapshot);
  }

  @Test
  void doesNotCreateAReplacementWhenVanillaProducedNoMatchingDrop() {
    ItemRef snapshot = mock(ItemRef.class);
    ItemRef droppedStack = mock(ItemRef.class);
    ItemEntityRef dropped = mock(ItemEntityRef.class);
    when(snapshot.type()).thenReturn(Id.minecraft("player_head"));
    when(droppedStack.type()).thenReturn(Id.minecraft("stone"));
    when(dropped.item()).thenReturn(droppedStack);

    assertThat(DiscoveryRelicAppraiser.restoreAppraisedDrop(List.of(dropped), snapshot)).isFalse();

    verify(dropped, never()).setItem(snapshot);
  }

  @Test
  void randomSkillPoolExcludesDiscoveryDisabledAndDeniedBranches() {
    PlayerRef playerRef = mock(PlayerRef.class);
    when(playerRef.uuid()).thenReturn(UUID.randomUUID());
    Skill<?> source = mock(Skill.class);
    Skill<?> eligible = mock(Skill.class);
    Skill<?> disabled = mock(Skill.class);
    Skill<?> denied = mock(Skill.class);
    when(eligible.isEnabled()).thenReturn(true);
    when(eligible.hasUsePermission(playerRef, eligible)).thenReturn(true);
    when(disabled.isEnabled()).thenReturn(false);
    when(denied.isEnabled()).thenReturn(true);
    when(denied.hasUsePermission(playerRef, denied)).thenReturn(false);

    assertThat(DiscoveryRelicAppraiser.eligibleRandomSkills(
        List.of(source, eligible, disabled, denied),
        playerRef,
        source
    )).containsExactly(eligible);
  }

  @Test
  void randomSkillXpAlwaysStaysInsideNormalizedNonNegativeBounds() {
    assertThat(DiscoveryRelicAppraiser.randomSkillXp(20D, 60D, 0D)).isEqualTo(20D);
    assertThat(DiscoveryRelicAppraiser.randomSkillXp(20D, 60D, 1D)).isEqualTo(60D);
    assertThat(DiscoveryRelicAppraiser.randomSkillXp(60D, 20D, 0.5D)).isEqualTo(40D);
    assertThat(DiscoveryRelicAppraiser.randomSkillXp(-20D, 60D, -1D)).isZero();
    assertThat(DiscoveryRelicAppraiser.randomSkillXp(-20D, 60D, 2D)).isEqualTo(60D);
    assertThat(DiscoveryRelicAppraiser.randomSkillXp(Double.NaN, Double.POSITIVE_INFINITY, 0.5D)).isZero();
    assertThat(DiscoveryRelicAppraiser.randomSkillXp(20_000D, 50_000D, 0.5D))
        .isEqualTo(DiscoveryRelicAppraiser.MAX_RANDOM_SKILL_XP);
    assertThat(DiscoveryRelicAppraiser.randomSkillXp(20_000D, 20D, 0.5D)).isEqualTo(5_010D);
  }

  @Test
  void blockRoundTripRunsBeforeDropRoutersAndOnlyAfterCommittedPlacement() throws Exception {
    Method place = DiscoveryRelicAppraiser.class.getDeclaredMethod("on", BlockPlaceEvent.class);
    Method drop = DiscoveryRelicAppraiser.class.getDeclaredMethod("on", BlockDropItemEvent.class);
    Handle placeHandler = place.getAnnotation(Handle.class);
    Handle dropHandler = drop.getAnnotation(Handle.class);

    assertThat(placeHandler.ignoreCancelled()).isTrue();
    assertThat(placeHandler.priority()).isEqualTo(EventPriority.MONITOR);
    assertThat(dropHandler.ignoreCancelled()).isTrue();
    assertThat(dropHandler.priority()).isEqualTo(EventPriority.LOWEST);
  }
}
