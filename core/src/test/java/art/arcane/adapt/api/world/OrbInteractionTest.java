package art.arcane.adapt.api.world;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.item.DataBoundItem;
import art.arcane.adapt.api.notification.Notifier;
import art.arcane.adapt.content.item.ExperienceOrb;
import art.arcane.adapt.content.item.KnowledgeOrb;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.GameMode;
import art.arcane.adapt.platform.entity.Hand;
import art.arcane.adapt.platform.entity.PlayerInventoryRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerInteractEntityEvent;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.event.player.UseResult;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.testkit.FakeItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrbInteractionTest extends AdaptTestBase {
  private AdaptServer server;
  private PlayerRef player;
  private AdaptPlayer adaptPlayer;
  private PlayerSkillLine skillLine;
  private final Map<Hand, ItemRef> held = new EnumMap<>(Hand.class);

  @BeforeEach
  void setup() throws Exception {
    server = mock(AdaptServer.class, CALLS_REAL_METHODS);
    Field ticks = AdaptServer.class.getDeclaredField("orbUseTicks");
    ticks.setAccessible(true);
    ticks.set(server, new ConcurrentHashMap<UUID, Integer>());
    player = mock(PlayerRef.class);
    when(player.uuid()).thenReturn(UUID.randomUUID());
    when(player.ticksLived()).thenReturn(100);
    PlayerInventoryRef inventory = mock(PlayerInventoryRef.class);
    when(player.inventory()).thenReturn(inventory);
    when(inventory.hand(any(Hand.class))).thenAnswer(call -> held.get(call.<Hand>getArgument(0)));
    doAnswer(call -> {
      Hand hand = call.getArgument(0);
      ItemRef item = call.getArgument(1);
      if (item == null) {
        held.remove(hand);
      } else {
        held.put(hand, item);
      }
      return null;
    }).when(inventory).setHand(any(Hand.class), any());
    doReturn(null).when(server).getOnlineAdaptPlayer(any());
    AdaptServer runtime = mock(AdaptServer.class);
    adaptPlayer = mock(AdaptPlayer.class);
    skillLine = mock(PlayerSkillLine.class);
    when(core.getAdaptServer()).thenReturn(runtime);
    when(runtime.getOnlineAdaptPlayer(player.uuid())).thenReturn(adaptPlayer);
    when(adaptPlayer.isRuntimeReady()).thenReturn(true);
    when(adaptPlayer.getPlayer()).thenReturn(player);
    when(adaptPlayer.getSkillLine("herbalism")).thenReturn(skillLine);
    when(adaptPlayer.getNot()).thenReturn(mock(Notifier.class));
    when(adaptPlayer.getData()).thenReturn(new PlayerData());
  }

  @Test
  void bottleUseConsumesOnceAndSuppressesVanillaAndSecondHand() {
    ItemRef main = orb("experience_bottle", 2);
    ItemRef off = orb("player_head", 1);
    held.put(Hand.MAIN_HAND, main);
    held.put(Hand.OFF_HAND, off);
    PlayerInteractEvent first = interaction(Hand.MAIN_HAND, InteractAction.RIGHT_CLICK_BLOCK);
    PlayerInteractEvent second = interaction(Hand.OFF_HAND, InteractAction.RIGHT_CLICK_BLOCK);

    server.on(first);
    server.on(second);

    verifyAwards(1);
    assertThat(held.get(Hand.MAIN_HAND).amount()).isEqualTo(1);
    assertThat(held.get(Hand.OFF_HAND)).isSameAs(off);
    assertThat(first.useBlock()).isEqualTo(UseResult.DENY);
    assertThat(first.useItem()).isEqualTo(UseResult.DENY);
    assertThat(second.useItem()).isEqualTo(UseResult.DENY);
  }

  @Test
  void offhandHeadConsumesInCreative() {
    when(player.gameMode()).thenReturn(GameMode.CREATIVE);
    held.put(Hand.OFF_HAND, orb("player_head", 1));

    server.on(interaction(Hand.OFF_HAND, InteractAction.RIGHT_CLICK_AIR));

    verifyAwards(1);
    assertThat(held.get(Hand.OFF_HAND)).isNull();
  }

  @Test
  void unavailableRuntimeRestoresTheUnspentOrb() {
    ItemRef original = orb("snowball", 3);
    held.put(Hand.MAIN_HAND, original);
    when(adaptPlayer.isRuntimeReady()).thenReturn(false);

    server.on(interaction(Hand.MAIN_HAND, InteractAction.RIGHT_CLICK_AIR));

    assertThat(held.get(Hand.MAIN_HAND)).isSameAs(original);
    assertThat(original.amount()).isEqualTo(3);
  }

  @Test
  void interactionDeniedByAnotherPluginDoesNotAwardOrConsume() {
    ItemRef original = orb("player_head", 1);
    held.put(Hand.MAIN_HAND, original);
    PlayerInteractEvent event = interaction(Hand.MAIN_HAND, InteractAction.RIGHT_CLICK_BLOCK);
    event.setCancelled(true);

    server.on(event);

    verifyAwards(0);
    assertThat(held.get(Hand.MAIN_HAND)).isSameAs(original);
  }

  @Test
  void ordinaryBottleKeepsVanillaBehavior() {
    held.put(Hand.MAIN_HAND, stack("experience_bottle", 1));
    PlayerInteractEvent event = interaction(Hand.MAIN_HAND, InteractAction.RIGHT_CLICK_AIR);

    server.on(event);

    assertThat(event.useItem()).isNotEqualTo(UseResult.DENY);
    verifyAwards(0);
  }

  @Test
  void entityInteractionCancelsEquippingAndDuplicateAirUse() {
    held.put(Hand.MAIN_HAND, orb("player_head", 2));
    PlayerInteractEntityEvent event = new PlayerInteractEntityEvent(player, mock(EntityRef.class), Hand.MAIN_HAND, null);

    server.on(event);
    server.on(interaction(Hand.MAIN_HAND, InteractAction.RIGHT_CLICK_AIR));

    assertThat(event.isCancelled()).isTrue();
    verifyAwards(1);
    assertThat(held.get(Hand.MAIN_HAND).amount()).isEqualTo(1);
  }

  @Test
  void knowledgeOrbUsesTheSameConsumptionPath() {
    ItemRef item = stack("player_head", 1);
    item.data().set(DataBoundItem.dataKey(KnowledgeOrb.Data.class), "{\"knowledgeMap\":{\"herbalism\":3}}");
    held.put(Hand.MAIN_HAND, item);

    server.on(interaction(Hand.MAIN_HAND, InteractAction.RIGHT_CLICK_AIR));

    verify(skillLine).giveKnowledge(3);
    verifyAwards(0);
    assertThat(held.get(Hand.MAIN_HAND)).isNull();
  }

  @Test
  void aLaterTickCanConsumeTheNextOrb() {
    ItemRef item = orb("snowball", 2);
    held.put(Hand.MAIN_HAND, item);
    server.on(interaction(Hand.MAIN_HAND, InteractAction.RIGHT_CLICK_AIR));
    when(player.ticksLived()).thenReturn(101);

    server.on(interaction(Hand.MAIN_HAND, InteractAction.RIGHT_CLICK_AIR));

    verifyAwards(2);
    assertThat(held.get(Hand.MAIN_HAND)).isNull();
  }

  private ItemRef orb(String type, int amount) {
    ItemRef item = stack(type, amount);
    item.data().set(ExperienceOrb.io.dataKey(), "{\"experienceMap\":{\"herbalism\":5.0}}");
    return item;
  }

  private void verifyAwards(int count) {
    verify(skillLine, times(count)).award(any(), any(), anyDouble(), any());
  }

  private ItemRef stack(String type, int count) {
    return new FakeItem(Id.minecraft(type), count);
  }

  private PlayerInteractEvent interaction(Hand hand, InteractAction action) {
    return new PlayerInteractEvent(player, action, hand, held.get(hand), null, null, null, null, null);
  }
}
