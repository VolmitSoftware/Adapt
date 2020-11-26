package com.volmit.adapt.util;

import java.io.File;

public interface Converter
{
	public String getInExtension();

	public String getOutExtension();

	public void convert(File in, File out);
}
