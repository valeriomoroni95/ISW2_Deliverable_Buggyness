package logic;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.collections4.map.MultiKeyMap;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.google.common.collect.Iterables;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;

public class JiraLogic {

	// Multimap<data della release, nome versione, indice versione>
	private Multimap<LocalDate, String> versionListWithReleaseDateAndIndex;

	// Map<ticketID, (Opening Version, Fixed Version)>
	private Multimap<Integer, Double> proportionTickets = MultimapBuilder.treeKeys().linkedListValues().build();

	// Map<ticketID, (OV, FV)>
	private Multimap<Integer, Integer> ticketNoAVList = MultimapBuilder.treeKeys().linkedListValues().build();

	// MultiKeyMap<Versione del file, path del file, lista delle metriche>
	private MultiKeyMap mapToBuildDataset;

	private List<Integer> ticketsList;

	// Map<ticketID, (IV, FV)>
	private Map<Integer, List<Integer>> ticketBuggyIndex;

	private static final String RELEASE_DATE = "releaseDate";
	private static final int METRICS_NUMBER = 10;

	public JiraLogic(Multimap<LocalDate, String> versionListWithReleaseDateAndIndex, MultiKeyMap fileMapDataset,
			Map<Integer, List<Integer>> ticketBuggyIndex, List<Integer> ticketList) {

		this.versionListWithReleaseDateAndIndex = versionListWithReleaseDateAndIndex;
		this.mapToBuildDataset = fileMapDataset;
		this.ticketBuggyIndex = ticketBuggyIndex;
		this.ticketsList = ticketList;
	}

	// Metto un record vuoto per le metriche nella mappa (indice release, nome del file, metriche con valori 0)

	public void putEmptyRecord(int releaseIndex, String filename) {
	
		if (!mapToBuildDataset.containsKey(releaseIndex, filename)) {

			ArrayList<Integer> emptyArrayList = new ArrayList<>();

			for (int i = 0; i < METRICS_NUMBER; i++) {
				emptyArrayList.add(0);
			}

			mapToBuildDataset.put(releaseIndex, filename, emptyArrayList);
		}
	}

//	Ritorna la lista delle Affected Version di un ticket Jira, prendendo in input
//	l'Array JSON da Jira

	public List<String> getJsonAVList(JSONArray json) throws JSONException {

		List<String> affectedVersionList = new ArrayList<>();

		if (json.length() > 0) {

			// Per ciascuna release nelle Affected Version
			for (int i = 0; i < json.length(); i++) {

				JSONObject singleRelease = json.getJSONObject(i);

				// controlla se la release ha una data
				if (singleRelease.has(RELEASE_DATE)) {
					//se la release ha una data, la aggiungo alla lista prendendo il campo nome del JSONObject
					affectedVersionList.add(singleRelease.getString("name"));
				}
			}
		}

		return affectedVersionList;
	}

//	Ritorna la lista contenente (IV,FV, ticketID) per ciascun ticket contenuto nel messaggio di commit,
//	prendendo in input il nome del progetto ed il relativo messaggio di commit

	public List<Integer> getTicketMessageCommitBuggy(String commitMsg, String projectName) {

		List<Integer> outcomes = new ArrayList<>();
		Pattern pattern = null;
		Matcher matcher = null;
		
		//Per ciascuna entry della mappa <ticketID, (IV, FV)>
		for (Map.Entry<Integer, List<Integer>> entry : ticketBuggyIndex.entrySet()) {
			// Controlla se il messaggio di commit contiene l'espressione
			// "NomeProgetto-IssueID"
			pattern = Pattern.compile("\\b" + projectName + "-" + entry.getKey() + "\\b", Pattern.CASE_INSENSITIVE);
			matcher = pattern.matcher(commitMsg);
			
			// Controlla se il messaggio di commit contiene l'issue ID marcato come "not
			// checked"
			if (matcher.find() && !outcomes.contains(entry.getKey())) {
				outcomes.add(ticketBuggyIndex.get(entry.getKey()).get(0));
				outcomes.add(ticketBuggyIndex.get(entry.getKey()).get(1));
				outcomes.add(entry.getKey());
			}
		}
		//System.out.println(outcomes);
		return outcomes;
	}

	public List<Integer> getTicketMessageCommitBugFix(String commitMessage, String projectName) {

		List<Integer> outcomes = new ArrayList<>();
		Pattern pattern = null;
		Matcher matcher = null;

		for (Integer entry : ticketsList) {

			// Controlla se il messaggio di commit contiene l'espressione
			// "NomeProgetto-IssueID"
			pattern = Pattern.compile("\\b" + projectName + "-" + entry + "\\b", Pattern.CASE_INSENSITIVE);
			matcher = pattern.matcher(commitMessage);

			// Controlla se il messaggio di commit contiene l'issue ID marcato come "not
			// checked"
			if (matcher.find() && !outcomes.contains(entry)) {
				outcomes.add(entry);
			}
		}
		return outcomes;
	}

	// Calcolo il valore delle metriche richieste per singolo file contenuto nel
	// commit

	public List<Integer> getMetrics(DiffEntry entry, int version, DiffFormatter diffFormatter,
			List<DiffEntry> filesChanged, List<Integer> ticketAssociated, int upperBoundVersion) throws IOException {

//		 Struttura metriche: 0->LOC_Touched; 1-> NR; 2-> NFix 
//		 3->LOC_Added; 4->MAX_LOC_Added; 5->ChgSetSize; 6->Max_ChgSet
//		 7->Avg_ChgSet; 8->AVG_LOC_Added.

		// Prende le metriche correnti per la coppia (versione, nome del file)
		@SuppressWarnings("unchecked")
		ArrayList<Integer> outcome = (ArrayList<Integer>) mapToBuildDataset.get(version, entry.getNewPath());

		// Controllo se la versione di appartenenza del file è minore dell'upperBound
		if (version < upperBoundVersion) {

			int linesTouched = 0;
			int linesAdded = 0;
			int chgSetSize = 0;

			// Numero totale di file "committati"
			chgSetSize = filesChanged.size();

			// Per ogni modifica fatta al file
			for (Edit edit : diffFormatter.toFileHeader(entry).toEditList()) {

				// Ne controllo il tipo ed incremento la variabile corrispondente (in caso di insert, delete o replace)
				if (edit.getType() == Edit.Type.INSERT) {
					linesAdded += edit.getEndB() - edit.getBeginB();
					linesTouched += edit.getEndB() - edit.getBeginB();
				} else if (edit.getType() == Edit.Type.DELETE) {
					linesTouched += edit.getEndA() - edit.getBeginA();
				} else if (edit.getType() == Edit.Type.REPLACE) {
					linesTouched += edit.getEndA() - edit.getBeginA();
				}
			}

			// Aggiorno ogni metrica
			outcome.set(0, outcome.get(0) + linesTouched);
			outcome.set(1, outcome.get(1) + 1);

			// Controllo se il commit è associato a qualche ticket
			if (ticketAssociated.isEmpty()) {
				outcome.set(2, outcome.get(2) + 0);

			} else {

				// In caso affermativo, metto a buggy e mi calcolo "NFix", cioè il numero di bug
				// fixati
				outcome.set(2, outcome.get(2) + ticketAssociated.size());
				outcome.set(9, 1);  //Setto la classe a buggy
			}

			outcome.set(3, outcome.get(3) + linesAdded); //Aggiorno le linee di codice aggiunte

			if (linesAdded > outcome.get(4)) {  //Aggiorno il numero massimo di linee di codice aggiunte
				outcome.set(4, linesAdded);
			}

			outcome.set(5, outcome.get(5) + chgSetSize); //Aggiorno il changing set size

			if (chgSetSize > outcome.get(6)) {    //Mi calcolo il massimo changing set 
				outcome.set(6, chgSetSize);
			}
		}

		return outcome;
	}

	// Imposto il file come "buggy" nella multi key map del dataset

	public void setClassBugginess(List<Integer> ticketAssociatedWithCommit, DiffEntry entry, int numberOfVersions) {

		// Controllo la lista dei ticket associati al commit ed il tipo di modifiche
		// apportate al file
		if (!ticketAssociatedWithCommit.isEmpty() && (entry.getChangeType() == DiffEntry.ChangeType.MODIFY
				|| entry.getChangeType() == DiffEntry.ChangeType.DELETE)) {

			// Per ogni ticket (IV, OV, ticketId)
			for (int i = 0; i < ticketAssociatedWithCommit.size(); i += 3) {

				int startVersion = ticketAssociatedWithCommit.get(i);
				int endVersion = ticketAssociatedWithCommit.get(i + 1);

				// per ciascuna versione nella lista di AV controllo se l'indice di versione è
				// incluso
				// nella prima metà delle release
				for (int version = startVersion; version < endVersion && version < numberOfVersions; version++) {

					if (!mapToBuildDataset.containsKey(version, entry.getNewPath())) {
						putEmptyRecord(version, entry.getNewPath());

						// Imposto la classe come buggy
						List<Integer> outcome = (ArrayList<Integer>) mapToBuildDataset.get(version, entry.getNewPath());
						outcome.set(9, 1);
						mapToBuildDataset.replace(version, entry.getNewPath(), outcome);
					}
				}
			}
		}
	}

	// Calcola il valore di proportion usando i ticket con lista delle AV prese da
	// Jira
	public void getBuggyVersionJiraAVList(List<String> affectedVersionList, String resolutionDate, String creationDate,
			int ticketID) {
		
		// inizializzo a 0 le variabili da calcolare
		double proportion = 0;
		int fvIndex = 0;
		int ivIndex = 0;
		int ovIndex = 0;

		ovIndex = getOpeningVersion(creationDate);
		fvIndex = getFixedVersion(resolutionDate);
		ivIndex = getIVfromAffectedVersion(affectedVersionList, creationDate);

		// Se l'indice di IV è !=0, allora il ticket ha associato una AV valida
		if (ivIndex != 0) {

			// Calcolo P e l'aggiungo alla lista di tutti i ticket con Proportion
			// Se non ho valori errati di fv, ov ed iv
			if (!(fvIndex == ovIndex || fvIndex == ivIndex || fvIndex < ivIndex)) {
				//Mi calcolo il valore di proportion utilizzando la formula
				proportion = getAVProportion(ivIndex, fvIndex, ovIndex);
				
				//Se il valore di proportion è positivo, inserisco il valore nella mappa 
				//in questo formato: 203=[1.0, 2.0] 
				if (proportion > 0) {
					proportionTickets.put(ticketID, 1.0);
					proportionTickets.put(ticketID, proportion);
				}
			}
			
			// Prendo l'indice di IV e FV del ticket
			getBuggyVersions(ticketID, fvIndex, ivIndex);

		} else {

			// Se l'affected version non è presente, allora metto il ticket nella lista dei tickets 
			// che hanno bisogno di proportion per la stime dell'Injected Version
			ticketNoAVList.put(ticketID, ovIndex);
			ticketNoAVList.put(ticketID, fvIndex);
		}
		System.out.println("Questa è la lista ticketNoAVList: "+ticketNoAVList.toString());
	}

	// Calcolo l'IV per i ticket con lista delle AV da Jira

	public void getBuggyVersionProportionTicket() {

		int ivIndx = 1;

		// Vedo tra tutti i commit senza lista delle AV
		for (int i : ticketNoAVList.keySet()) {

			int fvIndx = Iterables.get(ticketNoAVList.get(i), 1);
			int ovIndx = Iterables.get(ticketNoAVList.get(i), 0);

			// Prendo la proportion media dei ticket precedenti
			int proportion = (int) Math.round(getProportionPreviousTicket(i));

			// Controllo se la lista delle AV è non vuota
			if (fvIndx != ovIndx) {

				// Uso la formula se il valore medio dei ticket precedenti è >0
				if (proportion > 0) {
					ivIndx = fvIndx - (fvIndx - ovIndx) * proportion;
					// se il valore di IV è minore di 1, la imposto di default a 1
					if (ivIndx < 1)
						ivIndx = 1;
					// altrimenti, per semplicità, assumo IV=OV
				} else {
					ivIndx = ovIndx;
				}

				// Prendo gli indici IV e FV del ticket
				getBuggyVersions(i, fvIndx, ivIndx);
			}
		}
	}

	// Calcolo l'indice della versione d'appartenenza di un file, data in input
	// la data del commit
	public int getCommitAppartainingVersionIndex(LocalDate fileCommitDate) {

		int lastIndex = 0;

		// Iterate over all version with release date
		for (LocalDate ld : versionListWithReleaseDateAndIndex.keySet()) {

			Collection<String> lineKey = versionListWithReleaseDateAndIndex.get(ld);

			int size = lineKey.size();
			String lastIndexValue = null;

			// Se la size è uguale a 3, prendo il terzo elemento come indice, altrimenti il
			// secondo
			if (size == 3) {
				lastIndexValue = Iterables.get(lineKey, 2);
			} else {
				lastIndexValue = Iterables.get(lineKey, 1);
			}

			lastIndex = Integer.valueOf(lastIndexValue);

			// Mi interrompo se trovo la versione d'appartenenza del file
			// prima della fine dell'iterazione
			if (ld.isAfter(fileCommitDate)) {
				break;
			}
		}
		return lastIndex;
	}

	// Mi calcolo il valore di Proportion dei ticket precedenti (se ce ne sono,
	// altrimenti il valore è zero)

	public double getProportionPreviousTicket(int ticketID) {

		int count = 0;
		double proportion = 0;
		double outcome;

		// Per ogni ticket che ha un valore calcolato di Proportion
		for (int i : proportionTickets.keySet()) {

			// Controllo se l'ID del ticket è inferiore al considerato e ci sommo P
			if (i < ticketID && Iterables.get(proportionTickets.get(i), 0) != -1.0) {
				count += 1;
				proportion += Iterables.get(proportionTickets.get(i), 1);
			}
		}

		// Se il numero di ticket precedenti è >0, mi calcolo il valore medio di P
		if (count > 0) {
			outcome = count / proportion;
		} else {

			// Altrimenti assegno 0
			outcome = 0;
		}

		return outcome;
	}

	// Calcolo con la formula il valore di proportion a partire dal ticket
	// considerato 
	public double getAVProportion(int iv, int fv, int ov) {
		double fvIv = (double) fv - iv;
		double fvOv = (double) fv - ov;
		return fvIv / fvOv;
	}

	// Controllo gli indici di IV e FV ed aggiungo il ticket al commit con lista
	// delle Affected Version valida
	public void getBuggyVersions(int ticketID, int fvIndx, int ivIndx) {

		List<Integer> avVersionListNotEmpty = new ArrayList<>();
		// Controllo sugli indici delle versioni
		if (fvIndx != ivIndx && ivIndx < fvIndx) {

			// Aggiungo il ticket alla lista di ticket con AV list non vuota
			avVersionListNotEmpty.add(ivIndx);
			avVersionListNotEmpty.add(fvIndx);
			ticketBuggyIndex.put(ticketID, avVersionListNotEmpty);
		}
		System.out.println("Valore di ticketBuggyIndex: "+ticketBuggyIndex.toString());
	}

	// Calcolo l'indice della Fixed Version
	public int getFixedVersion(String resolutionDate) throws NumberFormatException {

		int fvIndex = 0;

		// Itero su tutte le versioni con release date
		for (LocalDate ld : versionListWithReleaseDateAndIndex.keySet()) {

//			Assegno prima del controllo per garantirmi che, se un ticket ha una data di risoluzione
//			seguente all'ultima versione rilasciata, la associo a quest'ultima versione. In questo 
//			modo assegno una FV al ticket non reale, perché non voglio perdere la lista delle AV
//			del ticket

//			se la keymap corrente ha 3 elementi, allora anziché prendere quello in posizione 2 prendi quello in posizione 3
			Collection<String> lineKey = versionListWithReleaseDateAndIndex.get(ld);
			int size = lineKey.size();
			String fxIndexValue = null;
			if (size == 3) {
				fxIndexValue = Iterables.get(lineKey, 2);
			} else {
				fxIndexValue = Iterables.get(lineKey, 1);
			}
			fvIndex = Integer.valueOf(fxIndexValue);
			
			//Mi fermo se la data è successiva o uguale alla resolutionDate del ticket
			if (ld.isEqual(LocalDate.parse(resolutionDate)) || ld.isAfter(LocalDate.parse(resolutionDate))) {

				break;
			}
		}
		
		return fvIndex;
	}

	// Calcolo l'indice dell'Opening Version
	public int getOpeningVersion(String ticketCreationDate) throws NumberFormatException {

		int ovIndex = 0;
		// Itero sopra ogni versione che ha associata una release date

		for (LocalDate ld : versionListWithReleaseDateAndIndex.keySet()) {

//			Assegno prima del controllo per garantirmi che, se un commit ha una data di commit
//			seguente all'ultima versione rilasciata, la associo a quest'ultima versione. In questo 
//			modo assegno una FV al ticket non reale, perché non voglio perdere la lista delle AV
//			del ticket

//			se la keymap corrente ha 3 elementi, allora anziché prendere quello in posizione 2 prendi quello in posizione 3
			Collection<String> lineKey = versionListWithReleaseDateAndIndex.get(ld);
			int size = lineKey.size();
			String oxIndexValue = null;
			if (size == 3) {
				oxIndexValue = Iterables.get(lineKey, 2);
			} else {
				oxIndexValue = Iterables.get(lineKey, 1);
			}
			ovIndex = Integer.valueOf(oxIndexValue);
			
			
			//Mi fermo se la data è successiva o uguale alla data di creazione del ticket
			if (ld.isAfter(LocalDate.parse(ticketCreationDate)) || ld.isEqual(LocalDate.parse(ticketCreationDate))) {

				break;
			}

		}

		return ovIndex;
	}

	// Calcolo l'indice della IV più vecchia a partire dalla lista delle AV di Jira
	public int getIVfromAffectedVersion(List<String> avVersionList, String creationDate) {

		int ivVersion = 0;

//		Itero su tutte le versioni releaseDate e versionsList per trovare
//		l'AV più vecchia
		for (LocalDate ld : versionListWithReleaseDateAndIndex.keySet()) {
			for (String s : avVersionList) {

				Collection<String> lineKey = versionListWithReleaseDateAndIndex.get(ld);
				int size = lineKey.size();
				String ivVersionValue = null;

//				Controllo se l'indice della versione è uguale a quella contenuta nella lista,
//				controllando inoltre che la versione di rilascio è precedente alla creazione del
//				ticket
				if (Iterables.get(versionListWithReleaseDateAndIndex.get(ld), 0).equals(s)
						&& ld.isBefore(LocalDate.parse(creationDate))) {

//				se la keymap corrente ha 3 elementi, allora anziché prendere quello in posizione 2 prendi quello in posizione 3
					if (size == 3) {
						ivVersionValue = Iterables.get(lineKey, 2);
					} else {
						ivVersionValue = Iterables.get(lineKey, 1);
					}
					ivVersion = Integer.valueOf(ivVersionValue);
					break;
				}
			}
		}

		return ivVersion;
	}

}