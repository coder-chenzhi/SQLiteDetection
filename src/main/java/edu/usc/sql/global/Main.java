package edu.usc.sql.global;

public class Main {

	public static long programTotal;
	public static long rewriteTotal;
	public static long analysisTotal;
	public static void main(String[] args) {

		
/*	    try {
				PrintWriter pwTime = new PrintWriter(new FileWriter("/home/yingjun/Documents/sqlite/time.txt", true));
				pwTime.println("StartTotal:" + System.currentTimeMillis());
				pwTime.close();
	    }
		catch(IOException ex)
		{
		
		}*/
		long start = System.currentTimeMillis();
		GlobalCallGraph gfg  = new GlobalCallGraph(args[0],args[1],args[2],args[3],args[4]);
		//new  GlobalCallGraph("/home/yingjun/Documents/eclipse/workspace/SQLiteDetection/bin/");
		
		String[] argsIns = new String [3];
		argsIns[0] = "-w";
		argsIns[1] = "-process-dir";
		argsIns[2] = args[1]+args[3]; //apk
		if(!gfg.getRegionMap().isEmpty())
		{			
			//AutoInstrument ais = new AutoInstrument(argsIns,gfg.getRegionMap(),gfg.getInstrumentPos(),gfg.getInstrumentEntry());
			//ais.performInstrument(args[4]);			
		}
		
		long end = System.currentTimeMillis();
		programTotal = end - start;
		
		/*
	    try {
				PrintWriter pwTime = new PrintWriter(new FileWriter(args[4], true));
				String apkpath = args[1]+args[3];						
				pwTime.println(apkpath.substring(apkpath.indexOf("/App")));
				pwTime.println("Soot:"+(programTotal-analysisTotal-rewriteTotal)+"ms");
				pwTime.println("Analysis:"+analysisTotal+"ms");
				pwTime.println("Rewrite:"+rewriteTotal+"ms");
				pwTime.println("Total:"+programTotal + "ms");
				pwTime.close();
	    }
		catch(IOException ex)
		{
		
		}
		*/
		
	}

}
