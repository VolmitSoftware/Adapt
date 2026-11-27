package art.arcane.adapt.engine.bridge;

import art.arcane.adapt.AdaptCore;
import art.arcane.adapt.api.content.Adaptation;
import art.arcane.adapt.api.content.SimpleAdaptation;
import art.arcane.adapt.api.content.SimpleSkill;
import art.arcane.adapt.api.internal.AdaptationRuntime;
import art.arcane.adapt.api.internal.ComponentHooks;
import art.arcane.adapt.api.internal.SkillRuntime;
import art.arcane.adapt.api.mutation.BlockKey;
import art.arcane.adapt.api.mutation.BlockSlot;
import art.arcane.adapt.api.mutation.MutationBlock;
import art.arcane.adapt.api.mutation.MutationKit;
import art.arcane.adapt.api.mutation.SkillUpgrade;
import art.arcane.adapt.api.mutation.UpgradeDeclaration;
import art.arcane.adapt.api.text.TextKeys;
import art.arcane.adapt.engine.adaptation.AdaptationHost;
import art.arcane.adapt.engine.addon.BuildScope;
import art.arcane.adapt.engine.component.ComponentHost;
import art.arcane.adapt.engine.mutation.MutatedSkill;
import art.arcane.adapt.engine.mutation.kit.Kits;
import art.arcane.adapt.engine.mutation.upgrade.UpgradeCaches;
import art.arcane.adapt.engine.skill.SkillHost;
import art.arcane.adapt.engine.support.PlatformSupport;
import art.arcane.adapt.platform.value.Id;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Builds content from the suppliers its addon declared. A skill or adaptation attaches from its constructor; the host
 * made at attach is checked against the instance once the supplier returns, then the skill's adaptations and mutation
 * pieces are built with the skill as their context.
 */
public final class ComponentBuilder {
  private static final String UPGRADE = "/upgrade/";
  private static final ThreadLocal<SkillHost> BUILT_SKILL = new ThreadLocal<>();
  private static final ThreadLocal<AdaptationHost> BUILT_ADAPTATION = new ThreadLocal<>();
  private static final ThreadLocal<SkillHost> BUILDING_FOR = new ThreadLocal<>();
  private static final ThreadLocal<Id> BUILDING_FOR_ID = new ThreadLocal<>();

  private ComponentBuilder() {
  }

  /** Builds a skill and everything it declares; the registry places what this returns. */
  public static <S extends SimpleSkill<?>> S buildSkill(Supplier<S> factory) {
    Objects.requireNonNull(factory, "factory");
    SkillHost previous = BUILT_SKILL.get();
    BUILT_SKILL.remove();
    S built;
    SkillHost host;
    try {
      built = factory.get();
      host = BUILT_SKILL.get();
    } finally {
      restore(BUILT_SKILL, previous);
    }
    if (built == null) {
      throw new IllegalStateException("A skill factory of " + BuildScope.owner() + " returned null");
    }
    if (host == null || host.skill() != built) {
      throw new IllegalStateException(built.getClass().getName()
          + " was not built by its own supplier; a factory must return the skill it constructs");
    }
    for (SimpleSkill.AdaptationEntry entry : built.getDeclaredAdaptations()) {
      SimpleAdaptation<?> adaptation = buildFor(host, host.getId(), entry.factory());
      if (!adaptation.getId().equals(entry.id())) {
        adaptation.unregister();
        throw new IllegalStateException(built.getClass().getName() + " declares adaptation " + entry.id()
            + " but its factory built " + adaptation.getId());
      }
      place(host, adaptation);
    }
    if (!(built instanceof MutatedSkill)) {
      buildKit(host, built);
    }
    UpgradeCaches.forget();
    return built;
  }

  /** Builds the upgrades and the six blocks of the skill's kit, registered or generated, with the skill as context. */
  private static void buildKit(SkillHost host, SimpleSkill<?> built) {
    MutationKit kit = Kits.of(built);
    Set<Id> declared = new HashSet<>();
    for (SimpleSkill.AdaptationEntry entry : built.getDeclaredAdaptations()) {
      declared.add(entry.id());
    }
    for (UpgradeDeclaration declaration : kit.upgrades()) {
      for (Id source : declaration.sources()) {
        if (!declared.contains(source)) {
          throw new IllegalStateException(built.getClass().getName() + " declares upgrade " + declaration.id()
              + " fusing " + source + ", which is not one of its adaptations");
        }
      }
      SkillUpgrade<?> upgrade = buildFor(host, host.getId(), declaration.factory());
      if (!upgrade.getId().equals(declaration.id())) {
        upgrade.unregister();
        throw new IllegalStateException(built.getClass().getName() + " declares upgrade " + declaration.id()
            + " but its factory built " + upgrade.getId());
      }
      if (usable("adaptation", host, upgrade)) {
        AdaptationHost.of(upgrade).placeOn(host);
        host.addUpgrade(upgrade);
      }
    }
    for (BlockSlot slot : BlockSlot.values()) {
      MutationBlock<?> block = buildFor(host, host.getId(), kit.block(slot));
      if (!block.getKey().equals(new BlockKey(host.getId(), slot))) {
        block.unregister();
        throw new IllegalStateException(built.getClass().getName() + " declares a " + slot + " block that is "
            + block.getKey());
      }
      if (Id.ADAPT.equals(block.getId().namespace())) {
        block.setLocalizationKey(Id.ADAPT + ".mutated.block." + host.getId().path() + "."
            + slot.name().toLowerCase(Locale.ROOT));
      }
      AdaptationHost.of(block).placeOn(host);
      host.addBlock(block, usable("mutation-block", host, block));
    }
  }

  /** Builds an adaptation another addon places on {@code skill}; the registry places it. */
  public static <A extends SimpleAdaptation<?>> A buildAdaptation(Id skill, Supplier<A> factory) {
    return buildFor(null, Objects.requireNonNull(skill, "skill"), Objects.requireNonNull(factory, "factory"));
  }

  /** Builds an adaptation the engine makes for {@code skill} itself, such as a mutated skill's adaptations. */
  public static <A extends SimpleAdaptation<?>> A buildFor(SkillHost skill, Supplier<A> factory) {
    return buildFor(skill, skill.getId(), factory);
  }

  /** Lists {@code adaptation} on {@code skill} unless the running platform cannot run it. */
  public static boolean place(SkillHost skill, Adaptation<?> adaptation) {
    if (!usable("adaptation", skill, adaptation)) {
      return false;
    }
    AdaptationHost.of(adaptation).placeOn(skill);
    skill.addAdaptation(adaptation);
    UpgradeCaches.forget();
    return true;
  }

  private static boolean usable(String kind, SkillHost skill, Adaptation<?> adaptation) {
    PlatformSupport.Verdict verdict = PlatformSupport.check(adaptation.getPlatformNeeds());
    if (verdict.usable()) {
      return true;
    }
    PlatformSupport.leaveOut(kind, skill.getLineKey(), adaptation.getId().toString(), verdict);
    ComponentHost.of(adaptation).retireComponent();
    return false;
  }

  static SkillRuntime attach(SimpleSkill<?> skill, ComponentHooks hooks) {
    if (BUILT_SKILL.get() != null) {
      throw new IllegalStateException(skill.getClass().getName() + " constructs a second skill inside its supplier");
    }
    SkillHost host = new SkillHost(skill, hooks, BuildScope.owner(), core());
    BUILT_SKILL.set(host);
    return host;
  }

  static AdaptationRuntime attach(SimpleAdaptation<?> adaptation, ComponentHooks hooks) {
    if (BUILT_ADAPTATION.get() != null) {
      throw new IllegalStateException(adaptation.getClass().getName()
          + " constructs a second adaptation inside its supplier");
    }
    Id skill = BUILDING_FOR_ID.get();
    if (skill == null) {
      throw new IllegalStateException(adaptation.getClass().getName()
          + " is constructed outside a supplier Adapt is running");
    }
    if (Id.ADAPT.equals(adaptation.getId().namespace())
        && adaptation.getLocalizationKey().equals(TextKeys.adaptation(adaptation.getId()))) {
      adaptation.setLocalizationKey(builtInKey(adaptation.getId()));
    }
    AdaptationHost host = new AdaptationHost(adaptation, hooks, BuildScope.owner(), core(), skill,
        BUILDING_FOR.get());
    BUILT_ADAPTATION.set(host);
    return host;
  }

  private static <A extends SimpleAdaptation<?>> A buildFor(@Nullable SkillHost skill, Id skillId,
                                                            Supplier<A> factory) {
    AdaptationHost previous = BUILT_ADAPTATION.get();
    SkillHost previousSkill = BUILDING_FOR.get();
    Id previousSkillId = BUILDING_FOR_ID.get();
    BUILT_ADAPTATION.remove();
    restore(BUILDING_FOR, skill);
    BUILDING_FOR_ID.set(skillId);
    A built;
    AdaptationHost host;
    try {
      built = factory.get();
      host = BUILT_ADAPTATION.get();
    } finally {
      restore(BUILT_ADAPTATION, previous);
      restore(BUILDING_FOR, previousSkill);
      restore(BUILDING_FOR_ID, previousSkillId);
    }
    if (built == null) {
      throw new IllegalStateException("An adaptation factory of " + BuildScope.owner() + " returned null");
    }
    if (host == null || host.adaptation() != built) {
      throw new IllegalStateException(built.getClass().getName()
          + " was not built by its own supplier; a factory must return the adaptation it constructs");
    }
    return built;
  }

  /**
   * The message key prefix of a built-in adaptation: the built-in bundle names {@code adapt:herbalism/green_thumb}'s
   * messages {@code herbalism.green_thumb.*}, without the {@code adaptation.} segment the public convention adds, and
   * an upgrade {@code adapt:swords/upgrade/witherblade}'s {@code upgrade.swords.witherblade.*}.
   */
  public static String builtInKey(Id adaptation) {
    String path = adaptation.path();
    int upgrade = path.indexOf(UPGRADE);
    if (upgrade > 0 && path.indexOf('/') == upgrade && path.indexOf('/', upgrade + UPGRADE.length()) < 0) {
      return Id.ADAPT + ".upgrade." + path.substring(0, upgrade) + "." + path.substring(upgrade + UPGRADE.length());
    }
    return Id.ADAPT + "." + path.replace('/', '.');
  }

  private static <T> void restore(ThreadLocal<T> local, @Nullable T previous) {
    if (previous == null) {
      local.remove();
    } else {
      local.set(previous);
    }
  }

  private static AdaptCore core() {
    return AdaptCore.get();
  }

}
