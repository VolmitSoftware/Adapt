package art.arcane.adapt.bukkit.event;

import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.event.AdaptEventBus;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.player.EntityExhaustionEvent;
import art.arcane.adapt.platform.event.player.PlayerChatEvent;
import art.arcane.adapt.platform.event.player.PlayerTradeEvent;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlayerGapTranslationsTest {
  private final List<Throwable> failures = new ArrayList<>();
  private final AdaptEventBus bus = new AdaptEventBus((owner, event, error) -> failures.add(error));
  private GapRefs refs;
  private Player player;

  @BeforeEach
  void setUp() {
    refs = new GapRefs();
    player = mock(Player.class);
  }

  @AfterEach
  void tearDown() {
    refs.close();
    assertThat(failures).isEmpty();
  }

  @Test
  void exhaustionIsWrittenBackAndNonPlayersAreSkipped() {
    org.bukkit.event.entity.EntityExhaustionEvent event = new org.bukkit.event.entity.EntityExhaustionEvent(player,
        org.bukkit.event.entity.EntityExhaustionEvent.ExhaustionReason.SPRINT, 0.4F);
    AtomicReference<EntityExhaustionEvent> seen = new AtomicReference<>();
    bus.register(this, EntityExhaustionEvent.class, EventPriority.HIGH, core -> {
      seen.set(core);
      core.setExhaustion(0.1F);
    });

    assertThat(PlayerGapTranslations.EXHAUSTION.target(event)).isEqualTo(EntityExhaustionEvent.class);
    PlayerGapTranslations.EXHAUSTION.bridge(event, core -> bus.post(core, EventPriority.HIGH));

    assertThat(seen.get().reason()).isEqualTo(EntityExhaustionEvent.Reason.SPRINT);
    assertThat(event.getExhaustion()).isEqualTo(0.1F);
  }

  @Test
  void tradeIsPostedOnlyForVillagerTradesAndWritesBackFlags() {
    Villager villager = mock(Villager.class);
    MerchantRecipe recipe = mock(MerchantRecipe.class);
    when(recipe.getResult()).thenReturn(mock(ItemStack.class));
    io.papermc.paper.event.player.PlayerTradeEvent trade = new io.papermc.paper.event.player.PlayerTradeEvent(player, villager,
        recipe, true, true);
    io.papermc.paper.event.player.PlayerPurchaseEvent purchase = new io.papermc.paper.event.player.PlayerPurchaseEvent(player,
        recipe, true, true);
    AtomicReference<PlayerTradeEvent> seen = new AtomicReference<>();
    bus.register(this, PlayerTradeEvent.class, EventPriority.MONITOR, core -> {
      seen.set(core);
      core.setRewardExp(false);
    });

    assertThat(PlayerGapTranslations.TRADE.target(purchase)).isNull();
    PlayerGapTranslations.TRADE.bridge(trade, core -> bus.post(core, EventPriority.MONITOR));

    assertThat(seen.get().merchant()).isSameAs(refs.wrap(villager, EntityRef.class));
    assertThat(trade.isRewardingExp()).isFalse();
    assertThat(trade.willIncreaseTradeUses()).isTrue();
  }

  @Test
  @SuppressWarnings("deprecation")
  void chatCancelIsWrittenBack() {
    org.bukkit.event.player.AsyncPlayerChatEvent event = new org.bukkit.event.player.AsyncPlayerChatEvent(true, player, "hello",
        new HashSet<>());
    AtomicReference<PlayerChatEvent> seen = new AtomicReference<>();
    bus.register(this, PlayerChatEvent.class, EventPriority.HIGHEST, core -> {
      seen.set(core);
      core.setCancelled(true);
    });

    PlayerGapTranslations.CHAT.bridge(event, core -> bus.post(core, EventPriority.HIGHEST));

    assertThat(seen.get().message()).isEqualTo("hello");
    assertThat(seen.get().async()).isTrue();
    assertThat(event.isCancelled()).isTrue();
  }

  @Test
  void exhaustionOfNonPlayersIsNotTranslated() {
    org.bukkit.entity.HumanEntity human = mock(org.bukkit.entity.HumanEntity.class);
    org.bukkit.event.entity.EntityExhaustionEvent event = new org.bukkit.event.entity.EntityExhaustionEvent(human,
        org.bukkit.event.entity.EntityExhaustionEvent.ExhaustionReason.WALK, 0.1F);

    assertThat(PlayerGapTranslations.EXHAUSTION.target(event)).isNull();
  }
}
