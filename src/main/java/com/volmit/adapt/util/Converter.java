package com.volmit.adapt.util;

import java.io.File;

public interface Converter {
    String getInExtension();

    String getOutExtension();

    void convert(File in, File out);
}
