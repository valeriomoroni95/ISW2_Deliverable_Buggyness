package main;

import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

public class LoggerClass {
	
private LoggerClass() {
		
		throw new IllegalStateException("There are many problems in LoggerClass.java");
	}
	
	private static final Logger Log = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME );     
    
    //Mi imposto il logger
	public static void setupLogger() {
    	
        LogManager.getLogManager().reset();
        Log.setLevel(Level.ALL);
        
        ConsoleHandler consoleHandler = new ConsoleHandler();
        consoleHandler.setLevel(Level.FINE);
        Log.addHandler(consoleHandler);

        try {
            FileHandler fileHandler = new FileHandler("LoggerClass.log", true);
            fileHandler.setLevel(Level.FINE);
            Log.addHandler(fileHandler);
        } catch (java.io.IOException e) {            
        	Log.log(Level.SEVERE, "CATCH BLOCK: There are problems in the file logger!!!", e);
        }
        
    } 
	
	public static void errorLog(String message) {
    	
    	Log.severe(message);
    }
    
    public static void infoLog(String message ) {
    	
    	Log.info(message);
    }
    

}
