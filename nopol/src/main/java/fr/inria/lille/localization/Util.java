package fr.inria.lille.localization;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class Util {
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
				System.out.format("Error/Warning occurs when executing %s :\n %s \n", cmd, error);
			}
		}catch (Exception err){
			err.printStackTrace();
		}
		
		return output;
	}
}
