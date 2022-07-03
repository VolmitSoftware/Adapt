package com.volmit.adapt.content.block;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.cyberpwn.spatial.matter.slices.RawMatter;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.UUID;

public class ScaffoldMatter extends RawMatter<ScaffoldMatter.ScaffoldData> {
    public ScaffoldMatter(int width, int height, int depth) {
        super(width, height, depth, ScaffoldData.class);
    }

    @Override
    public void writeNode(ScaffoldData scaffoldData, DataOutputStream dos) throws IOException {
        dos.writeLong(scaffoldData.getUuid().getMostSignificantBits());
        dos.writeLong(scaffoldData.getUuid().getLeastSignificantBits());
        dos.writeLong(scaffoldData.getTime());
    }

    @Override
    public ScaffoldData readNode(DataInputStream din) throws IOException {
        return ScaffoldData.builder()
            .uuid(new UUID(din.readLong(), din.readLong()))
            .time(din.readLong())
            .build();
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ScaffoldData
    {
        private UUID uuid;
        @Builder.Default
        private long time = 0;
    }
}
