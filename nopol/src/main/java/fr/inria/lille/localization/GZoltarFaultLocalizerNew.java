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

	public GZoltarFaultLocalizerNew(NopolContext nopolContext) {
		runGZoltar(nopolContext);
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
		String gzBuildPath = srcDir + "/../gzBuild";
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
		String testsStr = Util.runCmd(String.format("cat %s | cut -d ',' -f 2 | cut -d '#' -f 1 | tr '\n' ':'", unitTestFilePath)).trim();
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
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<? extends StatementSourceLocation> getStatements() {
		// TODO Auto-generated method stub
		return null;
	}

	
}
