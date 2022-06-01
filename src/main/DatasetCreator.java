package main;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.apache.commons.collections4.MapIterator;
import org.apache.commons.collections4.keyvalue.MultiKey;
import org.apache.commons.collections4.map.MultiKeyMap;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.util.io.NullOutputStream;

import logic.JiraLogic;

public class DatasetCreator {
	
	// Stringhe utilizzate frequentemente
	public static final String USER_DIRECTORY = "user.dir";
	public static final String FILE_EXTENSION = ".java";
	
	
	public DatasetCreator() {
		//Non fa niente, creato per risolvere code smell
	}
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public void buildDatasetUp(String projectName, JiraLogic jiraLogic, int latestVersion, MultiKeyMap mapToBuildDataset) throws IOException, GitAPIException {

		ArrayList<Integer> fileMetrics;
		FileRepositoryBuilder builder = new FileRepositoryBuilder();

		// Imposto la cartella del progetto
		String repoFolder = System.getProperty(USER_DIRECTORY) + "/" + projectName + "/.git";
		Repository repo = builder.setGitDir(new File(repoFolder)).readEnvironment().findGitDir().build();
		
		LoggerClass.infoLog("Inizio a costruirmi i dati per il csv...");
		// Provo ad aprire la repo su Git
		try (Git git = new Git(repo)) {

			Iterable<RevCommit> commits = null;

			// Mi prendo tutti i commit
			commits = git.log().all().call();

			// Itero sul singolo commit nella lista di commits
			for (RevCommit commit : commits) {

				// Vedo se ciascuno ha un "parent commit"
				if (commit.getParentCount() != 0) {

					// Prendo la data del commit
					LocalDate commitDate = commit.getCommitterIdent().getWhen().toInstant()
							.atZone(ZoneId.systemDefault()).toLocalDate();

					// Prendo la versione di appartenenza del commit
					int appartainingVersion = jiraLogic.getCommitAppartainingVersionIndex(commitDate);

					List<DiffEntry> filesChanged;

					// Vedo se l'indice della versione fa parte della prima metà delle release
					if (appartainingVersion < latestVersion + 1) {
						
						//Analizzo il messaggio di commit di bugfix del ticket e lo aggiungo alla lista
						List<Integer> ticketBugFix = jiraLogic.getTicketMessageCommitBugFix(commit.getFullMessage(),
								projectName);

						// Prendo la lista dei ticket associati al commit
						List<Integer> ticketInformationBugginess = jiraLogic
								.getTicketMessageCommitBuggy(commit.getFullMessage(), projectName);

						// Prendo un nuovo formatter per prendere le differenze tra commit->parent
						// commit
						try (DiffFormatter differencesBetweenCommits = new DiffFormatter(NullOutputStream.INSTANCE)) {

							differencesBetweenCommits.setRepository(repo);

							// Prendo le differenze tra i due commit
							filesChanged = differencesBetweenCommits.scan(commit.getParent(0), commit);

							// Per ogni file cambiato nella lista dei file cambiati
							for (DiffEntry singleFile : filesChanged) {

								// Ci riferiamo solo a classi java
								if (singleFile.getNewPath().endsWith(FILE_EXTENSION)) {

									// Metto (solo se non presente) un record vuoto per la coppia (versione, path
									// del file)
									jiraLogic.putEmptyRecord(appartainingVersion, singleFile.getNewPath());

									// Prendo le metriche aggiornate del file in questione
									fileMetrics = (ArrayList<Integer>) jiraLogic.getMetrics(singleFile,
											appartainingVersion, differencesBetweenCommits, filesChanged, ticketBugFix,
											latestVersion + 1);

									mapToBuildDataset.replace(appartainingVersion, singleFile.getNewPath(), fileMetrics);

									// Se ci sono ticket associati al commit, imposto la relativa bugginess)
									jiraLogic.setClassBugginess(ticketInformationBugginess, singleFile,
											latestVersion + 1);
								}
							}
						}
					}
				}
			}
		}

	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public void writeCSVFile(String projectName, MultiKeyMap mapToBuildDataset, int latestVersion) throws IOException {
		
		LoggerClass.infoLog("Inizio ufficialmente a scrivere il csv, dopo aver fatto tutti i calcoli dovuti");
		
		// Imposto il nome del file
		try (FileWriter csvWriter = new FileWriter("csv/" + projectName + "_datasetDeliverableBuggyness.csv")) {

			/*
			 * Metriche prese in considerazione:
			 *
			 * 0 - LOC_Touched: somma, tra le revisioni, delle linee di codice aggiunte,
			 * modificate o rimosse 1 - NR: numero delle revisioni 2 - NFix: numero di bug
			 * fixati 3 - LOC_Added: somma, tra le revisioni, delle linee di codice aggiunte
			 * 4 - MAX_LOC_Added: massimo numero, tra le revisioni, di linee di codice
			 * cambiate 5 - ChgSetSize: numero di file "committati" insieme con il file in
			 * questione 6 - MAX_ChgSet: massimo numero di file committati insieme al file
			 * in questione tra le revisioni 7 - AVG_ChgSet: numero medio di file committati
			 * insieme al file in questione tra le revisioni 8 - Avg_LOC_Added: numero medio
			 * di linee di codice aggiunte per revisione 9 - Buggyness
			 * 
			 */

			// La prima riga è il numero di versione
			csvWriter.append("Version Number");
			csvWriter.append(",");
			csvWriter.append("Filename");
			csvWriter.append(",");
			csvWriter.append("LOC_Touched");
			csvWriter.append(",");
			csvWriter.append("Number_Revisions");
			csvWriter.append(",");
			csvWriter.append("NumberBugFix");
			csvWriter.append(",");
			csvWriter.append("LOC_Added");
			csvWriter.append(",");
			csvWriter.append("MAX_LOC_Added");
			csvWriter.append(",");
			csvWriter.append("ChgSetSize");
			csvWriter.append(",");
			csvWriter.append("Max_ChgSet");
			csvWriter.append(",");
			csvWriter.append("AVG_ChgSet");
			csvWriter.append(",");
			csvWriter.append("Avg_LOC_Added");
			csvWriter.append(",");
			csvWriter.append("Buggy");
			csvWriter.append("\n");

			Map<String, List<Integer>> monthsMap = new TreeMap<>();
			String buggy;
			int averageLOCAdded;
			int averageChgSet;
			
			//Creo un iterator per esplorare la mappa che mi costruirà il dataset
			MapIterator datasetIterator = mapToBuildDataset.mapIterator();

			// Itero sul dataset
			//Controllo inizialmente se ci sono elementi su cui iterare
			while (datasetIterator.hasNext()) {
				//Mi prendo la prossima chiave nell'iterazione
				datasetIterator.next();
				MultiKey key = (MultiKey) datasetIterator.getKey();

				// Prendo la lista di metriche associate alla multikey
				ArrayList<Integer> fileMetrics = (ArrayList<Integer>) mapToBuildDataset.get(key.getKey(0), key.getKey(1));
				
				//Inserisco in monthsMap: --> [versione, nomefile, listadellemetriche] ---> ordine alfanumerico
				
				monthsMap.put(String.valueOf(key.getKey(0)) + "," + (String) key.getKey(1), fileMetrics);
			}

			for (Map.Entry<String, List<Integer>> mapEntry : monthsMap.entrySet()) {

				ArrayList<Integer> fileMetrics = (ArrayList<Integer>) mapEntry.getValue();
				
				// Controllo che l'indice della versione sia contenuto nella prima metà delle
				// release
				if (Integer.valueOf(mapEntry.getKey().split(",")[0]) <= (latestVersion) + 1) {
					//Setto la buggyness vedendo il valore dell'ultima colonna
					if (fileMetrics.get(9).equals(0))
						buggy = "No";
					else
						buggy = "Yes";

					if (fileMetrics.get(1).equals(0)) {
						averageLOCAdded = 0;
						averageChgSet = 0;
					} else {
						//Calcolo l'AvgLocAdded e l'AvgChgSet
						averageLOCAdded = fileMetrics.get(5) / fileMetrics.get(1);
						averageChgSet = fileMetrics.get(3) / fileMetrics.get(1);
					}

					// Scrivo i dati sul file CSV
					csvWriter.append(mapEntry.getKey().split(",")[0] + "," + mapEntry.getKey().split(",")[1] + ","
							+ fileMetrics.get(0) + "," + fileMetrics.get(1) + "," + fileMetrics.get(2) + ","
							+ fileMetrics.get(3) + "," + fileMetrics.get(4) + "," + fileMetrics.get(5) + ","
							+ fileMetrics.get(6) + "," + averageLOCAdded + "," + averageChgSet + "," + buggy);

					csvWriter.append("\n");
				}
			}
			csvWriter.flush();
		}
	}
	
}
