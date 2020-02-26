/*
 * Copyright (C) 2013 INRIA
 *
 * This software is governed by the CeCILL-C License under French law and
 * abiding by the rules of distribution of free software. You can use, modify
 * and/or redistribute the software under the terms of the CeCILL-C license as
 * circulated by CEA, CNRS and INRIA at http://www.cecill.info.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the CeCILL-C License for more details.
 *
 * The fact that you are presently reading this means that you have had
 * knowledge of the CeCILL-C license and that you accept its terms.
 */
package fr.inria.lille.localization;

import com.gzoltar.core.GZoltar;
import com.gzoltar.core.components.Statement;
import com.gzoltar.core.components.count.ComponentCount;
import com.gzoltar.core.instr.testing.TestResult;
import fr.inria.lille.localization.metric.Metric;
import fr.inria.lille.localization.metric.Ochiai;
import fr.inria.lille.repair.common.config.NopolContext;
import fr.inria.lille.repair.nopol.SourceLocation;
import gov.nasa.jpf.tool.Run;
import xxl.java.junit.TestCase;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.shared.utils.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A list of potential bug root-cause.
 *
 * @author Favio D. DeMarco
 */
public final class GZoltarFaultLocalizerNew implements FaultLocalizer{

	private static final String dir = System.getProperty("user.dir");
	private Metric metric;
	
	private List<StatementExt> statements;
	
	private static final Logger logger = LoggerFactory.getLogger(GZoltarFaultLocalizerNew.class);
	private String gzBuildPath;
	private int totalPassedTests = 0;
	private int totalFailedTests = 0;
	private List<SourceLocation> slList;
	private NopolContext nopolContext; // not necessary, mainly for debugging use.
	private Map<SourceLocation, List<fr.inria.lille.localization.TestResult>> results;
	private List<String> testsList;
	
	public GZoltarFaultLocalizerNew(NopolContext nopolContext) {
		this.nopolContext = nopolContext;
		runGZoltar(nopolContext);
		setTestListPerStatement();
	}
	
	private void runGZoltar(NopolContext nopolContext){
		// 1) add GZoltar jar: nopol/lib/GZoltar/xxx.jar
		// 2) set parameters
//		String gzoltarDir = new File("").getAbsolutePath() + "/lib/GZoltar/";
//		String junitPath = new File("").getAbsolutePath() + "/lib/junit-4.11.jar";
		String gzoltarDir = new File(nopolContext.getGzPath()).getAbsolutePath();
		String junitPath = gzoltarDir + "/../junit-4.11.jar";
		String hamcrestPath = gzoltarDir + "/hamcrest-core.jar";
		String gzCliPath = gzoltarDir + "/com.gzoltar.cli-1.7.3-SNAPSHOT-jar-with-dependencies.jar";
		String gzAgentPath = gzoltarDir + "/com.gzoltar.agent.rt-1.7.3-SNAPSHOT-all.jar";
//		if (logger.isDebugEnabled()) {
		logger.info("gzoltarDir: {}, user dir: {}", gzoltarDir, dir);
//		}
		
		// copy resources/properties files except .java to ensure the coming successful compilation
		String srcDir = new File(nopolContext.getSrcPath()).getAbsolutePath();
//		if(srcDir.endsWith("/")){
//			srcDir = srcDir.substring(0, srcDir.length() - 1);
//		}
		//String buildDir = srcDir.substring(0, srcDir.lastIndexOf('/') + 1);
		//String gzBuildPath = buildDir + "gzBuild";
		this.gzBuildPath = srcDir + "/../gzBuild";
		File gzBuildDir = new File(gzBuildPath);
//		try {
//			FileUtils.copyDirectory(new File(srcDir), gzBuildDir);
//		} catch (IOException e) {
//			e.printStackTrace();
//		}
		String cmdCp1;
		if(! gzBuildDir.exists()){
			cmdCp1 = String.format("mkdir %s && cp -rn %s/* %s", gzBuildPath, srcDir, gzBuildPath);
		}else{
			cmdCp1 = String.format("rm -rf %s/* && cp -rn %s/* %s", gzBuildPath, srcDir, gzBuildPath);
		}
//		String cmdCp1 = String.format("cp -rn %s/* %s", srcDir, gzBuildPath);
		String cmdCp2 = String.format("find %s -name \"*.java\" -type f -delete", gzBuildPath);
		String result = Util.runCmd(cmdCp1);
		result = Util.runCmd(cmdCp2);
				
		// compile
		String cmdCompile1 = String.format("find %s -type f -name '*.java' -print | xargs javac -cp %s -d %s",
				srcDir, nopolContext.getClassPath(), gzBuildPath);
		String cmdCompile2 = String.format("find %s -type f -name '*.java' -print | xargs javac -cp %s:%s:%s -d %s",
				nopolContext.getTestPath(), gzBuildPath, nopolContext.getClassPath(), junitPath, gzBuildPath);
		String cmdCompile = String.format("%s; %s", cmdCompile1, cmdCompile2);
		result = Util.runCmd(cmdCompile1);
		result = Util.runCmd(cmdCompile2);
		
		// list test methods
		String unitTestFilePath = gzBuildPath + "/tests.txt";
		String cmdMethod = String.format("java -cp %s:%s:%s:%s:%s com.gzoltar.cli.Main listTestMethods %s --outputFile %s",
				gzBuildPath, junitPath, hamcrestPath, gzCliPath, nopolContext.getClassPath(), gzBuildPath, unitTestFilePath);
		Util.runCmd(cmdMethod);
		
		String serFilePath = gzBuildPath + "/gzoltar.ser";
		String testsStr = Util.runCmd(String.format("cat %s | cut -d ',' -f 2 | cut -d '#' -f 1 | uniq | tr '\n' ':'", unitTestFilePath)).trim();
		
		// another way to get testsStr
//		this.testsList = Util.readTestFile(new File(nopolContext.getSrcPath()).getAbsolutePath() + "/../gzBuild/tests.txt");
//		List<String> testClassesList = new ArrayList<>();
//		String testsStr = "";
//		for (String testCase : testsList){
//			String testClass = testCase.split("#")[0];
//			if (! testClassesList.contains(testClass)){
//				testClassesList.add(testClass);
//			}
//		}
		
		
//		Util.writeToFile(gzBuildPath + "/testStr.txt", testsStr, false);
//		String testsStr = Util.runCmd(String.format("head %s -n 1 | cut -d ',' -f 2 | cut -d '#' -f 1 | tr '\n' ':'", unitTestFilePath)).trim();
//		String testsStr = Util.runCmd(String.format("echo $(cat %s | cut -d ',' -f 2 | cut -d '#' -f 1 | tr '\n' ':')", unitTestFilePath));
		String cmdCoverage = String.format("java -javaagent:%s=destfile=%s,buildlocation=%s,excludes=%s,inclnolocationclasses=false,output=\"file\" %s %s",
			gzAgentPath, serFilePath, gzBuildPath, testsStr, //"$(cat %s | cut -d ',' -f 2 | cut -d '#' -f 1 | tr '\n' ':')",
			String.format(" -cp %s:%s:%s:%s:%s ", 
					gzBuildPath, junitPath, hamcrestPath, gzCliPath, nopolContext.getClassPath()
					),
			String.format(" com.gzoltar.cli.Main runTestMethods --testMethods \"%s\" --collectCoverage ", unitTestFilePath)
			);  // I made a big mistake here: I ommit an "s" in "runTestMethods". To find this bug, I spent about 2 hours on this.
		// Reason: its not easy, especially the tests (testsStr) are so large/long.
//		logger.debug(cmdCoverage);
		Util.runCmd(cmdCoverage);
		
		// matrix and spectra output
		String cmdMatrix = String.format("java -cp %s:%s:%s:%s:%s com.gzoltar.cli.Main faultLocalizationReport --outputDirectory %s --buildLocation %s --dataFile %s %s", 
				gzBuildPath, junitPath, hamcrestPath, gzCliPath, nopolContext.getClassPath(), gzBuildPath, gzBuildPath, serFilePath,
				" --granularity \"line\" --inclPublicMethods --inclStaticConstructors --inclDeprecatedMethods --family \"sfl\" --formula \"ochiai\" --metric \"entropy\" --formatter \"txt\" "
				);
		Util.runCmd(cmdMatrix);
		
		// matrix simplification
		String cmdSimplify = String.format("cp %s/matrix_simplify.py %s && cd %s && python3.6 matrix_simplify.py", gzoltarDir, gzBuildPath, gzBuildPath);
		Util.runCmd(cmdSimplify);
		
		System.out.println();
	}

	@Override
	public Map<SourceLocation, List<fr.inria.lille.localization.TestResult>> getTestListPerStatement() {
		return this.results;
	}	
	
	public void setTestListPerStatement() {	
		if (this.gzBuildPath == null){
			this.gzBuildPath = new File(nopolContext.getSrcPath()).getAbsolutePath() + "/../gzBuild";
		}
		
		Map<SourceLocation, List<fr.inria.lille.localization.TestResult>> results = new HashMap<>();
		this.slList = Util.readStmtFile(this.gzBuildPath + "/sfl/txt/spectra.faulty.csv");
		if (this.testsList == null){ // for debugging use
			this.testsList = Util.readTestFile(this.gzBuildPath + "/tests.txt");
		}
//		List<String> testsList = Util.readTestFile(this.gzBuildPath + "/tests.txt");
		List<Pair<List<Integer>, String>> matrixList = Util.readMatrixFile(this.gzBuildPath + "/sfl/txt/filtered_matrix.txt");
		this.totalPassedTests = Util.totalPassedTests;
		this.totalFailedTests = Util.totalFailedTests;
		
		List<StatementSourceLocation> sslList = new ArrayList<>();
		//for (SourceLocation sl : slList){
		for (int i = 0; i < slList.size(); i++){
			SourceLocation sl = slList.get(i);
			int executedPassedCount = 0;
			int executedFailedCount = 0;
			for (Pair<List<Integer>, String> pair : matrixList){
				if (pair.getLeft().contains(i)){
					if (pair.getRight().equals("+")){
						executedPassedCount += 1;
					}else{
						executedFailedCount += 1;
					}
				}
			}
			
			StatementSourceLocation ssl = new StatementSourceLocation(new Ochiai(), sl);
			ssl.setEf(executedFailedCount);
			ssl.setEp(executedPassedCount);
			ssl.setNf(this.totalFailedTests - executedFailedCount);
			ssl.setNp(this.totalPassedTests - executedPassedCount);
			sslList.add(ssl);
		}
		Collections.sort(sslList, new Comparator<StatementSourceLocation>(){
			@Override
			public int compare(final StatementSourceLocation o1, final StatementSourceLocation o2){
				return Double.compare(o2.getSuspiciousness(), o1.getSuspiciousness());
			}
		});
		
		// write to file.
		String writePath = this.gzBuildPath + "/sfl/txt/ochiai.nopol.csv";
		Util.writeToFile(writePath, "", false);
		for (StatementSourceLocation ssl : sslList){
			String line = ssl.getLocation().getContainingClassName() + ":" + ssl.getLocation().getLineNumber() + ";" + ssl.getSuspiciousness() + "\n";
			Util.writeToFile(writePath, line);
		}
		
		
		for (int i = 0; i < matrixList.size(); i++){
			boolean testResult;
			if(matrixList.get(i).getRight().equals("+")){
				testResult = true;
			}else{
				testResult = false;
			}
			List<Integer> coveredStmtIndexList = matrixList.get(i).getLeft();
			
			TestResultImpl test = new TestResultImpl(TestCase.from(this.testsList.get(i)), testResult);
			
			for(int index : coveredStmtIndexList){
				SourceLocation sl = slList.get(index);
				
				if (!results.containsKey(sl)) {
					results.put(sl, new ArrayList<fr.inria.lille.localization.TestResult>());
				}
				results.get(sl).add(test);
			}
		}
		
		LinkedHashMap<SourceLocation, List<fr.inria.lille.localization.TestResult>> map = new LinkedHashMap<>();
		for (StatementSourceLocation ssl : sslList){
			map.put(ssl.getLocation(), results.get(ssl.getLocation()));
		}
		this.results = map;
	}

	@Override
	public List<? extends StatementSourceLocation> getStatements() {
		return null;
	}
}
