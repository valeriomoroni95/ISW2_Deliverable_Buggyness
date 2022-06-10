package machine_learning;

import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import weka.core.Instances;
import weka.classifiers.Evaluation;
import weka.classifiers.trees.RandomForest;
import weka.classifiers.bayes.NaiveBayes;
import weka.core.converters.ConverterUtils.DataSource;
import weka.classifiers.lazy.IBk;

public class EvaluationLogic {
	
	//Stringhe utili per i filenames
	private static final String TRAINING = "_training.arff";
	private static final String TESTING = "_testing.arff";

	public static void main(String[] args) throws Exception{

		//Dichiaro in una lista i progetti da svolgere: posso farli insieme o uno alla volta.
		String[] projects = {"BOOKKEEPER", "OPENJPA"};

		//Dichiaro il numero di revisioni di ogni progetto (Bookkeeper:7, OpenJpa:18)
		Integer[] maxVersions = {7, 18};

		//Per ciascun progetto
		for (int i = 0; i < projects.length; i++) {

			//Apro il file di output dal calcolo delle metriche
			try (FileWriter csvMaker = new FileWriter("csv/"+projects[i]+ "_metrics.csv")) {

				// Scrivo la prima riga del file in output
				csvMaker.append("Dataset_Name,N°TrainingRelease,Classifier,Precision,Recall,AUC,Kappa\n");

				// Itero sulla singola versione, applicando così Walk Forward
				for (int j = 1; j < maxVersions[i]; j++) {

					//Creo il file .arff per il training, fino alla versione n-esima del progetto in questione
					ArffBuilder.buildTrainingSetWalkForward(projects[i], j);

					//Creo il file .arff per il testing, fino alla versione n+1
					ArffBuilder.buildTestingSetWalkForward(projects[i] ,j+1);

					// Prendo i file .arff appena creati per darli in input a Weka
					DataSource source_training = new DataSource("arff/"+projects[i] + TRAINING);
					Instances training_data = source_training.getDataSet();
					DataSource source_testing = new DataSource("arff/"+projects[i] + TESTING);
					Instances testing_data = source_testing.getDataSet();

					// Setto il numero di attributi per ciascuno
					// dei due dataset, -1 perché l'ultimo è proprio
					// l'oggetto della predizione
					int attributesNumber = training_data.numAttributes();
					training_data.setClassIndex(attributesNumber - 1);
					testing_data.setClassIndex(attributesNumber - 1);

					// Faccio la new dei tre classificatori presi in esame
					IBk classifier_IBk = new IBk();
					RandomForest classifier_RandomForest = new RandomForest();
					NaiveBayes classifier_NaiveBayes = new NaiveBayes();

					// Dò al classificatore il training set
					classifier_IBk.buildClassifier(training_data);
					classifier_RandomForest.buildClassifier(training_data);
					classifier_NaiveBayes.buildClassifier(training_data);

					// Creo un oggetto Evaluation con i dati di training 
					Evaluation evaluate = new Evaluation(training_data);	

					// Valuto ciascun modello con ogni classificatore ed i dati di testing
					// Scrivo poi il risultato sul file in output.
					evaluate.evaluateModel(classifier_NaiveBayes, testing_data); 
					csvMaker.append(projects[i] + "," + j + ",NaiveBayes," + evaluate.precision(0) + "," + evaluate.recall(0) +  "," + evaluate.areaUnderROC(0) + "," + evaluate.kappa() + "\n");
					
					evaluate.evaluateModel(classifier_RandomForest, testing_data); 
					csvMaker.append(projects[i] + "," + j + ",RandomForest," + evaluate.precision(0) + "," + evaluate.recall(0) +  "," + evaluate.areaUnderROC(0) + "," + evaluate.kappa() + "\n");

					evaluate.evaluateModel(classifier_IBk, testing_data); 
					csvMaker.append(projects[i] + "," + j + ",IBk," + evaluate.precision(0) + "," + evaluate.recall(0) +  "," + evaluate.areaUnderROC(0) + "," + evaluate.kappa() + "\n");

				}

				// Cancello il file creato in precedenza
				Files.deleteIfExists(Paths.get(projects[i] + TESTING));
				Files.deleteIfExists(Paths.get(projects[i] + TRAINING));
				csvMaker.flush();
			}
		}
	}

}
