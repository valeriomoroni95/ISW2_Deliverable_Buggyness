package logic;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class ParserJson {

	private ParserJson() {

	}

	private static String readAll(Reader rd) throws IOException {

		StringBuilder sb = new StringBuilder();
		int cp;
		while ((cp = rd.read()) != -1) {
			sb.append((char) cp);
		}
		return sb.toString();
	}

	public static JSONObject readJsonFromUrl(String url) throws IOException, JSONException {

		try (InputStream is = new URL(url).openStream();) {
			BufferedReader rd = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8.name()));
			String jsonText = readAll(rd);
			return new JSONObject(jsonText);
		}
	}
	
	//Funzione creata per fixare delle versioni sbagliate presenti in Bookkeeper.
	public static void fixBookkeeperVersions(JSONArray versions, String releaseDate) throws JSONException {
		for (int i = 0; i < versions.length(); i++) {
			JSONObject item = versions.getJSONObject(i);
			if(item.get("name").equals("4.1.1") && item.get(releaseDate).equals("2013-01-16")) {
				item.put("name", "4.2.1");
				item.put(releaseDate, "2013-02-27");
			}else if(item.get("name").equals("4.2.3") && item.get(releaseDate).equals("2014-06-27")) {
				item.put(releaseDate, "2013-06-27");
			}else if(item.get("name").equals("4.3.0") && item.get(releaseDate).equals("2014-02-02")) {
				item.put(releaseDate, "2014-10-14");
			}else if(item.get("name").equals("4.2.1") && item.get(releaseDate).equals("2013-03-27")) {
				item.put("name", "4.2.4");
				item.put(releaseDate, "2015-01-16");
			}else if(item.get("name").equals("4.5.1") && item.get(releaseDate).equals("2017-09-10")) {
				item.put(releaseDate, "2017-11-22");
			}else if(item.get("name").equals("4.6.0") && item.get(releaseDate).equals("2017-11-10")) {
				item.put(releaseDate, "2017-12-27");
			}
		}
	}

}
