package fr.inria.lille.localization;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.inria.lille.repair.nopol.SourceLocation;

public class Util {
	private static final Logger logger = LoggerFactory.getLogger(Util.class);
	
	public static int totalPassedTests = 0;
	public static int totalFailedTests = 0;
	
	public static String runCmd(String cmd) {
		String output = "";
		
		try{
			String[] commands = {"bash", "-c", cmd};
			Process proc = Runtime.getRuntime().exec(commands);
			BufferedReader stdInput = new BufferedReader(new InputStreamReader(proc.getInputStream()));
			BufferedReader stdError = new BufferedReader(new InputStreamReader(proc.getErrorStream()));
			
			// read output
			String line = null;
			while ((line = stdInput.readLine()) != null){
				output += line + "\n";
			}
			
			// read error if exists
			String error = "";
			while ((line = stdError.readLine()) != null){
				error += line + "\n";
			}
			if(!error.equals("")){
				logger.warn(String.format("Error/Warning occurs when executing %s :\n %s \n", cmd, error));
			}
		}catch (Exception err){
			err.printStackTrace();
		}
		
		return output;
	}
	
	public static void writeToFile(String path, String content){
		writeToFile(path, content, true);
	}
	
	public static void writeToFile(String path, String content, boolean append){
		BufferedWriter output = null;
		try {
			output = new BufferedWriter(new FileWriter(path, append));
			output.write(content);
			output.close();
        } catch (final IOException e) {
            e.printStackTrace();
        }
	}
	
	public static List<String> readTestFile(String path){
		List<String> testsList = new ArrayList<>();
		try {
            final BufferedReader in = new BufferedReader(
                new InputStreamReader(new FileInputStream(path), StandardCharsets.UTF_8));
            String line;
            while ((line = in.readLine()) != null) {
            	// e.g., JUNIT,org.jfree.chart.entity.junit.LegendItemEntityTests#testSerialization
            	if (line.length() == 0) logger.error("Empty line in %s", path);
            	testsList.add(line.split(",")[1]); // add test
            }
            in.close();
        } catch (final IOException e) {
            e.printStackTrace();
        }
        return testsList;
	}
	
	public static List<SourceLocation> readStmtFile(String path){
		List<SourceLocation> stmtList = new ArrayList<>();
		try {
            final BufferedReader in = new BufferedReader(
                new InputStreamReader(new FileInputStream(path), StandardCharsets.UTF_8));
            String line;
            in.readLine(); // skip first row
            while ((line = in.readLine()) != null) {
            	// e.g., org.jfree.data.general$AbstractDataset#AbstractDataset():95
            	if (line.length() == 0) logger.error("Empty line in %s", path);
            	SourceLocation sl = new SourceLocation(line.split(":")[0].split("#")[0].replace("$", "."), Integer.parseInt(line.split(":")[1]));
            	stmtList.add(sl); // add test
            }
            logger.info(String.format("The total suspicious statements: %d", stmtList.size()));
            in.close();
        } catch (final IOException e) {
            e.printStackTrace();
        }
        return stmtList;
	}
	
	public static List<Pair<List<Integer>, String>> readMatrixFile(String path){
		List<Pair<List<Integer>, String>> matrixList = new ArrayList<>();
		try {
            final BufferedReader in = new BufferedReader(
                new InputStreamReader(new FileInputStream(path), StandardCharsets.UTF_8));
            String line;
            int cnt = 0;
            int unrelatedTestCnt = 0;
            while ((line = in.readLine()) != null) {
            	if (line.length() == 0) logger.error(String.format("Empty line in %s", path));
            	String testResult = line.substring(line.length() - 1);
//            	if (! testResult.equals("+") && ! testResult.equals("-")){
//            		logger.error("Unknown testResult: %s", testResult);
//            	}
            	
            	if (testResult.equals("+")){
            		totalPassedTests += 1;
            	}else if(testResult.equals("-")){
            		totalFailedTests += 1;
            	}else{
            		logger.error(String.format("Unknown testResult: %s", testResult));
            	}
            	
//            	List<String coverageList = Arrays.asList(line.substring(0, line.length() - 1).trim().split(" "));
//            	String coverage = line.substring(0, line.length() - 1).replace(" ", "");
            	List<Integer> coveredStmtIndexList = new ArrayList<>();
            	String coverage = line.replace(" ", "");
            	int index = -1;
            	while( (index = coverage.indexOf("1", index + 1)) >= 0){ // add "+ 1", otherwise it's a infinite loop
            		coveredStmtIndexList.add(index);
            	}
            	if(coveredStmtIndexList.size() == 0){
            		logger.info(String.format("The test case (index: %s) is not executed by any stmts in Spectra", cnt));
            		unrelatedTestCnt ++;
            	}
            	
            	cnt ++;
            	matrixList.add(new Pair<>(coveredStmtIndexList, testResult)); // add test
            }
            logger.info(String.format("The unrelated test cases: %d", unrelatedTestCnt));
            logger.info(String.format("The total test cases: %d", cnt));
            
            in.close();
        } catch (final IOException e) {
            e.printStackTrace();
        }
        return matrixList;
	}
}