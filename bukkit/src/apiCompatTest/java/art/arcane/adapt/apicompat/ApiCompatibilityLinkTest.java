package art.arcane.adapt.apicompat;

import art.arcane.adapt.api.ability.AbilityContext;
import art.arcane.adapt.api.ability.AbilityCostContext;
import art.arcane.adapt.api.ability.AbilityCostKind;
import art.arcane.adapt.api.ability.AbilityCostProvider;
import art.arcane.adapt.api.ability.AbilityQuote;
import art.arcane.adapt.api.ability.AbilityUseDecision;
import art.arcane.adapt.api.ability.AbilityUsePolicy;
import art.arcane.adapt.api.adaptation.Adaptation;
import art.arcane.adapt.api.potion.AdaptBrewCompleteEvent;
import art.arcane.adapt.api.potion.BrewingRecipe;
import art.arcane.adapt.api.potion.PotionBuilder;
import art.arcane.adapt.api.skill.Skill;
import art.arcane.adapt.api.world.AdaptPlayer;
import art.arcane.adapt.content.event.AdaptAdaptationTeleportEvent;
import art.arcane.adapt.content.event.AdaptAdaptationUseEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.ServicesManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.mockito.MockedStatic;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The sample addon in src/apiCompatSample is compiled against the pre-port API (a stub jar written from
 * api-baseline.txt) and loaded here against the built plugin jar: every Adapt call site must resolve, and the calls
 * must behave like the pre-port API.
 */
class ApiCompatibilityLinkTest {
  private static final String ADDON = "com.example.adaptaddon.";
  private static URLClassLoader addon;
  private static List<Path> addonRoots;

  @BeforeAll
  static void loadAddon() throws IOException {
    addonRoots = new ArrayList<>();
    List<URL> urls = new ArrayList<>();
    for (String root : System.getProperty("adapt.apicompat.sample").split(File.pathSeparator)) {
      Path path = Path.of(root);
      addonRoots.add(path);
      urls.add(path.toUri().toURL());
    }
    addon = new URLClassLoader(urls.toArray(new URL[0]), ApiCompatibilityLinkTest.class.getClassLoader());
  }

  @AfterAll
  static void closeAddon() throws IOException {
    addon.close();
  }

  @Test
  void everyAdaptCallSiteInTheAddonResolvesAgainstThePluginJar() throws IOException {
    List<String> failures = new ArrayList<>();
    Set<String> sites = new LinkedHashSet<>();
    for (ClassNode node : addonClasses()) {
      for (MethodNode method : node.methods) {
        for (AbstractInsnNode instruction : method.instructions) {
          if (instruction instanceof MethodInsnNode call && call.owner.startsWith("art/arcane/adapt/")) {
            String site = call.owner + "." + call.name + call.desc;
            if (sites.add(site)) {
              resolve(call).ifPresent(failures::add);
            }
          } else if (instruction instanceof FieldInsnNode field && field.owner.startsWith("art/arcane/adapt/")) {
            String site = field.owner + "." + field.name + ":" + field.desc;
            if (sites.add(site)) {
              resolve(field).ifPresent(failures::add);
            }
          }
        }
      }
    }
    assertTrue(sites.size() >= 40, "the addon should exercise the API, found only " + sites.size() + " call sites");
    assertTrue(failures.isEmpty(), "unresolved pre-port API call sites:\n" + String.join("\n", failures));
  }

  @Test
  void addonClassesInitialiseAgainstThePluginJar() throws IOException {
    for (ClassNode node : addonClasses()) {
      try {
        Class.forName(node.name.replace('/', '.'), true, addon);
      } catch (ClassNotFoundException | LinkageError error) {
        fail(node.name + " does not load: " + error);
      }
    }
  }

  @Test
  void brewingRecipeTakesAndReturnsBukkitTypes() throws ReflectiveOperationException {
    ItemStack base = mock(ItemStack.class);
    ItemStack result = mock(ItemStack.class);
    BrewingRecipe recipe = (BrewingRecipe) call("AdaptAddon", "glowRecipe", base, result);

    assertEquals("addon-glow", recipe.getId());
    assertEquals(400, recipe.getBrewingTime());
    assertEquals(2, recipe.getFuelCost());
    assertEquals(Material.GLOWSTONE_DUST, call("AdaptAddon", "ingredient", recipe));
    assertSame(base, call("AdaptAddon", "basePotion", recipe));
    assertSame(result, call("AdaptAddon", "result", recipe));
  }

  @Test
  void potionBuilderKeepsBukkitColourNameLoreAndMaterial() throws ReflectiveOperationException {
    PotionBuilder builder = (PotionBuilder) call("AdaptAddon", "splash");

    assertEquals(PotionBuilder.Type.SPLASH, builder.getType());
    assertEquals(Color.fromRGB(10, 20, 30), call("AdaptAddon", "color", builder));
    assertEquals(Component.text("Swift"), call("AdaptAddon", "name", builder));
    assertEquals("Swift", builder.getName());
    assertEquals(1, call("AdaptAddon", "loreLines", builder));
    assertEquals(Material.SPLASH_POTION, call("AdaptAddon", "material", PotionBuilder.Type.SPLASH));
  }

  @Test
  void xpAwardTakesABukkitLocation() throws ReflectiveOperationException {
    World world = mock(World.class);
    UUID worldId = UUID.randomUUID();
    when(world.getUID()).thenReturn(worldId);
    Location location = new Location(world, 1, 64, -3);
    try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
      bukkit.when(() -> Bukkit.getWorld(worldId)).thenReturn(world);
      Object award = call("AdaptAddon", "questAward", location);

      assertEquals(location, call("AdaptAddon", "awardLocation", award));
      assertNull(call("AdaptAddon", "awardLocation", call("AdaptAddon", "questAward", (Location) null)));
    }
  }

  @Test
  void addonListenerReceivesTheThreeAdaptEvents() throws ReflectiveOperationException {
    Listener listener = (Listener) Class.forName(ADDON + "AddonListener", true, addon).getConstructor().newInstance();
    for (Method method : listener.getClass().getMethods()) {
      if (method.isAnnotationPresent(EventHandler.class)) {
        Class<?> event = method.getParameterTypes()[0];
        assertTrue(event.getMethod("getHandlerList").getReturnType().getName().equals("org.bukkit.event.HandlerList"));
      }
    }

    ItemStack result = mock(ItemStack.class);
    BrewingRecipe recipe = (BrewingRecipe) call("AdaptAddon", "glowRecipe", mock(ItemStack.class), result);
    UUID brewer = UUID.randomUUID();
    invoke(listener, "onBrew", new AdaptBrewCompleteEvent(mock(Block.class), recipe, brewer, 3));
    assertSame(result, invoke(listener, "lastBrewResult"));
    assertEquals(brewer, invoke(listener, "lastBrewer"));
    assertEquals(3, invoke(listener, "brewedPotions"));

    AdaptPlayer player = mock(AdaptPlayer.class);
    Adaptation<?> adaptation = adaptation("addon-blocked");
    AdaptAdaptationUseEvent use = new AdaptAdaptationUseEvent(false, player, adaptation);
    invoke(listener, "onUse", use);
    assertEquals("addon-blocked", invoke(listener, "lastAdaptation"));
    assertTrue(use.isCancelled());

    World world = mock(World.class);
    Location spot = new Location(world, 4, 70, 4);
    AdaptAdaptationTeleportEvent teleport = new AdaptAdaptationTeleportEvent(false, player, adaptation("rift-blink"),
        spot, spot.clone());
    invoke(listener, "onTeleport", teleport);
    assertEquals(spot, invoke(listener, "lastDestination"));
    assertTrue(teleport.isCancelled());
  }

  @Test
  void addonAbilityProvidersImplementTheCurrentInterfaces() throws ReflectiveOperationException {
    AbilityUsePolicy policy = (AbilityUsePolicy) Class.forName(ADDON + "AddonAbilityRules$UsePolicy", true, addon)
        .getConstructor().newInstance();
    AbilityCostProvider costs = (AbilityCostProvider) Class.forName(ADDON + "AddonAbilityRules$CostProvider", true,
        addon).getConstructor().newInstance();
    Player player = mock(Player.class);
    when(player.getUniqueId()).thenReturn(UUID.randomUUID());

    AbilityUseDecision blocked = policy.evaluate(AbilityContext.check("addon-blocked-dash", "agility", 2, player));
    AbilityUseDecision allowed = policy.evaluate(AbilityContext.check("dash", "agility", 2, player));
    assertFalse(blocked.allowed());
    assertTrue(allowed.allowed());
    assertEquals("adapt-addon", policy.providerId());

    AbilityCostContext cost = new AbilityCostContext(AbilityContext.check("dash", "agility", 2, player), "dash",
        AbilityCostKind.values()[0], Optional.empty(), 1);
    assertEquals(AbilityQuote.pass(), costs.quote(cost));

    Plugin plugin = mock(Plugin.class);
    ServicesManager services = mock(ServicesManager.class);
    try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
      bukkit.when(Bukkit::getServicesManager).thenReturn(services);
      call("AdaptAddon", "registerAbilityProviders", plugin, policy, costs);
    }
    verify(services).register(AbilityUsePolicy.class, policy, plugin, ServicePriority.Normal);
    verify(services).register(AbilityCostProvider.class, costs, plugin, ServicePriority.Normal);
  }

  @Test
  void xpAndLevelEntryPointsLinkWithBukkitPlayers() throws ReflectiveOperationException {
    Player player = mock(Player.class);
    when(player.getUniqueId()).thenReturn(UUID.randomUUID());
    Skill<?> skill = mock(Skill.class);

    callIgnoringRuntimeState("AdaptAddon", "awardXp", player, skill, 5.0D);
    callIgnoringRuntimeState("AdaptAddon", "level", adaptation("rift-blink"), player);
    callIgnoringRuntimeState("AdaptAddon", "icon", skill);
    callIgnoringRuntimeState("AdaptAddon", "bukkitPlayer", mock(AdaptPlayer.class));
  }

  private static Adaptation<?> adaptation(String name) {
    Adaptation<?> adaptation = mock(Adaptation.class);
    Skill<?> skill = mock(Skill.class);
    when(adaptation.getName()).thenReturn(name);
    when(skill.getName()).thenReturn("rift");
    when(adaptation.getSkill()).thenAnswer(ignored -> skill);
    return adaptation;
  }

  private static Optional<String> resolve(MethodInsnNode call) {
    try {
      Class<?> owner = Class.forName(call.owner.replace('/', '.'), false, addon);
      MethodType type = MethodType.fromMethodDescriptorString(call.desc, addon);
      MethodHandles.Lookup lookup = MethodHandles.publicLookup();
      switch (call.getOpcode()) {
        case Opcodes.INVOKESTATIC -> lookup.findStatic(owner, call.name, type);
        case Opcodes.INVOKESPECIAL -> lookup.findConstructor(owner, type);
        default -> lookup.findVirtual(owner, call.name, type);
      }
      return Optional.empty();
    } catch (ReflectiveOperationException | LinkageError | TypeNotPresentException error) {
      return Optional.of(call.owner + "." + call.name + call.desc + " -> " + error);
    }
  }

  private static Optional<String> resolve(FieldInsnNode field) {
    try {
      Class<?> owner = Class.forName(field.owner.replace('/', '.'), false, addon);
      Class<?> type = MethodType.fromMethodDescriptorString("()" + field.desc, addon).returnType();
      MethodHandles.Lookup lookup = MethodHandles.publicLookup();
      if (field.getOpcode() == Opcodes.GETSTATIC || field.getOpcode() == Opcodes.PUTSTATIC) {
        lookup.findStaticGetter(owner, field.name, type);
      } else {
        lookup.findGetter(owner, field.name, type);
      }
      return Optional.empty();
    } catch (ReflectiveOperationException | LinkageError | TypeNotPresentException error) {
      return Optional.of(field.owner + "." + field.name + ":" + field.desc + " -> " + error);
    }
  }

  private static List<ClassNode> addonClasses() throws IOException {
    List<ClassNode> classes = new ArrayList<>();
    for (Path root : addonRoots) {
      if (!Files.isDirectory(root)) {
        continue;
      }
      List<Path> files;
      try (Stream<Path> walk = Files.walk(root)) {
        files = walk.filter((Path file) -> file.toString().endsWith(".class")).sorted().toList();
      }
      for (Path file : files) {
        ClassNode node = new ClassNode();
        try (InputStream input = Files.newInputStream(file)) {
          new ClassReader(input).accept(node, 0);
        }
        classes.add(node);
      }
    }
    assertFalse(classes.isEmpty(), "no addon classes under " + addonRoots);
    return classes;
  }

  private static Object call(String type, String name, Object... arguments) throws ReflectiveOperationException {
    Class<?> owner = Class.forName(ADDON + type, true, addon);
    for (Method method : owner.getMethods()) {
      if (method.getName().equals(name) && method.getParameterCount() == arguments.length) {
        try {
          return method.invoke(null, arguments);
        } catch (InvocationTargetException failure) {
          if (failure.getCause() instanceof RuntimeException runtime) {
            throw runtime;
          }
          if (failure.getCause() instanceof Error error) {
            throw error;
          }
          throw failure;
        }
      }
    }
    throw new NoSuchMethodException(type + "." + name);
  }

  /**
   * Runs a call whose effect needs a running Adapt (player data, XP ledgers). Without one it may fail, but never with
   * a linkage error: that is what this proves.
   */
  private static void callIgnoringRuntimeState(String type, String name, Object... arguments)
      throws ReflectiveOperationException {
    try {
      call(type, name, arguments);
    } catch (RuntimeException expected) {
      assertNoLinkageFailure(expected);
    } catch (LinkageError linkage) {
      fail(type + "." + name + " failed to link: " + linkage);
    }
  }

  private static void assertNoLinkageFailure(Throwable failure) {
    for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
      if (cause instanceof LinkageError) {
        fail("linkage failure: " + cause);
      }
    }
  }

  private static Object invoke(Object target, String name, Object... arguments) throws ReflectiveOperationException {
    for (Method method : target.getClass().getMethods()) {
      if (method.getName().equals(name) && method.getParameterCount() == arguments.length) {
        return method.invoke(target, arguments);
      }
    }
    throw new NoSuchMethodException(name);
  }
}
