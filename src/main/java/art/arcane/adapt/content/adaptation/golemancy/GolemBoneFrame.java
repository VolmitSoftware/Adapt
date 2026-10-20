/*------------------------------------------------------------------------------
 -   Adapt is a Skill/Integration plugin  for Minecraft Bukkit Servers
 -   Copyright (c) 2022 Arcane Arts (Volmit Software)
 -
 -   This program is free software: you can redistribute it and/or modify
 -   it under the terms of the GNU General Public License as published by
 -   the Free Software Foundation, either version 3 of the License, or
 -   (at your option) any later version.
 -
 -   This program is distributed in the hope that it will be useful,
 -   but WITHOUT ANY WARRANTY; without even the implied warranty of
 -   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 -   GNU General Public License for more details.
 -
 -   You should have received a copy of the GNU General Public License
 -   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 -----------------------------------------------------------------------------*/

package art.arcane.adapt.content.adaptation.golemancy;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.adaptation.AdaptationConfig;
import art.arcane.adapt.api.adaptation.AdaptationRequirements;
import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.adapt.api.attribute.AdaptAttributeService;
import art.arcane.adapt.api.golem.ChannelMask;
import art.arcane.adapt.api.golem.GolemRoster;
import art.arcane.adapt.api.golem.GolemRuneType;
import art.arcane.adapt.api.golem.GolemStatDelta;
import art.arcane.adapt.api.golem.ServantFrame;
import art.arcane.adapt.api.golem.overseer.Overseer;
import art.arcane.adapt.api.golem.reveal.RevealSettings;
import art.arcane.adapt.api.minion.ServantPack;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.localization.catalog.GolemancyMessages;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.math.M;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GolemBoneFrame extends SimpleAdaptation<GolemBoneFrame.Config> {
  private static final String SERVANT_ADAPTATION = "tragoul-skeletal-servant";
  private static final long SWEEP_INTERVAL_MILLIS = 5_000L;
  private static final String LATTICE_SLOT = "lattice_";
  private static final List<PlayerPreference<?>> PREFERENCES = List.of(CommonPreferences.ENABLED, GolemancyPreferences.OBEY_OVERSEER);

  private final OwnerPreferences owners = new OwnerPreferences(this);
  private final Map<UUID, Ember> embers = new ConcurrentHashMap<>();
  private final OverseerServants servants = new OverseerServants(this::obeysOverseer, this::channels, this::pack);
  private final ServantFrame.Frame frame = new Frame();
  private volatile GolemRunicAugments augments;
  private volatile AspectCache aspects;

  public GolemBoneFrame() {
    super("golem-bone-frame");
    registerConfiguration(Config.class);
    setLocalizationKey("golemancy.bone_frame");
    setIcon(Material.BONE_BLOCK);
    setInterval(SWEEP_INTERVAL_MILLIS);
  }

  @Override
  public void unregister() {
    Overseer.unregister(servants);
    ServantFrame.unregister(frame);
    embers.clear();
    augments = null;
    super.unregister();
  }

  @Override
  protected void onRuntimeActivated() {
    Overseer.register(servants);
    ServantFrame.register(frame);
  }

  @Override
  protected void normalizeLoadedConfig(Config loadedConfig) {
    double range = loadedConfig.servantLabelRange;
    loadedConfig.servantLabelRange = Double.isFinite(range) ? Math.clamp(range, RevealSettings.MIN_RANGE, RevealSettings.MAX_LABEL_RANGE) : RevealSettings.defaults().labelRange();
  }

  @Override
  public void addStats(int level, Element v) {
    Config config = getConfig();
    unlockLore(v, GolemancyMessages.BONE_FRAME_LORE1);
    int slots = BoneFrameRules.latticeSlots(config.latticeSlotsByLevel, level);
    if (slots > 0) {
      statLore(v, slots, 2);
    }
    statLore(v, Form.pc(BoneFrameRules.share(config.servantShareByLevel, level), 0), 3);
  }

  @Override
  public List<PlayerPreference<?>> getPlayerPreferences() {
    return PREFERENCES;
  }

  @Override
  public boolean hasTickDemand() {
    return !embers.isEmpty();
  }

  @Override
  public void onTick() {
    long now = M.ms();
    embers.values().removeIf(ember -> ember.expiresAt() <= now);
  }

  boolean obeysOverseer(UUID owner) {
    return ownerLevel(owner) >= 1 && owners.enabled(owner, GolemancyPreferences.OBEY_OVERSEER);
  }

  int channels(UUID owner) {
    return roster(owner).flatMap(GolemRoster::servantChannel).map(ChannelMask::of).orElse(ChannelMask.NONE);
  }

  double share(UUID owner) {
    return BoneFrameRules.share(getConfig().servantShareByLevel, ownerLevel(owner));
  }

  boolean servantMenuUnlocked(UUID owner) {
    if (ownerLevel(owner) >= 1) {
      return true;
    }
    AdaptPlayer player = getServer().getOnlineAdaptPlayer(owner);
    return player != null && !player.getData().getGolemancy().lattice().isEmpty();
  }

  int latticeSlots(int level) {
    return BoneFrameRules.latticeSlots(getConfig().latticeSlotsByLevel, level);
  }

  int mostLatticeSlots() {
    return BoneFrameRules.mostSlots(getConfig().latticeSlotsByLevel, getMaxLevel());
  }

  int slotUnlockLevel(int index) {
    return BoneFrameRules.slotUnlockLevel(getConfig().latticeSlotsByLevel, getMaxLevel(), index);
  }

  Set<GolemRuneType> aspects() {
    Config config = getConfig();
    AspectCache cache = aspects;
    if (cache == null || cache.config() != config) {
      cache = new AspectCache(config, BoneFrameRules.aspects(config.latticeAspects, Adapt::warn));
      aspects = cache;
    }
    return cache.aspects();
  }

  Optional<GolemRoster> roster(UUID owner) {
    AdaptPlayer player = getServer().getOnlineAdaptPlayer(owner);
    return player == null ? Optional.empty() : Optional.of(player.getData().getGolemancy());
  }

  Optional<AdaptPlayer> adaptPlayer(Player player) {
    return Optional.ofNullable(getServer().getOnlineAdaptPlayer(player.getUniqueId()));
  }

  Optional<ServantPack> pack() {
    return AdaptationRequirements.find(SERVANT_ADAPTATION) instanceof ServantPack found ? Optional.of(found) : Optional.empty();
  }

  Optional<GolemRunicAugments> runicAugments() {
    GolemRunicAugments cached = augments;
    if (cached != null || getSkill() == null) {
      return Optional.ofNullable(cached);
    }
    for (Adaptation<?> sibling : getSkill().getAdaptations()) {
      if (sibling instanceof GolemRunicAugments found) {
        augments = found;
        return Optional.of(found);
      }
    }
    return Optional.empty();
  }

  void summoned(Player owner, Skeleton servant) {
    UUID ownerId = owner.getUniqueId();
    int level = ownerLevel(ownerId);
    if (level < 1) {
      return;
    }
    int slots = latticeSlots(level);
    Optional<GolemRunicAugments> runic = runicAugments();
    if (slots <= 0 || runic.isEmpty()) {
      return;
    }
    int maxTier = runic.get().maxTier(runic.get().ownerLevel(ownerId));
    Map<GolemRuneType, Integer> active = BoneFrameRules.active(roster(ownerId).map(GolemRoster::lattice).orElse(List.of()), slots, aspects(), maxTier);
    if (!active.isEmpty()) {
      applyLattice(servant, runic.get().lattice(active));
    }
  }

  void struck(Skeleton servant, LivingEntity victim) {
    Ember ember = embers.get(servant.getUniqueId());
    if (ember != null) {
      runicAugments().ifPresent(runic -> runic.ignite(victim, ember.igniteTicks()));
    }
  }

  void strengthen(Skeleton servant, GolemStatDelta delta) {
    AdaptAttributeService.get().apply(servant, getName(), LATTICE_SLOT + delta.stat().id(), delta.stat().attribute(), delta.amount(), delta.operation());
  }

  void fireproof(Skeleton servant) {
    servant.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, PotionEffect.INFINITE_DURATION, 0, false, false, true));
  }

  private void applyLattice(Skeleton servant, GolemRunicAugments.Lattice lattice) {
    for (GolemStatDelta delta : lattice.stats()) {
      strengthen(servant, delta);
    }
    if (lattice.igniteTicks() > 0) {
      long expiresAt = pack().map(found -> found.expiresAt(servant)).orElse(0L);
      embers.put(servant.getUniqueId(), new Ember(lattice.igniteTicks(), expiresAt));
    }
    if (lattice.fireImmune()) {
      fireproof(servant);
    }
  }

  private record Ember(int igniteTicks, long expiresAt) {
  }

  private record AspectCache(Config config, Set<GolemRuneType> aspects) {
  }

  private final class Frame implements ServantFrame.Frame {
    @Override
    public int level(UUID owner) {
      return GolemBoneFrame.this.ownerLevel(owner);
    }

    @Override
    public int channels(UUID owner) {
      return GolemBoneFrame.this.channels(owner);
    }

    @Override
    public double labelRange() {
      return getConfig().servantLabelRange;
    }

    @Override
    public List<Skeleton> servants(UUID owner) {
      return pack().map(found -> found.servants(owner)).orElse(List.of());
    }

    @Override
    public long expiresAt(Skeleton servant) {
      return pack().map(found -> found.expiresAt(servant)).orElse(0L);
    }

    @Override
    public void summoned(Player owner, Skeleton servant) {
      GolemBoneFrame.this.summoned(owner, servant);
    }

    @Override
    public void struck(Skeleton servant, LivingEntity victim) {
      GolemBoneFrame.this.struck(servant, victim);
    }

    @Override
    public double share(UUID owner) {
      return GolemBoneFrame.this.share(owner);
    }

    @Override
    public boolean servantMenuUnlocked(Player player) {
      return GolemBoneFrame.this.servantMenuUnlocked(player.getUniqueId());
    }

    @Override
    public void openLattice(Player player, int rosterPage) {
      BoneLatticeGui.open(GolemBoneFrame.this, player, rosterPage);
    }

    @Override
    public boolean surrender(Skeleton servant) {
      return pack().map(found -> found.surrender(servant)).orElse(false);
    }

    @Override
    public void crumble(Skeleton servant) {
      pack().ifPresentOrElse(found -> found.crumble(servant), servant::remove);
    }
  }

  @ConfigDescription("Brings your Tragoul skeletal servants into Golemancy: they answer your Crook signals, glow in your servant channel with owner-only status labels, wear a Bone Lattice of runes, and pay Golemancy XP for their kills.")
  protected static class Config extends AdaptationConfig {
    @ConfigDoc(value = "Bone Lattice rune slots your skeletal servants get on summon, by Bone Frame level from level 1 upward, from 0 to 7.", impact = "Higher values let servants wear more runes.")
    List<Integer> latticeSlotsByLevel = new ArrayList<>(List.of(0, 2, 3));
    @ConfigDoc(value = "Rune aspects the Bone Lattice accepts, as aspect ids such as might.", impact = "Listed aspects can be slotted into the lattice; other runes are refused.")
    List<String> latticeAspects = new ArrayList<>(List.of("swiftness", "might", "ember", "bulwark"));
    @ConfigDoc(value = "Share of the owned-golem kill XP that your skeletal servants' kills pay into Golemancy, by Bone Frame level from level 1 upward. 1.0 pays the full golem kill value.", impact = "Higher values level Golemancy faster from servant kills.")
    List<Double> servantShareByLevel = new ArrayList<>(List.of(0.35, 0.35, 1.0));
    @ConfigDoc(value = "Blocks within which you see status labels over your skeletal servants while holding a Crook or another reveal item, from 1 to 64.", impact = "Higher values show servant labels from farther away.")
    double servantLabelRange = 8;

    public Config() {
      maxLevel = 3;
      requires = new ArrayList<>(List.of("golem-animus-binding:1", "tragoul-skeletal-servant:1"));
    }
  }
}
