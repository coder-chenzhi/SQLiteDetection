package edu.usc.sql.traversal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import edu.usc.sql.graphs.EdgeInterface;
import edu.usc.sql.graphs.NodeInterface;


public class DFS {

	 public static List<NodeInterface> dfs(Set<NodeInterface> allNodes, NodeInterface entry){
		Map<NodeInterface,Boolean> marked = new HashMap<>();;
		for(NodeInterface n:allNodes)
		{
			marked.put(n, false);
		}
		
		List<NodeInterface> dfsNode = new ArrayList<>();

        Stack<NodeInterface> st = new Stack<NodeInterface>();
        st.push(entry);
        while(!st.isEmpty()){
        	NodeInterface v = st.pop();
            if(!marked.get(v)){
                marked.put(v, true);
                dfsNode.add(v);
                for(EdgeInterface e: v.getOutEdges()){
                    if(!marked.get(e.getDestination())){
                        st.push(e.getDestination());
                    }
                }
  
            }
        }
        return dfsNode;

	 }
	 public static List<NodeInterface> reversedfs(Set<NodeInterface> allNodes, NodeInterface exit){
		Map<NodeInterface,Boolean> marked = new HashMap<>();;
		for(NodeInterface n:allNodes)
		{
			marked.put(n, false);
		}
		
		List<NodeInterface> dfsNode = new ArrayList<>();

        Stack<NodeInterface> st = new Stack<NodeInterface>();
        st.push(exit);
        while(!st.isEmpty()){
        	NodeInterface v = st.pop();
            if(!marked.get(v)){
                marked.put(v, true);
                dfsNode.add(v);
                for(EdgeInterface e: v.getInEdges()){

                    if(!marked.get(e.getSource())){
                        st.push(e.getSource());
                    }
                }
  
            }
        }
        return dfsNode;

	 }
}
