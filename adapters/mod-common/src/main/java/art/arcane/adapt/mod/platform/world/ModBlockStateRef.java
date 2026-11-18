package art.arcane.adapt.mod.platform.world;

import art.arcane.adapt.mod.platform.ModConversions;
import art.arcane.adapt.mod.platform.ModRefs;
import art.arcane.adapt.platform.item.ItemRef;
import art.arcane.adapt.platform.value.Id;
import art.arcane.adapt.platform.world.BlockStateRef;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class ModBlockStateRef implements BlockStateRef {
    private final BlockState state;

    public ModBlockStateRef(BlockState state) {
        this.state = Objects.requireNonNull(state, "state");
    }

    public BlockState state() {
        return state;
    }

    @Override
    public Id type() {
        return ModConversions.id(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
    }

    @Override
    public boolean isAir() {
        return state.isAir();
    }

    @Override
    public boolean isSolid() {
        return state.isSolid();
    }

    @Override
    public String property(String name) {
        Property<?> property = state.getBlock().getStateDefinition().getProperty(name);
        return property == null ? null : value(state, property);
    }

    @Override
    public Map<String, String> properties() {
        Map<String, String> out = new LinkedHashMap<>();
        for (Property<?> property : state.getProperties()) {
            out.put(property.getName(), value(state, property));
        }
        return out;
    }

    @Override
    public BlockStateRef with(String name, String value) {
        Property<?> property = state.getBlock().getStateDefinition().getProperty(name);
        if (property == null) {
            return this;
        }
        BlockState changed = with(state, property, value);
        return changed == null ? this : new ModBlockStateRef(changed);
    }

    @Override
    public int age() {
        IntegerProperty age = ageProperty();
        return age == null ? -1 : state.getValue(age);
    }

    @Override
    public int maxAge() {
        IntegerProperty age = ageProperty();
        if (age == null) {
            return -1;
        }
        int max = 0;
        for (Integer value : age.getPossibleValues()) {
            max = Math.max(max, value);
        }
        return max;
    }

    @Override
    public Id placementItem() {
        return ModConversions.id(BuiltInRegistries.ITEM.getKey(state.getBlock().asItem()));
    }

    @Override
    public Id breakSound() {
        return ModConversions.id(state.getSoundType().getBreakSound().location());
    }

    @Override
    public boolean isPreferredTool(ItemRef tool) {
        return !state.requiresCorrectToolForDrops() || ModRefs.item(tool).isCorrectToolForDrops(state);
    }

    @Override
    public String asString() {
        return BlockStateParser.serialize(state);
    }

    @Override
    public Object unwrap() {
        return state;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ModBlockStateRef that && state == that.state;
    }

    @Override
    public int hashCode() {
        return state.hashCode();
    }

    @Override
    public String toString() {
        return asString();
    }

    private IntegerProperty ageProperty() {
        Property<?> property = state.getBlock().getStateDefinition().getProperty("age");
        return property instanceof IntegerProperty integer ? integer : null;
    }

    private static <T extends Comparable<T>> String value(BlockState state, Property<T> property) {
        return property.getName(state.getValue(property));
    }

    private static <T extends Comparable<T>> BlockState with(BlockState state, Property<T> property, String value) {
        Optional<T> parsed = property.getValue(value);
        return parsed.map((T present) -> state.setValue(property, present)).orElse(null);
    }
}
