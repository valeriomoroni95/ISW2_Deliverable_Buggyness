package machine_learning;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ArffBuilder {
	
	//Stringhe utili per i filenames
	private static final String TRAINING = "_training.arff";
	private static final String TESTING = "_testing.arff";
	
	
	private ArffBuilder() throws Exception {
		throw new Exception("This should not be called!");
	}
	
	public static int appendToCSV(FileWriter csvMaker, String row) throws IOException {
		
		int defectiveCount = 0;
		
		String[] splitRow = row.split(",");
		
		// Salto le prime due colonne lette dal csv, ovvero n°versione + nome_file
		for (int i = 2; i < splitRow.length; i++) {
			//Se con l'indice arrivo alla colonna della buggyness
			if (i == splitRow.length - 1) {
				//Se è buggy aumento il contatore di 1
				if(splitRow[i].equals("Yes"))
					defectiveCount += 1;
				//Vado a capo dopo l'ultimo elemento letto
				csvMaker.append(splitRow[i] + "\n");
			} else {
				//Se non è l'ultima colonna scrivo l'elemento ed aggiungo una virgola al csv.
				csvMaker.append(splitRow[i] + ",");
			}
		}
		return defectiveCount;
	}
	
	
	//Mi costruisco il file .arff per il training set
	public static List<Integer> buildTrainingSetWalkForward(String projectName, int trainingLimit) throws IOException {

		int fileCount = 0;
		int defectiveCount = 0;

		ArrayList<Integer> statsHolder = new ArrayList<>();

		// Creo il file .arff con il nome del progetto in questione 
		try (FileWriter csvMaker = new FileWriter(projectName + TRAINING)) {

			// Aggiungo le dichiarazioni degli attributi , del progetto e dei dati
			csvMaker.append("@relation " + projectName + "\n\n");
			csvMaker.append("@attribute LOC_Touched real\n");
			csvMaker.append("@attribute NumberRevisions real\n");
			csvMaker.append("@attribute NumberBugFix real\n");
			csvMaker.append("@attribute LOC_Added real\n");
			csvMaker.append("@attribute MAX_LOC_Added real\n");
			csvMaker.append("@attribute Chg_Set_Size real\n");
			csvMaker.append("@attribute Max_Chg_Set real\n");
			csvMaker.append("@attribute AVG_Chg_Set real\n");
			csvMaker.append("@attribute Avg_LOC_Added real\n");
			csvMaker.append("@attribute Buggy {Yes, No}\n\n");
			csvMaker.append("@data\n");

			// Prendo il dataset creato da DatasetCreator
			try (BufferedReader reader = new BufferedReader(new FileReader("csv/" + projectName + "_datasetDeliverableBuggyness.csv"))){ 

				//Leggo e salto la prima riga, che contiene solo i nomi delle colonne
				String line = reader.readLine();

				// Leggo tutto quanto il documento
				while ((line = reader.readLine()) != null){  

					// Se il numero di versione rientra nei limiti, aumento il contatore e scrivo la riga
					// in questione
					if (Integer.parseInt(line.split(",")[0]) <= trainingLimit ) {

						fileCount += 1;

						defectiveCount += appendToCSV(csvMaker, line);
					}
				}
				csvMaker.flush();

				statsHolder.add(fileCount);
				statsHolder.add(defectiveCount);

				return statsHolder;

			}
		}
	}

	//Mi costruisco il file .arff per il testing set
	public static List<Integer> buildTestingSetWalkForward(String projectName, int testing) throws IOException {

		int fileCount = 0;
		int defectiveCount = 0;
		ArrayList<Integer> statsHolder = new ArrayList<>();
		// Creo il file .arff con il nome del progetto preso in esame
		try (FileWriter csvWriter = new FileWriter(projectName + TESTING)) {

			// Aggiungo le dichiarazioni degli attributi, del progetto e dei dati
			csvWriter.append("@relation " + projectName + "\n\n");
			csvWriter.append("@attribute LOC_Touched real\n");
			csvWriter.append("@attribute NumberRevisions real\n");
			csvWriter.append("@attribute NumberBugFix real\n");
			csvWriter.append("@attribute LOC_Addedr real\n");
			csvWriter.append("@attribute MAX_LOC_Added real\n");
			csvWriter.append("@attribute Chg_Set_Size real\n");
			csvWriter.append("@attribute Max_Chg_Set real\n");
			csvWriter.append("@attribute AVG_Chg_Set real\n");
			csvWriter.append("@attribute Avg_LOC_Added real\n");
			csvWriter.append("@attribute Buggy {Yes, No}\n\n");
			csvWriter.append("@data\n");

			// Prendo il dataset creato da DatasetCreator
			try (BufferedReader reader = new BufferedReader(new FileReader("csv/" + projectName + "_datasetDeliverableBuggyness.csv"))){  

				// Leggo la prima riga, che contiene solo il nome delle colonne
				String line = reader.readLine();

				// Leggo fino alla fine 
				while ((line = reader.readLine()) != null){  

					// Controllo se la versione è uguale a quella selezionata per il testing.
					// Uso sempre tutte le versioni precedenti alla corrente.
					if (Integer.parseInt(line.split(",")[0]) == testing ) {

						fileCount+= 1;

						// Aggiungo le righe lette dal csv senza le prime due colonne
						defectiveCount += appendToCSV(csvWriter, line);
					}
				}
				csvWriter.flush();
				statsHolder.add(fileCount);
				statsHolder.add(defectiveCount);
			}
		}
		return statsHolder;
	}

}
