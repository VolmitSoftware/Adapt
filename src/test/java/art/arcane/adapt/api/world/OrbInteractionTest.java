package art.arcane.adapt.api.world;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.content.item.ExperienceOrb;
import art.arcane.adapt.content.item.KnowledgeOrb;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrbInteractionTest extends AdaptTestBase {
  private AdaptServer server;
  private Player player;
  private ExperienceOrb previousExperience;
  private KnowledgeOrb previousKnowledge;
  private ExperienceOrb.Data data;
  private MockedStatic<ExperienceOrb> experience;
  private MockedStatic<KnowledgeOrb> knowledge;
  private final Map<EquipmentSlot, ItemStack> held = new EnumMap<>(EquipmentSlot.class);

  @BeforeEach
  void setup() throws Exception {
    server = mock(AdaptServer.class, CALLS_REAL_METHODS);
    Field ticks = AdaptServer.class.getDeclaredField("orbUseTicks");
    ticks.setAccessible(true);
    ticks.set(server, new ConcurrentHashMap<UUID, Integer>());
    player = mock(Player.class);
    when(player.getUniqueId()).thenReturn(UUID.randomUUID());
    when(player.getTicksLived()).thenReturn(100);
    PlayerInventory inventory = mock(PlayerInventory.class);
    when(player.getInventory()).thenReturn(inventory);
    when(inventory.getItem(any(EquipmentSlot.class))).thenAnswer(call -> held.get(call.getArgument(0)));
    doAnswer(call -> {
      held.put(call.getArgument(0), call.getArgument(1));
      return null;
    }).when(inventory).setItem(any(EquipmentSlot.class), any());
    doReturn(null).when(server).getOnlineAdaptPlayer(any());
    previousExperience = ExperienceOrb.io;
    previousKnowledge = KnowledgeOrb.io;
    ExperienceOrb.io = mock(ExperienceOrb.class);
    KnowledgeOrb.io = mock(KnowledgeOrb.class);
    experience = mockStatic(ExperienceOrb.class);
    knowledge = mockStatic(KnowledgeOrb.class);
    data = mock(ExperienceOrb.Data.class);
    when(data.apply(player)).thenReturn(true);
  }

  @AfterEach
  void restore() {
    experience.close();
    knowledge.close();
    ExperienceOrb.io = previousExperience;
    KnowledgeOrb.io = previousKnowledge;
  }

  @Test
  void bottleUseConsumesOnceAndSuppressesVanillaAndSecondHand() {
    ItemStack main = orb(Material.EXPERIENCE_BOTTLE, 2);
    ItemStack off = orb(Material.PLAYER_HEAD, 1);
    held.put(EquipmentSlot.HAND, main);
    held.put(EquipmentSlot.OFF_HAND, off);
    PlayerInteractEvent first = interaction(EquipmentSlot.HAND, Action.RIGHT_CLICK_BLOCK);
    PlayerInteractEvent second = interaction(EquipmentSlot.OFF_HAND, Action.RIGHT_CLICK_BLOCK);

    server.on(first);
    server.on(second);

    verify(data, times(1)).apply(player);
    assertThat(held.get(EquipmentSlot.HAND).getAmount()).isEqualTo(1);
    assertThat(held.get(EquipmentSlot.OFF_HAND)).isSameAs(off);
    assertThat(first.useInteractedBlock()).isEqualTo(Event.Result.DENY);
    assertThat(first.useItemInHand()).isEqualTo(Event.Result.DENY);
    assertThat(second.useItemInHand()).isEqualTo(Event.Result.DENY);
  }

  @Test
  void offhandHeadConsumesInCreative() {
    when(player.getGameMode()).thenReturn(GameMode.CREATIVE);
    held.put(EquipmentSlot.OFF_HAND, orb(Material.PLAYER_HEAD, 1));

    server.on(interaction(EquipmentSlot.OFF_HAND, Action.RIGHT_CLICK_AIR));

    verify(data).apply(player);
    assertThat(held.get(EquipmentSlot.OFF_HAND)).isNull();
  }

  @Test
  void unavailableRuntimeRestoresTheUnspentOrb() {
    ItemStack original = orb(Material.SNOWBALL, 3);
    held.put(EquipmentSlot.HAND, original);
    when(data.apply(player)).thenReturn(false);

    server.on(interaction(EquipmentSlot.HAND, Action.RIGHT_CLICK_AIR));

    assertThat(held.get(EquipmentSlot.HAND)).isSameAs(original);
    assertThat(original.getAmount()).isEqualTo(3);
  }

  @Test
  void interactionDeniedByAnotherPluginDoesNotAwardOrConsume() {
    ItemStack original = orb(Material.PLAYER_HEAD, 1);
    held.put(EquipmentSlot.HAND, original);
    PlayerInteractEvent event = interaction(EquipmentSlot.HAND, Action.RIGHT_CLICK_BLOCK);
    event.setCancelled(true);

    server.on(event);

    verify(data, never()).apply(player);
    assertThat(held.get(EquipmentSlot.HAND)).isSameAs(original);
  }

  @Test
  void ordinaryBottleKeepsVanillaBehavior() {
    held.put(EquipmentSlot.HAND, stack(Material.EXPERIENCE_BOTTLE, 1));
    PlayerInteractEvent event = interaction(EquipmentSlot.HAND, Action.RIGHT_CLICK_AIR);

    server.on(event);

    assertThat(event.useItemInHand()).isNotEqualTo(Event.Result.DENY);
    verify(data, never()).apply(player);
  }

  @Test
  void entityInteractionCancelsEquippingAndDuplicateAirUse() {
    held.put(EquipmentSlot.HAND, orb(Material.PLAYER_HEAD, 2));
    PlayerInteractEntityEvent event = new PlayerInteractEntityEvent(player, mock(Entity.class), EquipmentSlot.HAND);

    server.on(event);
    server.on(interaction(EquipmentSlot.HAND, Action.RIGHT_CLICK_AIR));

    assertThat(event.isCancelled()).isTrue();
    verify(data, times(1)).apply(player);
    assertThat(held.get(EquipmentSlot.HAND).getAmount()).isEqualTo(1);
  }

  @Test
  void knowledgeOrbUsesTheSameConsumptionPath() {
    ItemStack item = stack(Material.PLAYER_HEAD, 1);
    KnowledgeOrb.Data reward = mock(KnowledgeOrb.Data.class);
    when(KnowledgeOrb.io.hasData(item)).thenReturn(true);
    knowledge.when(() -> KnowledgeOrb.get(item)).thenReturn(reward);
    when(reward.apply(player)).thenReturn(true);
    held.put(EquipmentSlot.HAND, item);

    server.on(interaction(EquipmentSlot.HAND, Action.RIGHT_CLICK_AIR));

    verify(reward).apply(player);
    verify(data, never()).apply(player);
    assertThat(held.get(EquipmentSlot.HAND)).isNull();
  }

  @Test
  void aLaterTickCanConsumeTheNextOrb() {
    ItemStack item = orb(Material.SNOWBALL, 2);
    held.put(EquipmentSlot.HAND, item);
    server.on(interaction(EquipmentSlot.HAND, Action.RIGHT_CLICK_AIR));
    ItemStack remaining = held.get(EquipmentSlot.HAND);
    when(ExperienceOrb.io.hasData(remaining)).thenReturn(true);
    experience.when(() -> ExperienceOrb.get(remaining)).thenReturn(data);
    when(player.getTicksLived()).thenReturn(101);

    server.on(interaction(EquipmentSlot.HAND, Action.RIGHT_CLICK_AIR));

    verify(data, times(2)).apply(player);
    assertThat(held.get(EquipmentSlot.HAND)).isNull();
  }

  private ItemStack orb(Material material, int amount) {
    ItemStack item = stack(material, amount);
    when(ExperienceOrb.io.hasData(item)).thenReturn(true);
    experience.when(() -> ExperienceOrb.get(item)).thenReturn(data);
    return item;
  }

  private ItemStack stack(Material material, int count) {
    ItemStack item = mock(ItemStack.class);
    AtomicInteger amount = new AtomicInteger(count);
    when(item.getType()).thenReturn(material);
    when(item.getAmount()).thenAnswer(call -> amount.get());
    doAnswer(call -> {
      amount.set(call.getArgument(0));
      return null;
    }).when(item).setAmount(anyInt());
    when(item.clone()).thenAnswer(call -> stack(material, amount.get()));
    return item;
  }

  private PlayerInteractEvent interaction(EquipmentSlot hand, Action action) {
    return new PlayerInteractEvent(player, action, held.get(hand), null, null, hand);
  }
}
