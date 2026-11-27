package art.arcane.adapt.content.adaptation.stealth.blocks;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.mutation.BlockUse;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.api.mutation.Outcome;
import art.arcane.adapt.api.mutation.StrikeBlock;
import art.arcane.adapt.api.mutation.StrikeForm;
import art.arcane.adapt.content.skill.SkillStealth;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.block.BlockReceiveGameEvent;
import art.arcane.adapt.platform.event.entity.EntityTargetEvent;
import art.arcane.adapt.platform.event.player.PlayerJoinEvent;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.value.Id;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * For a few seconds the player makes no sound, sculk hears nothing they do, mobs hunting them drop their aggression
 * and no mob can take them as a target. Leaving the server or a reload ends it, and a silence a crash left saved with
 * the player ends when they join.
 */
public class StealthWorkStrike extends StrikeBlock<StealthWorkStrike.Config> {
  static final DataKey<Byte> SILENCED = DataKey.adapt("stealth_work_silenced", DataCodecs.BYTE);
  private final Map<UUID, Long> hiddenUntil = playerState();

  public StealthWorkStrike() {
    super(SkillStealth.ID, StrikeForm.WORK);
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("white_wool"));
  }

  @Override
  public Outcome strike(BlockUse use) {
    PlayerRef player = use.player();
    int ticks = (int) (use.scaled(getConfig().millisAtFirstLevel, getConfig().millisAtTopLevel) / 50D);
    long until = System.currentTimeMillis() + (ticks * 50L);
    hiddenUntil.put(player.uuid(), until);
    silence(player);
    StealthFightStrike.shakeOff(player, getConfig().aggroRadius);
    if (!AdaptApi.scheduler().runEntity(player, () -> reveal(player, until), ticks)) {
      reveal(player, until);
      return Outcome.MISSED;
    }
    return Outcome.lasting(player, ticks);
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(EntityTargetEvent e) {
    if (e.target() instanceof PlayerRef player && hidden(player)) {
      e.setCancelled(true);
    }
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(BlockReceiveGameEvent e) {
    if (e.entity() instanceof PlayerRef player && hidden(player)) {
      e.setCancelled(true);
    }
  }

  @Handle(priority = EventPriority.LOWEST, ignoreCancelled = false)
  public void on(PlayerJoinEvent e) {
    PlayerRef player = e.player();
    if (!hiddenUntil.containsKey(player.uuid()) && player.data().has(SILENCED)) {
      unsilence(player);
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerQuitEvent e) {
    PlayerRef player = e.player();
    if (hiddenUntil.remove(player.uuid()) != null) {
      unsilence(player);
    }
  }

  @Override
  public void unregister() {
    Set<UUID> silenced = Set.copyOf(hiddenUntil.keySet());
    hiddenUntil.clear();
    super.unregister();
    for (UUID id : silenced) {
      PlayerRef player = AdaptApi.platform().host().player(id);
      if (player != null && AdaptApi.scheduler().owns(player)) {
        unsilence(player);
      } else if (player != null) {
        AdaptApi.scheduler().runEntity(player, () -> unsilence(player));
      }
    }
  }

  private boolean hidden(PlayerRef player) {
    Long until = hiddenUntil.get(player.uuid());
    return until != null && System.currentTimeMillis() <= until;
  }

  private void reveal(PlayerRef player, long until) {
    if (hiddenUntil.remove(player.uuid(), until)) {
      unsilence(player);
    }
  }

  private static void silence(PlayerRef player) {
    player.data().set(SILENCED, (byte) 1);
    player.setSilent(true);
  }

  private static void unsilence(PlayerRef player) {
    player.setSilent(false);
    player.data().remove(SILENCED);
  }

  @ConfigDescription("Your steps and block sounds are silent and mobs ignore you for 5 s.")
  public static class Config extends MutationBlock.Config {
    @ConfigDoc(value = "How long you stay silent and ignored at level 1, in milliseconds.", impact = "Higher values hide you longer.")
    double millisAtFirstLevel = 5_000D;
    @ConfigDoc(value = "How long you stay silent and ignored at the top level, in milliseconds.", impact = "Higher values hide you longer.")
    double millisAtTopLevel = 7_000D;
    @ConfigDoc(value = "Reach in blocks within which mobs already hunting you drop their aggression.", impact = "Higher values calm mobs farther away.")
    double aggroRadius = 32D;
  }
}
