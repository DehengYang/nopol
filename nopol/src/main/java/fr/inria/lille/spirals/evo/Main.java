package fr.inria.lille.spirals.evo;

import fr.inria.lille.commons.synthesis.smt.solver.SolverFactory;
import fr.inria.lille.repair.common.config.Config;
import fr.inria.lille.repair.common.patch.Patch;
import fr.inria.lille.repair.common.synth.StatementType;
import fr.inria.lille.repair.nopol.NoPol;
import fr.inria.lille.spirals.evo.processors.RemoveEvosuiteEffectsProcessor;
import fr.inria.lille.spirals.evo.processors.TestSelectionProcessor;
import org.apache.commons.cli.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.Launcher;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import xxl.java.library.FileLibrary;
import xxl.java.library.JavaLibrary;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Main {

    public final static String solver = "z3";
    public final static char fileSeparator = '/';
    public final static String outputsDir = "outputs";
    public final static String evoOutput = "generatedTests";
    public final static StatementType nopolType = StatementType.CONDITIONAL;
    public final static int maxTime = 10;
    public final static Logger logger = LoggerFactory.getLogger(Main.class);

    public static String solverPath =  null; //"lib/z3/z3_for_linux";
    public static String evosuitePath =  null; //"misc/evo/evosuite-1.0.2.jar";

    public static List<CtMethod<?>>  keptMethods = new ArrayList<CtMethod<?>>();
    public static List<CtMethod<?>>  removedMethods = new ArrayList<CtMethod<?>>();
    public static Map<String, List<Patch>> patches = new LinkedHashMap<String,List<Patch>>();
    
    public static boolean whetherSavePatch=true;

    public static void main(String[] args) throws IOException, ParseException {

        String cpClassFolder = null;
        String cpTestFolder = null;
        String srcClassFolder = null;
        String srcTestFolder = null;
        String destSrcTestFolder = null;
        String destCpTestFolder = null;
        String newTestFolder = null;
        String dependencies = null;
        String testClasses = null;
        String patchSaveFolder=null;

        Options options = new Options();
        options.addOption("cpClassFolder", true, "classes files (ex: project/target/classes)");
        options.addOption("cpTestFolder", true, "classes test files (ex: project/target/test-classes)");
        options.addOption("srcClassFolder", true, "location of project files (ex: project/src/java)");
        options.addOption("srcTestFolder", true, "location of tests files (ex: project/src/test)");
        options.addOption("newTestFolder", true, "location of new test generated by evoSuite. default: generatedTests");
        options.addOption("destSrcTestFolder", true, "java files where new tests will be added after validation. (ex: project/src/test) default: srcTestFolder");
        options.addOption("destCpTestFolder", true, "java files where new tests will be compiled after validation. (ex: project/target/test-classes) default: cpTestFolder");
        options.addOption("dependencies", true, "all other class or jar required for the news tests. cpClassFolde & cpTestFolder are not necessary. (ex: junit, hamcrest, evosuite)");
        options.addOption("testClasses", true, "test classes used to generate patch (default : null = all classes)");
        options.addOption("patchSaveFolder",true,"location used to save the generated patches if any");

        options.addOption("solverPath", true, "path for the solver");
        options.addOption("evosuitePath", true, "path of evosuite jar");


        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, args);


        if (cmd.getOptionValue("cpClassFolder") != null) {
            cpClassFolder = cmd.getOptionValue("cpClassFolder");
        }

        if (cmd.getOptionValue("cpTestFolder") != null) {
            cpTestFolder = cmd.getOptionValue("cpTestFolder");
        }

        if (cmd.getOptionValue("srcClassFolder") != null) {
            srcClassFolder = cmd.getOptionValue("srcClassFolder");
        }

        if (cmd.getOptionValue("srcTestFolder") != null) {
            srcTestFolder = cmd.getOptionValue("srcTestFolder");
        }

        destSrcTestFolder = srcTestFolder;
        if (cmd.getOptionValue("destSrcTestFolder") != null) {
            destSrcTestFolder = cmd.getOptionValue("destSrcTestFolder");
        }

        destCpTestFolder = cpTestFolder;
        if (cmd.getOptionValue("destCpTestFolder") != null) {
            destCpTestFolder = cmd.getOptionValue("destCpTestFolder");
        }

        if (cmd.getOptionValue("newTestFolder") != null) {
            newTestFolder = cmd.getOptionValue("newTestFolder");
        }

        if (cmd.getOptionValue("dependencies") != null) {
            dependencies = cmd.getOptionValue("dependencies");
        }

        if (cmd.getOptionValue("testClasses") != null) {
            testClasses = cmd.getOptionValue("testClasses");
        }
        
        if (cmd.getOptionValue("patchSaveFolder") != null) {
        	patchSaveFolder = cmd.getOptionValue("patchSaveFolder");
        }
        
        if (cmd.getOptionValue("solverPath") != null) {
            solverPath = cmd.getOptionValue("solverPath");
        }

        if (cmd.getOptionValue("evosuitePath") != null) {
            evosuitePath = cmd.getOptionValue("evosuitePath");
        }

        if(!cmd.hasOption("cpClassFolder") || !cmd.hasOption("cpTestFolder") ||
                !cmd.hasOption("srcClassFolder") || !cmd.hasOption("srcTestFolder")
                || !cmd.hasOption("solverPath") || !cmd.hasOption("evosuitePath")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp( "list of parameters", options );
            System.exit(0);
        }

        String[] testsClassesArray = (testClasses == null) ? null : testClasses.split(File.pathSeparator);
        tryAllTests(cpClassFolder, cpTestFolder, srcClassFolder, srcTestFolder, destSrcTestFolder, destCpTestFolder, newTestFolder, dependencies, true, testsClassesArray, whetherSavePatch, patchSaveFolder, new Config());

    }


    /**
     * this method analyze all java class in generatedTestDir and return java paths composed of packages.class
     * @param generatedTestFolder
     * @param classPath
     * @return List<String> list of class ({package.package.classname})
     */
    public static List<String> getNewTestClasses(String generatedTestFolder, String classPath){

        List<String> testsClasses = new ArrayList<String>();
        logger.debug("--------------------------------------------------");
        logger.debug(" ##### Search tests files path ##### ");
        Launcher spoon = new Launcher();
        spoon.getEnvironment().setAutoImports(true);
        spoon.addInputResource(generatedTestFolder);
        spoon.getEnvironment().setSourceClasspath(classPath.split(File.pathSeparator));
        spoon.buildModel();

        //getannotatedMethod.. could be better
        for(CtType<?> clazz : spoon.getFactory().Class().getAll()){
            String className = clazz.getPackage().toString()+"."+clazz.getSimpleName();
            logger.debug("[FOUND] "+className);
            testsClasses.add(className);
        }

        return testsClasses;
    }


    /**
     * this method analyze all java class in generatedTestDir and return a list of all Junit method
     * @param generatedTestFolder
     * @param classPath
     * @return List<CtMethod> list of methods
     */
    public static List<CtMethod<?>> getNewTestsMethods(String generatedTestFolder, String classPath){
        List<CtMethod<?>>  testsMethods = new ArrayList<CtMethod<?>>();

        logger.debug("--------------------------------------------------");
        logger.debug(" ##### Search tests methods ##### ");
        Launcher spoon = new Launcher();
        spoon.getEnvironment().setAutoImports(true);
        spoon.addInputResource(generatedTestFolder);
        spoon.getEnvironment().setSourceClasspath(classPath.split(File.pathSeparator));
        spoon.buildModel();
        //getannotatedMethod.. could be better
        for(CtType<?> clazz : spoon.getFactory().Class().getAll()){
            methodLoop:
                for(CtMethod<?> method : clazz.getAllMethods()){
                    for(CtAnnotation<? extends Annotation> annotation : method.getAnnotations()){
                        if(annotation.getSignature().equals("@org.junit.Test")){
                            logger.debug("[FOUND] "+method.getSignature());
                            testsMethods.add(method);
                            continue methodLoop;
                        }
                    }
                }
        }

        return testsMethods;
    }


    /**
     * Launch nopol on project
     * @param cpClassFolder : location of project's compiled java
     * @param cpTestFolder : location of project's compiled tests
     * @param srcClassFolder : location of project's java
     * @param srcTestFolder : location of project's tests
     * @param destSrcTestFolder : location for the kept tests
     * @param destCpTestFolder : location to compile the kept tests
     * @param dependencies : all dependencies for the project and evosuite tests
     * @param testClasses : test class used to generate patch (null for all tests)
     * @return  the list of patch found by nopol
     */
    public static List<Patch> NopolPatchGeneration(String cpClassFolder,String  cpTestFolder, 
            String srcClassFolder, String srcTestFolder, String destSrcTestFolder, 
            String destCpTestFolder, String dependencies, String[] testClasses, Config config) {

        //sources contain main java and test java.
        String sources = srcClassFolder+File.pathSeparatorChar+srcTestFolder+File.pathSeparatorChar+destSrcTestFolder;
        String cp = cpClassFolder+File.pathSeparatorChar+cpTestFolder+File.pathSeparatorChar+destCpTestFolder+File.pathSeparatorChar+dependencies;

        //create sources array
        String[] sourcesArray = sources.split(File.pathSeparator);
        File[] sourceFiles = new File[sourcesArray.length];
        for(int i = 0; i<sourcesArray.length; i++){
            sourceFiles[i] = FileLibrary.openFrom(sourcesArray[i]);	
        }


        //create classpath
        //URL[] classPath = FileUtils.getURLs(sources.split(File.pathSeparator));
        URL[] classPath = JavaLibrary.classpathFrom(cp);

        logger.debug("Launch nopol with:");
        logger.debug("sources = "+sources);
        logger.debug("classpath = "+cp);
        logger.debug("testClasses = "+testClasses);


        config.setMaxTime(maxTime);
        NoPol nopol = new NoPol(sourceFiles, classPath, nopolType, config);
        List<Patch> currentPatches;
        if(testClasses == null){
            currentPatches = nopol.build();   
        }
        else{
            currentPatches = nopol.build(testClasses);
        }

        return currentPatches;
    }


    /**
     * Launch nopol with all tests added one by one on the project
     * @param cpClassFolder : location of project's compiled java
     * @param cpTestFolder : location of project's compiled tests
     * @param srcClassFolder : location of project's java
     * @param srcTestFolder : location of project's tests
     * @param destSrcTestFolder : location for the kept tests
     * @param destCpTestFolder : location to compile the kept tests
     * @param newTestFolder : location to generate news tests
     * @param dependencies : all dependencies for the project and evosuite tests
     * @param generateTest : boolean, false if tests are already generated in newTestFolder
     * @param firstTestClasses : tests class used to generate patch
     */
    public static void tryAllTests(String cpClassFolder, String cpTestFolder, 
            String srcClassFolder, String srcTestFolder, String destSrcTestFolder, 
            String destCpTestFolder, final String newTestFolder, String dependencies, boolean generateTest, String[] firstTestClasses, boolean whetherSavePatch, String patchSaveFolder, Config config){

        //create dest folders if not exist
        new File(destSrcTestFolder).mkdirs();
        new File(destCpTestFolder).mkdirs();

        List<Patch> currentPatches = null;
        String className = null;
        String[] testClasses = firstTestClasses;

        //build classpath
        final String classPath = cpClassFolder+File.pathSeparatorChar+dependencies;
        
        SolverFactory.setSolver(solver, solverPath);

        logger.debug("--------------------------------------------------");
        logger.debug(" ##### launch nopol without new tests ##### ");

        currentPatches = NopolPatchGeneration(cpClassFolder, cpTestFolder, srcClassFolder, srcTestFolder, destSrcTestFolder, destCpTestFolder, dependencies, testClasses, config);
        patches.put("basic", currentPatches);
        if(currentPatches.isEmpty()){
            logger.debug("### ----- NO PATCH FOUND -----");
            return;
        }

        logger.debug("### ----- PATCH FOUND -----");
        for (Patch patch : currentPatches) {
            logger.debug(patch.toString());
        }
        className = currentPatches.get(0).getRootClassName();
        if(generateTest){
            logger.debug(" #### run EvoSuite jar #### ");
            generateEvoSuiteTests(newTestFolder, classPath, className);
        }

        List<String> newTestClasses = getNewTestClasses(newTestFolder, classPath);

        if(testClasses != null){
            testClasses = concat(testClasses, newTestClasses.toArray(new String[newTestClasses.size()]));
        }

        List<CtMethod<?>> testsMethods = getNewTestsMethods(newTestFolder, classPath);
        

        logger.debug("###########################################");
        logger.debug("######## start to try each methods ########");
        logger.debug("###########################################");
        for(CtMethod<?> method : testsMethods){
            logger.debug("--------------------------------------------------");
            logger.debug("# TEST METHOD : "+method.getSignature());
            logger.debug("--------------------------------------------------");

            keptMethods.add(method);
            
            logger.debug("### Remove EvoSuite &  Recompile Tests ");
            Launcher spoonLauncher = new Launcher();
            spoonLauncher.addProcessor(new TestSelectionProcessor(keptMethods));
            spoonLauncher.addProcessor(new RemoveEvosuiteEffectsProcessor());
            spoonLauncher.addInputResource(newTestFolder);
            spoonLauncher.getEnvironment().setSourceClasspath(classPath.split(File.pathSeparator));
            spoonLauncher.setSourceOutputDirectory(destSrcTestFolder);
            spoonLauncher.getEnvironment().setShouldCompile(true);
            spoonLauncher.setBinaryOutputDirectory(destCpTestFolder);
            spoonLauncher.getEnvironment().setComplianceLevel(7);
            spoonLauncher.run();


            logger.debug("### Launch Nopol");
            currentPatches = NopolPatchGeneration(cpClassFolder, cpTestFolder, srcClassFolder, srcTestFolder, destSrcTestFolder, destCpTestFolder, dependencies, testClasses, config);
            if(!currentPatches.isEmpty()){
                logger.debug("### ----- PATCH FOUND -----");
                for (Patch patch : currentPatches) {
                    logger.debug(patch.toString());
                }
                logger.debug("### METHOD KEPT : "+method.getSignature());
            }
            else{
                logger.debug("### ----- NO PATCH FOUND -----");
                logger.debug("### METHOD REMOVED : "+method.getSignature());
                keptMethods.remove(method);
                removedMethods.add(method);
            }
            patches.put(method.getSimpleName(), currentPatches);

            displayHistory();

        }


        logger.debug("### End of program. Recompile keeping all good tests");
        Launcher lastLauncher = new Launcher();
        lastLauncher.addProcessor(new TestSelectionProcessor(keptMethods));
        lastLauncher.addProcessor(new RemoveEvosuiteEffectsProcessor());
        lastLauncher.addInputResource(newTestFolder);
        lastLauncher.getEnvironment().setSourceClasspath(classPath.split(File.pathSeparator));
        lastLauncher.setSourceOutputDirectory(destSrcTestFolder);
        lastLauncher.getEnvironment().setShouldCompile(true);
        lastLauncher.setBinaryOutputDirectory(destCpTestFolder);
        lastLauncher.run();

        displayHistory();
        
        if(whetherSavePatch)
        	savePatchtoDisk(patchSaveFolder);
    }




    /**
     * Display all patches generated for each method
     */
    private static void displayHistory() {
        for (Map.Entry<String, List<Patch>> entry : patches.entrySet()) {
            String method = entry.getKey();
            String patch = "null";
            if(entry.getValue() != null && !entry.getValue().isEmpty()){
                patch = entry.getValue().get(0).toString();
            }
            logger.debug(method+" <===> "+patch);
        }  

    }

    /**
     * Generate evosuite test on newTestFolder for the class ClassName on the project classPath
     * @param newTestFolder
     * @param classPath
     * @param className
     * @return newTestFolder
     */
    public static String generateEvoSuiteTests(String newTestFolder, String classPath, String className){
        List<String> cmd = new ArrayList<String>();
        cmd.add("java");
        cmd.add("-jar");
        cmd.add(evosuitePath);
        cmd.add("-class");
        cmd.add(className);
        cmd.add("-projectCP");
        cmd.add(classPath);
        cmd.add("-generateSuite");
        cmd.add("-Dsearch_budget=30");
        cmd.add("-Dstopping_condition=MaxTime");
        cmd.add("-Dno_runtime_dependency=true");
        cmd.add("-Dtest_dir="+newTestFolder);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        //Map<String, String> env = pb.environment();
        Process p = null;
        try {
            pb.inheritIO();
            p = pb.start();
            p.waitFor();
        } catch (IOException e1) {
            e1.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } 
       
        return newTestFolder;
    }

    /**
     * concatenate a & b String array
     * @param a
     * @param b
     * @return a concatenated with b
     */
    public static String[] concat(String[] a, String[] b) {
        int aLen = a.length;
        int bLen = b.length;
        String[] c= new String[aLen+bLen];
        System.arraycopy(a, 0, c, 0, aLen);
        System.arraycopy(b, 0, c, aLen, bLen);
        return c;
    }
    
    /**
     * save generated patches to a folder
     */
    public static void savePatchtoDisk(String patchSaveFolder) {
    	new File(patchSaveFolder).mkdirs();
    	String filePath = patchSaveFolder+"//"+"generated_patches";
    	File patchFile= new File(filePath);
    	FileWriter writer;
    	BufferedWriter bufferWriter;
    	try  {
    		writer = new FileWriter (patchFile);
    		bufferWriter = new BufferedWriter (writer);
    		for(Map.Entry entry : patches.entrySet()){
    			writer.write(entry.getValue()+" "+entry.getKey());
    			writer.write("\r\n");
    			writer.flush();
    		}
    		bufferWriter.close();
    		writer.close();
    	} catch (IOException e) {
    		logger.debug(e.getMessage());
    	}
    }
}
