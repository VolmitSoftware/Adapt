package com.volmit.adapt.content.matter;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.cyberpwn.spatial.matter.slices.RawMatter;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.UUID;

public class BrewingStandOwnerMatter extends RawMatter<BrewingStandOwner> {
        public BrewingStandOwnerMatter(int w, int h, int d)
        {
            super(w,h,d,BrewingStandOwner.class);
        }

        public BrewingStandOwnerMatter()
        {
            this(1,1,1);
        }

        @Override
        public void writeNode(BrewingStandOwner brewingStandOwner, DataOutputStream dataOutputStream) throws IOException {
            dataOutputStream.writeLong(brewingStandOwner.getOwner().getMostSignificantBits());
            dataOutputStream.writeLong(brewingStandOwner.getOwner().getLeastSignificantBits());
        }

        @Override
        public BrewingStandOwner readNode(DataInputStream dataInputStream) throws IOException {
            return new BrewingStandOwner(new UUID(dataInputStream.readLong(), dataInputStream.readLong()));
        }
    }