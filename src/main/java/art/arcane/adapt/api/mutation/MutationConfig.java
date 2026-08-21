package art.arcane.adapt.api.mutation;

import art.arcane.adapt.Adapt;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import art.arcane.adapt.util.config.ConfigFileSupport;
import art.arcane.volmlib.util.bukkit.WorldIdentity;
import lombok.Getter;
import org.bukkit.generator.WorldInfo;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Getter
@ConfigDescription("Experimental Mutation settings. Mutations are off by default and must be enabled by the server owner.")
public class MutationConfig {
  private static final Object CONFIG_LOCK = new Object();
  private static volatile MutationConfig config;

  @ConfigDoc(value = "Turns on the experimental Mutation feature.", impact = "Saved choices stay intact while the feature is off.")
  private boolean enabled = false;
  @ConfigDoc("Player level needed for the first Mutation slot.")
  private int slotOneUnlockLevel = 25;
  @ConfigDoc("Player level needed for the second Mutation slot.")
  private int slotTwoUnlockLevel = 50;
  @ConfigDoc("Player level at which active Mutations lose their drawbacks.")
  private int perfectAdaptationLevel = 200;
  @ConfigDoc("Allows level 200 to remove Mutation drawbacks.")
  private boolean perfectAdaptationEnabled = true;
  @ConfigDoc("Required level for one learned Adaptation in each of the two skill groups.")
  private int minimumAdaptationLevel = 1;
  @ConfigDoc("Wait time after changing either Mutation slot.")
  private long switchCooldownMillis = 600_000L;
  @ConfigDoc("How long combat blocks Mutation changes.")
  private long combatLockMillis = 10_000L;
  @ConfigDoc("Allows players to change Mutations at an Adapt bookshelf.")
  private boolean switchingEnabled = true;
  @ConfigDoc("Makes each first choice permanent until an administrator changes it.")
  private boolean permanentSelection;
  @ConfigDoc("Allows Mutation control effects in PvP.")
  private boolean pvpEnabled = true;
  @ConfigDoc("Allows group Mutation effects when each player has opted in.")
  private boolean cooperativeEffectsEnabled = true;
  @ConfigDoc("How players opt in to group Mutation effects.")
  private ConsentMode cooperativeConsentMode = ConsentMode.EXPLICIT;
  @ConfigDoc("How long a bookshelf interaction allows Mutation changes.")
  private long bookshelfTokenMillis = 60_000L;
  @ConfigDoc("How far a player may move from the bookshelf while changing Mutations.")
  private double bookshelfMaximumDistance = 8D;
  @ConfigDoc("Turns on Mutation particles.")
  private boolean particlesEnabled = true;
  @ConfigDoc("Turns on Mutation sounds.")
  private boolean soundsEnabled = true;
  @ConfigDoc("Worlds where Mutations do not work.")
  private List<String> worldBlacklist = new ArrayList<>();
  @ConfigDoc("Which skills belong to each Mutation skill group.")
  private Map<String, List<String>> domainMembership = defaultDomainMembership();

  private GaleLung galeLung = new GaleLung();
  private BastionSpine bastionSpine = new BastionSpine();
  private VerdantMolt verdantMolt = new VerdantMolt();
  private Temperbound temperbound = new Temperbound();
  private ParadoxScar paradoxScar = new ParadoxScar();
  private ArsenalCortex arsenalCortex = new ArsenalCortex();
  private Packmind packmind = new Packmind();
  private TrophyCrucible trophyCrucible = new TrophyCrucible();
  private UmbralEcho umbralEcho = new UmbralEcho();
  private LivingLattice livingLattice = new LivingLattice();
  private MasterworkBond masterworkBond = new MasterworkBond();
  private Deepblood deepblood = new Deepblood();
  private MycelialNerve mycelialNerve = new MycelialNerve();
  private Gravebloom gravebloom = new Gravebloom();
  private ResonantFormula resonantFormula = new ResonantFormula();

  public static MutationConfig get() {
    synchronized (CONFIG_LOCK) {
      if (config == null) {
        config = load(new MutationConfig(), true);
      }
      return config;
    }
  }

  public static MutationConfig defaults() {
    MutationConfig defaults = new MutationConfig();
    defaults.normalize();
    return defaults;
  }

  public static boolean reload() {
    synchronized (CONFIG_LOCK) {
      try {
        config = load(config == null ? new MutationConfig() : config, false);
        return true;
      } catch (Throwable error) {
        error.printStackTrace();
        return false;
      }
    }
  }

  public static boolean reloadSnapshot(String raw, File sourceFile) {
    synchronized (CONFIG_LOCK) {
      try {
        config = ConfigFileSupport.parseSnapshot(
            raw,
            sourceFile,
            MutationConfig.class,
            "mutations",
            MutationConfig::normalize
        );
        return true;
      } catch (Throwable error) {
        error.printStackTrace();
        return false;
      }
    }
  }

  public MutationProgression progression() {
    return new MutationProgression(
        slotOneUnlockLevel,
        slotTwoUnlockLevel,
        perfectAdaptationLevel,
        perfectAdaptationEnabled
    );
  }

  public Profile profile(MutationType type) {
    if (type == null) {
      return new Profile();
    }
    return switch (type) {
      case GALE_LUNG -> galeLung;
      case BASTION_SPINE -> bastionSpine;
      case VERDANT_MOLT -> verdantMolt;
      case TEMPERBOUND -> temperbound;
      case PARADOX_SCAR -> paradoxScar;
      case ARSENAL_CORTEX -> arsenalCortex;
      case PACKMIND -> packmind;
      case TROPHY_CRUCIBLE -> trophyCrucible;
      case UMBRAL_ECHO -> umbralEcho;
      case LIVING_LATTICE -> livingLattice;
      case MASTERWORK_BOND -> masterworkBond;
      case DEEPBLOOD -> deepblood;
      case MYCELIAL_NERVE -> mycelialNerve;
      case GRAVEBLOOM -> gravebloom;
      case RESONANT_FORMULA -> resonantFormula;
    };
  }

  public List<String> skills(MutationDomain domain) {
    if (domain == null) {
      return List.of();
    }
    List<String> configured = domainMembership.get(domain.name().toLowerCase(Locale.ROOT));
    return configured == null ? List.of() : List.copyOf(configured);
  }

  public boolean isWorldAllowed(WorldInfo world, MutationType type) {
    if (world == null) {
      return false;
    }
    String worldKey = WorldIdentity.serialize(world);
    if (worldBlacklist.contains(worldKey)) {
      return false;
    }
    return type == null || !profile(type).worldBlacklist.contains(worldKey);
  }

  private static MutationConfig load(MutationConfig fallback, boolean overwriteOnFailure) {
    if (Adapt.instance == null) {
      fallback.normalize();
      return fallback;
    }
    File canonical = Adapt.instance.getDataFile("adapt", "mutations.toml");
    File legacy = Adapt.instance.getDataFile("adapt", "mutations.json");
    try {
      MutationConfig loaded = ConfigFileSupport.load(
          canonical,
          legacy,
          MutationConfig.class,
          fallback,
          overwriteOnFailure,
          "mutations",
          "Created missing config [adapt/mutations.toml] from defaults."
      );
      loaded.normalize();
      return loaded;
    } catch (IOException error) {
      throw new IllegalStateException("Failed to load Mutation configuration", error);
    }
  }

  void normalize() {
    slotOneUnlockLevel = Math.max(0, slotOneUnlockLevel);
    slotTwoUnlockLevel = Math.max(slotOneUnlockLevel, slotTwoUnlockLevel);
    perfectAdaptationLevel = Math.max(slotTwoUnlockLevel, perfectAdaptationLevel);
    minimumAdaptationLevel = Math.max(1, minimumAdaptationLevel);
    switchCooldownMillis = clampDuration(switchCooldownMillis, 0L);
    combatLockMillis = clampDuration(combatLockMillis, 0L);
    bookshelfTokenMillis = Math.max(1_000L, Math.min(300_000L, bookshelfTokenMillis));
    bookshelfMaximumDistance = clamp(bookshelfMaximumDistance, 2D, 32D);
    worldBlacklist = normalizeWorldKeys(worldBlacklist, 256);
    domainMembership = normalizeDomains(domainMembership);
    cooperativeConsentMode = cooperativeConsentMode == null ? ConsentMode.EXPLICIT : cooperativeConsentMode;
    galeLung = galeLung == null ? new GaleLung() : galeLung;
    bastionSpine = bastionSpine == null ? new BastionSpine() : bastionSpine;
    verdantMolt = verdantMolt == null ? new VerdantMolt() : verdantMolt;
    temperbound = temperbound == null ? new Temperbound() : temperbound;
    paradoxScar = paradoxScar == null ? new ParadoxScar() : paradoxScar;
    arsenalCortex = arsenalCortex == null ? new ArsenalCortex() : arsenalCortex;
    packmind = packmind == null ? new Packmind() : packmind;
    trophyCrucible = trophyCrucible == null ? new TrophyCrucible() : trophyCrucible;
    umbralEcho = umbralEcho == null ? new UmbralEcho() : umbralEcho;
    livingLattice = livingLattice == null ? new LivingLattice() : livingLattice;
    masterworkBond = masterworkBond == null ? new MasterworkBond() : masterworkBond;
    deepblood = deepblood == null ? new Deepblood() : deepblood;
    mycelialNerve = mycelialNerve == null ? new MycelialNerve() : mycelialNerve;
    gravebloom = gravebloom == null ? new Gravebloom() : gravebloom;
    resonantFormula = resonantFormula == null ? new ResonantFormula() : resonantFormula;
    for (MutationType type : MutationType.values()) {
      profile(type).normalize();
    }
  }

  private static Map<String, List<String>> defaultDomainMembership() {
    LinkedHashMap<String, List<String>> defaults = new LinkedHashMap<>();
    for (MutationDomain domain : MutationDomain.values()) {
      defaults.put(domain.name().toLowerCase(Locale.ROOT), MutationCatalog.defaults().domainSkills(domain));
    }
    return defaults;
  }

  private static Map<String, List<String>> normalizeDomains(Map<String, List<String>> configured) {
    LinkedHashMap<String, List<String>> normalized = new LinkedHashMap<>();
    Map<String, List<String>> source = configured == null ? Map.of() : configured;
    for (MutationDomain domain : MutationDomain.values()) {
      String key = domain.name().toLowerCase(Locale.ROOT);
      List<String> values = source.containsKey(key)
          ? source.get(key)
          : MutationCatalog.defaults().domainSkills(domain);
      normalized.put(key, normalizeStrings(values, 64));
    }
    return normalized;
  }

  private static List<String> normalizeStrings(List<String> values, int cap) {
    ArrayList<String> normalized = new ArrayList<>();
    if (values == null) {
      return normalized;
    }
    for (String value : values) {
      if (value == null || value.isBlank()) {
        continue;
      }
      String clean = value.trim().toLowerCase(Locale.ROOT);
      if (!normalized.contains(clean)) {
        normalized.add(clean);
        if (normalized.size() >= cap) {
          break;
        }
      }
    }
    return normalized;
  }

  private static List<String> normalizeWorldKeys(List<String> values, int cap) {
    ArrayList<String> normalized = new ArrayList<>();
    if (values == null) {
      return normalized;
    }
    for (String value : values) {
      if (value == null || value.isBlank()) {
        continue;
      }
      String worldKey = WorldIdentity.parse(value).toString();
      if (!normalized.contains(worldKey)) {
        normalized.add(worldKey);
        if (normalized.size() >= cap) {
          break;
        }
      }
    }
    return normalized;
  }

  private static double clamp(double value, double minimum, double maximum) {
    if (!Double.isFinite(value)) {
      return minimum;
    }
    return Math.max(minimum, Math.min(maximum, value));
  }

  private static long clampDuration(long value, long minimum) {
    return Math.max(minimum, Math.min(MutationLimits.MAX_DURATION_MILLIS, value));
  }

  private static int clampTicks(int value, int minimum) {
    return Math.max(minimum, Math.min(MutationLimits.MAX_DELAY_TICKS, value));
  }

  public enum ConsentMode {
    EXPLICIT,
    PARTY,
    FRIEND,
    DISABLED
  }

  @Getter
  public static class Profile {
    @ConfigDoc("Turns this Mutation on after the experimental feature is enabled.")
    private boolean enabled = true;
    @ConfigDoc("Allows this Mutation to affect player-versus-player combat.")
    private boolean pvpEnabled = true;
    @ConfigDoc("Shows this Mutation's particles.")
    private boolean particlesEnabled = true;
    @ConfigDoc("Plays this Mutation's sounds.")
    private boolean soundsEnabled = true;
    @ConfigDoc("Worlds where this Mutation does not work.")
    private List<String> worldBlacklist = new ArrayList<>();
    @ConfigDoc("Other Mutation IDs that cannot be equipped with this one.")
    private List<String> conflicts = new ArrayList<>();

    private void normalize() {
      worldBlacklist = normalizeWorldKeys(worldBlacklist, 256);
      conflicts = normalizeStrings(conflicts, MutationType.values().length);
      normalizeValues();
    }

    protected void normalizeValues() {
    }
  }

  @Getter
  public static final class GaleLung extends Profile {
    @ConfigDoc("Maximum Momentum charge.")
    private double maximumMomentum = 100D;
    @ConfigDoc("Momentum gained per block while sprinting.")
    private double sprintMomentumPerBlock = 8D;
    @ConfigDoc("Momentum gained per block while airborne.")
    private double airborneMomentumPerBlock = 4D;
    @ConfigDoc("How quickly Momentum empties after the player stops moving.")
    private long stationaryVentMillis = 1_250L;
    @ConfigDoc("Knockback taken at full Momentum. 1.35 means 35 percent more.")
    private double burdenKnockbackMultiplier = 1.35D;
    @ConfigDoc("How far the player moves around a target after a charged melee hit.")
    private double meleeFlankDistance = 1.5D;
    @ConfigDoc("How far a charged projectile moves its target.")
    private double projectileDisplacement = 0.45D;

    @Override
    protected void normalizeValues() {
      maximumMomentum = clamp(maximumMomentum, 1D, MutationLimits.GALE_MOMENTUM);
      sprintMomentumPerBlock = clamp(sprintMomentumPerBlock, 0D, maximumMomentum);
      airborneMomentumPerBlock = clamp(airborneMomentumPerBlock, 0D, maximumMomentum);
      stationaryVentMillis = clampDuration(stationaryVentMillis, 100L);
      burdenKnockbackMultiplier = clamp(burdenKnockbackMultiplier, 1D, 2D);
      meleeFlankDistance = clamp(meleeFlankDistance, 0D, 3D);
      projectileDisplacement = clamp(projectileDisplacement, 0D, 1.5D);
    }
  }

  @Getter
  public static final class BastionSpine extends Profile {
    @ConfigDoc("How long the player must stand still before bracing.")
    private long anchorChargeMillis = 1_500L;
    @ConfigDoc("Maximum force stored while braced.")
    private double maximumStability = 8D;
    @ConfigDoc("Stored force gained per point of incoming damage.")
    private double stabilityPerDamage = 0.5D;
    @ConfigDoc("Range of the forward push.")
    private double waveRange = 5D;
    @ConfigDoc("Width of the forward push in degrees.")
    private double waveAngleDegrees = 90D;
    @ConfigDoc("Maximum push speed applied to a target.")
    private double maximumVelocity = 0.85D;
    @ConfigDoc("Maximum targets hit by one push.")
    private int maximumTargets = 12;

    @Override
    protected void normalizeValues() {
      anchorChargeMillis = clampDuration(anchorChargeMillis, 250L);
      maximumStability = clamp(maximumStability, 1D, MutationLimits.BASTION_STABILITY);
      stabilityPerDamage = clamp(stabilityPerDamage, 0.01D, 4D);
      waveRange = clamp(waveRange, 1D, 12D);
      waveAngleDegrees = clamp(waveAngleDegrees, 15D, 180D);
      maximumVelocity = clamp(maximumVelocity, 0.1D, 1.5D);
      maximumTargets = Math.max(1, Math.min(MutationLimits.BASTION_TARGETS, maximumTargets));
    }
  }

  @Getter
  public static final class VerdantMolt extends Profile {
    @ConfigDoc("Ticks the player must crouch and hold still before cleansing.")
    private int chargeTicks = 50;
    @ConfigDoc("Wait time between cleanses.")
    private long cooldownMillis = 90_000L;
    @ConfigDoc("Saturation removed when cleansing before level 200.")
    private double saturationCost = 6D;
    @ConfigDoc("Ticks during which new effects are blocked after cleansing.")
    private int recoveryTicks = 40;
    @ConfigDoc("Maximum potion effects checked by one cleanse.")
    private int maximumEffects = 32;

    @Override
    protected void normalizeValues() {
      chargeTicks = clampTicks(chargeTicks, 10);
      cooldownMillis = clampDuration(cooldownMillis, 0L);
      saturationCost = clamp(saturationCost, 0D, 20D);
      recoveryTicks = clampTicks(recoveryTicks, 1);
      maximumEffects = Math.max(1, Math.min(MutationLimits.VERDANT_EFFECTS, maximumEffects));
    }
  }

  @Getter
  public static final class Temperbound extends Profile {
    @ConfigDoc("How long linked armor stops working after a piece is removed or swapped.")
    private long rejectionMillis = 30_000L;

    @Override
    protected void normalizeValues() {
      rejectionMillis = clampDuration(rejectionMillis, 0L);
    }
  }

  @Getter
  public static final class ParadoxScar extends Profile {
    @ConfigDoc("Minimum move or teleport distance needed to create a Return Point.")
    private double minimumDistance = 8D;
    @ConfigDoc("How long a Return Point remains available.")
    private long echoLifetimeMillis = 12_000L;
    @ConfigDoc("Maximum distance a player may return.")
    private double maximumReturnDistance = 64D;
    @ConfigDoc("Delay before an enemy-damaged Return Point breaks.")
    private int hostileCollapseTicks = 60;

    @Override
    protected void normalizeValues() {
      minimumDistance = clamp(minimumDistance, 1D, 64D);
      echoLifetimeMillis = clampDuration(echoLifetimeMillis, 1_000L);
      maximumReturnDistance = clamp(maximumReturnDistance, minimumDistance, 128D);
      hostileCollapseTicks = clampTicks(hostileCollapseTicks, 1);
    }
  }

  @Getter
  public static final class ArsenalCortex extends Profile {
    @ConfigDoc("Time allowed between different weapon or tool types in a combo.")
    private long chainTimeoutMillis = 5_000L;
    @ConfigDoc("Maximum number of steps stored in a combo.")
    private int maximumChain = 4;
    @ConfigDoc("How long repeating the same type stops the combo.")
    private long dullnessMillis = 3_000L;

    @Override
    protected void normalizeValues() {
      chainTimeoutMillis = clampDuration(chainTimeoutMillis, 250L);
      maximumChain = Math.max(2, Math.min(MutationLimits.ARSENAL_CHAIN, maximumChain));
      dullnessMillis = clampDuration(dullnessMillis, 0L);
    }
  }

  @Getter
  public static final class Packmind extends Profile {
    @ConfigDoc("How long a marked target remains marked.")
    private long quarryMillis = 20_000L;
    @ConfigDoc("Maximum distance for pets and opted-in players to help.")
    private double participationRange = 16D;
    @ConfigDoc("Maximum teamwork charge.")
    private int maximumTempo = 6;
    @ConfigDoc("Maximum pets and players counted for one target.")
    private int maximumMembers = 8;
    @ConfigDoc("Owner damage before someone helps. 0.8 means 20 percent less.")
    private double waitingDamageFactor = 0.8D;

    @Override
    protected void normalizeValues() {
      quarryMillis = clampDuration(quarryMillis, 1_000L);
      participationRange = clamp(participationRange, 2D, 32D);
      maximumTempo = Math.max(1, Math.min(MutationLimits.PACK_TEMPO, maximumTempo));
      maximumMembers = Math.max(1, Math.min(MutationLimits.PACK_MEMBERS, maximumMembers));
      waitingDamageFactor = clamp(waitingDamageFactor, 0.1D, 1D);
    }
  }

  @Getter
  public static final class TrophyCrucible extends Profile {
    @ConfigDoc("How long a prepared trophy remains ready.")
    private long imprintLifetimeMillis = 1_800_000L;
    @ConfigDoc("Extra distance from which the matching mob type can notice the player.")
    private double recognitionRange = 16D;

    @Override
    protected void normalizeValues() {
      imprintLifetimeMillis = clampDuration(imprintLifetimeMillis, 1_000L);
      recognitionRange = clamp(recognitionRange, 2D, 32D);
    }
  }

  @Getter
  public static final class UmbralEcho extends Profile {
    @ConfigDoc("Angle change that counts as attacking from a new side.")
    private double angleBucketDegrees = 45D;
    @ConfigDoc("How long the last angle and weapon type are remembered.")
    private long techniqueMemoryMillis = 5_000L;
    @ConfigDoc("Delay before the repeated control effect occurs.")
    private int echoDelayTicks = 8;
    @ConfigDoc("How long repeating the same approach reveals the player.")
    private int exposureTicks = 60;
    @ConfigDoc("Maximum enemy attack histories stored per player.")
    private int maximumTargetMemories = 8;

    @Override
    protected void normalizeValues() {
      angleBucketDegrees = clamp(angleBucketDegrees, 15D, 180D);
      techniqueMemoryMillis = clampDuration(techniqueMemoryMillis, 250L);
      echoDelayTicks = clampTicks(echoDelayTicks, 1);
      exposureTicks = clampTicks(exposureTicks, 1);
      maximumTargetMemories = Math.max(1, Math.min(MutationLimits.UMBRAL_TARGETS, maximumTargetMemories));
    }
  }

  @Getter
  public static final class LivingLattice extends Profile {
    @ConfigDoc("Maximum Root Charge earned from harvesting and replanting.")
    private double maximumRootCharge = 12D;
    @ConfigDoc("Maximum blocks placed in a temporary path.")
    private int pathLength = 5;
    @ConfigDoc("How long temporary path blocks remain.")
    private long blockLifetimeMillis = 15_000L;
    @ConfigDoc("How long early collapse blocks another path.")
    private long collapseLockMillis = 4_000L;
    @ConfigDoc("Maximum temporary path blocks tracked per player.")
    private int maximumBlocks = 16;
    @ConfigDoc("Maximum temporary paths tracked per player.")
    private int maximumStructures = 3;

    @Override
    protected void normalizeValues() {
      maximumRootCharge = clamp(maximumRootCharge, 1D, MutationLimits.LATTICE_ROOT_CHARGE);
      pathLength = Math.max(1, Math.min(8, pathLength));
      blockLifetimeMillis = clampDuration(blockLifetimeMillis, 1_000L);
      collapseLockMillis = clampDuration(collapseLockMillis, 0L);
      maximumBlocks = Math.max(1, Math.min(MutationLimits.LATTICE_BLOCKS, maximumBlocks));
      maximumStructures = Math.max(1, Math.min(MutationLimits.LATTICE_STRUCTURES, maximumStructures));
    }
  }

  @Getter
  public static final class MasterworkBond extends Profile {
    @ConfigDoc("Wait time before linking a replacement Masterwork tool.")
    private long abandonCooldownMillis = 86_400_000L;

    @Override
    protected void normalizeValues() {
      abandonCooldownMillis = clampDuration(abandonCooldownMillis, 0L);
    }
  }

  @Getter
  public static final class Deepblood extends Profile {
    @ConfigDoc("Highest Y level where natural mining earns Deep Charge.")
    private int maximumDepthY = 16;
    @ConfigDoc("Deep Charge earned per natural block mined.")
    private double ichorPerBlock = 1D;
    @ConfigDoc("Maximum Deep Charge stored.")
    private double maximumIchor = 100D;
    @ConfigDoc("Deep Charge spent for one underground natural-healing step.")
    private double regenerationCost = 4D;
    @ConfigDoc("Deep Charge spent to stop the linked tool from breaking.")
    private double toolPreservationCost = 25D;
    @ConfigDoc("Time above ground for stored Deep Charge to fall by half.")
    private long aboveGroundHalfLifeMillis = 300_000L;

    @Override
    protected void normalizeValues() {
      ichorPerBlock = clamp(ichorPerBlock, 0D, 100D);
      maximumIchor = clamp(maximumIchor, 1D, MutationLimits.DEEPBLOOD_ICHOR);
      regenerationCost = clamp(regenerationCost, 0D, maximumIchor);
      toolPreservationCost = clamp(toolPreservationCost, 0D, maximumIchor);
      maximumDepthY = Math.max(-2_048, Math.min(2_048, maximumDepthY));
      aboveGroundHalfLifeMillis = clampDuration(aboveGroundHalfLifeMillis, 1_000L);
    }
  }

  @Getter
  public static final class MycelialNerve extends Profile {
    @ConfigDoc("Maximum distance for sharing a helpful effect.")
    private double range = 16D;
    @ConfigDoc("Shared effect duration. 0.5 means half of the original time.")
    private double copiedDurationFactor = 0.5D;
    @ConfigDoc("Owner effect duration before level 200. 0.75 means three quarters.")
    private double rootDurationFactor = 0.75D;
    @ConfigDoc("Maximum pets and opted-in players receiving one effect.")
    private int maximumRecipients = 8;
    @ConfigDoc("How long fire stops effect sharing.")
    private long reconnectLockMillis = 5_000L;

    @Override
    protected void normalizeValues() {
      range = clamp(range, 2D, 32D);
      copiedDurationFactor = clamp(copiedDurationFactor, 0.05D, 1D);
      rootDurationFactor = clamp(rootDurationFactor, 0.05D, 1D);
      maximumRecipients = Math.max(1, Math.min(MutationLimits.MYCELIAL_RECIPIENTS, maximumRecipients));
      reconnectLockMillis = clampDuration(reconnectLockMillis, 0L);
    }
  }

  @Getter
  public static final class Gravebloom extends Profile {
    @ConfigDoc("How long each flower remains active.")
    private long lifetimeMillis = 20_000L;
    @ConfigDoc("Range in which a flower helps crops and animals.")
    private double radius = 6D;
    @ConfigDoc("Maximum active flowers per player.")
    private int maximumBlooms = 3;
    @ConfigDoc("Natural healing while a flower is active. 0.5 means half healing.")
    private double regenerationFactor = 0.5D;
    @ConfigDoc("Ticks between flower pulses.")
    private int pulseTicks = 20;
    @ConfigDoc("Maximum crops checked by one flower pulse.")
    private int maximumCrops = 16;
    @ConfigDoc("Maximum animals checked by one flower pulse.")
    private int maximumAnimals = 8;

    @Override
    protected void normalizeValues() {
      lifetimeMillis = clampDuration(lifetimeMillis, 1_000L);
      radius = clamp(radius, 1D, 12D);
      maximumBlooms = Math.max(1, Math.min(MutationLimits.GRAVEBLOOM_BLOOMS, maximumBlooms));
      regenerationFactor = clamp(regenerationFactor, 0D, 1D);
      pulseTicks = clampTicks(pulseTicks, 5);
      maximumCrops = Math.max(1, Math.min(MutationLimits.GRAVEBLOOM_CROPS, maximumCrops));
      maximumAnimals = Math.max(1, Math.min(MutationLimits.GRAVEBLOOM_ANIMALS, maximumAnimals));
    }
  }

  @Getter
  public static final class ResonantFormula extends Profile {
    @ConfigDoc("Time allowed to craft, brew, and enchant once each.")
    private long sigilLifetimeMillis = 600_000L;
    @ConfigDoc("How long a repeated activity blocks new progress.")
    private long collapseLockMillis = 30_000L;
    @ConfigDoc("Strength of the repeated control effect. 0.5 means half strength.")
    private double echoFactor = 0.5D;
    @ConfigDoc("Delay before the repeated control effect occurs.")
    private int echoDelayTicks = 10;

    @Override
    protected void normalizeValues() {
      sigilLifetimeMillis = clampDuration(sigilLifetimeMillis, 1_000L);
      collapseLockMillis = clampDuration(collapseLockMillis, 0L);
      echoFactor = clamp(echoFactor, 0.05D, 1D);
      echoDelayTicks = clampTicks(echoDelayTicks, 1);
    }
  }
}
