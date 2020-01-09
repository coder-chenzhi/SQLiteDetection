package edu.usc.sql.global;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import soot.Unit;
import soot.jimple.EnterMonitorStmt;
import soot.jimple.Stmt;
import soot.tagkit.BytecodeOffsetTag;
import soot.tagkit.Tag;
import edu.usc.sql.graphs.EdgeInterface;
import edu.usc.sql.graphs.Node;
import edu.usc.sql.graphs.NodeInterface;
import edu.usc.sql.graphs.cfg.CFGInterface;

public class GlobalFlowAnalysis {
	private Set<NodeInterface> allNode;
	private Set<EdgeInterface> allEdge;
	private NodeInterface entry;
	private Map<NodeInterface,Set<Pair<NodeInterface,Integer>>> dSet = new HashMap<>();
	private CFGInterface cfg;
	private Set<NodeInterface> modQ;
	private Set<String> Rel;
	private Map<NodeInterface,CFGInterface> preStoreCFG;
	private Set<Pair<NodeInterface,Integer>>input;
	private Map<NodeInterface, String> modToSig;
	private Map<NodeInterface,Integer> modToOffset = new HashMap<>();
	private Map<NodeInterface,String> synToSig;
	public GlobalFlowAnalysis(CFGInterface cfg,Set<NodeInterface> modQ,Set<String> Rel,Map<NodeInterface,CFGInterface> preStoreCFG,Set<Pair<NodeInterface,Integer>>input, Map<NodeInterface, String> modToSig,Map<NodeInterface, String> synToSig)
	{
		allNode = cfg.getAllNodes();
		allEdge = cfg.getAllEdges();
		
		entry = cfg.getEntryNode();
		
		this.cfg = cfg;
		this.modQ = modQ;
		this.Rel = Rel;
		this.preStoreCFG = preStoreCFG;
		this.input = input;
		this.modToSig = modToSig;
		this.synToSig = synToSig;
		startDetection();

			//append to call graph path
			for(Pair<NodeInterface,Integer> out: dSet.get(entry))
			{
				if(input!=null&&isInInput(out.getFirst()))
					continue;
				if(modToSig.containsKey(out.getFirst()))
				{
					String s = modToSig.get(out.getFirst());
					s += "@"+cfg.getSignature()+modToOffset.get(out.getFirst());
					modToSig.put(out.getFirst(),s);
				}
				else
				{
					if(!synToSig.containsKey(out.getFirst()))
						System.err.println("Global Flow Analysis: Call-graph path is not initialized");
				}
			}
		
	}
	
	public Set<Pair<NodeInterface, Integer>> getTargetNode()
	{
		return dSet.get(entry);
	}
	private void startDetection()
	{


		
		for(NodeInterface n:allNode)
		{
			//database write operations in Loops, Gen Set
			if(modQ.contains(n))
			{
				Pair<NodeInterface,Integer> init = new Pair<>(n,0);
				HashSet<Pair<NodeInterface,Integer>> h = new HashSet<>();
				h.add(init);
				dSet.put(n, h);
				
				//End of call-graph path
				Unit actualNode = (Unit) ((Node)n).getActualNode();
			
				int offset = -1;
				for(Tag t : actualNode.getTags())
				{
					if(t instanceof BytecodeOffsetTag)
					{
						offset = ((BytecodeOffsetTag) t).getBytecodeOffset();
					}
				}
				modToSig.put(n, cfg.getSignature()+actualNode.getJavaSourceStartLineNumber()+","+offset);
				modToOffset.put(n, n.getOffset());
			}
			//Tuples from other methods
			else if(n.equals(cfg.getExitNode()))
			{
				if(input!=null)
					dSet.put(n,input);
				else
					dSet.put(n, new HashSet<Pair<NodeInterface,Integer>>());
			}
			 
			else
			{	
				//if it is a lock method or entermonitor stmt
				boolean isSyn = false;
				Stmt actualNode = (Stmt) ((Node)n).getActualNode();
				if(actualNode!=null)
				{
					if(actualNode instanceof EnterMonitorStmt)
						isSyn = true;
					
					else if(actualNode.containsInvokeExpr())
					{
						if(actualNode.getInvokeExpr().getMethod().getSignature().equals("<java.util.concurrent.locks.Lock: void lock()>"))
							isSyn = true;
					}
					
				}
				if(isSyn)
				{
					//System.out.println("Syn:"+n.getOffset()+":"+actualNode+" "+cfg.getSignature());
					Pair<NodeInterface,Integer> init = new Pair<>(n,0);
					HashSet<Pair<NodeInterface,Integer>> h = new HashSet<>();
					h.add(init);
					dSet.put(n, h);
					int offset = -1;
					for(Tag t : actualNode.getTags())
					{
						if(t instanceof BytecodeOffsetTag)
						{
							offset = ((BytecodeOffsetTag) t).getBytecodeOffset();
						}
					}
					synToSig.put(n, cfg.getSignature()+actualNode.getJavaSourceStartLineNumber()+","+offset);
					
				}
				else
					dSet.put(n, new HashSet<Pair<NodeInterface,Integer>>());
			
			}
					
		}
		
		
		//System.out.println(allNode.size());
		//System.out.println(allDfsNode.size());
		
		List<NodeInterface> topo = topoSort(allNode,new HashSet<EdgeInterface>());
		
		//System.out.println(cfg.getSignature()+" "+(topo.size()==allNode.size()));

		
		boolean change = true;

		while (change) {
			change = false;
			for(int i = topo.size()-1;i>=0;i--)
			{
				
				NodeInterface n = topo.get(i);
				Unit actualNode = (Unit) ((Node)n).getActualNode();
				
/*				System.out.println(n.getOffset()+": "+actualNode);
				System.out.println(dSet.get(n));
				System.out.println();*/
				
				Set<Pair<NodeInterface,Integer>> out = null;
				
				//Information may be flow out from callee
				if(dSet.get(n).isEmpty())
				{
					if(actualNode!=null&&((Stmt)actualNode).containsInvokeExpr())
					{
						String sig= ((Stmt)actualNode).getInvokeExpr().getMethod().getSignature();
						
						if(Rel.contains(sig))
						{
							/*System.out.println(Rel);
							System.out.println(sig);
							System.out.println(preStoreCFG.get(n));*/
							if(preStoreCFG.get(n)!=null)
							{
								out = new GlobalFlowAnalysis(preStoreCFG.get(n), modQ, Rel, preStoreCFG, dSet.get(n), modToSig, synToSig).getTargetNode();
								
								for(Pair<NodeInterface,Integer> outNode: out)
								{
									if(input!=null&&isInInput(outNode.getFirst()))
										continue;
									if(dSet.get(n).contains(outNode))
										continue;
									modToOffset.put(outNode.getFirst(),n.getOffset());
								}
							
							}
					
						}
					}
				}
				else
				{
					//exit node
					if(actualNode==null)
						out = dSet.get(n);
					else if(((Stmt)actualNode).containsInvokeExpr())
					{
						String sig= ((Stmt)actualNode).getInvokeExpr().getMethod().getSignature();
						if(sig.contains("<android.database.sqlite.SQLiteDatabase: void beginTransaction"))
						{
							
							
							//store the syn that is nested in beginTransaction, i.e., tuple with counter value = 0
							for(Pair<NodeInterface,Integer> p: dSet.get(n))
							{
								if(p.getSecond()==0)
								{
									if(synToSig.containsKey(p.getFirst()))
										synToSig.put(p.getFirst(), synToSig.get(p.getFirst())+"->beginTransaction in "+cfg.getSignature());
								}
							}
							out = operationOnD(dSet.get(n),-1);
						}
						else if(sig.contains("<android.database.sqlite.SQLiteDatabase: void endTransaction()>"))
							out =  operationOnD(dSet.get(n),1);
						else if(Rel.contains(sig))
						{
							
							if(preStoreCFG.get(n)!=null)
							{
								out = new GlobalFlowAnalysis(preStoreCFG.get(n), modQ, Rel, preStoreCFG, dSet.get(n), modToSig,synToSig).getTargetNode();
								for(Pair<NodeInterface,Integer> outNode: out)
								{
									if(input!=null&&isInInput(outNode.getFirst()))
										continue;
									if(dSet.get(n).contains(outNode))
										continue;
									modToOffset.put(outNode.getFirst(),n.getOffset());
								}
							}
							//strongly connected component
							else
							{
								//1. do nothing: Generate false negative
								
								//2. keep propagating dSet.get(n)  : Generate false positive or false negative if there exist begin or end transaction in the by-pass method
								out = dSet.get(n);
							}
						}
						else
							out = dSet.get(n);
					}
					else
						out = dSet.get(n);
					
				}

				if(out!=null&&!out.isEmpty())
				{
					for(EdgeInterface e: n.getInEdges())
					{
						NodeInterface prep = e.getSource();
						
						//Union
						if(!dSet.get(prep).containsAll(out))
						{
							change = true;
							dSet.get(prep).addAll(out);
						}
						
					}
				}
				

			}
		}

	}
	
	//opD(d,i): for all (s,j) in the (Statement,Int) set d. if j+i
	//>= 0, add (s,j+i) to the return set.
	private Set<Pair<NodeInterface,Integer>> operationOnD(Set<Pair<NodeInterface,Integer>> input,int i)
	{
		Set<Pair<NodeInterface,Integer>> output = new HashSet<>();
		for(Pair<NodeInterface,Integer> p: input)
		{
			if(p.getSecond()+i>=0)
			{
				output.add(new Pair<NodeInterface,Integer>(p.getFirst(),p.getSecond()+i));
			}
		}
		return output;
	}


	/**
	 * topological order
	 * @param allNode
	 * @param backedge back edges, such as edges from loop body to loop header
	 * @return
	 */
	public List<NodeInterface> topoSort(Set<NodeInterface> allNode,Set<EdgeInterface> backedge)
	{
		NodeInterface entry = null;
		Map<NodeInterface,List<EdgeInterface>> inEdgeMap = new HashMap<>();
		for(NodeInterface n:allNode)
		{
			if(n.getInEdges().isEmpty())
				entry = n;
			else
			{
				List<EdgeInterface> inEdge = new ArrayList<>();
				for(EdgeInterface e:n.getInEdges())
				{
					if(!backedge.contains(e))
					inEdge.add(e);
				}
				inEdgeMap.put(n, inEdge);
			}
		}
		//L ← Empty list that will contain the sorted elements
		List<NodeInterface> L = new ArrayList<>();
		//S ← Set of all nodes with no incoming edges
		Queue<NodeInterface> S = new LinkedList<>();
		
		S.add(entry);
		while(!S.isEmpty())
		{
			NodeInterface n = S.poll();
			L.add(n);
			for(EdgeInterface e:n.getOutEdges())
			{
				if(!backedge.contains(e))
				{
					inEdgeMap.get(e.getDestination()).remove(e);
				
				if(inEdgeMap.get(e.getDestination()).isEmpty())
					S.add(e.getDestination());
				}
			}
		}
		return L;
	}
	


	private boolean isInInput(NodeInterface n)
	{
		for(Pair<NodeInterface,Integer> in : input)
		{
			if(n.getName().equals(in.getFirst().getName()))
				return true;
		}
		return false;
	}

}
