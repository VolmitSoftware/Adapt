package art.arcane.adapt.content.adaptation.chronos;

import art.arcane.adapt.api.world.AdaptPlayer;
import io.papermc.paper.registry.RegistryAccess;
import net.kyori.adventure.key.Key;
import org.bukkit.Registry;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChronosPocketWatchPreferencesTest {
  @BeforeAll
  static void initializeEffects() {
    RegistryAccess access = mock(RegistryAccess.class, RETURNS_DEEP_STUBS);
    try (MockedStatic<RegistryAccess> registryAccess = mockStatic(RegistryAccess.class)) {
      registryAccess.when(RegistryAccess::registryAccess).thenReturn(access);
      Registry<PotionEffectType> effects = Registry.MOB_EFFECT;
      doAnswer(call -> mock(PotionEffectType.class)).when(effects).getOrThrow(any(Key.class));
      assertThat(PotionEffectType.SLOW_FALLING).isNotNull();
    }
  }

  @Test
  void preferenceChangeRestoresPreviousEffectWithoutRestoringSpentAirtime() throws ReflectiveOperationException {
    ChronosPocketWatch adaptation = adaptation();
    Player player = player();
    PotionEffect previous = new PotionEffect(PotionEffectType.SLOW_FALLING, 100, 1, false, true, true);
    when(player.getPotionEffect(PotionEffectType.SLOW_FALLING)).thenReturn(previous);
    ConcurrentHashMap<UUID, Long> budget = new ConcurrentHashMap<>();
    budget.put(player.getUniqueId(), 500L);
    set(adaptation, "airBudgetMillis", budget);
    apply(adaptation, player);
    when(player.getTicksLived()).thenReturn(30);
    clearInvocations(player);

    adaptation.onPlayerPreferencesChanged(owner(player));

    verify(player).removePotionEffect(PotionEffectType.SLOW_FALLING);
    ArgumentCaptor<PotionEffect> restored = ArgumentCaptor.forClass(PotionEffect.class);
    verify(player).addPotionEffect(restored.capture(), eq(true));
    assertThat(restored.getValue().getDuration()).isEqualTo(80);
    assertThat(restored.getValue().getAmplifier()).isEqualTo(1);
    assertThat(budget.get(player.getUniqueId())).isEqualTo(500L);
  }

  @Test
  void externalEffectReplacementRelinquishesOwnership() throws ReflectiveOperationException {
    ChronosPocketWatch adaptation = adaptation();
    Player player = player();
    apply(adaptation, player);
    EntityPotionEffectEvent event = mock(EntityPotionEffectEvent.class);
    when(event.getEntity()).thenReturn(player);
    when(event.getModifiedType()).thenReturn(PotionEffectType.SLOW_FALLING);
    adaptation.on(event);
    clearInvocations(player);

    adaptation.onPlayerPreferencesChanged(owner(player));

    verify(player, never()).removePotionEffect(PotionEffectType.SLOW_FALLING);
    verify(player, never()).addPotionEffect(any(PotionEffect.class), eq(true));
  }

  private static ChronosPocketWatch adaptation() throws ReflectiveOperationException {
    ChronosPocketWatch adaptation = mock(ChronosPocketWatch.class, CALLS_REAL_METHODS);
    doReturn(new ChronosPocketWatch.Config()).when(adaptation).getConfig();
    set(adaptation, "fallingEffects", new ConcurrentHashMap<UUID, Object>());
    set(adaptation, "applyingEffects", new ConcurrentHashMap<UUID, Boolean>());
    return adaptation;
  }

  private static Player player() {
    Player player = mock(Player.class);
    when(player.getUniqueId()).thenReturn(UUID.randomUUID());
    when(player.getTicksLived()).thenReturn(10);
    when(player.addPotionEffect(any(PotionEffect.class), eq(true))).thenReturn(true);
    return player;
  }

  private static AdaptPlayer owner(Player player) {
    AdaptPlayer owner = mock(AdaptPlayer.class);
    when(owner.getPlayer()).thenReturn(player);
    return owner;
  }

  private static void apply(ChronosPocketWatch adaptation, Player player) throws ReflectiveOperationException {
    Method method = ChronosPocketWatch.class.getDeclaredMethod("applyFallEffect", Player.class);
    method.setAccessible(true);
    method.invoke(adaptation, player);
  }

  private static void set(ChronosPocketWatch adaptation, String name, Object value) throws ReflectiveOperationException {
    Field field = ChronosPocketWatch.class.getDeclaredField(name);
    field.setAccessible(true);
    field.set(adaptation, value);
  }
}
