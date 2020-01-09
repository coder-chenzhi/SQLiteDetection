/**
 * 
 */
package edu.usc.sql.graphs.cfg;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import soot.PhaseOptions;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Unit;
import soot.jimple.IdentityStmt;
import soot.jimple.Stmt;
import soot.jimple.internal.JCaughtExceptionRef;
import soot.options.Options;
import soot.toolkits.graph.Block;
import soot.toolkits.graph.BlockGraph;
import soot.toolkits.graph.BriefBlockGraph;
import soot.toolkits.graph.DirectedGraph;
import soot.util.Chain;
import edu.usc.sql.graphs.Edge;
import edu.usc.sql.graphs.EdgeInterface;
import edu.usc.sql.graphs.Node;
import edu.usc.sql.graphs.NodeInterface;

/**
 * @author mian
 *
 */
public class SootBG extends AbstractCFG {
	private String methodsig="";
	private boolean isSynchronized=false;
	public static Map<String, CFGInterface> buildCFGs(String rootDir, String component) {

		String classPath = rootDir;
		String sootClassPath = Scene.v().getSootClassPath() + File.pathSeparator + classPath;
		
		//System.out.println(sootClassPath);
		
		Scene.v().setSootClassPath(sootClassPath);

		//Options.v().set_keep_line_number(true);
		//PhaseOptions.v().setPhaseOption("jb","use-original-names");
		
		String className = component.replace(File.separator, ".");
		className = className.substring(0, className.length()-6);

		SootClass sootClass = Scene.v().loadClassAndSupport(className);
		Scene.v().loadNecessaryClasses();
		sootClass.setApplicationClass();
		
		List<SootMethod> methodList = sootClass.getMethods();

		Map<String, CFGInterface> cfgMap = new HashMap<String, CFGInterface>();
		for (SootMethod sm : methodList) {
			CFGInterface sootCfg = new SootBG(sm.toString(),sm);
			cfgMap.put(sm.toString(), sootCfg);
		}
		
		return cfgMap;
	}
	public String getSignature(){
		return this.methodsig;
	}
	public SootBG(String graphName, SootMethod method) {
		this.methodsig=method.getSignature();
		this.isSynchronized = method.isSynchronized();
		BlockGraph sootCfg = new BriefBlockGraph(method.retrieveActiveBody());

		//System.out.println(method);
		//Later added into Set.
		Node<Block> entry = new Node<Block>();
		entry.setName("entry");
		entry.setNodeContent("entry");
		entry.setActualNode(null);
		entry.setOffset(-1);

		Node<Block> exit = new Node<Block>();
		exit.setName("exit");
		exit.setNodeContent("exit");
		exit.setActualNode(null);
		exit.setOffset(sootCfg.size()+1);
		HashMap<Block, Integer> offsetmap=new HashMap<Block, Integer>();
		
		// get a snapshot iterator of the unit since we are going to
		// mutate the chain when iterating over it.
		//
		Iterator stmtIt = sootCfg.iterator();
		int offset=0;
		// typical while loop for iterating over each statement
		while (stmtIt.hasNext()) {

			// cast back to a statement.
			Block stmt = (Block) stmtIt.next();
			offsetmap.put(stmt, offset);
			offset++;
		}
		
		// Without entry & exit nodes
		Map<Block, Node<Block>> nodeMap = new HashMap<Block, Node<Block>>();

		Set<EdgeInterface> entryOutEdges = entry.getOutEdges();

		// Setting the entry node's outEdges
		for(Block u : sootCfg.getHeads()) {
			Node<Block> successNode = null;
			if (nodeMap.containsKey(u)) {
				successNode = nodeMap.get(u);
				nodeMap.remove(u);
			} else {
				successNode = new Node<Block>();
				successNode.setNodeContent(u.toString());
				successNode.setActualNode(u);
				//****************************
				//     For WAM 
				//****************************
				if(isMethodEntryPoint(sootCfg, u)) {
					successNode.setEntryPoint(true);
				}
			}
			successNode.setOffset(offsetmap.get(u));

			EdgeInterface edge;
			if(successNode.isEntryPoint())
				edge = new Edge(entry, successNode, "real");
			else 
				edge = new Edge(entry, successNode, "fake");
			Set<EdgeInterface> inEdges = successNode.getInEdges();
			inEdges.add(edge);
			successNode.setInEdges(inEdges);
			nodeMap.put(u, successNode);
			entryOutEdges.add(edge);
		}
		entry.setOutEdges(entryOutEdges);


		Set<EdgeInterface> exitInEdges = exit.getInEdges();
		// Setting the exit node's inEdges
		for(Block u : sootCfg.getTails()) {
			Node<Block> precessNode = null;
			if (nodeMap.containsKey(u)) {
				precessNode = nodeMap.get(u);
				nodeMap.remove(u.toString());
			} else {
				precessNode = new Node<Block>();
				precessNode.setNodeContent(u.toString());
				precessNode.setActualNode(u);
				precessNode.setTail(true);
			}
			precessNode.setOffset(offsetmap.get(u));

			EdgeInterface edge = new Edge(precessNode, exit, "");
			Set<EdgeInterface> outEdges = precessNode.getOutEdges();
			outEdges.add(edge);
			precessNode.setOutEdges(outEdges);

			nodeMap.put(u, precessNode);
			exitInEdges.add(edge);
		}
		exit.setInEdges(exitInEdges);

		Iterator<Block> it = sootCfg.iterator();
		while (it.hasNext()) {
			Block u = it.next();
			Node<Block> node = null;
			if (nodeMap.containsKey(u)) {
				node = nodeMap.get(u);
				node.setOffset(offsetmap.get(u));
				nodeMap.remove(u.toString());
			} else {
				node = new Node<Block>();
				node.setNodeContent(u.toString());
				node.setActualNode(u);
				node.setOffset(offsetmap.get(u));

				
			}

			Set<EdgeInterface> outEdges = node.getOutEdges();
			List<Block> successors = sootCfg.getSuccsOf(u);

			if(!successors.isEmpty()) {
				for (Block succU : successors) {
					Node<Block> successNode = null;
					if (nodeMap.containsKey(succU)) {
						successNode = nodeMap.get(succU);
						nodeMap.remove(succU);
					} else {
						successNode = new Node<Block>();
						successNode.setNodeContent(succU.toString());
						successNode.setActualNode(succU);
					}
					successNode.setOffset(offsetmap.get(succU));

					EdgeInterface edge = new Edge(node, successNode, "");
					Set<EdgeInterface> inEdges = successNode.getInEdges();
					try{
					inEdges.add(edge);
					}
					catch(Exception e){
						System.out.println("offset "+node.getNodeContent());
						throw e;
					}
					successNode.setInEdges(inEdges);

					nodeMap.put(succU, successNode);

					outEdges.add(edge);
				}
			}
			node.setOutEdges(outEdges);
			node.setOffset(offsetmap.get(u));

			nodeMap.put(u, node);
		}

		//Initialize the member's of cfg
		this.name = name; 
		//System.out.println(nodeMap.values().size());
		nodeSet = new HashSet<NodeInterface>();
		nodeSet.add(entry);
		nodeSet.add(exit);
		nodeSet.addAll(nodeMap.values());
		nodeOffsetMap = new HashMap<Integer,NodeInterface>();
		for(NodeInterface n:nodeSet)
			
			nodeOffsetMap.put(n.getOffset(), n);
	}



	//*************************************************
	//    Copy and modify from WAM's Analysis.java
	//*************************************************
	private boolean isMethodEntryPoint(DirectedGraph<Block> cfg, Block s) {
		if (cfg.getHeads().contains(s)) {
			Unit first = s.getBody().getUnits().getFirst();

			if (first instanceof IdentityStmt) {
				IdentityStmt is = (IdentityStmt)first;
				if (is.getRightOp() instanceof JCaughtExceptionRef) {
					return false;
				} 
			}
			return true;
		}
		return false;
	}
	@Override
	public boolean isSynchronized() {
		// TODO Auto-generated method stub
		return isSynchronized;
	}
}
