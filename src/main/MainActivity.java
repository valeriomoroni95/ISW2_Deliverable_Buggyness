package main;

import org.apache.commons.collections4.map.LinkedMap;
import org.apache.commons.collections4.map.MultiKeyMap;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.util.FileUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

	// Stringhe utilizzate frequentemente
	public static final String USER_DIRECTORY = "user.dir";
	public static final String RELEASE_DATE = "releaseDate";
	public static final String FILE_EXTENSION = ".java";

	public static void main(String[] args) throws IOException, JSONException, GitAPIException {
		
		// Indice dell'ultimissima versione prendibile in considerazione
		int latestVersion;

		Map<Integer, List<Integer>> ticketsWithBuggyIndex;

		// nomi dei progetti da svolgere, inserire entrambi uno dopo l'altro o uno alla volta
		String[] projectList = { "BOOKKEEPER" };
		
		//Inizializzo il logger
		LoggerClass.setupLogger();
		LoggerClass.infoLog("Avvio il programma per la creazione del dataset...");

		for (String projectName : projectList) {

			ticketsWithBuggyIndex = new HashMap<>();
			ticketsList = new ArrayList<>();

			// Multimap<Data release, nome versione, indice versione>      Ordinamento naturale, utilizzo una linked list.
			Multimap<LocalDate, String> versionListWithReleaseDate = MultimapBuilder.treeKeys().linkedListValues().build();

			// Prendo la lista delle versioni con la data della release, formato: 2006-08-26=[0.9.0, 1]
			versionListWithReleaseDate = getVersionAndReleaseDate(projectName);
			
			// Scrivo la repository del progetto in questione, da github
			String projectRepository = "https://github.com/apache/" + projectName + ".git";

			jiraLogic = new JiraLogic(versionListWithReleaseDate, mapToBuildDataset, ticketsWithBuggyIndex, ticketsList);
			
			//Prendo la prima metà delle versioni!
			//Effettuo questa doppia divisione perché la size di una Multimap viene raddoppiata (key, value).
			//Per esempio, per Bookkeeper, ho 14 versioni totali, ma la size di versionListWithReleaseDate esce 28.
			//Per questo, per prendere solo la prima metà delle release, è come se dividessi per 4.
			latestVersion = (versionListWithReleaseDate.size()/2)/2;

			// Clono la repo nella cartella projectName, commenta questa linea di codice se il progetto è già
			// stato scaricato e non cancellato (il programma è stato interrotto prima di terminare)
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
						// putEmptyRecord aggiunge la lista di metriche azzerate per ogni file nella versione
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

			// Costruisco il dataset, partendo dall'oggetto dataBuilder
			DatasetCreator dataBuilder= new DatasetCreator();
			//passo al metodo il nome del progetto, l'istanza di JiraLogic, l'indice dell'ultima versione
			//e la mappa dove andrò a costruire il dataset
			dataBuilder.buildDatasetUp(projectName, jiraLogic, latestVersion, mapToBuildDataset);
			// Scrivo il dataset in un file CSV
			dataBuilder.writeCSVFile(projectName, mapToBuildDataset, latestVersion);
			
			LoggerClass.infoLog("Ho terminato di creare il dataset.");
			// A fine utilizzo, cancello la cartella con la repo del progetto
			FileUtils.delete(new File(projectName), 1);
		}

	}

	
	// Prende in input il nome del progetto e si prende le versioni e le date delle release.
	// Bookkeeper ha smesso di utilizzare Jira per gli Issue ed usa esclusivamente GitHub.
	public static Multimap<LocalDate, String> getVersionAndReleaseDate(String projectName) throws IOException, JSONException {

		Integer i;
		//Creo una Multimap fatta così <Data release, Nome Release>
		Multimap<LocalDate, String> versionsList = MultimapBuilder.treeKeys().linkedListValues().build();
		String releaseName = null;

		// Url per prendere le informazioni associate al progetto in Jira
		String url = "https://issues.apache.org/jira/rest/api/2/project/" + projectName;
		
		//Mi vengono stampati tutti i JSONObject con le informazioni da Jira
		JSONObject json = ParserJson.readJsonFromUrl(url);
		
		LoggerClass.infoLog("Inizio a prendere le versioni e le date delle release...");

		// Prendo l'array JSON associato alla versione del progetto
		JSONArray versions = json.getJSONArray("versions");
		
		//Cambio alcuni nomi e versioni di Bookkeeper perché sbagliate e non aggiornate.
		if(projectName=="BOOKKEEPER") {
			for (i = 0; i < versions.length(); i++) {
			JSONObject item = versions.getJSONObject(i);
			if(item.get("name").equals("4.1.1") && item.get(RELEASE_DATE).equals("2013-01-16")) {
				item.put("name", "4.2.1");
				item.put(RELEASE_DATE, "2013-02-27");
			}else if(item.get("name").equals("4.2.3") && item.get(RELEASE_DATE).equals("2014-06-27")) {
				item.put(RELEASE_DATE, "2013-06-27");
			}else if(item.get("name").equals("4.3.0") && item.get(RELEASE_DATE).equals("2014-02-02")) {
				item.put(RELEASE_DATE, "2014-10-14");
			}else if(item.get("name").equals("4.2.1") && item.get(RELEASE_DATE).equals("2013-03-27")) {
				item.put("name", "4.2.4");
				item.put(RELEASE_DATE, "2015-01-16");
			}else if(item.get("name").equals("4.5.1") && item.get(RELEASE_DATE).equals("2017-09-10")) {
				item.put(RELEASE_DATE, "2017-11-22");
			}else if(item.get("name").equals("4.6.0") && item.get(RELEASE_DATE).equals("2017-11-10")) {
				item.put(RELEASE_DATE, "2017-12-27");
			}
			}
		}
		
		// Per ogni versione
		for (i = 0; i < versions.length(); i++) {
			
			// controllo se ha una data per la release ed un nome
			if (versions.getJSONObject(i).has(RELEASE_DATE) && versions.getJSONObject(i).has("name")) {
				releaseName = versions.getJSONObject(i).get("name").toString();
				
				//La aggiungo alla lista delle versioni (data, nome release)
				versionsList.put(LocalDate.parse(versions.getJSONObject(i).get(RELEASE_DATE).toString()), releaseName);
			}
		}

		// Dò un indice a ciascuna versione
		int versionCount = 1;
		for (LocalDate ld : versionsList.keySet()) {
			versionsList.put(ld, String.valueOf(versionCount));
			versionCount++;
		}
		//versionsList ora contiene: <DataRelease = nomeRelease, versionCount>
		
		return versionsList;
	}
	
	//Utilizzo il codice del professore, RetrieveTicketsID, per cercare i ticket con bug chiusi o risolti,
	//con resolution fixed da Jira
	public static void getBuggyVersionAVTicket(String projectName) throws IOException, JSONException {

		Integer j = 0;
		Integer i = 0;
		Integer total = 1;
		String key = null;
		
		LoggerClass.infoLog("Inizio a cercare i ticket con bug, chiusi o risolti, con resolution fixed, da Jira...");
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
				// Mi prendo la chiave, ossia il ticket ID
				key = issues.getJSONObject(i % 1000).get("key").toString();
				// ed il JSONArray associato alle Affected Version
				JSONArray affectedVersionArray = singleJsonObject.getJSONArray("versions");
				//Aggiungo alla lista dei ticket l'ID dello stesso, splittandolo dalla chiave che riporta "NOME-#intero"
				ticketsList.add(Integer.valueOf(key.split("-")[1]));
				
				// Estraggo una lista Java dal JSONArray per prendermi la lista delle affected versions
				List<String> affectedVersionList = jiraLogic.getJsonAVList(affectedVersionArray);

				// Calcolo l'indice delle Affected Version del ticket [InjectedVersion,FixedVersion)
				
				//Lo split viene eseguito sulla "T" e viene preso il primo elemento perché è la data, dopo ci sarebbe l'ora,
				// che ignoro perché inutile per i nostri calcoli. Sto passando la lista delle affectedVersion, la resolutionDate,
				// la data di creazione del ticket e l'ID dello stesso.
				jiraLogic.getBuggyVersionJiraAVList(affectedVersionList,
						singleJsonObject.getString("resolutiondate").split("T")[0],
						singleJsonObject.getString("created").split("T")[0], Integer.parseInt(key.split("-")[1]));

			}
		} while (i < total);

	}	
	
}
