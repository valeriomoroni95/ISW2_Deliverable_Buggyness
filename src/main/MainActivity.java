package main;

import org.apache.commons.collections4.MapIterator;
import org.apache.commons.collections4.keyvalue.MultiKey;
import org.apache.commons.collections4.map.LinkedMap;
import org.apache.commons.collections4.map.MultiKeyMap;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.util.FileUtils;
import org.eclipse.jgit.util.io.NullOutputStream;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import logic.JiraLogic;
import logic.ParserJson;

public class MainActivity {
	
	private static JiraLogic jiraLogic;

	// MultiKeyMap<Versione del file, percorso del file, lista delle metriche>
	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static MultiKeyMap mapToBuildDataset = MultiKeyMap.multiKeyMap(new LinkedMap());

	// Mappa<,Id del ticket (Injected Version, Fixed Version)>
	private static List<Integer> ticketsList;

	// Indice dell'ultimissima versione
	private static int latestVersion;
	
	private static DatasetCreator dataBuilder;

	// Stringhe utilizzate frequentemente
	public static final String USER_DIRECTORY = "user.dir";
	public static final String RELEASE_DATE = "releaseDate";
	public static final String FILE_EXTENSION = ".java";

	public static void main(String[] args) throws IOException, JSONException, GitAPIException {

		Map<Integer, List<Integer>> ticketsWithBuggyIndex;

		// nomi dei progetti da svolgere
		String[] projectList = { "OPENJPA" };

		for (String projectName : projectList) {

			ticketsWithBuggyIndex = new HashMap<>();
			ticketsList = new ArrayList<>();

			// Multimap<Data release, nome versione, indice versione>
			Multimap<LocalDate, String> versionListWithReleaseDate = MultimapBuilder.treeKeys().linkedListValues().build();

			// Prendo la lista delle versioni con la data della release, formato: 2006-08-26=[0.9.0, 1]
			versionListWithReleaseDate = getVersionAndReleaseDate(projectName);

			String projectRepository = "https://github.com/apache/" + projectName + ".git";

			jiraLogic = new JiraLogic(versionListWithReleaseDate, mapToBuildDataset, ticketsWithBuggyIndex, ticketsList);
			latestVersion = (versionListWithReleaseDate.size() / 2) / 2;

			// Clono la repo nella cartella projectName

			Git.cloneRepository().setURI(projectRepository).setDirectory(new File(projectName)).call();

			// Prendo tutti i file nella repo
			try (Stream<File> fileStream = Files
					.walk(Paths.get(System.getProperty(USER_DIRECTORY) + "/" + projectName + "/"))
					.filter(Files::isRegularFile).map(Path::toFile)) {

				List<File> filesInFolder = fileStream.collect(Collectors.toList());
				
				// Per ogni file Java nella cartella
				for (File f : filesInFolder) {
					if (f.toString().endsWith(FILE_EXTENSION)) {

						// metto la coppia (versione, pathname del file) nella map del dataset
						for (int i = 1; i < (latestVersion) + 1; i++) {
							jiraLogic.putEmptyRecord(i, f.toString().replace(
									Paths.get(System.getProperty(USER_DIRECTORY)).toString() + "/" + projectName + "/",
									""));
						}
					}
				}
			}

			// Trovo gli indici delle IV e delle FV per i ticket con le AV di Jira
			getBuggyVersionAVTicket(projectName);

			// Trovo gli indici delle IV e delle FV per i ticket senza le AV riportate su
			// Jira
			// e che richiedono l'applicazione del metodo proportion
			jiraLogic.getBuggyVersionProportionTicket();

			// Costruisco il dataset (projectName, jiraLogic, latestVersion, mapToBuildDateset)
			dataBuilder = new DatasetCreator(projectName, jiraLogic, latestVersion, mapToBuildDataset);
			dataBuilder.buildDatasetUp(projectName, jiraLogic, latestVersion, mapToBuildDataset);
			// Scrivo il dataset in un file CSV
			dataBuilder.writeCSVFile(projectName, mapToBuildDataset, latestVersion);

			// A fine utilizzo, cancello la cartella con la repo del progetto
			FileUtils.delete(new File(projectName), 1);
		}

	}

	
	public static Multimap<LocalDate, String> getVersionAndReleaseDate(String projectName) throws IOException, JSONException {

		Integer i;
		Multimap<LocalDate, String> versionsList = MultimapBuilder.treeKeys().linkedListValues().build();
		String releaseName = null;

		// Url per prendere le informazioni associate al progetto in Jira
		String url = "https://issues.apache.org/jira/rest/api/2/project/" + projectName;

		JSONObject json = ParserJson.readJsonFromUrl(url);
		
		System.out.println(json.toString(4));

		// Prendo l'array JSON associato alla versione del progetto
		JSONArray versions = json.getJSONArray("versions");
		
		System.out.println(versions.toString(4));

		// Per ogni versione
		for (i = 0; i < versions.length(); i++) {

			// controllo se ha una data per la release ed un nome e l'aggiungo alla lista
			if (versions.getJSONObject(i).has(RELEASE_DATE) && versions.getJSONObject(i).has("name")) {
				releaseName = versions.getJSONObject(i).get("name").toString();
				
				System.out.println(releaseName);
				
				versionsList.put(LocalDate.parse(versions.getJSONObject(i).get(RELEASE_DATE).toString()), releaseName);
			}
		}

		// Dò un indice a ciascuna versione
		int versionCount = 1;
		for (LocalDate ld : versionsList.keySet()) {
			versionsList.put(ld, String.valueOf(versionCount));
			//versionsList.put(ld, String.valueOf(versionCount).length() > 1 ? String.valueOf(versionCount):"0"+String.valueOf(versionCount));
			versionCount++;
		}
		
		System.out.println(versionsList);
		return versionsList;
	}
	
	
	public static void getBuggyVersionAVTicket(String projectName) throws IOException, JSONException {

		Integer j = 0;
		Integer i = 0;
		Integer total = 1;
		String key = null;

		// Get JSON API for closed bugs w/ AV in the project
		do {
			// Only gets a max of 1000 at a time, so must do this multiple times if bugs
			// >1000
			j = i + 1000;
			String url = "https://issues.apache.org/jira/rest/api/2/search?jql=project=%22" + projectName
					+ "%22AND%22issueType%22=%22Bug%22AND(%22status%22=%22closed%22OR"
					+ "%22status%22=%22resolved%22)AND%22resolution%22=%22fixed%22&fields=key,versions,resolutiondate,created,fixVersions&startAt="
					+ i.toString() + "&maxResults=1000";
			JSONObject json = ParserJson.readJsonFromUrl(url);
			JSONArray issues = json.getJSONArray("issues");
			total = json.getInt("total");

			// Per ogni ticket chiuso
			for (; i < total && i < j; i++) {

				JSONObject singleJsonObject = (JSONObject) issues.getJSONObject(i % 1000).get("fields");
				System.out.println(singleJsonObject);
				// Mi prendo la chiave, ossia il ticket ID
				key = issues.getJSONObject(i % 1000).get("key").toString();
				
				System.out.println("Questa è la key demme: "+ key);
				// ed il JSONArray associato alle Affected Version
				JSONArray affectedVersionArray = singleJsonObject.getJSONArray("versions");
				System.out.println("Questo è l'array delle AV: "+affectedVersionArray);
				//Aggiungo alla lista dei ticket l'ID dello stesso, splittandolo dalla chiave che riporta "NOME-int"
				ticketsList.add(Integer.valueOf(key.split("-")[1]));
				

				// Estraggo una lista Java dal JSONArray per prendermi la lista delle affected versions
				List<String> affectedVersionList = jiraLogic.getJsonAVList(affectedVersionArray);

				// Calcolo l'indice delle Affected Version del ticket [InjectedVersion,
				// FixedVersion)
				
				//Lo split viene eseguito sulla "T" e viene preso il primo elemento perché è la data, dopo ci sarebbe l'ora
				jiraLogic.getBuggyVersionJiraAVList(affectedVersionList,
						singleJsonObject.getString("resolutiondate").split("T")[0],
						singleJsonObject.getString("created").split("T")[0], Integer.parseInt(key.split("-")[1]));

			}
		} while (i < total);

	}
	
	
	
	
	
}
