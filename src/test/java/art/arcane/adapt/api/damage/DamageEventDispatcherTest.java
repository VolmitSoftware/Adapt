package art.arcane.adapt.api.damage;

import art.arcane.adapt.AdaptTestBase;
import art.arcane.adapt.api.EventHandlerInvoker;
import com.google.common.base.Function;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.plugin.EventExecutor;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DamageEventDispatcherTest extends AdaptTestBase {
    @Test
    void modifiersAndDeferralFinishBeforeTheSaveRegardlessOfRegistrationOrder() throws Exception {
        DamageEventDispatcher dispatcher = new DamageEventDispatcher(plugin);
        List<String> calls = new ArrayList<>();
        SaveListener save = new SaveListener(0, event -> {
            calls.add("save");
            assertThat(event.getFinalDamage()).isEqualTo(6);
            event.setCancelled(true);
        });
        add(dispatcher, save);
        add(dispatcher, new DeferralListener(event -> {
            calls.add("deferral");
            assertThat(event.getFinalDamage()).isEqualTo(12);
            event.setDamage(event.getDamage() / 2);
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
    void damageMadeNonlethalDoesNotConsumeASave() throws Exception {
        DamageEventDispatcher dispatcher = new DamageEventDispatcher(plugin);
        List<String> calls = new ArrayList<>();
        add(dispatcher, new SaveListener(0, event -> calls.add("save")));
        add(dispatcher, new DeferralListener(event -> event.setDamage(event.getDamage() / 2)));
        add(dispatcher, new DamageListener(event -> event.setDamage(6)));

        EntityDamageEvent event = damage(20, 5);
        dispatcher.onDamage(event);

        assertThat(event.getFinalDamage()).isEqualTo(3);
        assertThat(calls).isEmpty();
    }

    @Test
    void equalPrioritiesUseTheFixedDefaultAndOnlyOneSave() throws Exception {
        DamageEventDispatcher dispatcher = new DamageEventDispatcher(plugin);
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
    void playerPriorityOverridesTheDefaultAndUnavailableSavesFallThrough() throws Exception {
        DamageEventDispatcher dispatcher = new DamageEventDispatcher(plugin);
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
    void existingProtectionPreventsModifiersDebtAndAnotherSave() throws Exception {
        DamageEventDispatcher dispatcher = new DamageEventDispatcher(plugin);
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
    void cancellationPolicyStillAllowsOptedInHandlers() throws Exception {
        DamageEventDispatcher dispatcher = new DamageEventDispatcher(plugin);
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
    void pendingProtectionAlsoCatchesDamageMadeLethalByAModifier() throws Exception {
        DamageEventDispatcher dispatcher = new DamageEventDispatcher(plugin);
        List<String> calls = new ArrayList<>();
        add(dispatcher, new SaveListener(0, event -> calls.add("new-save")));
        SaveListener pending = new SaveListener(2, event -> calls.add("pending-save")) {
            @Override
            public boolean protectsFromDamage(EntityDamageEvent event) {
                return event.getFinalDamage() >= 5D;
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
    void damageSubtypeFilteringIsPreserved() throws Exception {
        DamageEventDispatcher dispatcher = new DamageEventDispatcher(plugin);
        List<String> calls = new ArrayList<>();
        add(dispatcher, new DamageListener(event -> calls.add("any-damage")));
        EntityDamageListener entityListener = new EntityDamageListener(calls);
        Method method = EntityDamageListener.class.getMethod("on", EntityDamageByEntityEvent.class);
        dispatcher.register(entityListener, EntityDamageByEntityEvent.class,
                EventHandlerInvoker.createExecutor(entityListener, method, EntityDamageByEntityEvent.class, false), true);

        dispatcher.onDamage(damage(2, 5));
        EntityDamageByEntityEvent entityDamage = mock(EntityDamageByEntityEvent.class);
        dispatcher.onDamage(entityDamage);

        assertThat(calls).containsExactly("any-damage", "any-damage", "entity-damage");
    }

    @Test
    void oneHandlerFailureDoesNotSkipLaterDamageHandlersOrSaves() throws Exception {
        Logger logger = mock(Logger.class);
        when(plugin.getLogger()).thenReturn(logger);
        DamageEventDispatcher dispatcher = new DamageEventDispatcher(plugin);
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
        verify(logger).log(eq(Level.SEVERE), contains("damage handler"), any(Throwable.class));
    }

    @Test
    void unregisterStopsCallbacksAlreadyCapturedInTheCurrentSnapshot() throws Exception {
        DamageEventDispatcher dispatcher = new DamageEventDispatcher(plugin);
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
    void nestedDamageUsesItsOwnLethalityAndCancellation() throws Exception {
        DamageEventDispatcher dispatcher = new DamageEventDispatcher(plugin);
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

    private void add(DamageEventDispatcher dispatcher, DamageListener listener) throws Exception {
        add(dispatcher, listener, true);
    }

    private void add(DamageEventDispatcher dispatcher, DamageListener listener, boolean ignoreCancelled) throws Exception {
        Method method = listener.getClass().getMethod("on", EntityDamageEvent.class);
        EventExecutor executor = EventHandlerInvoker.createExecutor(listener, method, EntityDamageEvent.class, false);
        dispatcher.register(listener, EntityDamageEvent.class, executor, ignoreCancelled);
    }

    private EntityDamageEvent damage(double amount, double health) {
        Player player = mock(Player.class);
        when(player.getHealth()).thenReturn(health);
        Map<EntityDamageEvent.DamageModifier, Double> modifiers = new EnumMap<>(EntityDamageEvent.DamageModifier.class);
        modifiers.put(EntityDamageEvent.DamageModifier.BASE, amount);
        Map<EntityDamageEvent.DamageModifier, Function<? super Double, Double>> functions = new EnumMap<>(EntityDamageEvent.DamageModifier.class);
        functions.put(EntityDamageEvent.DamageModifier.BASE, damage -> damage);
        return new EntityDamageEvent(player, EntityDamageEvent.DamageCause.CUSTOM, mock(DamageSource.class), modifiers, functions);
    }

    public static class DamageListener implements Listener {
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

    public static class SaveListener extends DamageListener implements PlayerDeathSave {
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
        public void tryPreventDeath(EntityDamageEvent event) {
            super.on(event);
        }

        @Override
        public void on(EntityDamageEvent event) {
            tryPreventDeath(event);
        }

        @Override
        public int deathSavePriority(Player player) {
            return priority;
        }

        @Override
        public int defaultOrder() {
            return order;
        }
    }

    public static final class EntityDamageListener implements Listener {
        private final List<String> calls;

        EntityDamageListener(List<String> calls) {
            this.calls = calls;
        }

        public void on(EntityDamageByEntityEvent event) {
            calls.add("entity-damage");
        }
    }
}
