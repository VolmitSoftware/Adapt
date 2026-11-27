package art.arcane.adapt.engine.addon;

import art.arcane.adapt.api.AdaptApi;
import art.arcane.adapt.api.addon.AdaptAddon;
import art.arcane.adapt.api.addon.AddonHandle;
import art.arcane.adapt.api.addon.ContentRegistrar;
import art.arcane.adapt.api.command.AdaptCommand;
import art.arcane.adapt.api.event.AddonInstalledEvent;
import art.arcane.adapt.api.internal.Engine;
import art.arcane.adapt.api.internal.HostMenus;
import art.arcane.adapt.api.internal.HostPermissions;
import art.arcane.adapt.api.internal.HostRecipes;
import art.arcane.adapt.api.internal.HostScheduler;
import art.arcane.adapt.api.internal.InstalledAddon;
import art.arcane.adapt.api.text.Text;
import art.arcane.adapt.engine.bridge.HostKeys;
import art.arcane.adapt.platform.AdaptPlatform;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.permission.PermissionDefault;
import art.arcane.adapt.platform.permission.PermissionRegistration;
import art.arcane.adapt.platform.recipe.Ingredient;
import art.arcane.adapt.platform.recipe.RecipeRegistration;
import art.arcane.adapt.platform.recipe.RecipeSpec;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.testkit.FakeRecipes;
import art.arcane.adapt.testkit.TestAdapt;
import art.arcane.adapt.testkit.TestPlatform;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** An addon compiled against the API alone tries to act on Adapt's or another addon's state, and is refused. */
class HostileAddonTest {
  private static final Id ADAPT_RECIPE = Id.adapt("hostile_test/plate");

  @TempDir
  Path data;

  private TestAdapt adapt;

  @AfterEach
  void stop() {
    if (adapt != null) {
      adapt.close();
    }
  }

  @Test
  void aLateClaimOfTheHostKeyNamesAdaptsHolder() {
    HostKeys.key();

    assertThatThrownBy(() -> Engine.claimHost())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("already claimed by " + HostKeys.class.getName());
  }

  @Test
  void theBridgeRefusesAdaptEventsAndCallbacksWithoutTheKey() {
    adapt = TestAdapt.start(TestPlatform.builder(data).build());
    AddonHandle victim = AdaptApi.addons().install(new Quiet("victim"));

    assertThatThrownBy(() -> Engine.bridge().post(new AddonInstalledEvent("forged")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(AddonInstalledEvent.class.getName());
    assertThatThrownBy(() -> Engine.bridge().addonUninstalled(null, (InstalledAddon) victim))
        .hasMessage("Only Adapt's platform host may do this");
    assertThatThrownBy(() -> Engine.bridge().listenerUnsubscribed(null, this))
        .hasMessage("Only Adapt's platform host may do this");
    assertThatThrownBy(() -> Engine.bridge().veto(null, (InstalledAddon) victim, Id.of("victim", "thing")))
        .hasMessage("Only Adapt's platform host may do this");
    victim.uninstall();
  }

  @Test
  void thePlatformAddonsSeeHidesTheHostOnlyOperations() {
    adapt = TestAdapt.start(TestPlatform.builder(data).build());

    AdaptPlatform platform = AdaptApi.platform();

    assertThat(platform.scheduler()).isNotInstanceOf(HostScheduler.class);
    assertThat(platform.menus()).isNotInstanceOf(HostMenus.class);
    assertThat(platform.permissions()).isNotInstanceOf(HostPermissions.class);
    assertThat(platform.recipes()).isNotInstanceOf(HostRecipes.class);
    assertThat(platform.host()).isNotSameAs(adapt.platform().platform().host());
  }

  @Test
  void anAddonRemovesOnlyThePermissionNodesItRegistered() {
    TestPlatform platform = TestPlatform.builder(data).build();
    adapt = TestAdapt.start(platform);
    TestPlatform.Permissions nodes = (TestPlatform.Permissions) platform.platform().permissions();
    platform.platform().permissions().add("adapt.hostile.test", "Adapt's own", PermissionDefault.OP);

    PermissionRegistration squatted = AdaptApi.platform().permissions()
        .register("adapt.hostile.test", "taken over", PermissionDefault.TRUE);
    PermissionRegistration own = AdaptApi.platform().permissions()
        .register("hostile.own", "mine", PermissionDefault.TRUE);
    squatted.remove();
    own.remove();

    assertThat(nodes.registered()).contains("adapt.hostile.test").doesNotContain("hostile.own");
  }

  @Test
  void anAddonCannotReplaceARegisteredRecipeAndRemovesOnlyItsOwn() {
    TestPlatform platform = TestPlatform.builder(data).build();
    adapt = TestAdapt.start(platform);
    FakeRecipes recipes = (FakeRecipes) platform.platform().recipes();
    ItemRef stone = platform.items().create(Id.minecraft("stone"), 1);
    recipes.add(shapeless(ADAPT_RECIPE, stone));

    assertThatThrownBy(() -> AdaptApi.platform().recipes().register(shapeless(ADAPT_RECIPE, stone)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(ADAPT_RECIPE.toString());

    Id ownKey = Id.of("hostile", "plate");
    RecipeRegistration own = AdaptApi.platform().recipes().register(shapeless(ownKey, stone)).orElseThrow();
    assertThat(recipes.registered()).contains(ADAPT_RECIPE, ownKey);
    own.remove();

    assertThat(recipes.registered()).contains(ADAPT_RECIPE).doesNotContain(ownKey);
  }

  @Test
  void anAddonCommandPermissionOutsideItsNamespaceIsRefusedNamingNodeAndNamespace() {
    adapt = TestAdapt.start(TestPlatform.builder(data).build());

    AddonHandle handle = AdaptApi.addons().install(new ForeignPermission());

    assertThat(handle.rejection()).get().asString()
        .contains("adapt.admin.everything")
        .contains("squatter.");
    handle.uninstall();
  }

  private static RecipeSpec shapeless(Id key, ItemRef result) {
    return RecipeSpec.shapeless(key, List.of(Ingredient.of(Id.minecraft("stone"))), result);
  }

  private record Quiet(String namespace) implements AdaptAddon {
    @Override
    public void register(ContentRegistrar registrar) {
    }
  }

  private static final class ForeignPermission implements AdaptAddon {
    @Override
    public String namespace() {
      return "squatter";
    }

    @Override
    public void register(ContentRegistrar registrar) {
      registrar.command(AdaptCommand.named("squat")
          .help(Text.literal("Squat"))
          .permission("adapt.admin.everything")
          .executes(invocation -> {
          })
          .build());
    }
  }
}
