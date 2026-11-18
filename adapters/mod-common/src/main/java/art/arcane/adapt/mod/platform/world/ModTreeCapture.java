package art.arcane.adapt.mod.platform.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * A view of a level that records block writes instead of applying them, like Bukkit's BlockStateListPopulator.
 * Reads see the recorded writes; default interface methods run against the view so they never reach the level.
 */
final class ModTreeCapture implements InvocationHandler {
    private final ServerLevel level;
    private final Map<BlockPos, BlockState> placed = new LinkedHashMap<>();

    private ModTreeCapture(ServerLevel level) {
        this.level = level;
    }

    static ModTreeCapture of(ServerLevel level) {
        return new ModTreeCapture(level);
    }

    WorldGenLevel view() {
        return (WorldGenLevel) Proxy.newProxyInstance(WorldGenLevel.class.getClassLoader(), new Class<?>[]{WorldGenLevel.class}, this);
    }

    Map<BlockPos, BlockState> placed() {
        return placed;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String name = method.getName();
        Class<?>[] types = method.getParameterTypes();
        if (method.getDeclaringClass() == Object.class) {
            return switch (name) {
                case "equals" -> proxy == args[0];
                case "hashCode" -> System.identityHashCode(proxy);
                default -> "ModTreeCapture[" + level + "]";
            };
        }
        if (name.equals("setBlock") && types.length >= 3 && types[0] == BlockPos.class && types[1] == BlockState.class) {
            placed.put(((BlockPos) args[0]).immutable(), (BlockState) args[1]);
            return true;
        }
        if (name.equals("getBlockState") && types.length == 1 && types[0] == BlockPos.class) {
            return state((BlockPos) args[0]);
        }
        if (name.equals("getFluidState") && types.length == 1 && types[0] == BlockPos.class) {
            return fluid((BlockPos) args[0]);
        }
        if (name.equals("isStateAtPosition") && types.length == 2 && types[0] == BlockPos.class) {
            return ((Predicate<BlockState>) args[1]).test(state((BlockPos) args[0]));
        }
        if (name.equals("isFluidAtPosition") && types.length == 2 && types[0] == BlockPos.class) {
            return ((Predicate<FluidState>) args[1]).test(fluid((BlockPos) args[0]));
        }
        if (method.isDefault()) {
            return InvocationHandler.invokeDefault(proxy, method, args);
        }
        try {
            return method.invoke(level, args);
        } catch (InvocationTargetException error) {
            throw error.getCause();
        }
    }

    private BlockState state(BlockPos pos) {
        BlockState recorded = placed.get(pos);
        return recorded != null ? recorded : level.getBlockState(pos);
    }

    private FluidState fluid(BlockPos pos) {
        BlockState recorded = placed.get(pos);
        return recorded != null ? recorded.getFluidState() : level.getFluidState(pos);
    }
}
