package art.arcane.adapt.content.adaptation.discovery.upgraded;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.mutation.sound.MutationCue;
import art.arcane.adapt.api.mutation.upgrade.SkillUpgrade;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.api.world.PlayerData;
import art.arcane.adapt.api.world.PlayerSkillLine;
import art.arcane.adapt.content.adaptation.discovery.DiscoveryArmor;
import art.arcane.adapt.content.adaptation.discovery.DiscoveryBetterMending;
import art.arcane.adapt.content.adaptation.discovery.DiscoveryPolymath;
import art.arcane.adapt.content.adaptation.discovery.DiscoveryXpShare;
import art.arcane.adapt.content.adaptation.discovery.WorldArmor;
import art.arcane.adapt.content.adaptation.discovery.XpMending;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.EventPriority;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.PlayerExpChangeEvent;
import art.arcane.adapt.platform.event.player.PlayerInteractEvent;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Experimental Unity and Polymath fused: vanilla XP feeds your lowest skills, and every levelled skill adds to a
 * global XP-gain bonus. It also runs World Armor and Better Mending at their top levels; those originals stay kept for
 * every mutated player and stand down while this is held ({@link #covers}).
 */
public class UnityPrime extends SkillUpgrade<UnityPrime.Config> {
  private static final long REFRESH_INTERVAL_MILLIS = 2000L;
  private static final long MULTIPLIER_DURATION_MILLIS = 2600L;
  private final Map<UUID, Double> bonuses = playerState();
  private final WorldArmor armor = new WorldArmor(this, playerState());

  public UnityPrime() {
    super("upgraded-discovery-unity-prime");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("end_crystal"));
    setInterval(REFRESH_INTERVAL_MILLIS);
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.f(getOrbXp(level, 1), 1), 1);
    statLore(v, getLowestSkills(level), 2);
    statLore(v, Form.pc(getPerSkillBonus(level), 1), 3);
    DiscoveryArmor worldArmor = original(DiscoveryArmor.class);
    if (worldArmor != null) {
      statLore(v, worldArmor.getMaxLevel(), 4);
    }
    DiscoveryBetterMending mending = original(DiscoveryBetterMending.class);
    if (mending != null) {
      statLore(v, mending.getMaxLevel(), 5);
    }
  }

  @Override
  protected List<PlayerPreference<?>> upgradePreferences() {
    return List.of(CommonPreferences.ENABLED, DiscoveryBetterMending.XP_RESERVE, DiscoveryBetterMending.REPAIR_LIMIT);
  }

  @Override
  public void onPlayerPreferencesChanged(AdaptPlayer player) {
    if (!hasActiveAdaptation(player.getPlayer())) {
      player.getData().removeGlobalXPMultipliers(getName());
      armor.remove(player.getPlayer());
    }
  }

  /**
   * Whether the player holds the Unity Prime of {@code original}'s skill, which then runs World Armor and Better
   * Mending at their top levels in place of the kept originals.
   */
  public static boolean covers(Adaptation<?> original, PlayerRef player) {
    if (original == null || original.getSkill() == null || player == null) {
      return false;
    }
    for (Adaptation<?> upgrade : original.getSkill().getUpgrades()) {
      if (upgrade instanceof UnityPrime unity && unity.getActiveLevel(player) > 0) {
        return true;
      }
    }
    return false;
  }

  @Handle(priority = EventPriority.HIGHEST)
  public void on(PlayerInteractEvent e) {
    if (!XpMending.isPress(e)) {
      return;
    }
    PlayerRef player = e.player();
    DiscoveryBetterMending mending = original(DiscoveryBetterMending.class);
    if (mending == null || getActiveLevel(player, PlayerRef::isSneaking) <= 0) {
      return;
    }
    XpMending.Press press = XpMending.mend(this, player, mending.rates(mending.getMaxLevel()), e);
    if (press.outcome() == XpMending.Outcome.MENDED) {
      addStat(player, "discovery.unity-prime.durability-restored", press.damageBefore() - press.damageAfter());
      cue(player, MutationCue.WORK);
    }
  }

  @Handle(priority = EventPriority.MONITOR)
  public void on(PlayerExpChangeEvent e) {
    PlayerRef player = e.player();
    int level = getActiveLevel(player);
    if (level <= 0 || e.amount() <= 0) {
      return;
    }
    AdaptPlayer adaptPlayer = getPlayer(player);
    if (adaptPlayer == null) {
      return;
    }

    List<PlayerSkillLine> lowest = lowestSkills(adaptPlayer.getData(), getLowestSkills(level));
    double each = getOrbXp(level, Math.min(e.amount(), getConfig().maxOrbsPerGain));
    for (PlayerSkillLine line : lowest) {
      DiscoveryXpShare.feed(adaptPlayer, getServer().getSkillRegistry(), line, each,
          "adaptation:upgraded-discovery-unity-prime:distribution");
    }
    if (!lowest.isEmpty()) {
      addStat(player, "discovery.unity-prime.skills-fed", lowest.size());
      cue(player, MutationCue.WORK);
    }
  }

  @Override
  protected boolean usesLearnerBoundTicking() {
    return true;
  }

  @Override
  public boolean hasTickDemand() {
    return super.hasTickDemand() || !armor.isEmpty();
  }

  @Override
  public void onTick() {
    Set<UUID> ticked = new HashSet<>();
    for (AdaptPlayer adaptPlayer : learnedCandidates(System.currentTimeMillis())) {
      PlayerRef player = adaptPlayer.getPlayer();
      if (player != null && player.isOnline()) {
        ticked.add(player.uuid());
        Tasks.runEntity(player, () -> boostOwned(player));
      }
    }
    for (UUID playerId : List.copyOf(armor.players())) {
      if (ticked.contains(playerId)) {
        continue;
      }
      PlayerRef player = AdaptCore.platform().host().player(playerId);
      if (player == null || !player.isOnline()) {
        armor.forget(playerId);
      } else {
        Tasks.runEntity(player, () -> boostOwned(player));
      }
    }
  }

  @Override
  public void unregister() {
    armor.clear();
    super.unregister();
  }

  void boostOwned(PlayerRef player) {
    PlayerData data = getPlayer(player).getData();
    int level = getActiveLevel(player);
    if (level <= 0) {
      data.removeGlobalXPMultipliers(getName());
      bonuses.remove(player.uuid());
      armor.remove(player);
      return;
    }

    DiscoveryArmor worldArmor = original(DiscoveryArmor.class);
    if (worldArmor != null) {
      armor.update(player, worldArmor.getMaxLevel());
    }

    int levelled = DiscoveryXpShare.levelledSkills(data, getConfig().levelledSkillThreshold);
    double bonus = DiscoveryPolymath.totalBonus(levelled, getPerSkillBonus(level), getConfig().maxTotalBonus);
    if (bonus <= 0D) {
      return;
    }
    DiscoveryXpShare.boost(data, getName(), bonus, MULTIPLIER_DURATION_MILLIS);
    Double previous = bonuses.put(player.uuid(), bonus);
    if (previous == null || bonus > previous) {
      cue(player, MutationCue.BOON);
    }
  }

  private <A extends Adaptation<?>> A original(Class<A> type) {
    for (Adaptation<?> adaptation : getSkill().getAdaptations()) {
      if (type.isInstance(adaptation)) {
        return type.cast(adaptation);
      }
    }
    return null;
  }

  static List<PlayerSkillLine> lowestSkills(PlayerData data, int count) {
    List<PlayerSkillLine> lines = new ArrayList<>();
    for (PlayerSkillLine line : data.getSkillLines().values()) {
      if (line != null) {
        lines.add(line);
      }
    }
    lines.sort(Comparator.comparingInt(PlayerSkillLine::getLevel).thenComparingDouble(PlayerSkillLine::getXp));
    return lines.subList(0, Math.min(Math.max(0, count), lines.size()));
  }

  double getOrbXp(int level, int orbs) {
    return DiscoveryXpShare.orbXp(getConfig().xpPerOrb, getLevelPercent(level), orbs);
  }

  int getLowestSkills(int level) {
    return Math.max(1, getConfig().lowestSkillsBase + level);
  }

  double getPerSkillBonus(int level) {
    return DiscoveryPolymath.perSkillBonus(getLevelPercent(level), getConfig().perSkillBonusBase, getConfig().perSkillBonusFactor);
  }

  @ConfigDescription("Vanilla XP feeds your lowest skills, every levelled skill raises all XP gains, and World Armor and Better Mending run at their top levels.")
  public static class Config extends SkillUpgrade.Config {
    @ConfigDoc(value = "Skill XP per vanilla XP point at the top level; lower levels scale it down.", impact = "Higher values feed your lowest skills faster.")
    double xpPerOrb = 12;
    @ConfigDoc(value = "Most vanilla XP points counted from one pickup.", impact = "Higher values let big pickups feed more.")
    int maxOrbsPerGain = 5;
    @ConfigDoc(value = "Lowest skills fed by each pickup, before adding the level.", impact = "Higher values spread the XP over more skills.")
    int lowestSkillsBase = 0;
    @ConfigDoc(value = "Skill level a line must reach to count as levelled.", impact = "Higher values need deeper skills before they raise your gains.")
    int levelledSkillThreshold = 1;
    @ConfigDoc(value = "Global XP-gain bonus per levelled skill at level 1.", impact = "Higher values raise every gain more per skill.")
    double perSkillBonusBase = 0.02;
    @ConfigDoc(value = "Global XP-gain bonus per levelled skill gained across levels.", impact = "Higher values reward higher levels more.")
    double perSkillBonusFactor = 0.04;
    @ConfigDoc(value = "Largest combined global XP-gain bonus.", impact = "Higher values raise the ceiling of the stacked bonus.")
    double maxTotalBonus = 1.0;
  }
}
