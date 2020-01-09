package edu.usc.sql.global;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.Map.Entry;
import java.util.Set;

import edu.usc.sql.string.RegionNode;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Unit;
import soot.ValueBox;
import soot.jimple.EnterMonitorStmt;
import soot.jimple.Stmt;
import soot.jimple.internal.JimpleLocalBox;
import soot.jimple.toolkits.callgraph.CHATransformer;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.options.Options;
import edu.usc.sql.callgraph.EntryPoint;
import edu.usc.sql.callgraph.NewNode;
import edu.usc.sql.callgraph.StringCallGraph;
import edu.usc.sql.sootenvironment.AndroidApp;
import edu.usc.sql.graphs.Node;
import edu.usc.sql.graphs.NodeInterface;
import edu.usc.sql.graphs.cfg.CFGInterface;
import edu.usc.sql.graphs.cfg.SootCFG;

public class GlobalCallGraph {

	private Set<String> targetAPI = new HashSet<>();
	private Set<String> targetScanList = new HashSet<>();
	private Set<String> transactionAPI = new HashSet<>();

	private long totalLinesOfCode = 0;
	private long totalRevelantCode = 0;
	private Set<String> rSum = new HashSet<>();
	private Map<NodeInterface,String> modToRegion = new HashMap<>();
	private Map<NodeInterface, RegionNode> modToRegionNode = new HashMap<>();
	private Map<String,SootMethod> targetMethod = new HashMap<>();
	private Map<NodeInterface,CFGInterface> preStoreCFG = new HashMap<>();
	private Map<NodeInterface,String> modToSig = new HashMap<>();
	private Map<NodeInterface,String> synToSig = new HashMap<>();
	private Map<NodeInterface,String> modToCat = new HashMap<>();
	private Set<String> output = new HashSet<>();
	private Set<String> beginMethodManualVerify = new HashSet<>();
	private Set<String> lockMethodManualVerify = new HashSet<>();
	private Map<NodeInterface,Boolean> isLoopRelatedToLock = new HashMap<>();  
	private Map<String,RegionNode> sigToRegionNode = new HashMap<>();
	private Map<String,Set<String>> regionToDefine = new HashMap<>();
	private Set<String> entries = new HashSet<>();
	private EntryPoint entryPoint = new EntryPoint();
	private String outputFile;
	public GlobalCallGraph(String rtjar,String appfolder,String classlist,String apk,String outputFile)
	{
      
		targetAPI.add("<android.database.sqlite.SQLiteDatabase: long insert(java.lang.String,java.lang.String,android.content.ContentValues)>");
		targetAPI.add("<android.database.sqlite.SQLiteDatabase: long insertOrThrow(java.lang.String,java.lang.String,android.content.ContentValues)>");
		targetAPI.add("<android.database.sqlite.SQLiteDatabase: long insertWithOnConflict(java.lang.String,java.lang.String,android.content.ContentValues,int)>");
		targetAPI.add("<android.database.sqlite.SQLiteDatabase: int update(java.lang.String,android.content.ContentValues,java.lang.String,java.lang.String[])>");
		targetAPI.add("<android.database.sqlite.SQLiteDatabase: int updateWithOnConflict(java.lang.String,android.content.ContentValues,java.lang.String,java.lang.String[],int)>");
		targetAPI.add("<android.database.sqlite.SQLiteDatabase: void execSQL(java.lang.String)>");
		targetAPI.add("<android.database.sqlite.SQLiteDatabase: void execSQL(java.lang.String,java.lang.Object[])>");
		targetAPI.add("<android.database.sqlite.SQLiteDatabase: int delete(java.lang.String,java.lang.String,java.lang.String[])>");
		targetAPI.add("<android.database.sqlite.SQLiteDatabase: long replace(java.lang.String,java.lang.String,android.content.ContentValues)>");
		targetAPI.add("<android.database.sqlite.SQLiteDatabase: long replaceOrThrow(java.lang.String,java.lang.String,android.content.ContentValues)>");
		targetAPI.add("<android.database.sqlite.SQLiteStatement: void execute()>");
		targetAPI.add("<android.database.sqlite.SQLiteStatement: long executeInsert()>");
		targetAPI.add("<android.database.sqlite.SQLiteStatement: int executeUpdateDelete()>");
		
		
		//special 
		targetScanList.add("<android.database.sqlite.SQLiteOpenHelper: android.database.sqlite.SQLiteDatabase getWritableDatabase()>");
		targetScanList.add("<android.database.sqlite.SQLiteOpenHelper: android.database.sqlite.SQLiteDatabase getReadableDatabase()>");
		
		targetScanList.addAll(targetAPI);
		targetScanList.add("<android.database.sqlite.SQLiteDatabase: void endTransaction()>");
		targetScanList.add("<android.database.sqlite.SQLiteDatabase: void beginTransaction()>");
		targetScanList.add("<android.database.sqlite.SQLiteDatabase: void beginTransactionNonExclusive()>");
		targetScanList.add("<android.database.sqlite.SQLiteDatabase: void beginTransactionWithListener(android.database.sqlite.SQLiteTransactionListener)>");
		targetScanList.add("<android.database.sqlite.SQLiteDatabase: void beginTransactionWithListenerNonExclusive(android.database.sqlite.SQLiteTransactionListener)>");
		
		transactionAPI.add("<android.database.sqlite.SQLiteDatabase: void endTransaction()>");
		transactionAPI.add("<android.database.sqlite.SQLiteDatabase: void beginTransaction()>");
		transactionAPI.add("<android.database.sqlite.SQLiteDatabase: void beginTransactionNonExclusive()>");
		transactionAPI.add("<android.database.sqlite.SQLiteDatabase: void beginTransactionWithListener(android.database.sqlite.SQLiteTransactionListener)>");
		transactionAPI.add("<android.database.sqlite.SQLiteDatabase: void beginTransactionWithListenerNonExclusive(android.database.sqlite.SQLiteTransactionListener)>");

		this.outputFile = outputFile;
		startAndroid(rtjar,appfolder+apk,appfolder+classlist);

	}

	private void startAndroid(String arg0,String arg1,String arg2)
	{
		//"/home/yingjun/Documents/StringAnalysis/MethodSummary/"
		//"Usage: rt.jar app_folder classlist.txt"
		AndroidApp App=new AndroidApp(arg0,arg1,arg2);
		

		long s0 = System.currentTimeMillis();
		
		
		boolean existSQLInUse = false;
		Set<String> methodContainBegin = new HashSet<>();
		Set<String> allMethodsNotInLib = new HashSet<>();
		for(NewNode n: App.getCallgraph().getRTOdering())
		{

			if(n.getMethod().isConcrete()&&!n.getMethod().getDeclaringClass().isAbstract())
			{
				totalLinesOfCode+=n.getMethod().getActiveBody().getUnits().size();
				boolean isRelevant = false;
				boolean isModMethod = false;
				boolean isLib = containLib(n.getMethod().getSignature());
				if(!isLib)
					allMethodsNotInLib.add(n.getMethod().getSignature());
				for(Unit actualNode:n.getMethod().retrieveActiveBody().getUnits())
				{

					if(((Stmt)actualNode).containsInvokeExpr())
					{
						
						String sig= ((Stmt)actualNode).getInvokeExpr().getMethod().getSignature();
						String className = ((Stmt)actualNode).getInvokeExpr().getMethod().getDeclaringClass().toString();

						if(existSQLInUse==false&&className.contains("android.database.sqlite"))
						{
							if(!isLib)
								existSQLInUse = true;
						}
						if(sig.contains("<android.database.sqlite.SQLiteDatabase: void beginTransaction"))
						{
							methodContainBegin.add(n.getMethod().getSignature());
						}

						//is relevant
						if(targetScanList.contains(sig)||targetMethod.keySet().contains(sig))
						{
							isRelevant = true;
						}
						//need to do region analysis
						if(targetAPI.contains(sig)||rSum.contains(sig))
						{
							isModMethod = true;
						}
						
					}
				}
				
				if(isRelevant)
				{
					targetMethod.put(n.getMethod().getSignature(),n.getMethod());
					
				}
				if(isModMethod)
				{
					rSum.add(n.getMethod().getSignature());
				}

			}
			
		}
		
		//System.out.println("Number of relevant methods before adding begin and syn related methods:"+targetMethod.keySet().size());
		Map<String,SootMethod> beginSynCallees = identifyBeginSynCallee(App, methodContainBegin, allMethodsNotInLib);
		targetMethod.putAll(beginSynCallees);
		//System.out.println("Number of relevant methods after adding begin and syn related methods:"+targetMethod.keySet().size());
		
		//System.out.println(arg1);
		
		
		
		for(SootMethod sm : targetMethod.values())
		{
			totalRevelantCode += sm.retrieveActiveBody().getUnits().size();
		}
		
		
		
		
		/*
		Set<String> relMethod = new HashSet<>();
		for(String t:targetMethod.keySet())
			if(!containLib(t))
				relMethod.add(t);
		*/
		//System.out.println(App.getCallgraph().toDotRel(relMethod));
		




		
		Set<String> regionToMod = new HashSet<>();
		
		Set<NodeInterface> targetModNodes = new HashSet<>();
	
		Set<String> targetRegion = new HashSet<>();
		
		Set<String> removeRegion = new HashSet<>();
		
		

		
		for(NewNode n: App.getCallgraph().getHeads())
		{
			
					

			if(!targetMethod.keySet().contains(n.getMethod().getSignature()))
				continue;
			
			
			CFGInterface cfg = new SootCFG(n.getMethod().getSignature(),n.getMethod());


			
			new RegionAnalysis(cfg,targetAPI,rSum,null,null, modToRegion,modToRegionNode,targetMethod,preStoreCFG,modToSig);
			
			
			
			GlobalFlowAnalysis gfa = new GlobalFlowAnalysis(cfg, modToRegion.keySet(), targetMethod.keySet(), preStoreCFG, null, modToSig,synToSig);

			for(Pair<NodeInterface,Integer> p:gfa.getTargetNode())
			{
				if(((Node)p.getFirst()).getActualNode().toString().contains("getWritableDatabase")
						||((Node)p.getFirst()).getActualNode().toString().contains("getReadableDatabase"))
					removeRegion.add(modToRegion.get(p.getFirst()));
				else
				{
					if(synToSig.containsKey(p.getFirst()))
					{
						// syn that is not nested in begin
						//System.out.println(synToSig.get(p.getFirst()));
					}
					else
					{
					targetModNodes.add(p.getFirst());
					targetRegion.add(modToRegion.get(p.getFirst()));
					output.add(modToSig.get(p.getFirst())+"->"+modToRegion.get(p.getFirst()));
					}
					
				}
			}

		}

		int count = 0;
		Set<String> caculatedMethod = new HashSet<>();
		for(NewNode n: App.getCallgraph().getHeads())
		{
			if(targetMethod.keySet().contains(n.getMethod().getSignature()))
			{
				SootMethod sm = n.getMethod();
				caculatedMethod.add(sm.getSignature());
				count += sm.getActiveBody().getUnits().size();
			}
		}
		for(CFGInterface n: preStoreCFG.values())
		{
			SootMethod sm = App.getCallgraph().getRTOMap().get(n.getSignature()).getMethod();
			caculatedMethod.add(sm.getSignature());
			count += sm.getActiveBody().getUnits().size();
		}
		for(Entry<String,SootMethod> en : beginSynCallees.entrySet())
		{
			if(!caculatedMethod.contains(en.getKey()))
				count += en.getValue().getActiveBody().getUnits().size();
		}
		//System.out.println("Line of target bytecodes:"+count);

		//context sensitive 
		/*
		Set<String> nonTargetRegion = new HashSet<>();
		for(NodeInterface s:modToRegion.keySet())
			if(!targetModNodes.contains(s))
				nonTargetRegion.add(modToRegion.get(s));
		
		System.out.println(targetRegion);
		System.out.println(nonTargetRegion);
		*/
		
		
		List<CFGInterface> rtoCFG = new ArrayList<>();
		Map<String,CFGInterface> rtoCFGMap = new HashMap<>();
		for(NewNode n: App.getCallgraph().getRTOdering())
		{
			if(n.getMethod()!=null&&targetMethod.keySet().contains(n.getMethod().getSignature()))
			{
				CFGInterface cfg = new SootCFG(n.getMethod().getSignature(),n.getMethod());
				rtoCFG.add(cfg);
				rtoCFGMap.put(cfg.getSignature(), cfg);
	
			}
		}

		
		
		
		InterReachingDefinition ird = new InterReachingDefinition(rtoCFG,rtoCFGMap);

		for(NodeInterface mod: targetModNodes)
		{
			
			boolean inSideLoop = false;
			
			
			
			String sig = modToSig.get(mod).substring(0,modToSig.get(mod).indexOf(">")+1);
			//just in case modToSig starts with a constructor
			//e.g.,<com.andromo.dev21730.app21166.br: void <init>(android.content.Context)>-1,130
			if(sig.contains("<init>"))
			{
				int first = modToSig.get(mod).indexOf(">");
				sig = modToSig.get(mod).substring(0,modToSig.get(mod).indexOf(">",first+1)+1);
			}
			//System.out.println(sig);
			NodeInterface rdModNode = rtoCFGMap.get(sig).getNodeFromOffset(mod.getOffset());
			Unit actualNode = (Unit) ((Node)rdModNode).getActualNode();
			
			//Monitor printing
			/*
			System.out.println("Mod:"+modToSig.get(mod));
			ird.getOutermostMonitor(sig, rdModNode, modToSig.get(mod));
			System.out.println("Loop:"+modToRegionNode.get(mod).getCFG().getSignature());
			System.out.println("IS:"+ird.isSychronized(modToRegionNode.get(mod).getCFG().getSignature(),rtoCFGMap.get(modToRegionNode.get(mod).getCFG().getSignature()).getNodeFromOffset(modToRegionNode.get(mod).getBackEdge().getDestination().getOffset()), modToSig.get(mod)));
			*/
			//caller is the jimpleLocalBox of the UseBoxes
			String modVar = null;
			for(ValueBox v:actualNode.getUseBoxes())
			{
				if(v instanceof JimpleLocalBox)
					modVar = v.getValue().toString();
			}
			
			//System.out.println(modVar);
			
			
			
			List<String> fieldDefined= ird.getFieldSig(sig, rdModNode, modVar);
			
			//isDefined for fields
			for(String f:fieldDefined)
			for(Pair<String,String> defOffset:ird.getDefineLineNum(modToRegionNode.get(mod).getCFG().getSignature(),rtoCFGMap.get(modToRegionNode.get(mod).getCFG().getSignature()).getNodeFromOffset( modToRegionNode.get(mod).getBackEdge().getDestination().getOffset()), f, ""))
			{
				int i = Integer.parseInt(defOffset.getSecond());
				CFGInterface modCFG = null;
				for(CFGInterface temp:preStoreCFG.values())
					if(temp.getSignature().equals(defOffset.getFirst()))
					{	
						modCFG = temp;
						
						if(modToRegionNode.get(modCFG.getNodeFromOffset(i))!=null)
							inSideLoop = true;
					}
			}
			
			//isDefined for local variables
			for(Pair<String, String> defOffset:ird.getDefineLineNum(sig,rdModNode, modVar,modToSig.get(mod)))
			{
				int i = Integer.parseInt(defOffset.getSecond());
				CFGInterface modCFG = null;
				for(CFGInterface temp:preStoreCFG.values())
					if(temp.getSignature().equals(defOffset.getFirst()))
					{	
						modCFG = temp;
						
						if(modToRegionNode.get(modCFG.getNodeFromOffset(i))!=null)
							inSideLoop = true;
					}
			}


			if(!fieldDefined.isEmpty())
			{
				//is a field
				boolean isInSameClass = true;
				
				String regionSig = modToRegionNode.get(mod).getCFG().getSignature();
				String classOfRegion = regionSig.substring(1,regionSig.indexOf(":"));
				for(String f:fieldDefined)
				{
						
						String classOfField = f.substring(1,f.indexOf(":"));
						
						if(!classOfField.equals(classOfRegion))
							isInSameClass = false;
				}
				
				if(isInSameClass)
				{	if(!inSideLoop)
					modToCat.put(mod,"B@"+fieldDefined.get(0));
				}
				
			
			}
			else
			{
				//is a local variable
				
				List<String> declarePos = ird.getDeclarePosition(sig,rdModNode, modVar,modToSig.get(mod),modToRegionNode.get(mod).getCFG().getSignature());
				if(!declarePos.isEmpty())
				{
					for(String line:declarePos)
					{
						//Only use one declare position
						if(!containNodeWithOffset(modToRegionNode.get(mod).getNodeList(),Integer.parseInt(line)))
							modToCat.put(mod,"A@"+line);
					}
					
				}

			}

			//System.out.println(modToSig.get(mod)+"\n"+modToCat.get(mod));
		}
		
		boolean beginIsInmost = true;
		Set<String> synOrLockInBeginMethods = new HashSet<>();
		for(Entry<NodeInterface,String> syn:synToSig.entrySet())
		{
			//exist syn in begin
			if(syn.getValue().contains("->"))
			{
				//System.out.println("Syn or Lock in begin "+syn.getValue());
				String beginMethodSig = syn.getValue().split("->beginTransaction in ")[1];
				
				beginMethodManualVerify.add(beginMethodSig);
				if(allMethodsNotInLib.contains(beginMethodSig))
				{
					beginIsInmost = false;
					synOrLockInBeginMethods.add(beginMethodSig);
				}
			}
				
		}
		
		boolean beginIsOutmost = true;

		Set<NodeInterface> nodesInSyn = ird.getAllNodesInSyn();
		Set<NodeInterface> nodesInLock = ird.getAllNodesInLock();
		Set<String> beginInSynMethods = ird.getMethodContainsBeginInSyn(nodesInSyn);
		Set<String> beginInLockMethods = ird.getMethodContainsBeginInSyn(nodesInLock);
		for(String methodName : beginInSynMethods)
		{
			if(allMethodsNotInLib.contains(methodName))
			{
				//System.out.println("Begin in syn "+methodName);
				//synchronized are in well structured, no need to verify
				//lockMethodManualVerify.add(methodName);
				beginIsOutmost = false;
			}
		}
		for(String methodName : beginInLockMethods)
		{
			if(allMethodsNotInLib.contains(methodName))
			{
				//System.out.println("Begin in lock "+methodName);
				lockMethodManualVerify.add(methodName);
				beginIsOutmost = false;
			}
		}
		Set<NodeInterface> HP = new HashSet<>();
		Set<NodeInterface> inScopeHP = new HashSet<>();
		for(NodeInterface t: targetModNodes)
		{
			String modSig = modToSig.get(t);
			if(!containLib(modSig))
			{
				HP.add(t);
				if(modToCat.get(t)!=null)
				{
					inScopeHP.add(t);	
				}
			}
		}
		//Set<String> methodsContainSynOrLockInLoop = new HashSet<>();

		Set<NodeInterface> outAHP = new HashSet<>();
		Set<NodeInterface> inAHP = new HashSet<>();
		for(NodeInterface t:inScopeHP)
		{
			//Loop is not locked by any Java locks
			boolean isLoopinSyn = false;
			String loopMethod = modToRegionNode.get(t).getCFG().getSignature();
			NodeInterface loopHeader = rtoCFGMap.get(loopMethod).getNodeFromOffset(modToRegionNode.get(t).getBackEdge().getDestination().getOffset());
			if(nodesInSyn.contains(loopHeader)||nodesInLock.contains(loopHeader))
				isLoopinSyn = true;
			
			//Loop does not contain any Java lock

			CFGInterface loopCFG = rtoCFGMap.get(loopMethod);
			List<NodeInterface> loopNodes = new ArrayList<>();
			for(NodeInterface loopNode:modToRegionNode.get(t).getNodeList())
			{
				loopNodes.add(loopCFG.getNodeFromOffset(loopNode.getOffset()));
			}
			boolean isSynInLoop = isSynOrLockInLoop(loopNodes, allMethodsNotInLib, App.getCallgraph().getRTOMap());
			
			if(isLoopinSyn||isSynInLoop)
				isLoopRelatedToLock.put(t, true);
			else
				isLoopRelatedToLock.put(t, false);
			//begin and syn does not nest with each other or the program does not originally contain any begin
			if(beginIsInmost&&beginIsOutmost)
			{
				
				if(!isLoopinSyn)
				{
					//methodsContainSynOrLockInLoop.add(loopMethod);
					outAHP.add(t);
				}
				
				if(!isSynInLoop)
					inAHP.add(t);
			}
			else if(beginIsOutmost)
			{
				//Loop is not locked by any Java locks, the inserted begin will be the outmost
				if(!isLoopinSyn)
				{
					//methodsContainSynOrLockInLoop.add(loopMethod);
					outAHP.add(t);
				}
			}
			else if(beginIsInmost)
			{
				//Loop does not contain any Java lock, the inserted begin will be the inmost
				if(!isSynInLoop)
					inAHP.add(t);
			}
			else
			{
				/*
				Set<String> lockEntries = new HashSet<>();
				for(String methodName : beginInSynMethods)
					lockEntries.addAll(App.getCallgraph().getEntryMethod(methodName));
				for(String methodName : beginInLockMethods)
					lockEntries.addAll(App.getCallgraph().getEntryMethod(methodName));
				for(String methodName : synOrLockInBeginMethods)
					lockEntries.addAll(App.getCallgraph().getEntryMethod(methodName));
				
				Set<String> loopEntries = App.getCallgraph().getEntryMethod(loopMethod);
				
				//all are in main thread
				boolean lockInMain = true;
				for(String entry:lockEntries)
				{
					if(!isEntry(entry))
						lockInMain = false;
				}
				
				boolean loopInMain = true;
				for(String entry:loopEntries)
				{
					if(!isEntry(entry))
						lockInMain = false;
				}
				
				if((lockInMain&&loopInMain)||(!isSynInLoop&&!isSynInLoop))
				{
					inAHP.add(t);
					outAHP.add(t);
				}
				*/
				if((!isSynInLoop&&!isSynInLoop))
				{
					inAHP.add(t);
					outAHP.add(t);
				}
			}
			//System.out.println(t+" "+modToSig.get(t));
			//System.out.println("Outmost:"+beginIsOutmost + " Inmost:"+beginIsInmost+ " is AHP:"+AHP.contains(t));
		}
		Set<NodeInterface> AHP = null;
		//identify the instrumentation target
		if(inAHP.isEmpty()&&outAHP.isEmpty())
			AHP = inAHP;
		if(inAHP.isEmpty()&&!outAHP.isEmpty())
			AHP = outAHP;
		if(!inAHP.isEmpty()&&outAHP.isEmpty())
			AHP = inAHP;
		if(!inAHP.isEmpty()&&!outAHP.isEmpty())
		{
			if(inAHP.size() < outAHP.size())
				AHP = outAHP;
			else
				AHP = inAHP;
		}
		for(NodeInterface t: AHP)
		{
			if(modToCat.get(t)==null)
				continue;
			
			String modSig = modToSig.get(t);
			
			if(containLib(modSig))
				continue;
			//System.out.println(modSig);
			int i1 = modSig.lastIndexOf("@")+1;
			int i2 = modSig.lastIndexOf(">")+1;
			
			entries.add(modSig.substring(i1,i2));
			
			String regionSig = modToRegionNode.get(t).getSignature();
			sigToRegionNode.put(regionSig, modToRegionNode.get(t));
			if(regionToDefine.get(regionSig)==null)
			{
				Set<String> defineLine = new HashSet<>();
				defineLine.add(modToCat.get(t));
				regionToDefine.put(regionSig, defineLine);
			}
			else
				regionToDefine.get(regionSig).add(modToCat.get(t));
		}
		
		long s1 = System.currentTimeMillis();
		System.out.println("Time:"+(s1-s0));
		Main.analysisTotal = s1-s0;
		printResult(arg1.substring(arg1.indexOf("/App")), existSQLInUse,HP,inScopeHP,inAHP,outAHP,rtoCFGMap,count,(s1-s0));
		

		
	

		
		
	}
	private void printResult(String app, boolean existSQLInUse,
			Set<NodeInterface> HP, Set<NodeInterface> inScopeHP,
			Set<NodeInterface> inAHP,Set<NodeInterface> outAHP,
			Map<String, CFGInterface> rtoCFGMap, int count, long time
			) {
		Set<NodeInterface> AHP = null;
		if(inAHP.isEmpty()&&outAHP.isEmpty())
			AHP = inAHP;
		if(inAHP.isEmpty()&&!outAHP.isEmpty())
			AHP = outAHP;
		if(!inAHP.isEmpty()&&outAHP.isEmpty())
			AHP = inAHP;
		if(!inAHP.isEmpty()&&!outAHP.isEmpty())
		{
			if(inAHP.size() < outAHP.size())
				AHP = outAHP;
			else
				AHP = inAHP;
		}
		boolean existTransaction = false;
		StringBuilder result = new StringBuilder();
		
		result.append(app+"\n");
		
		for(NodeInterface t : HP)
		{
			String hpOrAhp;
			boolean isAHP = AHP.contains(t);
			if(isAHP)
				hpOrAhp = "AHP";
			else
				hpOrAhp = "HP";
			result.append("@"+hpOrAhp+"@"+modToCat.get(t)+"@"+modToSig.get(t)+"->"+modToRegion.get(t)+"\n");
			//if exist transaction related APIs in the call chain
			Set<String> transactionMethod = new HashSet<>();
			String pathSigs[] = modToSig.get(t).split("@");
			for(int i=1;i<pathSigs.length;i++ )
			{
				String pathSig = pathSigs[i];
				CFGInterface cfg = rtoCFGMap.get(pathSig.substring(0,pathSig.lastIndexOf(">")+1));
				if(cfg!=null)
				{	
					for(NodeInterface n: cfg.getAllNodes())
					{
						Unit actualNode = (Unit) ((Node)n).getActualNode();
						if(actualNode==null)
							continue;
						if(((Stmt)actualNode).containsInvokeExpr())
						{
							
							String sig= ((Stmt)actualNode).getInvokeExpr().getMethod().getSignature();
							if(transactionAPI.contains(sig))
							{
								transactionMethod.add(cfg.getSignature());
								existTransaction = true;
							}
						}
					}
				}
			}
			if(!transactionMethod.isEmpty())
			{
				result.append("Exist transaction on the call chain in:"+transactionMethod+"\n");
				beginMethodManualVerify.addAll(transactionMethod);
			}
			//length of call chain between db op and loop header
			String loopSig = modToRegionNode.get(t).getCFG().getSignature();
			String[] paths = modToSig.get(t).split("@");
			int loopCallChain = 0;
			
			for(int i = 1; i < paths.length; i++)
			{
				if(paths[i].contains(loopSig))
					break;
				loopCallChain++;

			}
			result.append("Length of call chain between db operation and loop header:"+loopCallChain+"\n");
			result.append("Length of call chain between db operation and call graph entry:"+(paths.length-1)+"\n");
			if(isLoopRelatedToLock.get(t)!=null)
				result.append("Target loop nests a lock or is nested in a lock:"+isLoopRelatedToLock.get(t)+"\n");
			else
				result.append("Target loop nests a lock or is nested in a lock:"+"not related"+"\n");
		}
		int numOfDBInLoop = 0;
		for(NodeInterface n: modToRegion.keySet())
		{
			if(!containLib(modToRegion.get(n)))
				numOfDBInLoop++;
		}
		
		
		result.append("Summary\n");
		result.append("Exist SQL in User Code:"+existSQLInUse+"\n");
		result.append("Exist Opp:"+!HP.isEmpty()+"\n");
		result.append("Exist Repair:"+!AHP.isEmpty()+"\n");
		result.append("Exist Transaction in HP:"+existTransaction+"\n");
		
		//result.append("Exist beginTransaction in synchronization:"+existBeginInSyn+"\n");
		//result.append("Exist beginTransaction in synchronization in background thread:"+existBeginInSynInBG+"\n");
		result.append("Num of DB in loop:"+numOfDBInLoop+"\n");
		result.append("Num of HP:"+HP.size()+"\n");
		result.append("Num of in scope HP:"+inScopeHP.size()+"\n");
		result.append("Num of AHP:"+AHP.size()+"\n");
		result.append("Num of begin-inmost AHP:"+inAHP.size()+"\n");
		result.append("Num of begin-outmost AHP:"+outAHP.size()+"\n");
		result.append("Line of target bytecodes in CCG:"+count+"\n");
		result.append("CCG prune out code:"+(1-totalRevelantCode*1.0/totalLinesOfCode)*100+"%\n");
		result.append("Num of bytecodes in program:"+totalLinesOfCode+"\n");
		//result.append("Time:"+time+"ms\n");
		boolean manual = false;
		if(!AHP.isEmpty())
		{
			if(!beginMethodManualVerify.isEmpty()||!lockMethodManualVerify.isEmpty())
				manual = true;
		}
		if(manual)
			result.append("Need to verify:"+manual+" begin method@"+beginMethodManualVerify+" lock method@"+lockMethodManualVerify+"\n");
		else
			result.append("Need to verify:"+manual+"\n");
		

	    try {
				PrintWriter pw = new PrintWriter(new FileWriter(outputFile, true));
				
				pw.println(result);
				
				pw.close();
	    }
		catch(IOException ex)
		{
		
		}
		System.out.println(result);
		
	}

	private boolean isSynOrLockInLoop(List<NodeInterface> loopNodes, Set<String> allMethodsNotInLib,
			Map<String,NewNode> rtoMap) {
		boolean containSyn = false;
		Set<String> loopCallees = new HashSet<>();
		for(NodeInterface n : loopNodes)
		{
			Stmt actualNode = (Stmt)((Node)n).getActualNode();
			if(actualNode!=null)
			{
				if(actualNode instanceof EnterMonitorStmt)
				{
					containSyn = true;
					break;
				}
				if(actualNode.containsInvokeExpr())
				{
					
					String methodName = actualNode.getInvokeExpr().getMethod().getSignature();
					if(methodName.equals("<java.util.concurrent.locks.Lock: void lock()>"))
					{
						containSyn = true;
						break;
					}
					if(allMethodsNotInLib.contains(methodName))
						loopCallees.add(methodName);
				}
			}
		}
		if(!containSyn)
		{
			Stack<String> processMethod = new Stack<>();
			processMethod.addAll(loopCallees);
			while(!processMethod.isEmpty())
			{
				if(containSyn)
					break;
				String currentMethod = processMethod.pop();
				NewNode n = rtoMap.get(currentMethod);
				SootMethod sm = n.getMethod();
				if(allMethodsNotInLib.contains(sm.getSignature()))
				{
					
					for(Unit actualNode:n.getMethod().retrieveActiveBody().getUnits())
					{
						if(actualNode instanceof EnterMonitorStmt)
						{
							containSyn = true;
							break;
						}
						if(((Stmt)actualNode).containsInvokeExpr())
						{
							
							SootMethod method = ((Stmt)actualNode).getInvokeExpr().getMethod();
							String methodSig = method.getSignature();
							if(methodSig.equals("<java.util.concurrent.locks.Lock: void lock()>"))
							{
								containSyn = true;
								break;
							}
							if(allMethodsNotInLib.contains(methodSig))
							{
								if(!loopCallees.contains(methodSig))
								{
									loopCallees.add(methodSig);
									processMethod.push(methodSig);
								}
							}
						}
					}
				}
			}
		}
		return containSyn;
	}
	private Map<String, SootMethod> identifyBeginSynCallee(AndroidApp App,
			Set<String> methodContainBegin, Set<String> allMethodsNotInLib) {
		Stack<String> processMethod = new Stack<>();
		processMethod.addAll(methodContainBegin);
		Map<String,NewNode> rtoMap = App.getCallgraph().getRTOMap();
		Set<String> beginRelatedCallees = new HashSet<>();
		beginRelatedCallees.addAll(methodContainBegin);
		//dfs
		while(!processMethod.isEmpty())
		{
			String currentMethod = processMethod.pop();
			NewNode n = rtoMap.get(currentMethod);
			SootMethod sm = n.getMethod();
			if(allMethodsNotInLib.contains(sm.getSignature()))
			{
				
				for(Unit actualNode:n.getMethod().retrieveActiveBody().getUnits())
				{
					if(((Stmt)actualNode).containsInvokeExpr())
					{
						
						SootMethod method = ((Stmt)actualNode).getInvokeExpr().getMethod();
						String methodSig = method.getSignature();

						if(allMethodsNotInLib.contains(methodSig))
						{
							if(!beginRelatedCallees.contains(methodSig))
							{
								beginRelatedCallees.add(methodSig);
								processMethod.push(methodSig);
							}
						}
					}
				}
			}
		}
		Map<String,SootMethod> targetMethod = new HashMap<>();
		for(NewNode n: App.getCallgraph().getRTOdering())
		{

			if(beginRelatedCallees.contains(n.getMethod().getSignature()))
			{
				boolean isRelevant = false;
				for(Unit actualNode:n.getMethod().retrieveActiveBody().getUnits())
				{
					
					if(((Stmt)actualNode).containsInvokeExpr())
					{
						SootMethod sm = ((Stmt)actualNode).getInvokeExpr().getMethod();
						
						
							if(sm.getSignature().equals("<java.util.concurrent.locks.Lock: void lock()>")||
									sm.getSignature().equals("<java.util.concurrent.locks.Lock: void unlock()>")||
									targetMethod.containsKey(sm.getSignature()))
							{
								isRelevant = true;
								break;
							}
										
					}
					
					if(actualNode instanceof EnterMonitorStmt)
					{
						isRelevant = true;
						break;
					}
				}
				if(isRelevant)
					targetMethod.put(n.getMethod().getSignature(),n.getMethod());
			}
		}
		return targetMethod;
	}

	public boolean isEntry(String methodSig)
	{
		String className = methodSig.split(":")[0].replace("<", "");
		SootClass sc = Scene.v().getSootClass(className);
		String secondHalf = methodSig.split(":")[1];
		String methodName = secondHalf.substring(1,secondHalf.length()-1);
		
		
		return entryPoint.isEntry(sc, methodName);
	}
	public boolean containNodeWithOffset(List<NodeInterface> nodeList,int offset)
	{
		for(NodeInterface n:nodeList)
			if(n.getOffset()==offset)
				return true;
		
		return false;
	}
	public static boolean containLib(String s)
	{
		if(s.startsWith("<com.google.")||
				s.startsWith("<com.urbanairship")||
				s.startsWith("<net.robotmedia")||
				s.startsWith("<com.localytics.android")||
				s.startsWith("<com.millennialmedia.android"))

			return true;
		else
			return false;
	}
	
	private boolean onlyContainLib(Set<String> region)
	{
		for(String targetRegion: region)
		{
			if(!containLib(targetRegion))
				return false;
				
		}
		return true;
	}
	
	public Map<String, RegionNode> getRegionMap()
	{
		return sigToRegionNode;
	}
	public Map<String, Set<String>> getInstrumentPos()
	{
		return regionToDefine;
	}
	
	public Set<String> getInstrumentEntry()
	{
		return entries;
	}
	//Testing purpose
	
	public GlobalCallGraph(String dir)
	{
      
		targetAPI.add("<android.database.sqlite.SQLiteDatabase: long insert(java.lang.String,java.lang.String,android.content.ContentValues)>");
		targetAPI.add("<android.database.sqlite.SQLiteDatabase: long insertOrThrow(java.lang.String,java.lang.String,android.content.ContentValues)>");
		targetAPI.add("<android.database.sqlite.SQLiteDatabase: long insertWithOnConflict(java.lang.String,java.lang.String,android.content.ContentValues,int)>");
		targetAPI.add("<android.database.sqlite.SQLiteDatabase: int update(java.lang.String,android.content.ContentValues,java.lang.String,java.lang.String[])>");
		targetAPI.add("<android.database.sqlite.SQLiteDatabase: int updateWithOnConflict(java.lang.String,android.content.ContentValues,java.lang.String,java.lang.String[],int)>");
		targetAPI.add("<android.database.sqlite.SQLiteDatabase: void execSQL(java.lang.String)>");
		targetAPI.add("<android.database.sqlite.SQLiteDatabase: void execSQL(java.lang.String,java.lang.String[])>");
		targetAPI.add("<android.database.sqlite.SQLiteDatabase: int delete(java.lang.String,java.lang.String,java.lang.String[])>");
		targetAPI.add("<android.database.sqlite.SQLiteDatabase: long replace(java.lang.String,java.lang.String,android.content.ContentValues)>");
		targetAPI.add("<android.database.sqlite.SQLiteDatabase: long replaceOrThrow(java.lang.String,java.lang.String,android.content.ContentValues)>");
		targetAPI.add("<android.database.sqlite.SQLiteStatement: void execute()>");
		targetAPI.add("<android.database.sqlite.SQLiteStatement: long executeInsert()>");
		targetAPI.add("<android.database.sqlite.SQLiteStatement: int executeUpdateDelete()>");
		
		targetScanList.addAll(targetAPI);
		targetScanList.add("<android.database.sqlite.SQLiteDatabase: void endTransaction()>");
		targetScanList.add("<android.database.sqlite.SQLiteDatabase: void beginTransaction()>");
		targetScanList.add("<android.database.sqlite.SQLiteDatabase: void beginTransactionNonExclusive()>");
		targetScanList.add("<android.database.sqlite.SQLiteDatabase: void beginTransactionWithListener(android.database.sqlite.SQLiteTransactionListener)>");
		targetScanList.add("<android.database.sqlite.SQLiteDatabase: void beginTransactionWithListenerNonExclusive(android.database.sqlite.SQLiteTransactionListener)>");

        Options.v().set_soot_classpath("/usr/lib/jvm/java-7-openjdk-amd64/jre/lib/rt.jar"+":"+dir);
      	Options.v().set_android_jars("/home/yingjun/Documents/sqlite/sqlAndroid");
      	Options.v().set_whole_program(true);
      	Options.v().set_verbose(false);
      	Options.v().set_keep_line_number(true);
      	Options.v().set_keep_offset(true);
      	Options.v().set_allow_phantom_refs(true);
      	
      	List<String> dirs= new ArrayList<>();
      	dirs.add(dir);
      	Options.v().set_process_dir(dirs);
		List<SootMethod> entryPoints=new ArrayList<SootMethod>();
		Set<SootMethod> allmethods=new HashSet<SootMethod>();
		

		List<String> classes = new ArrayList<>();
		classes.add("usc.sql.inter.testcase.NestedMethod");

		List<String> entry = new ArrayList<>();
		entry.add("<usc.sql.inter.testcase.NestedMethod: void m1(android.database.sqlite.SQLiteDatabase,java.lang.String)>");
		for(String line: classes)
		{
			SootClass sc=Scene.v().loadClassAndSupport(line);
			allmethods.addAll(sc.getMethods());
			sc.setApplicationClass();
			for(SootMethod sm:sc.getMethods())
			{
				//System.out.println(sm.getSignature());
				if(sm.isConcrete()&&entry.contains(sm.getSignature()))
					entryPoints.add(sm);
			}
		}
		
		Scene.v().loadNecessaryClasses();
		Scene.v().setEntryPoints(entryPoints);
		CHATransformer.v().transform();
		CallGraph cg = Scene.v().getCallGraph();
		startJava(new StringCallGraph(cg,allmethods));
		
/*		for(NewNode n: (new StringCallGraph(cg,allmethods).getRTOdering()))
		{

			if(n.getMethod().isConcrete())
			{
				if(n.getMethod().getSignature().contains("main"))
				{
					for(Unit actualNode:n.getMethod().retrieveActiveBody().getUnits())
					{
						System.out.println(actualNode);
					}
				}
			}
		}*/
	}
	
	public void startJava(StringCallGraph sc)
	{
		for(NewNode n: sc.getRTOdering())
		{

			if(n.getMethod().isConcrete())
			{
				boolean isRelevant = false;
				boolean isModMethod = false;
				for(Unit actualNode:n.getMethod().retrieveActiveBody().getUnits())
				{
					if(((Stmt)actualNode).containsInvokeExpr())
					{
						
						String sig= ((Stmt)actualNode).getInvokeExpr().getMethod().getSignature();
						//is relevant
						if(targetScanList.contains(sig)||targetMethod.keySet().contains(sig))
						{
							isRelevant = true;
						}
						//need to do region analysis
						if(targetAPI.contains(sig)||rSum.contains(sig))
						{
							isModMethod = true;
						}
						
					}
				}
				
				if(isRelevant)
				{
					targetMethod.put(n.getMethod().getSignature(),n.getMethod());
				}
				if(isModMethod)
				{
					rSum.add(n.getMethod().getSignature());
				}
			}
			
		}

		Set<NodeInterface> target = new HashSet<>();
		Set<String> targetRegion = new HashSet<>();
		for(NewNode n: sc.getHeads())
		{
			//System.out.println(n.getMethod().getSignature());
			CFGInterface cfg = new SootCFG(n.getMethod().getSignature(),n.getMethod());
			
			new RegionAnalysis(cfg,targetAPI,rSum,null,null, modToRegion,modToRegionNode, targetMethod,preStoreCFG,modToSig);
			
			GlobalFlowAnalysis gfa = new GlobalFlowAnalysis(cfg, modToRegion.keySet(), targetMethod.keySet(), preStoreCFG, null, modToRegion,synToSig);
			
			for(Pair<NodeInterface,Integer> p:gfa.getTargetNode())
			{
				target.add(p.getFirst());
				targetRegion.add(modToRegion.get(p.getFirst()));
			}
			//System.out.println(gfa.getTargetNode());
		}
		
		System.out.println(modToRegion);
		System.out.println(targetRegion);
		
		
	}
}
