package edu.usc.sql.instrument;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import edu.usc.sql.graphs.Node;
import edu.usc.sql.graphs.NodeInterface;
import edu.usc.sql.graphs.cfg.CFGInterface;
import edu.usc.sql.graphs.cfg.SootCFG;
import soot.Body;
import soot.BodyTransformer;
import soot.Local;
import soot.PackManager;
import soot.PatchingChain;
import soot.RefType;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Transform;
import soot.Unit;
import soot.jimple.IdentityStmt;
import soot.jimple.Jimple;
import soot.jimple.ReturnStmt;
import soot.jimple.ReturnVoidStmt;
import soot.jimple.Stmt;
import soot.jimple.ThrowStmt;
import soot.options.Options;

public class InstrumentGetWritable {

	public static void main(String[] args) {
		
		//prefer Android APK files// -src-prec apk
		Options.v().set_src_prec(Options.src_prec_apk);
		
		Options.v().set_output_format(Options.output_format_dex);

		Options.v().set_allow_phantom_refs(true);
		
		Scene.v().addBasicClass("java.io.PrintStream",SootClass.SIGNATURES);
        Scene.v().addBasicClass("java.lang.System",SootClass.SIGNATURES);
        Scene.v().addBasicClass("android.database.sqlite.SQLiteDatabase",SootClass.SIGNATURES);
        Scene.v().addBasicClass("java.lang.StringBuilder",SootClass.SIGNATURES);
        
        final boolean inTransaction = false;
        
        //App132
   		final String targetMethod = "clearFavorites";
        
        final int startID = 1,endID = 9;
        final int cloneID = -100;
        final boolean isEncapsulated = false;
        final String listenerSig = "<ch.sbb.mobile.android.lib.favorites.FavoritesFragment$2: void onClick(android.content.DialogInterface,int)>";
        
        
        PackManager.v().getPack("jtp").add(new Transform("jtp.myInstrumenter", new BodyTransformer() {

        	
			@Override
			protected void internalTransform(final Body b, String phaseName, @SuppressWarnings("rawtypes") Map options) {
				final PatchingChain<Unit> units = b.getUnits();
				

			
			  //add timestamp at the entry and exit of the method

				  	if(b.getMethod().getSignature().equals(listenerSig))
				  	{
						boolean inserted =false;
						for(Iterator<Unit> iter = units.snapshotIterator(); iter.hasNext();) {
							final Stmt s = (Stmt) iter.next();
							//first non-IdentityStmt
							if((!(s instanceof IdentityStmt))&&!inserted)
							{
								AndroidInstrument.addTimeStampAfter(b, units, s);
								inserted = true;
								
							}
							if(s instanceof ReturnStmt || s instanceof ReturnVoidStmt || s instanceof ThrowStmt)
							{
								AndroidInstrument.addTimeStampBefore(b, units, s);
							}
							
						}
				  	}
				

				
			
				
				
				//if(b.getMethod().getSignature().contains("com.abto.manicure.dao.DatabaseHelper"))
				
				if(!b.getMethod().getSignature().contains(targetMethod))
						return;
				System.out.println(b.getMethod().getSignature());
				
				Unit start=null,end=null,clone=null;
				
				CFGInterface cfg = new SootCFG(b.getMethod().getSignature(),b.getMethod());
				System.out.println(cfg.toDot());
				for(NodeInterface n:cfg.getAllNodes())
				{
					System.out.println(n.getOffset().toString()+" "+((Node)n).getActualNode());
					if(n.getOffset()==startID)
						start = (Unit) ((Node)n).getActualNode();
					else if(n.getOffset()== endID)
						end = (Unit) ((Node)n).getActualNode();
					else if(n.getOffset()==cloneID)
						clone = (Unit) ((Unit) ((Node)n).getActualNode()).clone();
					
				}
				

				
				if(inTransaction)
				{
					Local db = null;
					List<Unit> dbObjCreation = new ArrayList<>();
					
					
					//$r0 := @this: className
					Local r0 = (Local) units.getFirst().getDefBoxes().get(0).getValue();
					if(r0 != null)
					db = createDbObjFromGetWri(b,units, start,r0,"ch.sbb.mobile.android.lib.favorites.FavoritesJourneyDatabase","_helper", dbObjCreation);
					
			
					
					
					if(start==null||end==null||db==null)
					{
						System.out.println("NOT FOUND!!!");
					}
					else
					{
						
					String className;
					if(isEncapsulated)
						className = "com.dbseperate.DBSeperateClass";
					else
						className = "android.database.sqlite.SQLiteDatabase";
						
					//insert begin				
					SootMethod toCallBegin = Scene.v().getSootClass(className).getMethod("void beginTransaction()");
					units.insertAfter(Jimple.v().newInvokeStmt( Jimple.v().newVirtualInvokeExpr(db,toCallBegin.makeRef())), start);
										
					//insert db object creation
					units.insertAfter(dbObjCreation, start);
					
					if(clone!=null)
					units.insertAfter((Unit) clone.clone(), start);
	
					
				
					//insert set
					if(clone!=null)
					units.insertBefore((Unit) clone.clone(), end);
					
					SootMethod toCallSuc = Scene.v().getSootClass(className).getMethod("void setTransactionSuccessful()");
					units.insertBefore(Jimple.v().newInvokeStmt( Jimple.v().newVirtualInvokeExpr(db,toCallSuc.makeRef())), end);
					
					//insert end
					if(clone!=null)
					units.insertBefore((Unit) clone.clone(), end);
					
					SootMethod toCallEnd = Scene.v().getSootClass(className).getMethod("void endTransaction()");
					units.insertBefore(Jimple.v().newInvokeStmt( Jimple.v().newVirtualInvokeExpr(db,toCallEnd.makeRef())), end);
					}
					
				}
				
				AndroidInstrument.addTimeStampAroundLoop(b, units, start,end);
				//insert time stamp
				//addAgentTimeStamp(b, units,start, end);
				b.validate();
				
				
				
				CFGInterface cfgNew = new SootCFG(b.getMethod().getSignature(),b.getMethod());
				System.out.println(cfgNew.toDot());
				for(NodeInterface n:cfgNew.getAllNodes())
				{
					
					System.out.println(n.getOffset().toString()+" "+((Node)n).getActualNode());
				}
			}


		}));
		
		soot.Main.main(args);
	}

	public static Local createDbObjFromGetWri(Body body,PatchingChain<Unit> units,Unit start,Local r0, String className,String fieldName,List<Unit> list) {

		
		//DbHelper is an inner class
		
		Local db = null;
		SootClass sc=Scene.v().loadClassAndSupport(className);
		
		
		//$db0 := @this: className
        //$db0 = r0
        
        // $db1 = $db0.<field signature>
        Local $db1 = Jimple.v().newLocal("$db1", sc.getFieldByName(fieldName).getType());
        body.getLocals().add($db1);
        list.add(Jimple.v().newAssignStmt($db1, Jimple.v().newInstanceFieldRef(r0, sc.getFieldByName(fieldName).makeRef())));

	
        //2 db = virtualinvoke $db1.<ch.sbb.mobile.android.lib.favorites.FavoritesJourneyDatabase$DBHelper: android.database.sqlite.SQLiteDatabase getWritableDatabase()>()
        db = Jimple.v().newLocal("db", RefType.v("android.database.sqlite.SQLiteDatabase"));
        body.getLocals().add(db);
        
        
      
        SootMethod getWri = Scene.v().getSootClass(className+"$DBHelper").getSuperclass().getMethod("android.database.sqlite.SQLiteDatabase getWritableDatabase()");
        getWri.setDeclaringClass(Scene.v().getSootClass(className+"$DBHelper"));
        list.add(Jimple.v().newAssignStmt(db, Jimple.v().newVirtualInvokeExpr($db1,getWri.makeRef())));
        
        
		
/*		SootMethod sm = Scene.v().loadClassAndSupport(className).getMethod("android.database.sqlite.SQLiteDatabase getWriteableDatabase()");
		for(Unit u : sm.retrieveActiveBody().getUnits())
		{
			if(u.toString().contains("getWritableDatabase"))
			{
				VirtualInvokeExpr s =  (VirtualInvokeExpr) u.getUseBoxes().get(0).getValue();
				System.out.println(s.getMethod().makeRef());
				 list.add(Jimple.v().newAssignStmt(db, Jimple.v().newVirtualInvokeExpr($db1,s.getMethodRef())));
			}


		}*/
        
        System.out.println(list);
        
		//System.out.println(sc.getFieldByName("abc"));
		


		body.validate();
		return db;
	}
	
}
