package art.arcane.adapt.content.adaptation.discovery;

import art.arcane.adapt.api.skill.Skill;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DiscoveryRelicAppraiserTest {
  @Test
  void identifiesEveryPlaceableRelicCategoryWithoutTreatingOtherRelicsAsBlocks() {
    assertThat(DiscoveryRelicAppraiser.isAppraisableBlock(Material.PLAYER_HEAD)).isTrue();
    assertThat(DiscoveryRelicAppraiser.isAppraisableBlock(Material.CREEPER_HEAD)).isTrue();
    assertThat(DiscoveryRelicAppraiser.isAppraisableBlock(Material.SKELETON_SKULL)).isTrue();
    assertThat(DiscoveryRelicAppraiser.isAppraisableBlock(Material.PLAYER_WALL_HEAD)).isTrue();
    assertThat(DiscoveryRelicAppraiser.isAppraisableBlock(Material.MUSIC_DISC_13)).isFalse();
    assertThat(DiscoveryRelicAppraiser.isAppraisableBlock(Material.WAYFINDER_ARMOR_TRIM_SMITHING_TEMPLATE)).isFalse();
    assertThat(DiscoveryRelicAppraiser.isAppraisableBlock(Material.ANGLER_POTTERY_SHERD)).isFalse();
  }

  @Test
  void restoresTheSerializedRelicOnlyOntoItsMatchingCommittedDrop() {
    ItemStack snapshot = mock(ItemStack.class);
    ItemStack unrelatedStack = mock(ItemStack.class);
    ItemStack matchingStack = mock(ItemStack.class);
    Item unrelated = mock(Item.class);
    Item matching = mock(Item.class);
    when(snapshot.getType()).thenReturn(Material.PLAYER_HEAD);
    when(unrelatedStack.getType()).thenReturn(Material.STONE);
    when(matchingStack.getType()).thenReturn(Material.PLAYER_HEAD);
    when(matchingStack.getAmount()).thenReturn(1);
    when(unrelated.getItemStack()).thenReturn(unrelatedStack);
    when(matching.getItemStack()).thenReturn(matchingStack);

    assertThat(DiscoveryRelicAppraiser.restoreAppraisedDrop(List.of(unrelated, matching), snapshot)).isTrue();

    verify(snapshot).setAmount(1);
    verify(matching).setItemStack(snapshot);
  }

  @Test
  void doesNotCreateAReplacementWhenVanillaProducedNoMatchingDrop() {
    ItemStack snapshot = mock(ItemStack.class);
    ItemStack droppedStack = mock(ItemStack.class);
    Item dropped = mock(Item.class);
    when(snapshot.getType()).thenReturn(Material.PLAYER_HEAD);
    when(droppedStack.getType()).thenReturn(Material.STONE);
    when(dropped.getItemStack()).thenReturn(droppedStack);

    assertThat(DiscoveryRelicAppraiser.restoreAppraisedDrop(List.of(dropped), snapshot)).isFalse();

    verify(dropped, never()).setItemStack(snapshot);
  }

  @Test
  void randomSkillPoolExcludesDiscoveryDisabledAndDeniedBranches() {
    Player player = mock(Player.class);
    Skill<?> source = mock(Skill.class);
    Skill<?> eligible = mock(Skill.class);
    Skill<?> disabled = mock(Skill.class);
    Skill<?> denied = mock(Skill.class);
    when(eligible.isEnabled()).thenReturn(true);
    when(eligible.hasUsePermission(player, eligible)).thenReturn(true);
    when(disabled.isEnabled()).thenReturn(false);
    when(denied.isEnabled()).thenReturn(true);
    when(denied.hasUsePermission(player, denied)).thenReturn(false);

    assertThat(DiscoveryRelicAppraiser.eligibleRandomSkills(
        List.of(source, eligible, disabled, denied),
        player,
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
    EventHandler placeHandler = place.getAnnotation(EventHandler.class);
    EventHandler dropHandler = drop.getAnnotation(EventHandler.class);

    assertThat(placeHandler.ignoreCancelled()).isTrue();
    assertThat(placeHandler.priority()).isEqualTo(EventPriority.MONITOR);
    assertThat(dropHandler.ignoreCancelled()).isTrue();
    assertThat(dropHandler.priority()).isEqualTo(EventPriority.LOWEST);
  }
}
