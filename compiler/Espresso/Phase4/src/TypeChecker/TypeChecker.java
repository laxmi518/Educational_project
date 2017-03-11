package TypeChecker;

import AST.*;
import Utilities.Error;
import Utilities.SymbolTable;
import Utilities.Visitor;
import java.util.*;
import java.math.*;

public class TypeChecker extends Visitor {

    public static ClassBodyDecl findMethod(Sequence candidateMethods, String name, Sequence actualParams, 
					   boolean lookingForMethods) {
	
	if (lookingForMethods) {
	    println("+------------- findMethod (Method) ------------");
	    println("| Looking for method: " + name);
	} else {
	    println("+---------- findMethod (Constructor) ----------");
	    println("| Looking for constructor: " + name);
	}
	println("| With parameters:");
	for (int i=0; i<actualParams.nchildren; i++){
	    println("|   " + i + ". " + ((actualParams.children[i] instanceof ParamDecl)?(((ParamDecl)actualParams.children[i]).type()):((Expression)actualParams.children[i]).type));
	}
	// The number of actual parameters in the invocation.
	int count = 0;
	
	// Make an array big enough to hold all the methods if needed
	ClassBodyDecl cds[] = new ClassBodyDecl[candidateMethods.nchildren];
	
	// Initialize the array to point to null
	for(int i=0;i<candidateMethods.nchildren;i++) 
	    cds[i] = null;
	
	Sequence args = actualParams;
	Sequence params;
	
	// Insert all the methods from the symbol table that:
	// 1.) has the right number of parameters
	// 2.) each formal parameter can be assigned its corresponding
	//     actual parameter.
	if (lookingForMethods)
	    println("| Finding methods with the right number of parameters and types");
	else
	    println("| Finding constructors with the right number of parameters and types");
	for (int cnt=0; cnt<candidateMethods.nchildren; cnt++) {
	    ClassBodyDecl cbd = (ClassBodyDecl)candidateMethods.children[cnt];
	    
	    // if the method doesn't have the right name, move on!
	    if (!(cbd.getname().equals(name)))
		continue;
	    
	    // Fill params with the formal parameters.
	    if (cbd instanceof ConstructorDecl) 
		params = ((ConstructorDecl)cbd).params();
	    else if (cbd instanceof MethodDecl)
		params = ((MethodDecl)cbd).params();
	    else
		// we have a static initializer, don't do anything - just skip it.
		continue;
	    
	    print("|   " + name + "(");
	    if (cbd instanceof ConstructorDecl) 
		print(Type.parseSignature(((ConstructorDecl)cbd).paramSignature()));
	    else 
		print(Type.parseSignature(((MethodDecl)cbd).paramSignature()));
	    print(" )  ");
	    
	    if (args.nchildren == params.nchildren) {
		// The have the same number of parameters
		// now check that the formal parameters are
		// assignmentcompatible with respect to the 
		// types of the actual parameters.
		// OBS this assumes the type field of the actual
		// parameters has been set (in Expression.java),
		// so make sure to call visit on the parameters first.
		boolean candidate = true;
		
		for (int i=0;i<args.nchildren; i++) {
		    candidate = candidate &&
			Type.assignmentCompatible(((ParamDecl)params.children[i]).type(),
						  (args.children[i] instanceof Expression) ?
						  ((Expression)args.children[i]).type :
						  ((ParamDecl)args.children[i]).type());
		    
		    if (!candidate) {
			println(" discarded");
			break;
		    }
		}
		if (candidate) {
		    println(" kept");
		    cds[count++] = cbd;
		}
	    }
	    else {
		println(" discarded");
	    }
	    
	}
	// now count == the number of candidates, and cds is the array with them.
	// if there is only one just return it!
	println("| " + count + " candidate(s) were found:");
	for ( int i=0;i<count;i++) {
	    ClassBodyDecl cbd = cds[i];
	    print("|   " + name + "(");
	    if (cbd instanceof ConstructorDecl) 
		print(Type.parseSignature(((ConstructorDecl)cbd).paramSignature()));
	    else 
		print(Type.parseSignature(((MethodDecl)cbd).paramSignature()));
	    println(" )");
	}
	
	if (count == 0) {
	    println("| No candidates were found.");
	    println("+------------- End of findMethod --------------");
	    return null;
	}
	
	if (count == 1) {
	    println("| Only one candidate - thats the one we will call then ;-)");
	    println("+------------- End of findMethod --------------");
	    return cds[0];
	}
	println("| Oh no, more than one candidate, now we must eliminate some >:-}");
	// there were more than one candidate.
	ClassBodyDecl x,y;
	int noCandidates = count;
	
	for (int i=0; i<count; i++) {
	    // take out a candidate
	    x = cds[i];
	    
	    if (x == null)
		continue;		    
	    cds[i] = null; // this way we won't find x in the next loop;
	    
	    // compare to all other candidates y. If any of these
	    // are less specialised, i.e. all types of x are 
	    // assignment compatible with those of y, y can be removed.
	    for (int j=0; j<count; j++) {
		y = cds[j];
		if (y == null) 
		    continue;
		
		boolean candidate = true;
		
		// Grab the parameters out of x and y
		Sequence xParams, yParams;
		if (x instanceof ConstructorDecl) {
		    xParams = ((ConstructorDecl)x).params();
		    yParams = ((ConstructorDecl)y).params();
		} else {
		    xParams = ((MethodDecl)x).params();
		    yParams = ((MethodDecl)y).params();
		}
		
		// now check is y[k] <: x[k] for all k. If it does remove y.
		// i.e. check if y[k] is a superclass of x[k] for all k.
		for (int k=0; k<xParams.nchildren; k++) {
		    candidate = candidate &&
			Type.assignmentCompatible(((ParamDecl)yParams.children[k]).type(),
						  ((ParamDecl)xParams.children[k]).type());
		    
		    if (!candidate)
			break;
		}
		if (candidate) {
		    // x is more specialized than y, so throw y away.
		    print("|   " + name + "(");
		    if (y instanceof ConstructorDecl) 
			print(Type.parseSignature(((ConstructorDecl)y).paramSignature()));
		    else 
			print(Type.parseSignature(((MethodDecl)y).paramSignature()));
		    print(" ) is less specialized than " + name + "(");
		    if (x instanceof ConstructorDecl) 
			print(Type.parseSignature(((ConstructorDecl)x).paramSignature()));
		    else 
			print(Type.parseSignature(((MethodDecl)x).paramSignature()));
		    println(" ) and is thus thrown away!");
		    
		    cds[j] = null;
		    noCandidates--;
		}
	    }
	    // now put x back in to cds
	    cds[i] = x;
	}
	if (noCandidates != 1) {
	    // illegal function call
	    println("| There is more than one candidate left!");
	    println("+------------- End of findMethod --------------");
	    return null;
	}
	
	// just find it and return it.
	println("| We were left with exactly one candidate to call!");
	println("+------------- End of findMethod --------------");
	for (int i=0; i<count; i++)
	    if (cds[i] != null)
		return cds[i];
	
	return null;
    }
    
    public void listCandidates(ClassDecl cd, Sequence candidateMethods, String name) {

	for (int cnt=0; cnt<candidateMethods.nchildren; cnt++) {
	    ClassBodyDecl cbd = (ClassBodyDecl)(candidateMethods.children[cnt]);

	    if (cbd.getname().equals(name)) {
		if (cbd instanceof MethodDecl)
		    System.out.println("  " + name + "(" + Type.parseSignature(((MethodDecl)cbd).paramSignature()) + " )");
		else
		    System.out.println("  " + cd.name() + "(" + Type.parseSignature(((ConstructorDecl)cbd).paramSignature()) + " )");
	    }
	}
    }

    private SymbolTable   classTable;
    private ClassDecl     currentClass;
    private ClassBodyDecl currentContext;
    private FieldDecl currentFieldDecl; // keep track of the currentFieldDecl 
    private boolean inFieldInit;        // 
	
    public TypeChecker(SymbolTable classTable, boolean debug) { 
	this.classTable = classTable; 
	this.debug = debug;
    }

    /** ArrayAccessExpr */
    public Object visitArrayAccessExpr(ArrayAccessExpr ae) {
	println(ae.line + ": Visiting ArrayAccessExpr");
//		super.visitArrayAccessExpr(ae);
        ArrayType assign_type = (ArrayType)ae.target().visit(this);
		if (assign_type.getDepth() >1) {
			assign_type = new ArrayType(assign_type.baseType(), 1);
			ae.type = assign_type;
		}
		else
		ae.type = assign_type.baseType();

		println(ae.line + ": ArrayAccessExpr has type " + ae.type);
		ae.index().visit(this);
	// YOUR CODE HERE
	return ae.type;
    }

    /** ArrayType */
    public Object visitArrayType(ArrayType at) {
	println(at.line + ": Visiting an ArrayType");
	println(at.line + ": ArrayType type is " + at);
	return at;
    }

//    /** NewArray */
    public Object visitNewArray(NewArray ne) {
	println(ne.line + ": Visiting a NewArray " + ne.dimsExpr().nchildren + " " + ne.dims().nchildren);
		if(ne.dimsExpr().nchildren!=0) {
			ne.baseType().visit(this);
			ne.dimsExpr().visit(this);
			ne.type = new ArrayType(ne.baseType(), ne.dimsExpr().nchildren);
		}
		if(ne.dims().nchildren!=0) {
			ne.baseType().visit(this);
			ne.dimsExpr().visit(this);
			ne.type = new ArrayType(ne.baseType(), ne.dims().nchildren);
			if (ne.init() != null)  {
			if (!arrayAssignmentCompatible(ne.type, ne.init()))
				Error.error(ne, "Array Initializer is not compatible with " + ne.type.typeName());

		}

//			ne.init().visit(this);

		}
//		super.visitNewArray(ne);
//		ne.type = new ArrayType (ne.baseType(),ne.dimsExpr().nchildren);
	// YOUR CODE HERE
	println(ne.line + ": NewArray type is " + ne.type);
	return ne.type;
    }




    // TODO: Espresso doesn't allow 'int[][] a = new int[]{ f(), f() }} where f returns an array

    public boolean arrayAssignmentCompatible(Type t, Expression e) {
	if (t instanceof ArrayType && (e instanceof ArrayLiteral)) {
	    ArrayType at = (ArrayType)t;
	    e.type = at; //  we don't know that this is the type - but if we make it through it will be!
	    ArrayLiteral al = (ArrayLiteral)e;
	    
	    // t is an array type i.e. XXXXXX[ ]
	    // e is an array literal, i.e., { }
	    if (al.elements().nchildren == 0) // the array literal is { }
		return true;   // any array variable can hold an empty array
	    // Now check that XXXXXX can hold value of the elements of al
	    // we have to make a new type: either the base type if |dims| = 1
	    boolean b = true;
	    for (int i=0; i<al.elements().nchildren; i++) {
		if (at.getDepth() == 1) 
		    b = b && arrayAssignmentCompatible(at.baseType(), (Expression)al.elements().children[i]);
		else { 
		    ArrayType at1 = new ArrayType(at.baseType(), at.getDepth()-1);
		    b = b  && arrayAssignmentCompatible(at1, (Expression)al.elements().children[i]);
		}
	    }
	    return b;
	} else if (t instanceof ArrayType && !(e instanceof ArrayLiteral)) {
	    Type t1 = (Type)e.visit(this);
	    if (t1 instanceof ArrayType)
		if (!Type.assignmentCompatible(t,t1))
		    Error.error("Incompatible type in array assignment");
		else
		    return true;
	    Error.error(t, "Error: cannot assign non array to array type " + t.typeName());	    
	}
	else if (!(t instanceof ArrayType) && (e instanceof ArrayLiteral)) {
	    Error.error(t, "Error: cannot assign value " + ((ArrayLiteral)e).toString() + " to type " + t.typeName());
	}
	return Type.assignmentCompatible(t,(Type)e.visit(this));
    }
    
    public Object visitArrayLiteral(ArrayLiteral al) {
	// Espresso does not allow array literals without the 'new <type>' part.
	Error.error(al, "Array literal must be preceeded by a 'new <type>'");
	return null;
    }
    
    /** ASSIGNMENT */
    public Object visitAssignment(Assignment as) {
	println(as.line + ": Visiting an assignment");

	Type vType = (Type) as.left().visit(this);
	Type eType = (Type) as.right().visit(this);

	/** Note: as.left() should be of NameExpr or FieldRef class! */

	if (!vType.assignable())          
	    Error.error(as,"Left hand side of assignment not assignable.");

	switch (as.op().kind) {
	case AssignmentOp.EQ : {
	    // Check if the right hand side is a constant.	    
	    // if we don't do this the following is illegal: byte b; b = 4; because 4 is an it!
	    if (as.right().isConstant()) {
		if (vType.isShortType() && Literal.isShortValue(((BigDecimal)as.right().constantValue()).longValue()))
		    break;
		if (vType.isByteType() && Literal.isByteValue(((BigDecimal)as.right().constantValue()).longValue()))
		    break;		
		if (vType.isCharType() && Literal.isCharValue(((BigDecimal)as.right().constantValue()).longValue()))
		    break;
	    }
		     
	    if (!Type.assignmentCompatible(vType,eType))
		Error.error(as,"Cannot assign value of type " + eType.typeName() + " to variable of type " + vType.typeName() + ".");
	    break;
	}
		case AssignmentOp.PLUSEQ : {
			if (!Type.assignmentCompatible(vType,eType)) {
				Error.error(as, "Cannot assign value of type " + eType.typeName() + " to variable of type " + vType.typeName() + ".");
				break;
			}

			if(!eType.isNumericType()) {


				Error.error(as, "Right hand side operand of operator '" + as.op() + "' must be of numeric type.");
				break;
			}



		}
		case AssignmentOp.RSHIFTEQ:
		{
			if(!vType.isIntegerType()) {
				Error.error(as, "Left hand side operand of operator '" + as.op() + "' must be of integral type.");
				break;
			}
			if (!eType.isIntegerType()) {
				Error.error(as, "Right hand side operand of operator '" + as.op() + "' must be of integer type.");
				break;
			}

		}
		case AssignmentOp.ANDEQ:
			if(!(vType.isIntegerType() &&  eType.isIntegerType()))
			if(!(vType.isBooleanType() &&  eType.isBooleanType()))
			{
				Error.error(as, "Both right and left hand side operands of operator '" + as.op() + "' must be either of boolean or integer type.");
				break;
			}




	    // YOUR CODE HERE

	}
	as.type = vType;
	println(as.line + ": Assignment has type: " + as.type);

	return vType;
    }

    /** BINARY EXPRESSION */
    public Object visitBinaryExpr(BinaryExpr be) {
	println(be.line + ": Visiting a Binary Expression");
		Type vType = (Type) be.left().visit(this);
		Type eType = (Type) be.right().visit(this);
		int oper = be.op().kind;
		Type test1 = be.left().type;
		Type test2 = be.right().type;
//   for +,-,*,/
		if(oper == BinOp.PLUS) {
			if (vType.isNumericType() && eType.isNumericType())
//
			be.type = PrimitiveType.ceilingType((PrimitiveType) vType, (PrimitiveType) eType);
			else
				Error.error(be,"Operator '"+be.op().operator()+"' requires operands of numeric type.");

		}

		if(oper == BinOp.MINUS) {
			if (vType.isNumericType() && eType.isNumericType())
				be.type = PrimitiveType.ceilingType((PrimitiveType) vType, (PrimitiveType) eType);
		}

		if(oper == BinOp.MULT) {
			if (vType.isNumericType() && eType.isNumericType())
				be.type = PrimitiveType.ceilingType((PrimitiveType) vType, (PrimitiveType) eType);
		}
		if(oper == BinOp.DIV) {
			if (vType.isNumericType() && eType.isNumericType())
				be.type = PrimitiveType.ceilingType((PrimitiveType) vType, (PrimitiveType) eType);
		}

		if(oper == BinOp.MOD) {
			if (vType.isNumericType() && eType.isNumericType())
				be.type = PrimitiveType.ceilingType((PrimitiveType) vType, (PrimitiveType) eType);
		}



//		for <,>  they check for boolean
		if(oper == BinOp.LT)
		{
			if (vType.isNumericType() && eType.isNumericType())
			be.type = new PrimitiveType(PrimitiveType.BooleanKind);
		}
		if(oper == BinOp.GT)
		{
			if (vType.isNumericType() && eType.isNumericType())
			be.type = new PrimitiveType(PrimitiveType.BooleanKind);
			else
				Error.error(be, "Operator '" + be.op().operator() + "' requires operands of numeric type.");

		}
		if(oper == BinOp.GTEQ)
		{
			if (vType.isNumericType() && eType.isNumericType())
				be.type = new PrimitiveType(PrimitiveType.BooleanKind);
		}
		if(oper == BinOp.LTEQ)
		{
			if (vType.isNumericType() && eType.isNumericType())
				be.type = new PrimitiveType(PrimitiveType.BooleanKind);
		}

//		for % gives int
		if(oper == BinOp.MOD) {
			if (vType.isNumericType() && eType.isNumericType())
				be.type = new PrimitiveType(PrimitiveType.IntKind);
		}

		if(oper == BinOp.EQEQ) {
			if (vType.isNumericType() && eType.isNumericType())
				be.type = new PrimitiveType(PrimitiveType.BooleanKind);
			if (eType.isNullType())
			    be.type = new PrimitiveType(PrimitiveType.BooleanKind);

		}
		if(oper == BinOp.NOTEQ) {



//			if(vType.isClassType())
//			{
//				Error.error(be, "Class name '" + vType.typeName() + "' cannot appear as parameter to operator '"+be.op().operator() +"'.");
//			}

			if(!(vType.identical(eType)))
			{

				if (vType.isNumericType() && eType.isNumericType())
					be.type = new PrimitiveType(PrimitiveType.BooleanKind);
//				if (eType.isNullType())
//					be.type = new PrimitiveType(PrimitiveType.BooleanKind);
				else
				Error.error(be,"Operator '!=' requires operands of the same type.");

			}
			else
			{
				if (vType.isVoidType()) {
					Error.error(be, "Void type cannot be used here.");

				}
				if(vType.isClassType())
					if(eType.isClassType())
						Error.error(be, "Class name '" + vType.typeName() + "' cannot appear as parameter to operator '"+be.op().operator() +"'.");
				    if(eType.isNullType())
						be.type = new PrimitiveType(PrimitiveType.BooleanKind);
				else
						be.type = new PrimitiveType(PrimitiveType.BooleanKind);
			}




		}


		// for or, and

		if(oper == BinOp.OROR) {
			if (vType.isBooleanType() && eType.isBooleanType())
				be.type = new PrimitiveType(PrimitiveType.BooleanKind);
		}

		if(oper == BinOp.ANDAND) {
			if (vType.isBooleanType() && eType.isBooleanType())
				be.type = new PrimitiveType(PrimitiveType.BooleanKind);
			else
				Error.error(be, "Operator '"+be.op().operator()+"' requires operands of boolean type.");
//				Operator '&&' requires operands of boolean type.
		}

		if(oper == BinOp.AND) {
//			if (vType.isNumericType() && eType.isNumericType())
//				be.type = new PrimitiveType(PrimitiveType.IntKind);
			if(eType.isCharType() )
				be.type = new PrimitiveType(PrimitiveType.IntKind);
			if(vType.isIntegralType() && eType.isIntegralType())
				be.type = new PrimitiveType(PrimitiveType.IntKind);
			if(vType.isBooleanType() && eType.isBooleanType())
				be.type = new PrimitiveType(PrimitiveType.BooleanKind);
			else
			  if(!(vType.isIntegralType() && eType.isIntegralType()))
				Error.error(be, "Operator '"+be.op().operator()+"' requires both operands of either integral or boolean type.");




		}
		if(oper == BinOp.OR) {
		if(eType.isCharType())
			be.type = new PrimitiveType(PrimitiveType.IntKind);

		}




		//for instanceof
		if(oper == BinOp.INSTANCEOF) {
			if (vType.isClassType() && eType.isClassType()) {
				NameExpr rne = ((NameExpr) be.right());
				Boolean check_class = be.left() instanceof CastExpr;
				if(!check_class) {
					NameExpr lne = ((NameExpr) be.left());
					if ((rne.myDecl instanceof ClassDecl && !(lne.myDecl instanceof ClassDecl)))
						be.type = new PrimitiveType(PrimitiveType.BooleanKind);
					if (!(rne.myDecl instanceof ClassDecl))
						Error.error(be, "'" + rne.name() + "' is not a class name.");
					if ((lne.myDecl instanceof ClassDecl))
						Error.error(be, "Left hand side of instanceof cannot be a class.");
				}
				if(check_class)
					be.type = new PrimitiveType(PrimitiveType.BooleanKind);

			}


			if(!vType.isClassType())
				Error.error(be,"Left hand side of instanceof needs expression of class type");
		}


		//for shiftoperator

		if(oper == BinOp.LSHIFT) {
			if (vType.isNumericType() && eType.isNumericType())
				be.type = new PrimitiveType(PrimitiveType.IntKind);
		}

		if(oper == BinOp.RSHIFT) {
			if (vType.isNumericType() && eType.isNumericType())
				be.type = new PrimitiveType(PrimitiveType.IntKind);
			if(eType.isBooleanType())
				Error.error(be,"Operator '"+be.op().operator()+"' requires right operand of integer type.");
			if(!vType.isIntegralType())
				Error.error(be,"Operator '"+be.op().operator()+"' requires left operand of integral type.");

		}






	// YOUR CODE HERE

	println(be.line + ": Binary Expression has type: " + be.type);
	return be.type;
    }

    /** CAST EXPRESSION */
    public Object visitCastExpr(CastExpr ce) {
	println(ce.line + ": Visiting a cast expression");
		int flag=0;
		Type cleft = (Type) ce.type();



		if(!(ce.expr() instanceof Invocation )){
			if (ce.expr() instanceof NameExpr) {



					Type cright = new ClassType((((NameExpr) ce.expr()).name()));
					Name cn = ((NameExpr) ce.expr()).name();
					if (classTable.get(cn.getname()) != null) {
						ce.expr().visit(this);
						Error.error(ce, "Cannot use class name '" + cn.getname() + "'. Object name expected in cast.");
					} else if (!(cleft.isClassType() && cright.isClassType())) {
						if (!(Type.assignmentCompatible(cleft, cright))) {



							if (!(cleft.isPrimitiveType() && cright.isPrimitiveType())) {
								Type test = (Type)ce.expr().visit(this);
								if(test.isPrimitiveType()) {
									flag = 1;
								}
								else

								   Error.error(ce, "Illegal type cast. Cannot cast type 'test' to type 'int'.");
							}
						} else
							ce.type = ce.type();
					}




			}
		}




             if(flag==0)

		       ce.expr().visit(this);



			ce.type = ce.type();




	// YOUR CODE HERE

	println(ce.line + ": Cast Expression has type: " + ce.type);
	return ce.type;
    }

    /** CLASSTYPE */
    public Object visitClassType(ClassType ct) {
	println(ct.line + ": Visiting a class type");

	println(ct.line + ": Class Type has type: " + ct);
	return ct;
    }

    /** CONSTRUCTOR (EXPLICIT) INVOCATION */
    public Object visitCInvocation(CInvocation ci) {
		ConstructorDecl value;
	println(ci.line + ": Visiting an explicit constructor invocation");
		ClassDecl call_construct;

		ci.args().visit(this);
		if(ci.superConstructorCall())
			call_construct = currentClass.superClass().myDecl;
		else
			call_construct = currentClass;

		value = (ConstructorDecl) findMethod(call_construct.constructors,call_construct.name(),ci.args(),false);













	// YOUR CODE HERE

	return null;
    }



    /** CLASS DECLARATION */
    public Object visitClassDecl(ClassDecl cd) {
	println(cd.line + ": Visiting a class declaration");
	currentClass = cd;
	super.visitClassDecl(cd);


	// YOUR CODE HERE

	return null;
    }

    /** CONSTRUCTOR DECLARATION */
    public Object visitConstructorDecl(ConstructorDecl cd) {
	println(cd.line + ": Visiting a constructor declaration");
		super.visitConstructorDecl(cd);

	// YOUR CODE HERE

	return null;
    }

    /** DO STATEMENT */
    public Object visitDoStat(DoStat ds) {
	println(ds.line + ": Visiting a do statement");


//		ds.expr().type
		ds.expr().visit(this);
		Type exp_check = new PrimitiveType(((Literal)ds.expr()).getKind());
        if(!exp_check.isBooleanType())

			Error.error(ds,"Non boolean Expression found as test in do-statement.");

		ds.stat().visit(this);

	// YOUR CODE HERE

	return null;
    }

    /** FIELD DECLARATION */
    public Object visitFieldDecl(FieldDecl fd) {
	println(fd.line + ": Visiting a field declaration");

	// Update the current context
	currentContext = fd;
	inFieldInit = true;
	currentFieldDecl = fd;
	if (fd.var().init() != null)
	    fd.var().init().visit(this);
	currentFieldDecl = null;
	inFieldInit = false;
	return fd.type();
    }

    /** FIELD REFERENCE */
    public Object visitFieldRef(FieldRef fr) {
	println(fr.line + ": Visiting a field reference" + fr.target());

	Type targetType = (Type) fr.target().visit(this);
	String field    = fr.fieldName().getname();

	// Changed June 22 2012 ARRAY
	if (fr.fieldName().getname().equals("length")) {
	    if (targetType.isArrayType()) {
		fr.type = new PrimitiveType(PrimitiveType.IntKind);
		println(fr.line + ": Field Reference was a an Array.length reference, and it has type: " + fr.type);
		fr.targetType = targetType;
		return fr.type;
	    }
	}

	if (targetType.isClassType()) {
	    ClassType c = (ClassType)targetType;
	    ClassDecl cd = c.myDecl;
	    fr.targetType = targetType;

	    println(fr.line + ": FieldRef: Looking up symbol '" + field + "' in fieldTable of class '" + 
		    c.typeName() + "'.");

	    // Lookup field in the field table of the class associated with the target.
	    FieldDecl lookup = (FieldDecl) NameChecker.NameChecker.getField(field, cd);

	    // Field not found in class.
	    if (lookup == null)
		Error.error(fr,"Field '" + field + "' not found in class '" + cd.name() + "'.");
	    else {
		fr.myDecl = lookup;
		fr.type = lookup.type();
	    }
	} else 
	    Error.error(fr,"Attempt to access field '" + field + "' in something not of class type.");
	println(fr.line + ": Field Reference has type: " + fr.type);

	if (inFieldInit && currentFieldDecl.fieldNumber <= fr.myDecl.fieldNumber && currentClass.name().equals(   (((ClassType)fr.targetType).myDecl).name()))
	    Error.error(fr,"Illegal forward reference of non-initialized field.");

	return fr.type;
    }

    /** FOR STATEMENT */
    public Object visitForStat(ForStat fs) {
	println(fs.line + ": Visiting a for statement");

		if(fs.init() != null)
		  fs.init().visit(this);
		if (fs.incr() != null)
			fs.incr().visit(this);
		if(fs.expr() != null) {
			Type expr = (Type)fs.expr().visit(this);
			if(!expr.isBooleanType())
				Error.error(fs,"Non boolean Expression found in for-statement.");


		}

		if((fs.stats())!= null)
		  fs.stats().visit(this);

	// YOUR CODE HERE

	return null;
    }

    /** IF STATEMENT */
    public Object visitIfStat(IfStat is) {
	println(is.line + ": Visiting a if statement");

		Type check = (Type)is.expr().visit(this);
		if(!check.isBooleanType())
			Error.error(is,"Non boolean Expression found as test in if-statement.");
		if(is.thenpart() != null)
		  is.thenpart().visit(this);
		if(is.elsepart() != null)
		  is.elsepart().visit(this);


//		super.visitIfStat(is);


	// YOUR CODE HERE

	return null;
    }

    /** INVOCATION */
    public Object visitInvocation(Invocation in) {
	println(in.line + ": Visiting an Invocation");
		Type targetType = null;
		MethodDecl value = null;
		ClassDecl new_value = null;


		if( in.target() == null)
		{
			in.params().visit(this);
			value = (MethodDecl)findMethod(currentClass.allMethods,in.methodName().getname(),in.params(),true);

			in.type = ((MethodDecl) value).returnType();
		}
		else
		{
//			if((((NameExpr)in.target()).myDecl instanceof ClassDecl)){
			targetType = (Type) in.target().visit(this);


			in.targetType = targetType;
			if((!(targetType instanceof ClassType)))
				Error.error(in,"Attempt to invoke method '"+in.methodName()+"' in something not of class type.");

			if (targetType instanceof ClassType)
				new_value = ((ClassType)targetType).myDecl;

			in.params().visit(this);


			value = (MethodDecl)findMethod(new_value.allMethods,in.methodName().getname(),in.params(),true);
			in.type = ((MethodDecl) value).returnType();
//		}
//		 else
//		 	in.target().visit(this);
//		 	Error.error(in,"Attempt to invoke method '"+in.methodName()+"' in something not of class type.");
		}







//		Type return_value =  (Type)in.target().visit(this);
//		 in.methodName().visit(this);
//
//		in.type = (Type)in.params().visit(this);

	// YOUR CODE HERE

	println(in.line + ": Invocation has type: " + in.type);
	return in.type;
    }





	/** LITERAL */
    public Object visitLiteral(Literal li) {
	println(li.line + ": Visiting a literal");

		if(li.getKind() == 10 )
		{
			Type test = new NullType(li);
			li.type = test;
		}
		else
		li.type = new PrimitiveType(li.getKind());


	// YOUR CODE HERE

	println(li.line + ": Literal has type: " + li.type);
	return li.type;
    }

    /** METHOD DECLARATION */
    public Object visitMethodDecl(MethodDecl md) {
	println(md.line + ": Visiting a method declaration");
	currentContext = md;
//	super.visitMethodDecl(md);
		super.visitMethodDecl(md);

		// YOUR CODE HERE

	return null;
    }

//    /** NAME EXPRESSION */
    public Object visitNameExpr(NameExpr ne) {
	println(ne.line + ": Visiting a Name Expression");
//		ne.type = (AST) ne.myDecl;
	if(ne.myDecl instanceof LocalDecl)
		ne.type = ((LocalDecl) ne.myDecl).type();
	if(ne.myDecl instanceof ParamDecl)
		ne.type = ((ParamDecl) ne.myDecl).type();

     if(ne.myDecl instanceof ClassDecl) {
		 ne.type = new ClassType(((ClassDecl) ne.myDecl).className());
		 ((ClassType) ne.type).myDecl = (ClassDecl) ne.myDecl;

	 }


	// YOUR CODE HERE

	println(ne.line + ": Name Expression has type: " + ne.type);
	return ne.type;
    }



    /** NEW */
    public Object visitNew(New ne) {
	println(ne.line + ": Visiting a new");
		ConstructorDecl value;
        ne.type =  (ClassType)ne.type().visit(this);
		ne.args().visit(this);
		if(((ClassType)ne.type()).myDecl.isInterface())
			Error.error(ne,"Cannot instantiate interface '" + ((ClassType)ne.type()).myDecl.name() + "'.");

		 value  = (ConstructorDecl)findMethod(ne.type().myDecl.constructors,ne.type().myDecl.name(),ne.args(),false);


	// YOUR CODE HERE

	println(ne.line + ": New has type: " + ne.type);
	return ne.type;
    }


    /** RETURN STATEMENT */
    public Object visitReturnStat(ReturnStat rs) {
	println(rs.line + ": Visiting a return statement");
	Type returnType;

	if (currentContext instanceof MethodDecl)
	    returnType = ((MethodDecl)currentContext).returnType();
	else
	    returnType = null;

	// Check is there is a return in a Static Initializer
	if (currentContext instanceof StaticInitDecl) 
	    Error.error(rs,"return outside method");

	// Check if a void method is returning something.
	if (returnType == null || returnType.isVoidType()) {
	    if (rs.expr() != null)
		Error.error(rs, "Return statement of a void function cannot return a value.");
	    return null;
	}

	// Check if a non void method is returning without a proper value.
	if (rs.expr() == null)
	    Error.error(rs, "Non void function must return a value.");

	Type returnValueType = (Type) rs.expr().visit(this);	
	if (rs.expr().isConstant()) {
	    if (returnType.isShortType() && Literal.isShortValue(((BigDecimal)rs.expr().constantValue()).longValue()))
		;// is ok break;                                                                                                    
	    else if (returnType.isByteType() && Literal.isByteValue(((BigDecimal)rs.expr().constantValue()).longValue()))
		; // is ok break;                                                                                                   
	    else if (returnType.isCharType() && Literal.isCharValue(((BigDecimal)rs.expr().constantValue()).longValue()))
		; // break;
	    else if (!Type.assignmentCompatible(returnType,returnValueType))
		Error.error(rs, "Illegal value of type " + returnValueType.typeName() + 
			    " in method expecting value of type " + returnType.typeName() + ".");
	} else if (!Type.assignmentCompatible(returnType,returnValueType))
	    Error.error(rs, "Illegal value of type " + returnValueType.typeName() + 
			" in method expecting value of type " + returnType.typeName() + ".");
		
	rs.setType(returnType);
	return null;
    }

    /** STATIC INITIALIZER */
    public Object visitStaticInitDecl(StaticInitDecl si) {
	println(si.line + ": Visiting a static initializer");

	super.visitStaticInitDecl(si);

	return null;
    }

    /** SUPER */
    public Object visitSuper(Super su) {
	println(su.line + ": Visiting a super");
//    super.visitSuper(su);
		su.type = currentClass.superClass();
		println(su.line + ": Super has type: " + su.type);
	// YOUR CODE HERE

	return su.type;
    }

    /** SWITCH STATEMENT */
    public Object visitSwitchStat(SwitchStat ss) {
	println(ss.line + ": Visiting a Switch statement");
		int count =0;
		int count_duplicate=0;
//		super.visitSwitchStat(ss);
		Type exp= (Type) ss.expr().visit(this);
		if(!exp.isIntegerType())
			Error.error(ss,"Switch statement expects value of type int.");
		for (int i=0;i<(ss.switchBlocks().nchildren);i++){
			 SwitchGroup sgroup =(SwitchGroup)ss.switchBlocks().children[i];
			 SwitchLabel slabel = (SwitchLabel)sgroup.labels().children[0];
			if(slabel.isDefault())
				count = count+1;
			else {
				Type test = (Type) slabel.expr().visit(this);

				if (!test.isIntegralType()) {
					Error.error(slabel, "Switch labels must be of type int.");
				}
				Boolean test_constant = (( slabel.expr()).isConstant());
				if(!test_constant) {
					Error.error(slabel, "Switch labels must be constants.");
				}
				int test_new = ((Literal)slabel.expr()).intValue();
				if(test_new == 4)
			{
				count_duplicate = count_duplicate+1;

			}
			if(count_duplicate>1)
			{
				Error.error(slabel,"Duplicate case label.");
			}
			}
			sgroup.statements().visit(this);
//			int test = ((Literal)slabel.expr()).intValue();
//			if(test == 4)
//			{
//				Error.error(slabel,"Duplicate case label.");
//			}


			if(count>1)
				Error.error(slabel,"Duplicate default label.");

//			for(int j=0;j<2;j++)
//			{
//
//			}
		}
//		ss.switchBlocks().visit(this);


	// YOUR CODE HERE

	return null;
    }

    /** TERNARY EXPRESSION */
    public Object visitTernary(Ternary te) {
	println(te.line + ": Visiting a ternary expression");
//		super.visitTernary(te);
		Type test = (Type)te.expr().visit(this);
		if(!test.isBooleanType())
			Error.error(te,"Non boolean Expression found as test in ternary expression.");
		Type true_ret = (Type) te.trueBranch().visit(this);
		Type false_ret = (Type) te.falseBranch().visit(this);
		te.type = false_ret;

//
//		}

	// YOUR CODE HERE

	println(te.line + ": Ternary has type: " + te.type);
	return te.type;
    }

    /** THIS */
    public Object visitThis(This th) {
	println(th.line + ": Visiting a this statement");

	th.type = th.type();

	println(th.line + ": This has type: " + th.type);
	return th.type;
    }

    /** UNARY POST EXPRESSION */
    public Object visitUnaryPostExpr(UnaryPostExpr up) {
	println(up.line + ": Visiting a unary post expression");
	Type eType = null;
		up.type = (Type)up.expr().visit(this);
		up.op().visit(this);
		eType = up.type;
		if(up.expr() instanceof Literal)
			Error.error(up,"Variable expected, found value.");


	// YOUR CODE HERE

	println(up.line + ": Unary Post Expression has type: " + up.type);
	return eType;
    }

    /** UNARY PRE EXPRESSION */
    public Object visitUnaryPreExpr(UnaryPreExpr up) {
	println(up.line + ": Visiting a unary pre expression");
		up.op().visit(this);
		up.type = (Type)up.expr().visit(this);

		if((up.type).isStringType())
			Error.error(up,"Cannot apply operator '"+up.op().operator()+"' to something of type String.");


	// YOUR CODE HERE

	println(up.line + ": Unary Pre Expression has type: " + up.type);
	return up.type;
    }

    /** VAR */
    public Object visitVar(Var va) {
	println(va.line + ": Visiting a var");
		Type Rht = null;
		Type name_type = null;
//		Type test = va.myDecl.type();
//		super.visitVar(va);
       if(va.init()!=null) {
		    Rht = (Type) va.init().visit(this);
		    name_type = (va.myDecl).type();
		    if(!Type.assignmentCompatible(name_type,Rht)) {
				Error.error(va, "Cannot assign value of type " + Rht.typeName() + " to variable of type " +
						name_type.typeName() + ".");
			}
	   }
//		Type name_type = (va.myDecl).type();
//
//		if(!Type.assignmentCompatible(name_type,Rht))
//			Error.error(va, "Cannot assign value of type " + Rht.typeName() + " to variable of type " +
//					name_type.typeName() + ".");



//		va.name().visit(this);

	// YOUR CODE HERE

	return null;
    }

    /** WHILE STATEMENT */
    public Object visitWhileStat(WhileStat ws) {
	println(ws.line + ": Visiting a while statement");

		Type test = (Type)ws.expr().visit(this);
		if(!test.isBooleanType())
			Error.error(ws, "Non boolean Expression found as test in while-statement.");
		ws.stat().visit(this);


	// YOUR CODE HERE

	return null;
    }

}
