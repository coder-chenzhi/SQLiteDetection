package edu.usc.sql.global;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import soot.jimple.AssignStmt;
import soot.jimple.FieldRef;
import soot.jimple.Stmt;
import edu.usc.sql.string.LayerRegion;
import edu.usc.sql.graphs.Node;
import edu.usc.sql.graphs.NodeInterface;
import edu.usc.sql.graphs.cfg.CFGInterface;
import edu.usc.sql.graphs.cfg.SootCFG;

public class InterReachingDefinition {

	//signature to summary(	list of reaching definitions that are sqlite database type and can reach the exit )

	private Map<String,List<String>> sigToSum = new HashMap<>();
	private Map<String,List<String>> sigToFieldSum = new HashMap<>();
	private Map<String,ReachingDefinition> sigToRD = new HashMap<>();
	private Map<String, CFGInterface> rtoCFGMap;
	private List<CFGInterface> rtoCFG;
	public InterReachingDefinition(List<CFGInterface> rtoCFG, Map<String, CFGInterface> rtoCFGMap)
	{
		this.rtoCFG = rtoCFG;
		this.rtoCFGMap = rtoCFGMap;

		LayerRegion lll = new LayerRegion(null);
		for(CFGInterface cfg:rtoCFG)
		{
			sigToRD.put(cfg.getSignature(), new ReachingDefinition(cfg,"android.database.sqlite.SQLiteDatabase", sigToSum,sigToFieldSum, lll.identifyBackEdges(cfg.getAllNodes(),cfg.getAllEdges(), cfg.getEntryNode())));
			//System.out.println(cfg.getSignature()+"\n"+sigToRD.get(cfg.getSignature()).getOutSet(cfg.getExitNode()));
		}
		
		//System.out.println(sigToSum);
	}
	public Map<String,String> getInSetOfNode(String sig, NodeInterface loopEntry)
	{
		return sigToRD.get(sig).getInSet(loopEntry);
			
	}
	public List<String> getLineNumForUse(String sig,NodeInterface modNode,String modVar)
	{
		System.out.println(sigToRD.get(sig).getAllDef() + modVar);
		return sigToRD.get(sig).getLineNumForUse(modNode, modVar);
	}

	//return the <sig,offset> where the variable is defined
	public List<Pair<String,String>> getDefineLineNum(String sig,NodeInterface modNode,String modVar, String path)
	{
		List<Pair<NodeInterface,String>> temp = new ArrayList<>();
		
		temp.add(new Pair<NodeInterface,String>(modNode,modVar));
		
		//for(String field:getFieldSig(sig,modNode,modVar))
		//	temp.add(new Pair<NodeInterface,String>(modNode,field));
		
		List<Pair<String,String>> result = new ArrayList<>();
		for(String line:sigToRD.get(sig).getDefineLineNum((temp)))
		{
			if(!line.contains("-"))
				result.add(new Pair<String,String>(sig,line));
			else
			{
				//System.out.println(line);
				int paraOffset = Integer.parseInt(line.substring(1,line.indexOf(":")));
				String paraSig = line.substring(line.indexOf("<"),line.lastIndexOf(">")+1);
				
				//System.out.println(paraOffset+" "+paraSig);
				
				String[] pathCp = path.split("@");
				int index = 0;
				for(int i=0;i<pathCp.length;i++)
					if(pathCp[i].contains(paraSig))
						index = i;
				
				index+=1;
				
				if(index>=pathCp.length)
					continue;
				String nextSig = pathCp[index].substring(0,pathCp[index].lastIndexOf(">")+1);
				
				int offset = Integer.parseInt(pathCp[index].substring(pathCp[index].lastIndexOf(">")+1,pathCp[index].length()));
				
				//System.out.println(nextSig+" " +offset);
				
				CFGInterface callerCFG = rtoCFGMap.get(nextSig);
				NodeInterface callerModNode = callerCFG.getNodeFromOffset(offset);
				String callerModVar = ((Stmt)((Node)callerModNode).getActualNode()).getInvokeExpr().getArg(paraOffset).toString();
				
				result.addAll(getDefineLineNum(nextSig,callerModNode,callerModVar,path));
				
			}
		}
		return result;
	}


	//return the node where the field is assigned to a temporary variable  
	public List<String> getFieldSig(String sig,NodeInterface modNode,String modVar)
	{
		List<String> fieldList = new ArrayList<>();
		for(String i: sigToRD.get(sig).getLineNumForUse(modNode, modVar))
		{
			int fieldOffset = Integer.parseInt(i);

			NodeInterface fieldNode = rtoCFGMap.get(sig).getNodeFromOffset(fieldOffset);
			Stmt s = (Stmt)((Node)fieldNode).getActualNode();
			if(s instanceof AssignStmt)
			{
				if(((AssignStmt)s).getRightOp() instanceof FieldRef)
					fieldList.add(((FieldRef)((AssignStmt)s).getRightOp()).getField().getSignature());
			}
		}
		return fieldList;
	}
	
	//return where the dbo object is defined in the given method
	public List<String> getDeclarePosition(String sig,NodeInterface modNode,String modVar,String path,String targetSig)
	{
		
		List<String> result = new ArrayList<>();
		
		Stmt u = (Stmt) ((Node)modNode).getActualNode();
		
		
		if(sig.equals(targetSig))
		{
			result.addAll(sigToRD.get(sig).getLineNumForUse(modNode, modVar));
		}
			
		else
		{
			List<Pair<NodeInterface, String>> NodeToVarName = new ArrayList<>();
			NodeToVarName.add(new Pair<NodeInterface,String>(modNode,modVar));
			
			for(String line: sigToRD.get(sig).getUltimateLineNumForUse(NodeToVarName ))
			{
				if(line.contains("-"))
				{
				
				int paraOffset = Integer.parseInt(line.substring(1,line.indexOf(":")));
				String paraSig = line.substring(line.indexOf("<"),line.lastIndexOf(">")+1);
				
				//System.out.println(paraOffset+" "+paraSig);
				
				String[] pathCp = path.split("@");
				int index = 0;
				for(int i=0;i<pathCp.length;i++)
					if(pathCp[i].contains(paraSig))
						index = i;
				
				index+=1;
				
				if(index>=pathCp.length)
					continue;
				String nextSig = pathCp[index].substring(0,pathCp[index].lastIndexOf(">")+1);
				
				int offset = Integer.parseInt(pathCp[index].substring(pathCp[index].lastIndexOf(">")+1,pathCp[index].length()));
				
				//System.out.println(nextSig+" " +offset);
				
				CFGInterface callerCFG = rtoCFGMap.get(nextSig);
				NodeInterface callerModNode = callerCFG.getNodeFromOffset(offset);
				String callerModVar = ((Stmt)((Node)callerModNode).getActualNode()).getInvokeExpr().getArg(paraOffset).toString();
				
				result.addAll(getDeclarePosition(nextSig,callerModNode,callerModVar,path, targetSig));
				}
			}
		}
		return result;
		
	}
	public Set<NodeInterface> getAllNodesInSyn()
	{
		Set<NodeInterface> nodesInSyn = new HashSet<>();
		Set<String> methodsInSyn = new HashSet<>();
		//topo order
		for(int i = rtoCFG.size()-1; i >=0 ; i--)
		{
			CFGInterface cfg= rtoCFG.get(i);
			String sig = cfg.getSignature();

			
			for(NodeInterface n : cfg.getAllNodes())
			{
				Stmt actualNode = (Stmt) ((Node)n).getActualNode();
				
				if(methodsInSyn.contains(sig)||sigToRD.get(sig).isSynchronized(n))
				{
					
					
					nodesInSyn.add(n);
					if(actualNode!=null)
					{
						if(actualNode.containsInvokeExpr())
						{
							String methodName = actualNode.getInvokeExpr().getMethod().getSignature();
							
							methodsInSyn.add(methodName);
						}
					}
				}
			}
			
		}
		return nodesInSyn;
	}
	public Set<NodeInterface> getAllNodesInLock()
	{
		Set<NodeInterface> nodesInSyn = new HashSet<>();
		Set<String> methodsInSyn = new HashSet<>();
		//topo order
		for(int i = rtoCFG.size()-1; i >=0 ; i--)
		{
			CFGInterface cfg= rtoCFG.get(i);
			String sig = cfg.getSignature();

			
			for(NodeInterface n : cfg.getAllNodes())
			{
				Stmt actualNode = (Stmt) ((Node)n).getActualNode();
				
				if(methodsInSyn.contains(sig)||sigToRD.get(sig).isLocked(n))
				{
					
					
					nodesInSyn.add(n);
					if(actualNode!=null)
					{
						if(actualNode.containsInvokeExpr())
						{
							String methodName = actualNode.getInvokeExpr().getMethod().getSignature();
							
							methodsInSyn.add(methodName);
						}
					}
				}
			}
			
		}
		return nodesInSyn;
	}
	public Set<String> getMethodContainsBeginInSyn(Set<NodeInterface> nodesInSyn)
	{
		Set<String> methodContainsBeginInSyn = new HashSet<>();
		for(CFGInterface cfg : rtoCFG)
		{
			for(NodeInterface n : cfg.getAllNodes())
			{
				Stmt actualNode = (Stmt) ((Node)n).getActualNode();

					if(actualNode!=null)
					{
						if(actualNode.containsInvokeExpr())
						{
							String methodName = actualNode.getInvokeExpr().getMethod().getSignature();
							
							if(methodName.contains("<android.database.sqlite.SQLiteDatabase: void beginTransaction")
									&&nodesInSyn.contains(n))
							{
								//System.out.println(cfg.getSignature());
								methodContainsBeginInSyn.add(cfg.getSignature());
							}
						}
					}
				
			}
		}
		return methodContainsBeginInSyn;
		
	}
	public boolean containBeginInSyn(Set<NodeInterface> nodesInSyn)
	{
		
		for(CFGInterface cfg : rtoCFG)
		{
			for(NodeInterface n : cfg.getAllNodes())
			{
				Stmt actualNode = (Stmt) ((Node)n).getActualNode();

					if(actualNode!=null)
					{
						if(actualNode.containsInvokeExpr())
						{
							String methodName = actualNode.getInvokeExpr().getMethod().getSignature();
							
							if(methodName.contains("<android.database.sqlite.SQLiteDatabase: void beginTransaction")
									&&nodesInSyn.contains(n))
							{
								return true;
							}
						}
					}
				
			}
		}
		return false;
	}

	public boolean isSychronized(String sig, NodeInterface anyNode, String path)
	{
		if(rtoCFGMap.get(sig).isSynchronized())
			return true;
		else
		{
			boolean isSyn = false;
			String[] pathCp = path.split("@");
			int index = 0;
			for(int i=0;i<pathCp.length;i++)
			{
				if(pathCp[i].contains(sig))			
					index = i;
			}
			
			//check if the node is in synchronized block
			if(sigToRD.get(sig).isSynchronized(anyNode))
			{
				isSyn = true;
			}
			index++;
			//check if the parent method is in a synchronized block
			NodeInterface currentNode = anyNode;
			while(index<pathCp.length)
			{
				String nextSig = pathCp[index].substring(0,pathCp[index].lastIndexOf(">")+1);
				int offset = Integer.parseInt(pathCp[index].substring(pathCp[index].lastIndexOf(">")+1,pathCp[index].length()));			
				CFGInterface callerCFG = rtoCFGMap.get(nextSig);
				NodeInterface callerModNode = callerCFG.getNodeFromOffset(offset);
				currentNode = callerModNode;
				
				
				if(sigToRD.get(nextSig).isSynchronized(currentNode))
				{
					isSyn = true;
				}
			
				//System.out.println(nextSig+ " "+offset );
				index++;
			}
			return isSyn;
		}
	}
	public /*Pair<NodeInterface, String>*/void getOutermostMonitor(String sig, NodeInterface modNode, String path)
	{
		//get the outermost monitor following the path
		String[] pathCp = path.split("@");
		int index = 0;
		for(int i=0;i<pathCp.length;i++)
		{
			if(pathCp[i].contains(sig))
		
				index = i;
		}
		//index+=1;
		
		System.out.println(path);
		NodeInterface monitor = null;
		String monitorMethod = null;
		NodeInterface currentNode = modNode;
		while(index<pathCp.length)
		{
			String nextSig = pathCp[index].substring(0,pathCp[index].lastIndexOf(">")+1);
			int offset = Integer.parseInt(pathCp[index].substring(pathCp[index].lastIndexOf(">")+1,pathCp[index].length()));			
			CFGInterface callerCFG = rtoCFGMap.get(nextSig);
			NodeInterface callerModNode = callerCFG.getNodeFromOffset(offset);
			currentNode = callerModNode;
			//System.out.println(nextSig);
			//System.out.println(currentNode.getOffset());
			if(sigToRD.get(nextSig).isSynchronized(currentNode))
			{
				monitor = sigToRD.get(nextSig).getOutermostMonitor(currentNode);
				monitorMethod = nextSig;
			}
			

			//System.out.println(nextSig+ " "+offset );
			

			index++;
		}
		
		
		System.out.println("Outer:"+monitorMethod +":"+ monitor.getOffset());
	}
}
