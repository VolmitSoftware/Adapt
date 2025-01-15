package com.volmit.adapt.util.redis.codec;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

public interface Message {
    void encode(@NotNull DataOutput output) throws IOException;

    @FunctionalInterface
    interface Decoder<T extends Message> {
        @Nullable
        T decode(@NotNull DataInput input) throws IOException;
    }
}
