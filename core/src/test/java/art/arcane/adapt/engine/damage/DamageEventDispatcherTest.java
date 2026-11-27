package art.arcane.adapt.engine.damage;

import art.arcane.adapt.AdaptEnvironment;
import art.arcane.adapt.api.content.DamageDeferral;
import art.arcane.adapt.api.content.DeathSave;
import art.arcane.adapt.api.preference.CommonPreferences;
import art.arcane.adapt.platform.PlatformLog;
import art.arcane.adapt.platform.entity.EntityRef;
import art.arcane.adapt.platform.entity.PlayerRef;
import art.arcane.adapt.platform.event.entity.DamageCause;
import art.arcane.adapt.platform.event.entity.EntityDamageByEntityEvent;
import art.arcane.adapt.platform.event.entity.EntityDamageEvent;
import art.arcane.adapt.platform.value.Id;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DamageEventDispatcherTest {
    @Test
    void modifiersAndDeferralFinishBeforeTheSaveRegardlessOfRegistrationOrder() {
        DamageEventDispatcher dispatcher = new DamageEventDispatcher();
        List<String> calls = new ArrayList<>();
        SaveListener save = new SaveListener(0, event -> {
            calls.add("save");
            assertThat(event.finalDamage()).isEqualTo(6);
            event.setCancelled(true);
        });
        add(dispatcher, save);
        add(dispatcher, new DeferralListener(event -> {
            calls.add("deferral");
            assertThat(event.finalDamage()).isEqualTo(12);
            event.setDamage(event.damage() / 2);
        }));
        add(dispatcher, new DamageListener(event -> {
            calls.add("modifier");
            event.setDamage(12);
        }));

        EntityDamageEvent event = damage(20, 5);
        dispatcher.onDamage(event);

        assertThat(calls).containsExactly("modifier", "deferral", "save");
        assertThat(event.isCancelled()).isTrue();
    }

    @Test
    void damageMadeNonlethalDoesNotConsumeASave() {
        DamageEventDispatcher dispatcher = new DamageEventDispatcher();
        List<String> calls = new ArrayList<>();
        add(dispatcher, new SaveListener(0, event -> calls.add("save")));
        add(dispatcher, new DeferralListener(event -> event.setDamage(event.damage() / 2)));
        add(dispatcher, new DamageListener(event -> event.setDamage(6)));

        EntityDamageEvent event = damage(20, 5);
        dispatcher.onDamage(event);

        assertThat(event.finalDamage()).isEqualTo(3);
        assertThat(calls).isEmpty();
    }

    @Test
    void equalPrioritiesUseTheFixedDefaultAndOnlyOneSave() {
        DamageEventDispatcher dispatcher = new DamageEventDispatcher();
        List<Integer> calls = new ArrayList<>();
        for (int order = 2; order >= 0; order--) {
            int saveOrder = order;
            add(dispatcher, new SaveListener(order, event -> {
                calls.add(saveOrder);
                event.setCancelled(true);
            }));
        }

        dispatcher.onDamage(damage(20, 5));

        assertThat(calls).containsExactly(0);
    }

    @Test
    void playerPriorityOverridesTheDefaultAndUnavailableSavesFallThrough() {
        DamageEventDispatcher dispatcher = new DamageEventDispatcher();
        List<Integer> calls = new ArrayList<>();
        SaveListener preferred = new SaveListener(2, event -> calls.add(2));
        preferred.priority = 0;
        add(dispatcher, preferred);
        add(dispatcher, new SaveListener(1, event -> {
            calls.add(1);
            event.setCancelled(true);
        }));
        SaveListener last = new SaveListener(0, event -> calls.add(0));
        last.priority = 2;
        add(dispatcher, last);

        dispatcher.onDamage(damage(20, 5));

        assertThat(calls).containsExactly(2, 1);
    }

    @Test
    void existingProtectionPreventsModifiersDebtAndAnotherSave() {
        DamageEventDispatcher dispatcher = new DamageEventDispatcher();
        List<String> calls = new ArrayList<>();
        add(dispatcher, new SaveListener(0, event -> calls.add("new-save")));
        SaveListener pending = new SaveListener(2, event -> calls.add("pending-save"));
        pending.protects = true;
        add(dispatcher, pending);
        add(dispatcher, new DamageListener(event -> calls.add("modifier")));
        add(dispatcher, new DeferralListener(event -> calls.add("debt")));

        EntityDamageEvent event = damage(20, 5);
        dispatcher.onDamage(event);

        assertThat(event.isCancelled()).isTrue();
        assertThat(calls).isEmpty();
    }

    @Test
    void cancellationPolicyStillAllowsOptedInHandlers() {
        DamageEventDispatcher dispatcher = new DamageEventDispatcher();
        List<String> calls = new ArrayList<>();
        add(dispatcher, new DamageListener(event -> event.setCancelled(true)));
        add(dispatcher, new DamageListener(event -> calls.add("skipped")));
        add(dispatcher, new DamageListener(event -> calls.add("receives-cancelled")), false);
        add(dispatcher, new DeferralListener(event -> calls.add("debt")));
        add(dispatcher, new SaveListener(0, event -> calls.add("save")));

        dispatcher.onDamage(damage(20, 5));

        assertThat(calls).containsExactly("receives-cancelled");
    }

    @Test
    void pendingProtectionAlsoCatchesDamageMadeLethalByAModifier() {
        DamageEventDispatcher dispatcher = new DamageEventDispatcher();
        List<String> calls = new ArrayList<>();
        add(dispatcher, new SaveListener(0, event -> calls.add("new-save")));
        SaveListener pending = new SaveListener(2, event -> calls.add("pending-save")) {
            @Override
            public boolean protectsFromDamage(EntityDamageEvent event) {
                return event.finalDamage() >= 5D;
            }
        };
        add(dispatcher, pending);
        add(dispatcher, new DamageListener(event -> event.setDamage(20D)));

        EntityDamageEvent event = damage(2D, 5D);
        dispatcher.onDamage(event);

        assertThat(event.isCancelled()).isTrue();
        assertThat(calls).isEmpty();
    }

    @Test
    void damageSubtypeFilteringIsPreserved() {
        DamageEventDispatcher dispatcher = new DamageEventDispatcher();
        List<String> calls = new ArrayList<>();
        add(dispatcher, new DamageListener(event -> calls.add("any-damage")));
        EntityDamageListener entityListener = new EntityDamageListener(calls);
        dispatcher.register(entityListener, EntityDamageByEntityEvent.class,
                event -> entityListener.on((EntityDamageByEntityEvent) event), true);

        dispatcher.onDamage(damage(2, 5));
        EntityRef damager = mock(EntityRef.class);
        EntityDamageByEntityEvent entityDamage = new EntityDamageByEntityEvent(mock(EntityRef.class), damager, damager,
                DamageCause.ENTITY_ATTACK, Id.minecraft("mob_attack"), 0, false, null);
        dispatcher.onDamage(entityDamage);

        assertThat(calls).containsExactly("any-damage", "any-damage", "entity-damage");
    }

    @Test
    void oneHandlerFailureDoesNotSkipLaterDamageHandlersOrSaves() {
        PlatformLog previous = AdaptEnvironment.log();
        Path previousData = AdaptEnvironment.dataDirectory();
        PlatformLog logger = mock(PlatformLog.class);
        AdaptEnvironment.install(logger, () -> previousData);
        try {
            DamageEventDispatcher dispatcher = new DamageEventDispatcher();
            List<String> calls = new ArrayList<>();
            add(dispatcher, new DamageListener(event -> {
                throw new IllegalStateException("failure");
            }));
            add(dispatcher, new DamageListener(event -> calls.add("modifier")));
            add(dispatcher, new SaveListener(0, event -> {
                calls.add("save");
                event.setCancelled(true);
            }));

            dispatcher.onDamage(damage(20, 5));

            assertThat(calls).containsExactly("modifier", "save");
            verify(logger).error(contains("damage handler"), any(Throwable.class));
        } finally {
            AdaptEnvironment.install(previous, () -> previousData);
        }
    }

    @Test
    void unregisterStopsCallbacksAlreadyCapturedInTheCurrentSnapshot() {
        DamageEventDispatcher dispatcher = new DamageEventDispatcher();
        List<String> calls = new ArrayList<>();
        SaveListener retired = new SaveListener(0, event -> calls.add("retired"));
        add(dispatcher, retired);
        DamageListener retiring = new DamageListener(event -> dispatcher.unregister(retired));
        add(dispatcher, retiring);

        dispatcher.onDamage(damage(20, 5));
        dispatcher.unregister(retiring);
        add(dispatcher, new SaveListener(0, event -> calls.add("replacement")));
        dispatcher.onDamage(damage(20, 5));
        dispatcher.clear();
        dispatcher.onDamage(damage(20, 5));

        assertThat(calls).containsExactly("replacement");
    }

    @Test
    void nestedDamageUsesItsOwnLethalityAndCancellation() {
        DamageEventDispatcher dispatcher = new DamageEventDispatcher();
        List<EntityDamageEvent> saved = new ArrayList<>();
        EntityDamageEvent outer = damage(20, 5);
        EntityDamageEvent nested = damage(15, 5);
        add(dispatcher, new DamageListener(event -> {
            if (event == outer) {
                dispatcher.onDamage(nested);
            }
        }));
        add(dispatcher, new SaveListener(0, event -> {
            saved.add(event);
            event.setCancelled(true);
        }));

        dispatcher.onDamage(outer);

        assertThat(saved).containsExactly(nested, outer);
        assertThat(outer.isCancelled()).isTrue();
        assertThat(nested.isCancelled()).isTrue();
    }

    private void add(DamageEventDispatcher dispatcher, DamageListener listener) {
        add(dispatcher, listener, true);
    }

    private void add(DamageEventDispatcher dispatcher, DamageListener listener, boolean ignoreCancelled) {
        dispatcher.register(listener, EntityDamageEvent.class, listener::on, ignoreCancelled);
    }

    private EntityDamageEvent damage(double amount, double health) {
        PlayerRef player = mock(PlayerRef.class);
        when(player.health()).thenReturn(health);
        return new EntityDamageEvent(player, DamageCause.CUSTOM, Id.minecraft("generic"), amount, null);
    }

    public static class DamageListener {
        private final Consumer<EntityDamageEvent> action;

        DamageListener(Consumer<EntityDamageEvent> action) {
            this.action = action;
        }

        public void on(EntityDamageEvent event) {
            action.accept(event);
        }
    }

    public static final class DeferralListener extends DamageListener implements DamageDeferral {
        DeferralListener(Consumer<EntityDamageEvent> action) {
            super(action);
        }
    }

    public static class SaveListener extends DamageListener implements DeathSave {
        private final int order;
        private int priority = 1;
        private boolean protects;

        SaveListener(int order, Consumer<EntityDamageEvent> action) {
            super(action);
            this.order = order;
        }

        @Override
        public boolean protectsFromDamage(EntityDamageEvent event) {
            return protects;
        }

        @Override
        public CommonPreferences.DeathSavePriority deathSavePriority(PlayerRef player) {
            return CommonPreferences.DeathSavePriority.values()[priority];
        }

        @Override
        public int defaultOrder() {
            return order;
        }
    }

    public static final class EntityDamageListener {
        private final List<String> calls;

        EntityDamageListener(List<String> calls) {
            this.calls = calls;
        }

        public void on(EntityDamageByEntityEvent event) {
            calls.add("entity-damage");
        }
    }
}
