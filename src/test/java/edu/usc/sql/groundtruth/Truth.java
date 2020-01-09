package edu.usc.sql.groundtruth;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import edu.usc.sql.global.GlobalCallGraph;

public class Truth {


	public static void main(String[] args)
	{
		Map<String,Set<String>> gt = new HashMap<>();
	    Map<String,Set<String>> result = new HashMap<>();
		//Ground truth
		loadFromFile("gt.txt",gt);
		//Output
		loadFromFile("/home/yingjun/Documents/sqlite/outputNew1.txt",result);
		
		int base = 0;
		int truePositive=0;
		for(String app: result.keySet())
		{
			if(gt.get(app)!=null)  //shoule be removed after finishing building all the ground truth
			base += result.get(app).size();
			
			for(String out: result.get(app))
			{
				if(gt.get(app)!=null)
				if(gt.get(app).contains(out))
					truePositive++;
			}
		}
		System.out.println("Precision:"+truePositive*1.0/base * 100+"%");
		
	}
	
	public static void loadFromFile(String fileName,Map<String,Set<String>> gt)
	{
		try {
			File file = new File(fileName);
			FileReader fileReader = new FileReader(file);
			BufferedReader bufferedReader = new BufferedReader(fileReader);
			
			String line;
			
			String app = null;
			Set<String> output = new HashSet<>();
			
			while ((line = bufferedReader.readLine()) != null) {
				if(line.startsWith("App"))
				{
					app = line;
					output = new HashSet<>();
				}
				else if(line.startsWith("<"))
				{
					if(!GlobalCallGraph.containLib(line))
					output.add(line);					
				}
				else
				{
					gt.put(app, output);
				}
					
			}
			fileReader.close();
			System.out.println(gt.keySet().size());
			
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
}
