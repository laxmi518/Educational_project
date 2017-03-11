package NameChecker;

import AST.*;
import Utilities.Error;
import Utilities.SymbolTable;
import Utilities.Visitor;
import Utilities.Rewrite;

import java.util.*;
import Parser.*;;

public class NameChecker extends Visitor {

	/* getMethod traverses the class hierarchy to look for a method of
       name 'methodName'. We return #t if we find any method with the
       correct name. Since we don't have types yet we cannot look at
       the signature of the method, so all we do for now is look if
       any method is defined. The search is as follows:

       1) look in the current class
       2) look in its super class
       3) look in all the interfaces

       Remember that the an entry in the methodTable is a symbol table
       it self. It holds all entries of the same name, but with
       different signatures. (See Documentation)
	 */    
	public static SymbolTable getMethod(String methodName, ClassDecl cd) {

		// YOUR CODE HERE

		SymbolTable find = (SymbolTable)cd.methodTable.get(methodName);
		if (find != null)
			return find;
		//see in  super class if exist super class
		if (cd.superClass()!= null)
		{

			find = getMethod(methodName, cd.superClass().myDecl);
			if (find != null)
				return find;
		}

		if(cd.interfaces() != null)
		{

	
           int i=0;
		   while(i<cd.interfaces().nchildren)
			{

				find = getMethod(methodName, ((ClassType)cd.interfaces().children[i]).myDecl);
				if (find != null)
					return find;
				i++;
			}
		}


		return null;


	}


	/* Same as getMethod just for fields instead 
	 */
	public static AST getField(String fieldName, ClassDecl cd) {

		// YOUR CODE HERE

		AST find = (AST)cd.fieldTable.get(fieldName);
		if (find != null)
			return find;
		//see in  super class if exist super class
		if (cd.superClass()!= null)
		{

			find =  getField(fieldName, cd.superClass().myDecl);
			if (find != null)
				return find;
		}

		if(cd.interfaces() != null)
		{


			for (int i=0; i<cd.interfaces().nchildren; i++)
			{
				find =   getField(fieldName, ((ClassType)cd.interfaces().children[i]).myDecl);
				if (find != null)
					return find;
			}
		}

		return null;
	}

	/* Traverses all the classes and interfaces and builds a sequence
	   of the methods and constructors of the class hierarchy.
	 */
    public void getClassHierarchyMethods(ClassDecl cd, Sequence lst, Hashtable<String, Object> seenClasses) {
		// YOUR CODE HERE
        AST test = lst;
		AST result = (AST)(seenClasses.get(cd.name()));
		if (result !=null)
			return;

        int i=0;
		while(i<cd.body().nchildren)
		{
			if(cd.body().children[i] instanceof MethodDecl) {
				lst.append(cd.body().children[i]);
				seenClasses.put(cd.name(),cd);
			}
			i++;


		}

		if(cd.superClass()!= null)
			getClassHierarchyMethods(cd.superClass().myDecl,lst, seenClasses);

		if(cd.interfaces()!=null)
		{
			for (int j=0;j<cd.interfaces().nchildren;j++)
				getClassHierarchyMethods(((ClassType)cd.interfaces().children[j]).myDecl,lst,seenClasses);
		}

	}



	/* For each method (not constructors) in this list, check that if
       it exists more than once with the same parameter signature that
       they all return something of the same type. 
	*/
    public void checkReturnTypesOfIdenticalMethods(Sequence lst) {
////	// YOUR CODE HERE
		AST test = lst;
		// int i=0;
		for (int i=0;i<lst.nchildren;i++) {

			for (int j = 0; j < lst.nchildren; j++) {
				if(i!=j){
			    MethodDecl test1 = (MethodDecl) lst.children[i];		
				MethodDecl first = (MethodDecl) lst.children[i];
				MethodDecl second = (MethodDecl) lst.children[j];
				Type a = first.returnType();
				Type b = second.returnType();
				if(test1 == second);
				if (((first.getname()).equals(second.getname())))
					if((first.paramSignature()).equals(second.paramSignature()))

						if(!(first.returnType().signature()).equals(second.returnType().signature())) {
					Error.error("Method '" + second.getname() + "' has been declared with two different return types:", false);
					Error.error(first, Type.parseSignature(first.returnType().signature()) + " " + first.getname() + "(" + Type.parseSignature(first.paramSignature()) + " )", false);
					Error.error(second, Type.parseSignature(second.returnType().signature()) + " " + second.getname() + "(" + Type.parseSignature(second.paramSignature()) + " )",true);


				}


			}
		}
		}
//
    }
    
    /* Divides all the methods into two sequences: one for all the
       abstract ones, and one for all the concrete ones and check that
       all the methods that were delcared abstract were indeed
       implemented somewhere in the class hierarchy.  */
    

    // sup is the class in which the abstract method lives,
    // sub is the class in which the concrete method lives.
    public static boolean isSuper(ClassDecl sup, ClassDecl sub) {
	if (sup.name().equals(sub.name()))
	    return true;
		
	if (sub.superClass() != null && isSuper(sup, sub.superClass().myDecl))
	    return true;

	for (int i=0; i<sub.interfaces().nchildren; i++) 
	    if (isSuper(sup, ((ClassType)sub.interfaces().children[i]).myDecl))
		return true;

	return false;
    }


       public void checkImplementationOfAbstractClasses(ClassDecl cd, Sequence methods) {



		   // for (int i=0; i<methods.nchildren; i++) {
//			   MethodDecl abs_check = (MethodDecl) methods.children[i];
//			   if (abs_check.block() == null) {
//
//				   Error.error(abs_check, "Class '" + cd.name() + "' should be declared abstract; it does not implement\n" +
//						   Type.parseSignature(abs_check.returnType().signature()) +
//						   " " + abs_check.getname() + "(" + Type.parseSignature(abs_check.paramSignature()) + " )");
//			   }
//		   }

       	for (int i=0; i<methods.nchildren; i++) {
       		boolean check_condn = false;
			   MethodDecl abs_check = (MethodDecl) methods.children[i];
			   if (abs_check.block() == null)
			   {
                  for (int j=0; j<methods.nchildren; j++) {


	                  	if (i!=j)
	                  	{
	                  		MethodDecl concrete_test = (MethodDecl)methods.children[j];
	                  		if (concrete_test.block() != null)
	                  			 check_condn = (abs_check.getname().equals(concrete_test.getname()) && abs_check.paramSignature().equals(concrete_test.paramSignature())) ;
			 

	                  	}
	                

                        if (check_condn)
                    	break;


                  	}



                  	if (!check_condn)
                  	{
                  		Error.error(abs_check, "Class '" + cd.name() + "' should be declared abstract; it does not implement\n" +
						   Type.parseSignature(abs_check.returnType().signature()) +
						   " " + abs_check.getname() + "(" + Type.parseSignature(abs_check.paramSignature()) + " )");
                  	}

			   }
			}


  
       }
    
	public  void checkUniqueFields(Sequence fields, ClassDecl cd) {
		// YOUR CODE HERE

		for (int j = 0; j < cd.body().nchildren; j++) {
			if (cd.body().children[j] instanceof FieldDecl) {
				FieldDecl fd = (FieldDecl) cd.body().children[j];

				fields.append(fd);
			}
		}
		if (cd.superClass() != null) {
			AST cond =  cd.superClass().myDecl.body();
			for (int i = 0; i <cond.nchildren; i++)
				if (cond.children[i] instanceof FieldDecl)
					for (int k = 0; k < fields.nchildren; k++)
						if (((FieldDecl) fields.children[k]).name().equals(((FieldDecl) (cond.children[i])).name()))
							Error.error(( fields.children[i]),"Field '" +((FieldDecl)(cond.children[i])).name() +"' already defined.");
		}
	}






	// %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
	// %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
	// %%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%

	/**
	 * Points to the current scope.
	 */
	private SymbolTable currentScope;
	/**
	 * The class table<br>
	 * This is set in the constructor.
	 */
	private SymbolTable classTable;
	/**
	 * The current class in which we are working.<br>
	 * This is set in visitClassDecl().
	 */
	private ClassDecl   currentClass;
	
	public NameChecker(SymbolTable classTable, boolean debug) { 
		this.classTable = classTable; 
		this.debug = debug;
	}

	/** BLOCK */
	public Object visitBlock(Block bl) {
		println("Block:\t\t Creating new scope for Block.");
		currentScope = currentScope.newScope();
		super.visitBlock(bl);
		currentScope = currentScope.closeScope();
		return null;

	}


	/** CLASS DECLARATION */
	public Object visitClassDecl(ClassDecl cd) {
		println("ClassDecl:\t Visiting class '"+cd.name()+"'");

		// If we use the field table here as the top scope, then we do not
		// need to look in the field table when we resolve NameExpr. Note,
		// at this time we have not yet rewritten NameExprs which are really
		// FieldRefs with a null target as we have not resolved anything yet.
		currentScope = cd.fieldTable;
		currentClass = cd;

		Hashtable<String, Object> seenClasses = new Hashtable<String, Object>();

		// Check that the superclass is a class.
		if (cd.superClass() != null) 
			if (cd.superClass().myDecl.isInterface())
				Error.error(cd,"Class '" + cd.name() + "' cannot inherit from interface '" +
						cd.superClass().myDecl.name() + "'.");



		if (cd.superClass() != null) {
			if (cd.name().equals(cd.superClass().typeName()))
				Error.error(cd, "Class '" + cd.name() + "' cannot extend itself.");
			// If a superclass has a private default constructor, the 
			// class cannot be extended.
			ClassDecl superClass = (ClassDecl)classTable.get(cd.superClass().typeName());
			SymbolTable st = (SymbolTable)superClass.methodTable.get("<init>");
			ConstructorDecl ccd = (ConstructorDecl)st.get("");
			if (ccd != null && ccd.getModifiers().isPrivate())
			    Error.error(cd, "Class '" + superClass.className().getname() + "' cannot be extended because it has a private default constructor.");
		}
		
		// Visit the children
		super.visitClassDecl(cd);
			
		currentScope = null;

		// Check that the interfaces implemented are interfaces.
		for (int i=0; i<cd.interfaces().nchildren; i++) {
			ClassType ct = (ClassType)cd.interfaces().children[i];
			if (ct.myDecl.isClass())
				Error.error(cd,"Class '" + cd.name() + "' cannot implement class '" + ct.name() + "'.");
		}

		Sequence methods = new Sequence();
		
		getClassHierarchyMethods(cd, methods, seenClasses);

		checkReturnTypesOfIdenticalMethods(methods);

		// If the class is not abstract and not an interface it must implement all
		// the abstract functions of its superclass(es) and its interfaces.
		if (!cd.isInterface() && !cd.modifiers.isAbstract()) {
			checkImplementationOfAbstractClasses(cd, methods);
			// checkImplementationOfAbstractClasses(cd, new Sequence());
		}
		// All field names can only be used once in a class hierarchy
		checkUniqueFields(new Sequence(), cd);

		cd.allMethods = methods; // now contains only MethodDecls

		// Fill cd.constructors.
		SymbolTable st = (SymbolTable)cd.methodTable.get("<init>");
		ConstructorDecl cod;
		if (st != null) {
			for (Enumeration<Object> e = st.entries.elements() ; 
					e.hasMoreElements(); ) {
				cod = (ConstructorDecl)e.nextElement();
				cd.constructors.append(cod);
			}
		}

		// needed for rewriting the tree to replace field references
		// represented by NameExpr.
		println("ClassDecl:\t Performing tree Rewrite on " + cd.name());
		new Rewrite().go(cd, cd);

		return null;
	}
	// YOUR CODE HERE
	/** FORSTAT */
	public Object visitForStat(ForStat fs) {
		println("ForStat:\t Creating new scope for For Statement.");
		currentScope = currentScope.newScope();
		super.visitForStat(fs);
		currentScope = currentScope.closeScope();
		return null;
	}

	// for visitMethodDecl


	public Object visitMethodDecl(MethodDecl mtd) {
		println("MethodDecl:\t Creating new scope for Method '" + mtd.name() +"' with signature '" + mtd.paramSignature()  +"' (Parameters and Locals)." );
		currentScope = currentScope.newScope();
//		mtd.params().visit(this);
//		mtd.block().visit(this);
//		mtd.name().visit(this);
//		mtd.modifiers().visit(this);
//		mtd.name().visit(this);
		mtd.visitChildren(this);

//		super.visitMethodDecl(mtd);
		currentScope = currentScope.closeScope();
		return null;
	}

	//for constructor decl


	public Object visitConstructorDecl(ConstructorDecl cdl)
	{
		println("ConstructorDecl: Creating new scope for constructor <init> with signature '" + cdl.paramSignature()+ "' (Parameters and Locals).");
		currentScope = currentScope.newScope();

		cdl.params().visit(this);
		currentScope = currentScope.newScope();
		if (cdl.cinvocation() != null)
		{
			cdl.cinvocation().visit(this);
		}
		cdl.body().visit(this);
		currentScope = currentScope.closeScope();
		currentScope = currentScope.closeScope();
		return null;
	}

	/** visitLocalDecl */

	public Object visitLocalDecl(LocalDecl ldl)
	{
		println("LocalDecl:\t Declaring local symbol '" +ldl.name()+ "'.");
		ldl.visitChildren(this);
		currentScope.put(ldl.name(), ldl);
		return null;

	}

	public Object visitParamDecl(ParamDecl pdl)
	{
		println("ParamDecl:\t Declaring local symbol '" +pdl.name()+ "'.");


		/*** insert parameter in to current scope ***/
		pdl.visitChildren(this);
		currentScope.put(pdl.name(), pdl);


		return null;

	}

	public Object visitClassType(ClassType ct) {
		ClassDecl cd = (ClassDecl) classTable.get(ct.typeName());

		println("ClassType:\t Looking up class/interface '" + ct.typeName() + "' in class table.");

		if (cd == null)
			Error.error(ct,"Class '" + ct.typeName() + "' not found.");
		ct.myDecl = cd;
		return null;
	}

	public Object visitNameExpr(NameExpr ne) {

		println("NameExpr:\t Looking up symbol '" + ne.name().getname() + "'.");


     	/*search the current scope*/
		AST find = (AST) currentScope.get(ne.name().getname());

//
		if (find == null) {


     		/* search fieldtable of current class */
			find = (AST) currentClass.fieldTable.get(ne.name().getname());


			if (find == null) {

//				println("inside second   cfield null");
           	/*search in global class table */

				find = (AST) classTable.get(ne.name().getname());

				if (find == null) {
					find = getField(ne.name().getname(),currentClass);
					if (find!= null)
					{
						if (find instanceof FieldDecl)
							println(" Found Field");
						return null;
					}
					Error.error(ne,"Symbol '" + ne.name().getname() + "' not declared.",true);
					return null;
				}

				else
				{
//					println("everything here");

					if (find instanceof ClassDecl)
						println(" Found Class");
					return null;




				}


			}

			else{



				if (find instanceof FieldDecl)
					println(" Found Field");
				return null;

			}
		}

		else {
			if (find instanceof LocalDecl)
				println(" Found Local Variable");
			else if (find instanceof ParamDecl)
				println(" Found Parameter");
			else if (find instanceof FieldDecl)
				println(" Found Field");
			else if (find instanceof ClassDecl)
				println(" Found Class1");
		}


			return null;

	}

	public Object visitInvocation(Invocation in) {

		Expression target = in.target();
		if (target == null | target instanceof This) {
			println("Invocation:\t Looking up method '" + in.methodName() + "'.");
			SymbolTable find = (SymbolTable)getMethod(in.methodName().toString(),currentClass);
			if(find == null)
			{
				Error.error(in,"Method '" +in.methodName().toString()  + "' not found.",true);
			}
			in.visitChildren(this);
		} else {
			println("Invocation:\t Target too complicated for now!");
			in.visitChildren(this);
		}
		return null;
	}

	public Object visitFieldRef(FieldRef fr) {

		Expression target = fr.target();
		if (target == null | target instanceof This) {
			println("FieldRef:\t Looking up field '" + fr.fieldName() + "'.");
			 AST find = (AST) getField(fr.fieldName().toString(),currentClass);
			 if(find == null)
			 {
				 Error.error(fr,"Field '" + fr.fieldName().getname() + "' not found.",true);
			 }
			fr.visitChildren(this);

		} else {
			println("FieldRef:\t Target too complicated for now!");
			fr.visitChildren(this);
		}
		return null;
	}



	/** THIS */
	public Object visitThis(This th) {
		ClassType ct = new ClassType(new Name(new Token(16,currentClass.name(),0,0,0)));
		ct.myDecl = currentClass;
		th.type = ct;
		return null;
	}

}

