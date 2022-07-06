package machine_learning;

import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import main.LoggerClass;
import weka.core.Instances;
import weka.core.converters.ConverterUtils.DataSource;

public class FinalEvaluation {
	
	//Stringhe utili per i filenames
	private static final String TRAINING = "_training.arff";
	private static final String TESTING = "_testing.arff";

	public static void main(String[] args) throws Throwable{

		//Dichiaro in una lista i progetti da svolgere: posso farli insieme o uno alla volta.
		//Se fatti insieme l'esecuzione potrebbe durare parecchio.
		String[] projects = {"BOOKKEEPER", "OPENJPA"};

		//Dichiaro il numero di versioni di ogni progetto (Bookkeeper:7, OpenJpa:18)
		Integer[] maxVersions = {7, 18};
		
		LoggerClass.setupLogger();
		LoggerClass.infoLog("Avvio il programma per il calcolo delle metriche finali...");

		//Apro il file di output, risultante dal calcolo delle metriche finali
		try (FileWriter csvMaker = new FileWriter("csv/FinalMetrics.csv")) {

			// Scrivo la prima riga del file in output
			csvMaker.append("Dataset,#Training,%Training,%Defect Training,%Defect Testing,Classifier,Balancing,Feature Selection,TP,FP,TN,FN,Precision,Recall,ROC Area,Kappa\n");	

			// Per ciascun progetto
			for (int j = 0; j < projects.length; j++) {

				// Itero sulla singola versione, applicando così Walk Forward
				for (int i = 1; i < maxVersions[j]; i++) {

					// Mi creo i file .arff per il training ed il testing e prendo il numero di istanze 
					// totali, il numero di istanze buggy e non buggy, date in output dal metodo.
					List<Integer> statsFromTraining = ArffBuilder.buildTrainingSetWalkForward(projects[j], i);
					List<Integer> statsFromTesting = ArffBuilder.buildTestingSetWalkForward(projects[j], i+1);
					
					//Percentuale file totali training/file totali training + testing.
					double percentageTraining = statsFromTraining.get(0) / (double)(statsFromTraining.get(0) + statsFromTesting.get(0));
					//Percentuale file buggy nel training/file totali training
					double defectivePercentageTraining = statsFromTraining.get(1) / (double)statsFromTraining.get(0);
					//Percentuale file defective nel testing/file totali testing
					double defectivePercentageTesting = statsFromTesting.get(1) / (double)statsFromTesting.get(0);
					//1-((defective totali training e testing)/file totali training e testing))
					double percentageMajorityClass = 1 - ( (statsFromTraining.get(1) + statsFromTesting.get(1)) / (double)(statsFromTraining.get(0) + statsFromTesting.get(0)));

					// Prendo il file .arff per il training, fino all'i-esima versione.
					DataSource source2 = new DataSource(projects[j] + TRAINING);
					Instances noFilterTesting = source2.getDataSet();
					
					// Prendo il file .arff per il testing, fino all'i+1-esima versione.
					DataSource source = new DataSource(projects[j] + TESTING);
					Instances noFilterTraining = source.getDataSet();
					
					// Applico il sampling sui due dataset
					List<String> outcomeSampling = TechniquesLogic.applySampling(noFilterTraining, noFilterTesting, percentageMajorityClass, "False");
					for (String outcome : outcomeSampling) {
						csvMaker.append(projects[j] + "," + i  + "," + percentageTraining  + "," + defectivePercentageTraining  + "," + defectivePercentageTesting +"," + outcome);
					}
					
					// Applico la feature selection sui due dataset
					List<String> outcomeFeatureSelection = TechniquesLogic.applyFeatureSelection(noFilterTraining, noFilterTesting, percentageMajorityClass);
					for (String outcome : outcomeFeatureSelection) {
						csvMaker.append(projects[j] + "," + i  + "," + percentageTraining  + "," + defectivePercentageTraining  + "," + defectivePercentageTesting +"," + outcome);
					}	

				}
				// Elimino i file .arff generati
				Files.deleteIfExists(Paths.get(projects[j] + TESTING));
				Files.deleteIfExists(Paths.get(projects[j] + TRAINING));
			}

			// Flush finale del fileWriter
			csvMaker.flush();
		}
	}

}
