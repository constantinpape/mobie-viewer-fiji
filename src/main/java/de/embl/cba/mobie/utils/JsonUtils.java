package de.embl.cba.mobie.utils;

import com.google.gson.stream.JsonReader;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;

public abstract class JsonUtils
{
	public static ArrayList< String > readStringArray( JsonReader reader ) throws IOException
	{
		reader.beginArray();
		final ArrayList< String > strings = new ArrayList< String >();
		while ( reader.hasNext() )
			strings.add( reader.nextString() );
		reader.endArray();
		reader.close();

		return strings;
	}
}
