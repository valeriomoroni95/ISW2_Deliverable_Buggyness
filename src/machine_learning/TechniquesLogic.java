package machine_learning;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import weka.attributeSelection.CfsSubsetEval;
import weka.attributeSelection.GreedyStepwise;
import weka.classifiers.AbstractClassifier;
import weka.classifiers.Evaluation;
import weka.classifiers.bayes.NaiveBayes;
import weka.classifiers.lazy.IBk;
import weka.classifiers.meta.FilteredClassifier;
import weka.classifiers.trees.RandomForest;
import weka.core.Instances;
import weka.filters.Filter;
import weka.filters.supervised.attribute.AttributeSelection;
import weka.filters.supervised.instance.Resample;
import weka.filters.supervised.instance.SMOTE;
import weka.filters.supervised.instance.SpreadSubsample;

public class TechniquesLogic {
	
	private TechniquesLogic() {
		throw new IllegalStateException("This should not be called!");
	}
	
	//Stringhe utilizzate frequentemente per l'utilizzo delle varie tecniche, riportate qui per comodità.
	private static final String OVER_SAMPLING = "Over sampling";
	private static final String UNDER_SAMPLING = "Under sampling";
	private static final String SMOTE = "Smote";
	private static final String NO_SAMPLING = "No sampling";
	private static final Logger LOGGER = Logger.getLogger(TechniquesLogic.class.getName());

	
	//Applico feature selection, varie tecniche di sampling e valuto il modello. Prende in input l'oggetto della valutazione
	//il nome del classificatore, la percentuale, nel training set, della classe maggioritaria e ritorna una lista di 
	// metriche.
	public static List<String> applyFeatureSelection(Instances training, Instances testing, double percentageMajorityClass) throws HandledException{

		// Mi creo il filtro.
		AttributeSelection filter = new AttributeSelection();
		//Per la correlazione dei singoli attributi
		CfsSubsetEval eval = new CfsSubsetEval();
		//Ricerca greedy..
		GreedyStepwise search = new GreedyStepwise();
		//Impostata su backward search
		search.setSearchBackwards(true);
		//Imposto l'evaluator
		filter.setEvaluator(eval);
		//Ed imposto il filtro per la ricerca degli attributi
		filter.setSearch(search);

		try {
			//Applico il filtro al training ed al testing set
			filter.setInputFormat(training);
			Instances filteredTraining =  Filter.useFilter(training, filter);
			Instances testingFiltered = Filter.useFilter(testing, filter);
			//Prendo il numero di attributi filtrati per il training
			int numAttrFiltered = filteredTraining.numAttributes();
			//Imposto gli indici a numAttr-1, perché l'ultimo è l'oggetto della predizione.
			filteredTraining.setClassIndex(numAttrFiltered - 1);
			testingFiltered.setClassIndex(numAttrFiltered - 1);

			// Dopo aver applicato i filtri, applico il sampling per valutare il modello con i dataset
			// filtrati.
			return applySampling(filteredTraining, testingFiltered, percentageMajorityClass, "True");
		} catch (Exception e) {
			throw new HandledException("Errore durante l'applicazione dei filtri!");
		}


	}

	
	
	//Applico diverse tecniche di sampling e valuto il modello. Prendo in input l'oggetto della valutazione
	//il nome del classificatore, la percentuale, nel training set, della classe maggioritaria e ritorna una lista di 
	// stringhe con le metriche calcolate nelle varie run.
	public static List<String> applySampling(Instances training, Instances testing, double percentageMajorityClass, String featureSelection) throws HandledException {
		
		//Mi creo l'array che conterrà i risultati.
		ArrayList<String> result = new ArrayList<>();
		
		//Prendo i 3 classificatori da usare.
		IBk classifierIBk = new IBk();
		RandomForest classifierRF = new RandomForest();
		NaiveBayes classifierNB = new NaiveBayes();
		
		//Prendo il numero di attributi originale, senza filtri.
		int numAttrNoFilter = training.numAttributes();
		
		//Imposto gli indici a numAttr-1, perché l'ultimo è l'oggetto della predizione.
		training.setClassIndex(numAttrNoFilter - 1);
		testing.setClassIndex(numAttrNoFilter - 1);

		// Genero i tre classificatori con i dati del training set.
		try {
			classifierNB.buildClassifier(training);
			classifierRF.buildClassifier(training);
			classifierIBk.buildClassifier(training);
		} catch (Exception e) {
			throw new HandledException("Errore durante la generazione dei classificatori!");
		}
		

		// Prendo un oggetto evaluation e valuto senza sampling e feature selection.
		Evaluation eval;
		
		try {
			eval = new Evaluation(training);
			
			//Non applico nessun filtro per il sampling, ma valuto il classificatore senza questa tecnica.
			//Ripeto per i tre classificatori considerati ed aggiungo il risultato all'Arraylist che li 
			//mantiene tutti quanti.
			applyFilterForSampling(null, eval, training, testing, classifierRF);
			addResult(eval, result, "RF", NO_SAMPLING, featureSelection);

			applyFilterForSampling(null, eval, training, testing, classifierIBk);
			addResult(eval, result, "IBk", NO_SAMPLING, featureSelection);

			applyFilterForSampling(null, eval, training, testing, classifierNB);
			addResult(eval, result, "NB", NO_SAMPLING, featureSelection);

			// Mi creo un filtered classifier
			FilteredClassifier filteredClassifier = new FilteredClassifier();
			//Mi creo un altro dataset che sarà il mio UnderSampled
			SpreadSubsample  underSampling = new SpreadSubsample();
			//Gli dò i dati di training
			underSampling.setInputFormat(training);
			//Aggiungo le informazioni fornite (distribuzione uniforme)
			String[] options = new String[]{ "-M", "1.0"};
			underSampling.setOptions(options);
			//Imposto il filtro così creato al classificatore
			filteredClassifier.setFilter(underSampling);

			// Valuto di nuovo i tre classificatori, stavolta con il classificatore con filtro applicato
			//In questa fase sto applicando underSampling.
			eval = new Evaluation(training);

			applyFilterForSampling(filteredClassifier, eval, training, testing, classifierRF);
			addResult(eval, result, "RF", UNDER_SAMPLING, featureSelection);

			applyFilterForSampling(filteredClassifier, eval, training, testing, classifierIBk);
			addResult(eval, result, "IBk", UNDER_SAMPLING, featureSelection);

			applyFilterForSampling(filteredClassifier, eval, training, testing, classifierNB);
			addResult(eval, result, "NB", UNDER_SAMPLING, featureSelection);

			// Creo un altro classificatore
			filteredClassifier = new FilteredClassifier();
			//Stavolta applico over sampling ai dati di training
			Resample  overSampling = new Resample();
			overSampling.setInputFormat(training);
			//Applico over sampling con le opzioni fornite, raddoppiando la percentuale di istanze della classe maggioritaria
			String[] optionsForOverSampling = new String[]{"-B", "1.0", "-Z", String.valueOf(2*percentageMajorityClass*100)};
			//Imposto le opzioni
			overSampling.setOptions(optionsForOverSampling);
			//Imposto il filtro appena creato
			filteredClassifier.setFilter(overSampling);

			// Valuto nuovamente i tre classificatori sul testing set, questa volta applicando over sampling
			eval = new Evaluation(testing);	

			applyFilterForSampling(filteredClassifier, eval, training, testing, classifierRF);
			addResult(eval, result, "RF", OVER_SAMPLING, featureSelection);

			applyFilterForSampling(filteredClassifier, eval, training, testing, classifierIBk);
			addResult(eval, result, "IBk", OVER_SAMPLING, featureSelection);

			applyFilterForSampling(filteredClassifier, eval, training, testing, classifierNB);
			addResult(eval, result, "NB", OVER_SAMPLING, featureSelection);

			// Applico SMOTE, creandone una nuova istanza
			SMOTE smote = new SMOTE();
			filteredClassifier = new FilteredClassifier();
			smote.setInputFormat(training);
			filteredClassifier.setFilter(smote);

			// Valuto nuovamente i tre classificatori, stavolta con SMOTE applicato al training set
			eval = new Evaluation(testing);	

			applyFilterForSampling(filteredClassifier, eval, training, testing, classifierRF);
			addResult(eval, result, "RF", SMOTE, featureSelection);

			applyFilterForSampling(filteredClassifier, eval, training, testing, classifierIBk);
			addResult(eval, result, "IBk", SMOTE, featureSelection);

			applyFilterForSampling(filteredClassifier, eval, training, testing, classifierNB);
			addResult(eval, result, "NB", SMOTE, featureSelection);


		} catch (Exception e) {
			throw new HandledException("Errore nell'applicazione del sampling.");
		}	

		return result;


	}

	
	//Applico il filtro specificato con la tecnica di sampling. Prende in input un classificatore con applicato un filtro,
	//un oggetto evaluation, le istanze di training e testing, il nome del classificatore. Ritorna la valutazione del modello
	//con i filtri applicati.
	public static Evaluation applyFilterForSampling(FilteredClassifier filteredClassifier, Evaluation eval, Instances training, Instances testing, AbstractClassifier classifierName) {

		// Se è specificato un filtro, lo applico e valuto il modello. 
		try {
			if (filteredClassifier != null) {
				//Imposto il nome del classificatore
				filteredClassifier.setClassifier(classifierName);
				//Faccio la build di quest'ultimo, con i dati di training
				filteredClassifier.buildClassifier(training);
				//Valuto il classificatore sui dati di testing
				eval.evaluateModel(filteredClassifier, testing);

				// Altrimenti lo valuto semplicemente.
			} else {
				eval.evaluateModel(classifierName, testing);

			}
		} catch (Exception e) {
			LOGGER.info("Classe minoritaria insufficiente per SMOTE!");
		}
		return eval;
	}

	
	//Aggiungo il risultato alla lista delle metriche calcolate.
	//Prendo in input l'oggetto Evaluation, la lista di stringhe necessaria per aggiungere i risultati, il classificatore
	//in questione, il nome della tecnica di sampling, il nome della tecnica di feature selection.
	public static void addResult(Evaluation eval, List<String> result, String classifierAbb, String sampling, String featureSelection) {
		result.add(getMetrics(eval,classifierAbb, sampling, featureSelection));
	}

	
	//Scrive una stringa che riporta la lista delle metriche.
	//Prende in input l'oggetto Evaluation, il classificatore, la tecnica di balancing utilizzata, la tecnica di 
	//feature selection utilizzata.
	public static String getMetrics(Evaluation eval, String classifier, String balancing, String featureSelection) {
		return classifier + "," + balancing + "," + featureSelection + "," + eval.numTruePositives(1)  + "," + eval.numFalsePositives(1)  + "," + eval.numTrueNegatives(1)  + "," + eval.numFalseNegatives(1)  + "," + eval.precision(1)  + "," + eval.recall(1)  + "," + eval.areaUnderROC(1)  + "," + eval.kappa() + "\n";
	}

}
