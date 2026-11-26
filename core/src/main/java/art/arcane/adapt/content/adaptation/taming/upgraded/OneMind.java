package art.arcane.adapt.content.adaptation.taming.upgraded;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.adaptation.StatLines;
import art.arcane.adapt.api.mutation.engine.MutationPayouts;
import art.arcane.adapt.api.mutation.sound.LingeringSound;
import art.arcane.adapt.api.mutation.sound.MutationCue;
import art.arcane.adapt.api.mutation.tool.ToolUse;
import art.arcane.adapt.api.mutation.upgrade.SkillUpgrade;
import art.arcane.adapt.api.notification.AdaptHud;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.content.adaptation.taming.TameBonds;
import art.arcane.adapt.content.adaptation.taming.TameFetcher;
import art.arcane.adapt.content.adaptation.taming.TamePackCommand;
import art.arcane.adapt.content.adaptation.taming.TamePetRoles;
import art.arcane.adapt.content.adaptation.taming.TamePets;
import art.arcane.adapt.content.adaptation.taming.TameRecall;
import art.arcane.adapt.content.adaptation.taming.TamingPreferences;
import art.arcane.adapt.localization.AdaptLanguage;
import art.arcane.adapt.localization.catalog.TamingMessages;
import art.arcane.adapt.platform.data.DataCodecs;
import art.arcane.adapt.platform.data.DataKey;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.EntityTrait;
import art.arcane.adapt.platform.entity.LivingRef;
import art.arcane.adapt.platform.entity.MobRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.entity.TeleportCause;
import art.arcane.adapt.platform.event.Handle;
import art.arcane.adapt.platform.event.player.InteractAction;
import art.arcane.adapt.platform.event.player.PlayerQuitEvent;
import art.arcane.adapt.platform.menu.MenuRole;
import art.arcane.adapt.platform.value.Box;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.value.Loc;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.common.inventorygui.GuiElement;
import art.arcane.adapt.util.common.inventorygui.GuiLayout;
import art.arcane.adapt.util.common.inventorygui.GuiTheme;
import art.arcane.adapt.util.common.inventorygui.GuiWindow;
import art.arcane.adapt.util.common.scheduling.Tasks;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.localization.LanguageAudience;
import art.arcane.volmlib.util.localization.TextKey;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static art.arcane.volmlib.util.localization.MessageArgument.trusted;

/**
 * Alpha's Command, Beast Recall and Fetch on the Beastmaster's Whistle, so bones feed wolves again: right-click sends
 * the pack at what you look at, sneak-right-click recalls the nearest pet and calls your bonded mount, and left-click
 * opens the whistle's menu. Idle wolves fetch nearby drops on their own while fetch is on. From {@code rolesLevel} the
 * menu gives named pets a role: guard you, guard a spot you can build at, or fetch.
 */
public class OneMind extends SkillUpgrade<OneMind.Config> {
  private static final DataKey<Boolean> FETCH = DataKey.adapt("taming_whistle_fetch", DataCodecs.BOOLEAN);
  private static final double MIN_RECALL_DISTANCE_SQUARED = 9.0D;
  private static final int RECALL_CANDIDATES = 16;
  private static final int FETCH_PATH_REFRESH_TICKS = 5;
  private static final double SPOT_TOLERANCE = 2.0D;
  private static final double SPOT_LEASH = 16.0D;
  private static final int MENU_PETS = 18;
  private static final int LEFT_EDGE = -4;
  private final TamePackCommand pack = new TamePackCommand(this, cooldowns(), (PlayerRef p) -> {
    addStat(p, "taming.one-mind.commands", 1);
  });
  private final TameRecall recall = new TameRecall(this, playerState(), (PlayerRef p) -> {
    addStat(p, "taming.one-mind.recalls", 1);
  });
  private final TameFetcher fetcher = new TameFetcher(this, (PlayerRef owner) -> {
    addStat(owner, "taming.one-mind.items-fetched", 1);
  });

  public OneMind() {
    super("upgraded-taming-one-mind");
    registerConfiguration(Config.class);
    setIcon(Id.minecraft("goat_horn"));
    setInterval(1000);
    registerTool(new BeastmastersWhistle(this));
  }

  @Override
  protected List<PlayerPreference<?>> upgradePreferences() {
    return List.of(CommonPreferences.ENABLED, TamingPreferences.PETS, TamingPreferences.TARGETS,
        TamingPreferences.SITTING, TamingPreferences.HUNGER, TamingPreferences.ITEMS, TamingPreferences.COLLAR);
  }

  @Override
  public void onPlayerPreferencesChanged(AdaptPlayer player) {
    release(player.getPlayer());
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Form.f(getCommandRange(level), 1), 1);
    statLore(v, Form.f(getRecallRadius(level), 1), 2);
    statLore(v, C.YELLOW, "* ", Form.duration(getRecallCooldownTicks(level) * 50D, 1), 3);
    if (level >= getConfig().rolesLevel) {
      v.addLore(C.GREEN + AdaptLanguage.text(TamingMessages.ONE_MIND_LORE4));
    }
  }

  @Override
  protected boolean usesLearnerBoundTicking() {
    return true;
  }

  @Override
  public void unregister() {
    pack.close();
    fetcher.close();
    super.unregister();
  }

  @Override
  public void onTick() {
    for (AdaptPlayer data : learnedCandidates(System.currentTimeMillis())) {
      PlayerRef owner = data.getPlayer();
      if (owner != null) {
        Tasks.runEntity(owner, () -> tend(owner));
      }
    }
  }

  @Handle
  public void on(PlayerQuitEvent e) {
    release(e.player());
  }

  void whistle(ToolUse use) {
    PlayerRef p = use.player();
    int level = use.level();
    if (use.leftClick()) {
      if (use.action() == InteractAction.LEFT_CLICK_AIR && !use.sneaking()) {
        openMenu(p, level);
      }
      return;
    }
    if (!use.rightClick()) {
      return;
    }
    if (use.sneaking()) {
      recall(p, level);
      return;
    }
    LivingRef target = use.entity() instanceof LivingRef clicked ? clicked : pack.aim(p, getCommandRange(level));
    if (target == null) {
      return;
    }
    TameBonds.Bonds bonds = TameBonds.find(this);
    if (bonds != null && TameBonds.bondable(p, target) && !TameBonds.bondedTo(p, target)) {
      bonds.bond(p, target);
      return;
    }
    command(p, target);
  }

  private void tend(PlayerRef owner) {
    int level = getActiveLevel(owner);
    if (level <= 0 || !owner.isOnline()) {
      return;
    }
    boolean fetchAll = fetches(owner);
    boolean roles = level >= getConfig().rolesLevel;
    fetcher.fetch(owner, this::fetchTuning, (MobRef wolf) -> {
      TamePetRoles.Role role = roles ? TamePetRoles.role(wolf) : TamePetRoles.Role.NONE;
      return role == TamePetRoles.Role.FETCH || (fetchAll && role == TamePetRoles.Role.NONE);
    });
    if (roles) {
      guard(owner);
    }
  }

  private void guard(PlayerRef owner) {
    double reach = getConfig().guardRadius + SPOT_LEASH;
    for (LivingRef pet : TamePets.near(owner, Math.max(reach, getConfig().menuRadius))) {
      TamePetRoles.Role role = TamePetRoles.role(pet);
      if (pet instanceof MobRef mob && (role == TamePetRoles.Role.GUARD_OWNER || role == TamePetRoles.Role.GUARD_SPOT)) {
        Tasks.runEntity(mob, () -> guard(owner, mob, role));
      }
    }
  }

  private void guard(PlayerRef owner, MobRef pet, TamePetRoles.Role role) {
    if (!pet.isValid() || pet.isDead() || fighting(pet)) {
      return;
    }
    Loc post = role == TamePetRoles.Role.GUARD_SPOT ? TamePetRoles.spot(pet) : owner.location();
    if (post == null || !post.sameWorld(pet.location())) {
      return;
    }
    LivingRef threat = nearestThreat(owner, pet, post);
    if (threat != null) {
      AdaptCore.platform().host().animals().setSitting(pet, false);
      pet.setTarget(threat);
      return;
    }
    if (role != TamePetRoles.Role.GUARD_SPOT) {
      return;
    }
    Loc center = post.add(0.5D, 0D, 0.5D);
    double distanceSquared = pet.location().distanceSquared(center);
    if (distanceSquared > SPOT_LEASH * SPOT_LEASH) {
      pet.teleport(center, TeleportCause.PLUGIN);
    } else if (distanceSquared > SPOT_TOLERANCE * SPOT_TOLERANCE) {
      AdaptCore.platform().host().animals().setSitting(pet, false);
      pet.pathTo(center, 1.2D);
    } else {
      AdaptCore.platform().host().animals().setSitting(pet, true);
    }
  }

  private static boolean fighting(MobRef pet) {
    LivingRef target = pet.target();
    return target != null && target.isValid() && !target.isDead();
  }

  private @Nullable LivingRef nearestThreat(PlayerRef owner, MobRef pet, Loc post) {
    double radius = getConfig().guardRadius;
    LivingRef nearest = null;
    double nearestSquared = radius * radius;
    for (EntityRef entity : post.world().entitiesIn(Box.around(post.position(), radius, radius, radius),
        (EntityRef candidate) -> candidate instanceof LivingRef && candidate.has(EntityTrait.MONSTER))) {
      LivingRef living = (LivingRef) entity;
      double distanceSquared = living.location().distanceSquared(post);
      if (living.isValid() && !living.isDead() && distanceSquared <= nearestSquared && !MutationPayouts.farmed(owner, living)
          && canPVE(owner, living)) {
        nearest = living;
        nearestSquared = distanceSquared;
      }
    }
    return nearest;
  }

  private void command(PlayerRef p, LivingRef target) {
    if (!pack.command(p, target, this::commandTuning)) {
      return;
    }
    cue(p, MutationCue.FIGHT);
    LingeringSound lingering = linger(target);
    Tasks.runEntity(target, lingering::stop, getFocusTicks(getActiveLevel(p)));
  }

  private void recall(PlayerRef p, int level) {
    if (p.hasCooldown(BeastmastersWhistle.MATERIAL)) {
      return;
    }
    TameRecall.Tuning tuning = new TameRecall.Tuning(BeastmastersWhistle.MATERIAL, getRecallRadius(level), MIN_RECALL_DISTANCE_SQUARED,
        getRecallCooldownTicks(level), getConfig().recallHungerCost, TameRecall.candidateLimit(RECALL_CANDIDATES), TameRecall.affectedLimit(1));
    boolean pets = recall.recall(p, level, tuning);
    int hunger = Math.max(0, getConfig().recallHungerCost);
    TameBonds.Bonds bonds = TameBonds.find(this);
    boolean mount = bonds != null && TameRecall.affords(this, p, hunger) && bonds.call(p);
    if (mount && !pets) {
      p.setFoodLevel(Math.max(0, p.foodLevel() - hunger));
      p.setCooldown(BeastmastersWhistle.MATERIAL, getRecallCooldownTicks(level));
    }
    if (pets || mount) {
      cue(p, MutationCue.BOON);
    }
  }

  private void openMenu(PlayerRef p, int level) {
    List<LivingRef> named = new ArrayList<>();
    if (level >= getConfig().rolesLevel) {
      for (LivingRef pet : TamePets.near(p, getConfig().menuRadius)) {
        if (TamePetRoles.named(pet) && named.size() < MENU_PETS) {
          named.add(pet);
        }
      }
    }
    try (LanguageAudience.Scope audience = LanguageAudience.open(p.uuid())) {
      GuiWindow window = new GuiWindow(p);
      GuiTheme.apply(window, "taming-whistle");
      int rows = 1 + (int) Math.ceil(named.size() / (double) GuiLayout.WIDTH);
      window.setViewportHeight(rows);
      boolean fetching = fetches(p);
      window.setElement(-1, 0, GuiTheme.element("whistle-fetch", fetching ? Id.minecraft("lime_dye") : Id.minecraft("gray_dye"),
              "gui", "taming", "whistle", "fetch")
          .setName((fetching ? C.GREEN : C.GRAY) + AdaptLanguage.text(fetching ? TamingMessages.WHISTLE_FETCH_ON : TamingMessages.WHISTLE_FETCH_OFF))
          .setRole(MenuRole.SETTING)
          .onLeftClick(clicked -> {
            p.data().set(FETCH, !fetching);
            if (fetching) {
              fetcher.abortOwnerFetches(p.uuid());
            }
            openMenu(p, level);
          }));
      EntityRef vehicle = p.vehicle();
      TameBonds.Bonds bonds = TameBonds.find(this);
      if (bonds != null && vehicle instanceof LivingRef mount && TameBonds.bondable(p, mount)) {
        boolean bonded = TameBonds.bondedTo(p, mount);
        window.setElement(1, 0, GuiTheme.element("whistle-bond", Id.minecraft(bonded ? "golden_horse_armor" : "saddle"),
                "gui", "taming", "whistle", "bond")
            .setName(C.GOLD + AdaptLanguage.text(bonded ? TamingMessages.WHISTLE_BONDED : TamingMessages.WHISTLE_BOND))
            .setRole(MenuRole.ACTION)
            .onLeftClick(clicked -> {
              if (!bonded) {
                bonds.bond(p, mount);
              }
              openMenu(p, level);
            }));
      }
      for (int index = 0; index < named.size(); index++) {
        LivingRef pet = named.get(index);
        window.setElement(LEFT_EDGE + (index % GuiLayout.WIDTH), 1 + (index / GuiLayout.WIDTH), petElement(p, level, pet, index));
      }
      window.setTitle(AdaptLanguage.text(TamingMessages.WHISTLE_NAME));
      window.open();
    }
  }

  private GuiElement petElement(PlayerRef p, int level, LivingRef pet, int index) {
    TamePetRoles.Role role = TamePetRoles.role(pet);
    String label = AdaptLanguage.text(TamingMessages.WHISTLE_ROLE, trusted("role", AdaptLanguage.text(roleName(role))));
    return GuiTheme.element("whistle-pet-" + index, Id.minecraft("name_tag"), "gui", "taming", "whistle", "pet")
        .setName(C.WHITE + pet.customName())
        .addLore(C.GRAY + label)
        .setValue(label)
        .setRole(MenuRole.ENTRY)
        .onLeftClick(clicked -> {
          assign(p, pet, role.next());
          openMenu(p, level);
        });
  }

  private void assign(PlayerRef p, LivingRef pet, TamePetRoles.Role role) {
    Loc spot = p.location();
    if (role == TamePetRoles.Role.GUARD_SPOT && !canBlockPlace(p, spot)) {
      AdaptHud.actionBar(p, C.RED + AdaptLanguage.text(TamingMessages.WHISTLE_CANNOT_BUILD));
      Tasks.runEntity(pet, () -> TamePetRoles.assign(pet, role.next(), null));
      return;
    }
    Tasks.runEntity(pet, () -> {
      TamePetRoles.assign(pet, role, spot);
      if (role != TamePetRoles.Role.GUARD_SPOT) {
        AdaptCore.platform().host().animals().setSitting(pet, false);
      }
    });
  }

  private static TextKey roleName(TamePetRoles.Role role) {
    return switch (role) {
      case NONE -> TamingMessages.WHISTLE_ROLE_NONE;
      case GUARD_OWNER -> TamingMessages.WHISTLE_ROLE_GUARD_OWNER;
      case GUARD_SPOT -> TamingMessages.WHISTLE_ROLE_GUARD_SPOT;
      case FETCH -> TamingMessages.WHISTLE_ROLE_FETCH;
    };
  }

  boolean fetches(PlayerRef p) {
    Boolean stored = p.data().get(FETCH);
    return stored == null || stored;
  }

  private void release(PlayerRef p) {
    pack.clear(p);
    fetcher.abortOwnerFetches(p.uuid());
  }

  private TamePackCommand.Tuning commandTuning(int level) {
    return new TamePackCommand.Tuning(getCommandRange(level), getFocusTicks(level), level >= getMaxLevel() ? 1 : 0,
        (long) getConfig().commandCooldownMillis, TamePackCommand.petLimit(getConfig().maxPets), false);
  }

  private TameFetcher.Tuning fetchTuning(int level) {
    return new TameFetcher.Tuning(getConfig().fetchRange, getConfig().carryRate, getConfig().wolfSearchRadius, getConfig().maxWolves,
        getConfig().maxItemsPerSend, getConfig().fetchRange, getConfig().fetchWalkSpeed,
        (long) getConfig().fetchDeadlineMillis, FETCH_PATH_REFRESH_TICKS);
  }

  double getCommandRange(int level) {
    return getConfig().commandRangeBase + (level * getConfig().commandRangePerLevel);
  }

  int getFocusTicks(int level) {
    return Math.max(20, (int) Math.round(getConfig().focusTicksBase + (level * getConfig().focusTicksPerLevel)));
  }

  double getRecallRadius(int level) {
    return getConfig().recallRadiusBase + (level * getConfig().recallRadiusPerLevel);
  }

  int getRecallCooldownTicks(int level) {
    return Math.max(40, (int) Math.round(getConfig().recallCooldownTicksBase - (level * getConfig().recallCooldownTicksPerLevel)));
  }

  @ConfigDescription("The Beastmaster's Whistle sends, recalls and organises your pets, and wolves fetch drops on their own.")
  public static class Config extends SkillUpgrade.ToolConfig {
    @ConfigDoc(value = "Range for sending the pack at a target before level scaling, in blocks.", impact = "Higher values let you send the pack farther.")
    double commandRangeBase = 8;
    @ConfigDoc(value = "Sending range added per level, in blocks.", impact = "Higher values reward higher levels more.")
    double commandRangePerLevel = 8;
    @ConfigDoc(value = "Ticks pets stay focused on a target before level scaling.", impact = "Higher values keep the pack on the target longer.")
    double focusTicksBase = 80;
    @ConfigDoc(value = "Focus ticks added per level.", impact = "Higher values reward higher levels more.")
    double focusTicksPerLevel = 40;
    @ConfigDoc(value = "Time between two sends at a target, in milliseconds.", impact = "Lower values let you retarget the pack more often.")
    double commandCooldownMillis = 2500;
    @ConfigDoc(value = "Most pets sent at one target, capped at 24.", impact = "Lower values send smaller packs.")
    int maxPets = 12;
    @ConfigDoc(value = "Radius searched for a pet to recall before level scaling, in blocks.", impact = "Higher values recall pets from farther away.")
    double recallRadiusBase = 32;
    @ConfigDoc(value = "Recall radius added per level, in blocks.", impact = "Higher values reward higher levels more.")
    double recallRadiusPerLevel = 12;
    @ConfigDoc(value = "Whistle cooldown after a recall before level scaling, in ticks; at least 40.", impact = "Higher values make recalls rarer.")
    double recallCooldownTicksBase = 300;
    @ConfigDoc(value = "Recall cooldown removed per level, in ticks.", impact = "Higher values shorten the cooldown faster as you level.")
    double recallCooldownTicksPerLevel = 60;
    @ConfigDoc(value = "Food points each recall costs.", impact = "Higher values make recalls cost more hunger; 0 disables the cost.")
    int recallHungerCost = 1;
    @ConfigDoc(value = "Range around you in which wolves fetch drops, in blocks; capped at 11.", impact = "Higher values reach farther drops.")
    double fetchRange = 9;
    @ConfigDoc(value = "Chance each drop is fetched on a pass, from 0 to 1.", impact = "Higher values clear drops faster.")
    double carryRate = 0.85;
    @ConfigDoc(value = "Radius searched for idle wolves to send, in blocks.", impact = "Higher values send wolves from farther away.")
    double wolfSearchRadius = 24;
    @ConfigDoc(value = "Most wolves counted for one pass, capped at 12.", impact = "Higher values let larger packs fetch together.")
    int maxWolves = 6;
    @ConfigDoc(value = "Most drops fetched by one pass, capped at 8.", impact = "Higher values clear more drops per pass.")
    int maxItemsPerSend = 6;
    @ConfigDoc(value = "Walking speed of a fetching wolf, clamped to 0.1 - 4.0.", impact = "Higher values finish the round trip sooner.")
    double fetchWalkSpeed = 1.3;
    @ConfigDoc(value = "Time a fetch may run before it is given up, in milliseconds; clamped to 1000 - 60000.", impact = "Higher values let wolves work awkward routes longer.")
    double fetchDeadlineMillis = 9000;
    @ConfigDoc(value = "One Mind level from which named pets take roles in the whistle's menu.", impact = "Lower values unlock roles sooner.")
    int rolesLevel = 2;
    @ConfigDoc(value = "Radius around you in which the whistle's menu lists named pets, in blocks.", impact = "Higher values list pets farther away.")
    double menuRadius = 32;
    @ConfigDoc(value = "Radius around a guard's post in which it attacks hostile mobs, in blocks.", impact = "Higher values let guards engage from farther away.")
    double guardRadius = 10;
  }
}
