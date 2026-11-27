package art.arcane.adapt.content.adaptation.taming.upgraded;

import art.arcane.adapt.platform.menu.MenuClick;
import art.arcane.adapt.platform.menu.ClickKind;
import art.arcane.adapt.content.menu.MenuSheet;
import art.arcane.adapt.content.menu.MenuEntry;
import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.config.ConfigDescription;
import art.arcane.adapt.api.config.ConfigDoc;
import art.arcane.adapt.api.content.StatLines;
import art.arcane.adapt.api.hud.HudStatus;
import art.arcane.adapt.api.mutation.LingeringSound;
import art.arcane.adapt.api.mutation.MutationCue;
import art.arcane.adapt.api.mutation.Mutations;
import art.arcane.adapt.api.mutation.SkillUpgrade;
import art.arcane.adapt.api.mutation.ToolUse;
import art.arcane.adapt.api.player.PlayerProfile;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.api.preference.PlayerPreference;
import art.arcane.adapt.api.text.Format;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.api.text.TextFormat;
import art.arcane.adapt.content.adaptation.taming.TameBonds;
import art.arcane.adapt.content.adaptation.taming.TameFetcher;
import art.arcane.adapt.content.adaptation.taming.TamePackCommand;
import art.arcane.adapt.content.adaptation.taming.TamePetRoles;
import art.arcane.adapt.content.adaptation.taming.TamePets;
import art.arcane.adapt.content.adaptation.taming.TameRecall;
import art.arcane.adapt.content.adaptation.taming.TamingPreferences;
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
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;


/**
 * Alpha's Command, Beast Recall and Fetch on the Beastmaster's Whistle, so bones feed wolves again: right-click sends
 * the pack at what you look at, sneak-right-click recalls the nearest pet and calls your bonded mount, and left-click
 * opens the whistle's menu. Idle wolves fetch nearby drops on their own while fetch is on. From {@code rolesLevel} the
 * menu gives named pets a role: guard you, guard a spot you can build at, or fetch.
 */
public class OneMind extends SkillUpgrade<OneMind.Config> {
  public static final Id ID = Id.adapt("taming/upgrade/one_mind");

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
    super(ID);
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
  public void onPlayerPreferencesChanged(PlayerProfile player) {
    release(player.player());
  }

  @Override
  public void addStats(int level, StatLines v) {
    statLore(v, Format.f(getCommandRange(level), 1), 1);
    statLore(v, Format.f(getRecallRadius(level), 1), 2);
    statLore(v, TextFormat.YELLOW, "* ", Format.duration(getRecallCooldownTicks(level) * 50D, 1), 3);
    if (level >= getConfig().rolesLevel) {
      v.addLore(TextFormat.GREEN + Text.key("adapt.upgrade.taming.one_mind.lore4").resolve());
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
    for (PlayerProfile data : learnedCandidates(System.currentTimeMillis())) {
      PlayerRef owner = data.player();
      if (owner != null) {
        AdaptApi.scheduler().runEntity(owner, () -> tend(owner));
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
        AdaptApi.scheduler().runEntity(mob, () -> guard(owner, mob, role));
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
      AdaptApi.platform().host().animals().setSitting(pet, false);
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
      AdaptApi.platform().host().animals().setSitting(pet, false);
      pet.pathTo(center, 1.2D);
    } else {
      AdaptApi.platform().host().animals().setSitting(pet, true);
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
      if (living.isValid() && !living.isDead() && distanceSquared <= nearestSquared && !Mutations.farmed(owner, living)
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
    AdaptApi.scheduler().runEntity(target, lingering::stop, getFocusTicks(getActiveLevel(p)));
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
    int rows = 1 + (int) Math.ceil(named.size() / (double) MenuSheet.WIDTH);
    MenuSheet sheet = MenuSheet.themed(rows);
    boolean fetching = fetches(p);
    sheet.set(-1, 0, MenuEntry.modelled(fetching ? Id.minecraft("lime_dye") : Id.minecraft("gray_dye"),
            "gui", "taming", "whistle", "fetch")
        .name((fetching ? TextFormat.GREEN : TextFormat.GRAY) + (fetching ? Text.key("adapt.tool.taming.whistle.fetch_on") : Text.key("adapt.tool.taming.whistle.fetch_off")).resolve(p))
        .role(MenuRole.SETTING)
        .on(ClickKind.LEFT, (MenuClick clicked) -> {
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
      sheet.set(1, 0, MenuEntry.modelled(Id.minecraft(bonded ? "golden_horse_armor" : "saddle"),
              "gui", "taming", "whistle", "bond")
          .name(TextFormat.GOLD + (bonded ? Text.key("adapt.tool.taming.whistle.bonded") : Text.key("adapt.tool.taming.whistle.bond")).resolve(p))
          .role(MenuRole.ACTION)
          .on(ClickKind.LEFT, (MenuClick clicked) -> {
            if (!bonded) {
              bonds.bond(p, mount);
            }
            openMenu(p, level);
          }));
    }
    for (int index = 0; index < named.size(); index++) {
      LivingRef pet = named.get(index);
      sheet.set(LEFT_EDGE + (index % MenuSheet.WIDTH), 1 + (index / MenuSheet.WIDTH), petElement(p, level, pet));
    }
    sheet.title(Text.key("adapt.tool.taming.whistle.name").resolve(p)).open(p);
  }

  private MenuEntry petElement(PlayerRef p, int level, LivingRef pet) {
    TamePetRoles.Role role = TamePetRoles.role(pet);
    String label = Text.key("adapt.tool.taming.whistle.role").withMarkup("role", roleName(role).resolve(p)).resolve(p);
    return MenuEntry.modelled(Id.minecraft("name_tag"), "gui", "taming", "whistle", "pet")
        .name(TextFormat.WHITE + pet.customName())
        .lore(TextFormat.GRAY + label)
        .value(label)
        .role(MenuRole.ENTRY)
        .on(ClickKind.LEFT, (MenuClick clicked) -> {
          assign(p, pet, role.next());
          openMenu(p, level);
        });
  }

  private void assign(PlayerRef p, LivingRef pet, TamePetRoles.Role role) {
    Loc spot = p.location();
    if (role == TamePetRoles.Role.GUARD_SPOT && !canBlockPlace(p, spot)) {
      HudStatus.show(p, TextFormat.RED + Text.key("adapt.tool.taming.whistle.cannot_build").resolve());
      AdaptApi.scheduler().runEntity(pet, () -> TamePetRoles.assign(pet, role.next(), null));
      return;
    }
    AdaptApi.scheduler().runEntity(pet, () -> {
      TamePetRoles.assign(pet, role, spot);
      if (role != TamePetRoles.Role.GUARD_SPOT) {
        AdaptApi.platform().host().animals().setSitting(pet, false);
      }
    });
  }

  private static Text roleName(TamePetRoles.Role role) {
    return switch (role) {
      case NONE -> Text.key("adapt.tool.taming.whistle.role_none");
      case GUARD_OWNER -> Text.key("adapt.tool.taming.whistle.role_guard_owner");
      case GUARD_SPOT -> Text.key("adapt.tool.taming.whistle.role_guard_spot");
      case FETCH -> Text.key("adapt.tool.taming.whistle.role_fetch");
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
