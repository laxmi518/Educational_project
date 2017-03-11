package CodeGenerator;

import AST.*;
import Utilities.Error;
import Utilities.Visitor;

import java.util.*;

import Instruction.*;
import Jasmin.*;


class GenerateCode extends Visitor {

	private Generator gen;
	private ClassDecl currentClass;
	private boolean insideLoop = false;
	private boolean insideSwitch = false;
	private ClassFile classFile;
	private boolean RHSofAssignment = false;
	private boolean StringBuilderCreated = false;
	
	
	public GenerateCode(Generator g, boolean debug) {
		gen = g;
		this.debug = debug;
		classFile = gen.getClassFile();
	}

	public void setCurrentClass(ClassDecl cd) {
		this.currentClass = cd;
	}

	// ARRAY VISITORS START HERE

	/** ArrayAccessExpr */
	public Object visitArrayAccessExpr(ArrayAccessExpr ae) {
		println(ae.line + ": Visiting ArrayAccessExpr");
		classFile.addComment(ae, "ArrayAccessExpr");
		super.visitArrayAccessExpr(ae);
		// YOUR CODE HERE
		classFile.addComment(ae,"End ArrayAccessExpr");
		return null;
	}

	/** ArrayLiteral */
	public Object visitArrayLiteral(ArrayLiteral al) {
		println(al.line + ": Visiting an ArrayLiteral ");
		super.visitArrayLiteral(al);
		// YOUR CODE HERE
		return null;
	}

	/** NewArray */
	public Object visitNewArray(NewArray ne) {
		println(ne.line + ": NewArray:\t Creating new array of type " + ne.type.typeName());
		super.visitNewArray(ne);
		// YOUR CODE HERE
		return null;
	}

	// END OF ARRAY VISITORS

	// ASSIGNMENT
	public Object visitAssignment(Assignment as) {
		println(as.line + ": Assignment:\tGenerating code for an Assignment.");
		classFile.addComment(as, "Assignment");
		/* If a reference is needed then compute it
	          (If array type then generate reference to the	target & index)
	          - a reference is never needed if as.left() is an instance of a NameExpr
	          - a reference can be computed for a FieldRef by visiting the target
	          - a reference can be computed for an ArrayAccessExpr by visiting its target 
		 */
		if (as.left() instanceof FieldRef) {
			println(as.line + ": Generating reference for FieldRef target ");
			FieldRef fr= (FieldRef)as.left();
			fr.target().visit(this);		
			// if the target is a New and the field is static, then the reference isn't needed, so pop it! 
			if (fr.myDecl.isStatic() && fr.target() instanceof New) 
				classFile.addInstruction(new Instruction(RuntimeConstants.opc_pop));
		} else if (as.left() instanceof ArrayAccessExpr) {
			println(as.line + ": Generating reference for Array Access target");
			ArrayAccessExpr ae = (ArrayAccessExpr)as.left();
			classFile.addComment(as, "ArrayAccessExpr target");
			ae.target().visit(this);
			classFile.addComment(as, "ArrayAccessExpr index");
			ae.index().visit(this);
		}

		/* If the assignment operator is <op>= then
	            -- If the left hand side is a non-static field (non array): dup (object ref) + getfield
	            -- If the left hand side is a static field (non array): getstatic   
	            -- If the left hand side is an array reference: dup2 +	Xaload 
				-- If the left hand side is a local (non array): generate code for it: Xload Y 
		 */	        
		if (as.op().kind != AssignmentOp.EQ) {
			if (as.left() instanceof FieldRef) {
				println(as.line + ": Duplicating reference and getting value for LHS (FieldRef/<op>=)");
				FieldRef fr = (FieldRef)as.left();
				if (!fr.myDecl.isStatic()) {
					classFile.addInstruction(new Instruction(RuntimeConstants.opc_dup));
					classFile.addInstruction(new FieldRefInstruction(RuntimeConstants.opc_getfield, fr.targetType.typeName(),
							fr.fieldName().getname(), fr.type.signature()));
				} else 
					classFile.addInstruction(new FieldRefInstruction(RuntimeConstants.opc_getstatic, fr.targetType.typeName(),
							fr.fieldName().getname(), fr.type.signature()));
			} else if (as.left() instanceof ArrayAccessExpr) {
				println(as.line + ": Duplicating reference and getting value for LHS (ArrayAccessRef/<op>=)");
				ArrayAccessExpr ae = (ArrayAccessExpr)as.left();
				classFile.addInstruction(new Instruction(RuntimeConstants.opc_dup2));
				classFile.addInstruction(new Instruction(Generator.getArrayLoadInstruction(ae.type)));
			} else { // NameExpr
				println(as.line + ": Getting value for LHS (NameExpr/<op>=)");
				NameExpr ne = (NameExpr)as.left();
				int address = ((VarDecl)ne.myDecl).address();

				if (address < 4)
					classFile.addInstruction(new Instruction(Generator.getLoadInstruction(((VarDecl)ne.myDecl).type(), address, true)));
				else
					classFile.addInstruction(new SimpleInstruction(Generator.getLoadInstruction(((VarDecl)ne.myDecl).type(), address, true), address));
			}
		}

		/* Visit the right hand side (RHS) */
		boolean oldRHSofAssignment = RHSofAssignment;
		RHSofAssignment = true;
		as.right().visit(this);
		RHSofAssignment = oldRHSofAssignment;
		/* Convert the right hand sides type to that of the entire assignment */

		if (as.op().kind != AssignmentOp.LSHIFTEQ &&
		    as.op().kind != AssignmentOp.RSHIFTEQ &&
		    as.op().kind != AssignmentOp.RRSHIFTEQ)
		    gen.dataConvert(as.right().type, as.type);

		/* If the assignment operator is <op>= then
				- Execute the operator
		 */
		if (as.op().kind != AssignmentOp.EQ)
			classFile.addInstruction(new Instruction(Generator.getBinaryAssignmentOpInstruction(as.op(), as.type)));

		/* If we are the right hand side of an assignment
		     -- If the left hand side is a non-static field (non array): dup_x1/dup2_x1
			 -- If the left hand side is a static field (non array): dup/dup2
			 -- If the left hand side is an array reference: dup_x2/dup2_x2 
			 -- If the left hand side is a local (non array): dup/dup2 
		 */    
		if (RHSofAssignment) {
			String dupInstString = "";
			if (as.left() instanceof FieldRef) {
				FieldRef fr = (FieldRef)as.left();
				if (!fr.myDecl.isStatic())  
					dupInstString = "dup" + (fr.type.width() == 2 ? "2" : "") + "_x1";
				else 
					dupInstString = "dup" + (fr.type.width() == 2 ? "2" : "");
			} else if (as.left() instanceof ArrayAccessExpr) {
				ArrayAccessExpr ae = (ArrayAccessExpr)as.left();
				dupInstString = "dup" + (ae.type.width() == 2 ? "2" : "") + "_x2";
			} else { // NameExpr
				NameExpr ne = (NameExpr)as.left();
				dupInstString = "dup" + (ne.type.width() == 2 ? "2" : "");
			}
			classFile.addInstruction(new Instruction(Generator.getOpCodeFromString(dupInstString)));
		}

		/* Store
		     - If LHS is a field: putfield/putstatic
			 -- if LHS is an array reference: Xastore 
			 -- if LHS is a local: Xstore Y
		 */
		if (as.left() instanceof FieldRef) {
			FieldRef fr = (FieldRef)as.left();
			if (!fr.myDecl.isStatic()) 
				classFile.addInstruction(new FieldRefInstruction(RuntimeConstants.opc_putfield,
						fr.targetType.typeName(), fr.fieldName().getname(), fr.type.signature()));
			else 
				classFile.addInstruction(new FieldRefInstruction(RuntimeConstants.opc_putstatic,
						fr.targetType.typeName(), fr.fieldName().getname(), fr.type.signature()));
		} else if (as.left() instanceof ArrayAccessExpr) {
			ArrayAccessExpr ae = (ArrayAccessExpr)as.left();
			classFile.addInstruction(new Instruction(Generator.getArrayStoreInstruction(ae.type)));
		} else { // NameExpr				
			NameExpr ne = (NameExpr)as.left();
			int address = ((VarDecl)ne.myDecl).address() ;

			// CHECK!!! TODO: changed 'true' to 'false' in these getStoreInstruction calls below....
			if (address < 4)
				classFile.addInstruction(new Instruction(Generator.getStoreInstruction(((VarDecl)ne.myDecl).type(), address, false)));
			else {
				classFile.addInstruction(new SimpleInstruction(Generator.getStoreInstruction(((VarDecl)ne.myDecl).type(), address, false), address));
			}
		}
		classFile.addComment(as, "End Assignment");
		return null;
	}

	// BINARY EXPRESSION
    public Object visitBinaryExpr(BinaryExpr be) {
	println(be.line + ": BinaryExpr:\tGenerating code for " + be.op().operator() + " :  " + be.left().type.typeName() + " -> " + be.right().type.typeName() + " -> " + be.type.typeName() + ".");
	classFile.addComment(be, "Binary Expression");

		be.left().visit(this);
//		super.visitBinaryExpr(be);


        String lbl_or = "";
		String lbl_and = "";
		int op_kind = be.op().kind;
		if(op_kind == BinOp.OROR || op_kind == BinOp.ANDAND )
		{
			if(op_kind == BinOp.OROR) {
				lbl_or = "L" + gen.getLabel();
				classFile.addInstruction(new Instruction(RuntimeConstants.opc_dup));
				classFile.addInstruction(new JumpInstruction(RuntimeConstants.opc_ifne, lbl_or));
				classFile.addInstruction(new Instruction(RuntimeConstants.opc_pop));
			}
			else {
				lbl_and = "L" + gen.getLabel();
				classFile.addInstruction(new Instruction(RuntimeConstants.opc_dup));
				classFile.addInstruction(new JumpInstruction(RuntimeConstants.opc_ifeq, lbl_and));
				classFile.addInstruction(new Instruction(RuntimeConstants.opc_pop));
			}

		}

//		if(op_kind == BinOp.ANDAND)
//		{
//			lbl_and = "L"+gen.getLabel();
//			classFile.addInstruction(new Instruction(RuntimeConstants.opc_dup));
//			classFile.addInstruction(new JumpInstruction(RuntimeConstants.opc_ifeq,lbl_and));
//			classFile.addInstruction(new Instruction(RuntimeConstants.opc_pop));
//		}



		if(!(op_kind == BinOp.INSTANCEOF)) {
			be.right().visit(this);
		}
		if(op_kind == BinOp.OROR)
		{
			classFile.addInstruction(new LabelInstruction(RuntimeConstants.opc_label,lbl_or));
		}

		if(op_kind == BinOp.ANDAND)
		{
			classFile.addInstruction(new LabelInstruction(RuntimeConstants.opc_label,lbl_and));
		}


		if(op_kind == BinOp.EQEQ)
		{
			String opc;
			if(be.left().type.isIntegerType()) {
				opc = "if_" + be.left().type.getTypePrefix() + "cmpeq";
				if (opc.equals("if_lcmpeq"))
					opc = "if_icmpeq"; ///just for testing

				String label_eq = "L" + gen.getLabel();
				String label_neq = "L" + gen.getLabel();

				classFile.addInstruction(new JumpInstruction(Generator.getOpCodeFromString(opc), label_eq));
				classFile.addInstruction(new Instruction(RuntimeConstants.opc_iconst_0));
				classFile.addInstruction(new JumpInstruction(RuntimeConstants.opc_goto, label_neq));
				classFile.addInstruction(new LabelInstruction(RuntimeConstants.opc_label, label_eq));
				classFile.addInstruction(new Instruction(RuntimeConstants.opc_iconst_1));
				classFile.addInstruction(new LabelInstruction(RuntimeConstants.opc_label, label_neq));
			}
			else {
				String label_eq = "L" + gen.getLabel();
				String label_neq = "L" + gen.getLabel();

				classFile.addInstruction(new JumpInstruction(RuntimeConstants.opc_if_acmpeq, label_eq));

				classFile.addInstruction(new Instruction(RuntimeConstants.opc_iconst_0));
				classFile.addInstruction(new JumpInstruction(RuntimeConstants.opc_goto, label_neq));
				classFile.addInstruction(new LabelInstruction(RuntimeConstants.opc_label, label_eq));
				classFile.addInstruction(new Instruction(RuntimeConstants.opc_iconst_1));
				classFile.addInstruction(new LabelInstruction(RuntimeConstants.opc_label, label_neq));
			}





		}

		if(op_kind == BinOp.GT)
		{
			String label1 = "L"+gen.getLabel();
			String label2 = "L"+gen.getLabel();

			if(be.left().type.isIntegerType()) {
				classFile.addInstruction(new  JumpInstruction(RuntimeConstants.opc_if_icmpgt,label1));
			}
			if(be.left().type.isDoubleType())
			{
				classFile.addInstruction(new Instruction(RuntimeConstants.opc_dcmpg));
				classFile.addInstruction(new JumpInstruction(RuntimeConstants.opc_ifgt, label1));
			}

			classFile.addInstruction(new Instruction(RuntimeConstants.opc_iconst_0));
			classFile.addInstruction(new JumpInstruction(RuntimeConstants.opc_goto,label2));
			classFile.addInstruction(new LabelInstruction(RuntimeConstants.opc_label,label1));
			classFile.addInstruction(new Instruction(RuntimeConstants.opc_iconst_1));
			classFile.addInstruction(new LabelInstruction(RuntimeConstants.opc_label,label2));




		}

		if(op_kind == BinOp.GTEQ)
		{
			String label1 = "L"+gen.getLabel();
			String label2 = "L"+gen.getLabel();
			classFile.addInstruction(new  JumpInstruction(RuntimeConstants.opc_if_icmpge,label1));
			classFile.addInstruction(new Instruction(RuntimeConstants.opc_iconst_0));
			classFile.addInstruction(new JumpInstruction(RuntimeConstants.opc_goto,label2));
			classFile.addInstruction(new LabelInstruction(RuntimeConstants.opc_label,label1));
			classFile.addInstruction(new Instruction(RuntimeConstants.opc_iconst_1));
			classFile.addInstruction(new LabelInstruction(RuntimeConstants.opc_label,label2));




		}

		if(op_kind == BinOp.LT)
		{
			String label1 = "L"+gen.getLabel();
			String label2 = "L"+gen.getLabel();
			if(be.left().type.isIntegerType()) {


				classFile.addInstruction(new JumpInstruction(RuntimeConstants.opc_if_icmplt, label1));
			}
			if(be.left().type.isDoubleType())
			{
				classFile.addInstruction(new Instruction(RuntimeConstants.opc_dcmpg));
				classFile.addInstruction(new JumpInstruction(RuntimeConstants.opc_iflt, label1));

			}
			classFile.addInstruction(new Instruction(RuntimeConstants.opc_iconst_0));
			classFile.addInstruction(new JumpInstruction(RuntimeConstants.opc_goto,label2));
			classFile.addInstruction(new LabelInstruction(RuntimeConstants.opc_label,label1));
			classFile.addInstruction(new Instruction(RuntimeConstants.opc_iconst_1));
			classFile.addInstruction(new LabelInstruction(RuntimeConstants.opc_label,label2));




		}

		if(op_kind == BinOp.LTEQ)
		{
			String label1 = "L"+gen.getLabel();
			String label2 = "L"+gen.getLabel();
			classFile.addInstruction(new  JumpInstruction(RuntimeConstants.opc_if_icmple,label1));
			classFile.addInstruction(new Instruction(RuntimeConstants.opc_iconst_0));
			classFile.addInstruction(new JumpInstruction(RuntimeConstants.opc_goto,label2));
			classFile.addInstruction(new LabelInstruction(RuntimeConstants.opc_label,label1));
			classFile.addInstruction(new Instruction(RuntimeConstants.opc_iconst_1));
			classFile.addInstruction(new LabelInstruction(RuntimeConstants.opc_label,label2));




		}

		if(op_kind == BinOp.NOTEQ)
		{
			if(!(be.right().type.isStringType())) {
				String opc;
				opc = "if_" + be.left().type.getTypePrefix() + "cmpne";
				String label1 = "L" + gen.getLabel();
				String label2 = "L" + gen.getLabel();
				classFile.addInstruction(new JumpInstruction(Generator.getOpCodeFromString(opc), label1));
				classFile.addInstruction(new Instruction(RuntimeConstants.opc_iconst_0));
				classFile.addInstruction(new JumpInstruction(RuntimeConstants.opc_goto, label2));
				classFile.addInstruction(new LabelInstruction(RuntimeConstants.opc_label, label1));
				classFile.addInstruction(new Instruction(RuntimeConstants.opc_iconst_1));
				classFile.addInstruction(new LabelInstruction(RuntimeConstants.opc_label, label2));

			}
			else
			{
				String label1 = "L" + gen.getLabel();
				String label2 = "L" + gen.getLabel();
				classFile.addInstruction(new MethodInvocationInstruction(RuntimeConstants.opc_invokevirtual, "java/lang/String", "equals", "(Ljava/lang/Object;)Z"));
			   String test = "iconst_" + gen.getAddress();
			     classFile.addInstruction(new Instruction(Generator.getOpCodeFromString(test)));
				classFile.addInstruction(new Instruction(RuntimeConstants.opc_ixor));
			}



		}
		if(op_kind == BinOp.PLUS)
		{

			if(be.left().type.isNumericType() && be.right().type.isNumericType())
			{
//				String str_opc = be.type.getTypePrefix();
				String str_opc =  be.type.getTypePrefix()+"add";
				classFile.addInstruction(new Instruction(Generator.getOpCodeFromString(str_opc)));

			}

		}
		if(op_kind == BinOp.MINUS)
		{

			if(be.left().type.isNumericType() && be.right().type.isNumericType())
			{
//				String str_opc = be.type.getTypePrefix();
				String str_opc =  be.type.getTypePrefix()+"sub";
				classFile.addInstruction(new Instruction(Generator.getOpCodeFromString(str_opc)));

			}

		}

		if(op_kind == BinOp.MULT)
		{
			String str_opc =  be.type.getTypePrefix()+"mul";
			classFile.addInstruction(new Instruction(Generator.getOpCodeFromString(str_opc)));
		}

		if(op_kind == BinOp.DIV)
		{
			String str_opc =  be.type.getTypePrefix()+"div";
			classFile.addInstruction(new Instruction(Generator.getOpCodeFromString(str_opc)));
		}

		if(op_kind == BinOp.MOD)
		{
			String str_opc =  be.type.getTypePrefix()+"rem";
			classFile.addInstruction(new Instruction(Generator.getOpCodeFromString(str_opc)));
		}

		if(op_kind == BinOp.LSHIFT)
		{
			String opcode = be.left().type.getTypePrefix()+"shl";

			classFile.addInstruction(new Instruction(Generator.getOpCodeFromString(opcode)));
		}

		if(op_kind == BinOp.INSTANCEOF)
		{
			classFile.addInstruction(new ClassRefInstruction(RuntimeConstants.opc_instanceof, be.right().type.typeName()));
		}


		
	// YOUR CODE HERE
	classFile.addComment(be, "End BinaryExpr");
	return null;
    }

    // BREAK STATEMENT
    public Object visitBreakStat(BreakStat br) {
	println(br.line + ": BreakStat:\tGenerating code.");
	classFile.addComment(br, "Break Statement");
		classFile.addInstruction(new JumpInstruction(RuntimeConstants.opc_goto, gen.getBreakLabel()));

	// YOUR CODE HERE

	classFile.addComment(br, "End BreakStat");
	return null;
    }

    // CAST EXPRESSION
    public Object visitCastExpr(CastExpr ce) {
	println(ce.line + ": CastExpr:\tGenerating code for a Cast Expression.");
	classFile.addComment(ce, "Cast Expression");
	String instString ;

		super.visitCastExpr(ce);
		if((ce.expr().type.getTypePrefix())!=(ce.type().getTypePrefix())) {
			instString = ce.expr().type.getTypePrefix() + "2" + ce.type().getTypePrefix();
			classFile.addInstruction(new Instruction(Generator.getOpCodeFromString(instString)));

			if(ce.type().isByteType() || ce.type().isShortType()) {
				if (ce.type.isByteType()) {
					instString = ce.type.getTypePrefix() + "2" + "b";
					classFile.addInstruction(new Instruction(Generator.getOpCodeFromString(instString)));
				} else {
					instString = ce.type.getTypePrefix() + "2" + "s";
					classFile.addInstruction(new Instruction(Generator.getOpCodeFromString(instString)));
				}
			}


		}


		if((ce.expr().type.isIntegerType())&&(ce.type().isCharType())) {
			instString = ce.expr().type.getTypePrefix() + "2" + "c";
			classFile.addInstruction(new Instruction(Generator.getOpCodeFromString(instString)));

		}
		if(ce.expr().type.isClassType())
		{
			classFile.addInstruction(new ClassRefInstruction(RuntimeConstants.opc_checkcast,ce.type().typeName()));
		}

	// YOUR CODE HERE
	classFile.addComment(ce, "End CastExpr");
	return null;
    }
	public   String create_signature(ConstructorDecl cd_sig)
	{
		String total_sig = "("+cd_sig+")V";
		return total_sig;
	}

	public static String create_signature(MethodDecl md) {
		return "(" + md.paramSignature() + ")" + md.returnType().signature();
	}

    
	// CONSTRUCTOR INVOCATION (EXPLICIT)
	public Object visitCInvocation(CInvocation ci) {
		println(ci.line + ": CInvocation:\tGenerating code for Explicit Constructor Invocation.");     
		classFile.addComment(ci, "Explicit Constructor Invocation");

		classFile.addInstruction(new Instruction(RuntimeConstants.opc_aload_0));
        if(ci.targetClass.name() == "java/lang/Object"){
		classFile.addInstruction(new MethodInvocationInstruction(RuntimeConstants.opc_invokespecial,ci.constructor.getname(),"<init>","("+ci.constructor.paramSignature()+")"+"V"));
         super.visitCInvocation(ci);}
		else
		{
			super.visitCInvocation(ci);
			classFile.addInstruction(new MethodInvocationInstruction(RuntimeConstants.opc_invokespecial,ci.constructor.getname(),"<init>","("+ci.constructor.paramSignature()+")"+"V"));
		}
		// YOUR CODE HERE
		classFile.addComment(ci, "End CInvocation");
		return null;
	}

	// CLASS DECLARATION
	public Object visitClassDecl(ClassDecl cd) {
		println(cd.line + ": ClassDecl:\tGenerating code for class '" + cd.name() + "'.");

		// We need to set this here so we can retrieve it when we generate
		// field initializers for an existing constructor.
		currentClass = cd;

		for (int i = 0; i < cd.body().nchildren; i++) {
			if (cd.body().children[i] instanceof FieldDecl) {
				cd.body().children[i].visit(this);
			}
		}

		Object staticInit = cd.methodTable.get("<clinit>");
		if (staticInit == null) {

			FieldDecl fd;
			for (Enumeration<Object> e = cd.fieldTable.entries.elements(); e.hasMoreElements(); ) {
				fd = (FieldDecl)e.nextElement();
				if (fd.modifiers.isStatic() && fd.var().init() != null && (!fd.modifiers.isFinal() || (fd.modifiers.isFinal() &&
				!(fd.var().init() instanceof Literal)))) {

					cd.body().append(new StaticInitDecl(new Block(new Sequence())));
					break;
				}

			}
		}

//		super.visitClassDecl(cd);

		for (int i = 0; i < cd.body().nchildren; i++) {
			if (!(cd.body().children[i] instanceof FieldDecl)) {
				cd.body().children[i].visit(this);
			}
		}

//		int a =1;

		////test



//






//test
		// YOUR CODE HERE

		return null;
	}

	// CONSTRUCTOR DECLARATION
	public Object visitConstructorDecl(ConstructorDecl cd) {
		println(cd.line + ": ConstructorDecl: Generating Code for constructor for class " + cd.name().getname());

		classFile.startMethod(cd);

		classFile.addComment(cd, "Constructor Declaration");

		// 12/05/13 = removed if (just in case this ever breaks ;-) )
		cd.cinvocation().visit(this);

		classFile.addComment(cd, "Field Init Generation Start");
	   currentClass.visit(new GenerateFieldInits(gen,currentClass,false));




//		Iterator it = classFile.getFieldsIterator();
//		while (it.hasNext()) {
//
//			fldinit.visitFieldDecl(classFile.getFieldsIterator().next().getField());
//		}



		//note
		classFile.addComment(cd, "Field Init Generation End");
		cd.params().visit(this);
		cd.body().visit(this);



		// YOUR CODE HERE

		classFile.addInstruction(new Instruction(RuntimeConstants.opc_return));

		// We are done generating code for this method, so transfer it to the classDecl.
		cd.setCode(classFile.getCurrentMethodCode());
		classFile.endMethod();

		return null;
	}


	// CONTINUE STATEMENT
	public Object visitContinueStat(ContinueStat cs) {
		println(cs.line + ": ContinueStat:\tGenerating code.");
		classFile.addComment(cs, "Continue Statement");

		classFile.addInstruction(new JumpInstruction(RuntimeConstants.opc_goto, gen.getContinueLabel()));

		// YOUR CODE HERE

		classFile.addComment(cs, "End ContinueStat");
		return null;
	}

	// DO STATEMENT
	public Object visitDoStat(DoStat ds) {
		println(ds.line + ": DoStat:\tGenerating code.");
		classFile.addComment(ds, "Do Statement");

		// YOUR CODE HERE

		classFile.addComment(ds, "End DoStat");
		return null; 
	}


	// EXPRESSION STATEMENT
	public Object visitExprStat(ExprStat es) {	
		println(es.line + ": ExprStat:\tVisiting an Expression Statement.");
		classFile.addComment(es, "Expression Statement");

		es.expression().visit(this);

		if (es.expression() instanceof Invocation) {
			Invocation in = (Invocation)es.expression();
			if (in.targetMethod.returnType().isVoidType())
				println(es.line + ": ExprStat:\tInvocation of Void method where return value is not used anyways (no POP needed)."); 
			else {
				println(es.line + ": ExprStat:\tPOP added to remove non used return value for a '" + es.expression().getClass().getName() + "'.");
				gen.dup(es.expression().type, RuntimeConstants.opc_pop, RuntimeConstants.opc_pop2);
			}
		}
		else 
			if (!(es.expression() instanceof Assignment)) {
				gen.dup(es.expression().type, RuntimeConstants.opc_pop, RuntimeConstants.opc_pop2);
				println(es.line + ": ExprStat:\tPOP added to remove unused value left on stack for a '" + es.expression().getClass().getName() + "'.");
			}
		classFile.addComment(es, "End ExprStat");
		return null;
	}

	// FIELD DECLARATION
	public Object visitFieldDecl(FieldDecl fd) {
		println(fd.line + ": FieldDecl:\tGenerating code.");

		classFile.addField(fd);

		return null;
	}

	// FIELD REFERENCE
	public Object visitFieldRef(FieldRef fr) {
		println(fr.line + ": FieldRef:\tGenerating code (getfield code only!).");

		// Changed June 22 2012 Array
		// If we have and field reference with the name 'length' and an array target type
		if (fr.myDecl == null) { // We had a array.length reference. Not the nicest way to check!!
			classFile.addComment(fr, "Array length");
			fr.target().visit(this);
			classFile.addInstruction(new Instruction(RuntimeConstants.opc_arraylength));
			return null;
		}

		classFile.addComment(fr,  "Field Reference");

		// Note when visiting this node we assume that the field reference
		// is not a left hand side, i.e. we always generate 'getfield' code.

		// Generate code for the target. This leaves a reference on the 
		// stack. pop if the field is static!
		fr.target().visit(this);
		if (!fr.myDecl.modifiers.isStatic()) 
			classFile.addInstruction(new FieldRefInstruction(RuntimeConstants.opc_getfield, 
					fr.targetType.typeName(), fr.fieldName().getname(), fr.type.signature()));
		else {
			// If the target is that name of a class and the field is static, then we don't need a pop; else we do:
			if (!(fr.target() instanceof NameExpr && (((NameExpr)fr.target()).myDecl instanceof ClassDecl))) 
				classFile.addInstruction(new Instruction(RuntimeConstants.opc_pop));
			classFile.addInstruction(new FieldRefInstruction(RuntimeConstants.opc_getstatic,
					fr.targetType.typeName(), fr.fieldName().getname(),  fr.type.signature()));
		}
		classFile.addComment(fr, "End FieldRef");
		return null;
	}


	// FOR STATEMENT
	public Object visitForStat(ForStat fs) {
		println(fs.line + ": ForStat:\tGenerating code.");
		classFile.addComment(fs, "For Statement");
		String  start_label =  "L"+gen.getLabel();
		String  end_label =  "L"+gen.getLabel();
		String  inc_label =  "L"+gen.getLabel();
       if(!insideSwitch) {
		   gen.setContinueLabel(inc_label);
		   gen.setBreakLabel(end_label);
	   }

		if(fs.init()!=null)
			fs.init().visit(this);


		classFile.addInstruction(new LabelInstruction(RuntimeConstants.opc_label, start_label));

		if(fs.expr()!=null) {
			fs.expr().visit(this);
			classFile.addInstruction((new JumpInstruction(RuntimeConstants.opc_ifeq,end_label)));


		}

		if(fs.stats()!=null)
		{
			fs.stats().visit(this);
		}

		classFile.addInstruction((new LabelInstruction(RuntimeConstants.opc_label,inc_label)));
		if(fs.incr()!=null)
			fs.incr().visit(this);
         classFile.addInstruction(new JumpInstruction(RuntimeConstants.opc_goto,start_label));
		classFile.addInstruction((new LabelInstruction(RuntimeConstants.opc_label,end_label)));

//				gen.setContinueLabel(inc_label);
//		gen.setBreakLabel(end_label);

		// YOUR CODE HERE
		classFile.addComment(fs, "End ForStat");	
		return null;
	}

	// IF STATEMENT
	public Object visitIfStat(IfStat is) {
		println(is.line + ": IfStat:\tGenerating code.");
		classFile.addComment(is, "If Statement");

//		String label_false ="";
//		if(is.elsepart()!=null)
//		{
//			label_false = "L"+gen.getLabel();
//		}
//
//		String label_true = "L"+gen.getLabel();
//
//
//		if(is.expr()!=null)
//			is.expr().visit(this);
//
//		if(is.elsepart()!=null) {
//			classFile.addInstruction(new JumpInstruction(RuntimeConstants.opc_ifeq,label_false ));
//			is.thenpart().visit(this);
//			classFile.addInstruction(new JumpInstruction(RuntimeConstants.opc_goto,label_true));
//
//		}
//		if(is.elsepart()!=null)
//		{
//			classFile.addInstruction(new LabelInstruction(RuntimeConstants.opc_label,label_false));
//			is.elsepart().visit(this);
//		}
//		classFile.addInstruction(new LabelInstruction(RuntimeConstants.opc_label,label_true));

		String label1 = "L" + gen.getLabel();
		String label2 = "";
		if (is.elsepart() != null) {
			label2 = "L" + gen.getLabel();
		}

		if (is.expr() != null) {
			is.expr().visit(this);
		}


		classFile.addInstruction(new JumpInstruction(RuntimeConstants.opc_ifeq, label1));

		if (is.thenpart() != null) {
			is.thenpart().visit(this);
		}
		if (is.elsepart() != null) {
			classFile.addInstruction(new JumpInstruction(RuntimeConstants.opc_goto, label2));
		}

		classFile.addInstruction(new LabelInstruction(RuntimeConstants.opc_label, label1));

		if (is.elsepart() != null) {
			is.elsepart().visit(this);
			classFile.addInstruction(new LabelInstruction(RuntimeConstants.opc_label, label2));
		}




		// YOUR CODE HERE
		classFile.addComment(is,  "End IfStat");
		return null;
	}


	// INVOCATION
	public Object visitInvocation(Invocation in) {
		println(in.line + ": Invocation:\tGenerating code for invoking method '" + in.methodName().getname() + "' in class '"+in.targetMethod.getMyClass().name()+"'.");
		classFile.addComment(in, "Invocation");
		println(in.line + ": Invocation:\tGenerating code for the target.");
//		if(in.targetMethod.isStatic())
//		{
//			in.target().visit(this);
//			classFile.addInstruction(new Instruction(RuntimeConstants.opc_pop));
//			println((in.line+"Invocation:\tIssuing a POP instruction to remove target reference; not needed for static invocation."));
//
//		}

		if (in.targetMethod.isStatic()) {
			if (in.target() != null) {

				in.target().visit(this);
                  if(!(in.target() instanceof NameExpr)) {


//				if(in.targetMethod instanceof ClassDecl) {
					  println(in.line + ": Invocation:\tIssuing a POP instruction to remove target reference; not needed for static invocation.");
					  classFile.addInstruction(new Instruction(RuntimeConstants.opc_pop));
				  }

			}
		} else {
			if (in.target() == null) {
				classFile.addInstruction(new Instruction(RuntimeConstants.opc_aload_0));
			} else {
				in.target().visit(this);
			}
		}

		in.params().visit(this);
		if (!in.targetMethod.isStatic()) {

			int a=1;
			if(in.target()instanceof Super)
				classFile.addInstruction(new MethodInvocationInstruction(RuntimeConstants.opc_invokespecial, in.targetMethod.getMyClass().name(), in.methodName().getname(), "(" + in.targetMethod.paramSignature() + ")" + in.targetMethod.returnType().signature()));
            else if(in.targetMethod.getMyClass().isInterface())
			{
				classFile.addInstruction(new InterfaceInvocationInstruction(RuntimeConstants.opc_invokeinterface, in.targetMethod.getMyClass().name(), in.methodName().getname(), "(" + in.targetMethod.paramSignature() + ")" + in.targetMethod.returnType().signature(),(in.params().nchildren)+1));

			}
			else
			classFile.addInstruction(new MethodInvocationInstruction(RuntimeConstants.opc_invokevirtual, in.targetMethod.getMyClass().name(), in.methodName().getname(), "(" + in.targetMethod.paramSignature() + ")" + in.targetMethod.returnType().signature()));
		}

		else {
			int b=1;
			classFile.addInstruction(new MethodInvocationInstruction(RuntimeConstants.opc_invokestatic, in.targetMethod.getMyClass().name(), in.methodName().getname(), "(" + in.targetMethod.paramSignature() + ")" + in.targetMethod.returnType().signature()));
		}



		// YOUR CODE HERE






		classFile.addComment(in, "End Invocation");

//		StringBuilderCreated = oldStringBuilderCreated;
		return null;
	}

	// LITERAL
	public Object visitLiteral(Literal li) {
		println(li.line + ": Literal:\tGenerating code for Literal '" + li.getText() + "'.");
		classFile.addComment(li, "Literal");

		switch (li.getKind()) {
		case Literal.ByteKind:
		case Literal.CharKind:
		case Literal.ShortKind:
		case Literal.IntKind:
			gen.loadInt(li.getText());
			break;
		case Literal.NullKind:
			classFile.addInstruction(new Instruction(RuntimeConstants.opc_aconst_null));
			break;
		case Literal.BooleanKind:
			if (li.getText().equals("true")) 
				classFile.addInstruction(new Instruction(RuntimeConstants.opc_iconst_1));
			else
				classFile.addInstruction(new Instruction(RuntimeConstants.opc_iconst_0));
			break;
		case Literal.FloatKind:
			gen.loadFloat(li.getText());
			break;
		case Literal.DoubleKind:
			gen.loadDouble(li.getText());
			break;
		case Literal.StringKind:
			gen.loadString(li.getText());
			break;
		case Literal.LongKind:
			gen.loadLong(li.getText());
			break;	    
		}
		classFile.addComment(li,  "End Literal");
		return null;
	}

	// LOCAL VARIABLE DECLARATION
	public Object visitLocalDecl(LocalDecl ld) {
		if (ld.var().init() != null) {
			println(ld.line + ": LocalDecl:\tGenerating code for the initializer for variable '" + 
					ld.var().name().getname() + "'.");
			classFile.addComment(ld, "Local Variable Declaration");


			Boolean oldRHSofAssignment = RHSofAssignment;
			RHSofAssignment = true;

			super.visitLocalDecl(ld);

			RHSofAssignment =oldRHSofAssignment;
			int address = ld.address();
			if(ld.type().isIntegerType() || ld.type().isCharType() || ld.type().isBooleanType()) {
//				classFile.addInstruction(new Instruction(RuntimeConstants.opc_iload));
				if (address < 4) {
					String str_opc = ld.type().getTypePrefix() + "store_" + address;
					classFile.addInstruction(new Instruction(Generator.getOpCodeFromString(str_opc)));
				} else {
					String str_opc = ld.type().getTypePrefix() + "store";
					classFile.addInstruction(new SimpleInstruction(Generator.getOpCodeFromString(str_opc), address));

				}
			}
			if(ld.type().isClassType())
			{
				if(address<4) {
					String str_opc = ld.type().getTypePrefix() + "store_" + address;
					classFile.addInstruction(new Instruction(Generator.getOpCodeFromString(str_opc)));
				}
				else
				{
					String str_opc = ld.type().getTypePrefix() + "store";
					classFile.addInstruction(new SimpleInstruction(Generator.getOpCodeFromString(str_opc),address));

				}
			}


			// YOUR CODE HERE
			classFile.addComment(ld, "End LocalDecl");
		}
		else
			println(ld.line + ": LocalDecl:\tVisiting local variable declaration for variable '" + ld.var().name().getname() + "'.");

		return null;
	}

	// METHOD DECLARATION
	public Object visitMethodDecl(MethodDecl md) {
		println(md.line + ": MethodDecl:\tGenerating code for method '" + md.name().getname() + "'.");	
		classFile.startMethod(md);

		classFile.addComment(md, "Method Declaration (" + md.name() + ")");

		if (md.block() !=null) 
			md.block().visit(this);
		gen.endMethod(md);
		return null;
	}


	// NAME EXPRESSION
	public Object visitNameExpr(NameExpr ne) {
		classFile.addComment(ne, "Name Expression --");


		// ADDED 22 June 2012 
		if (ne.myDecl instanceof ClassDecl) {
			println(ne.line + ": NameExpr:\tWas a class name - skip it :" + ne.name().getname());
			classFile.addComment(ne, "End NameExpr");
			return null;
		}
		println(ne.line + ": NameExpr:\tGenerating code for a local var/param (access) for '" + ne.name().getname() + "'.");

		if(ne.myDecl instanceof LocalDecl)
		{
			int address = ((LocalDecl) ne.myDecl).address();
			if(ne.type.isIntegerType() || ne.type.isCharType() || ne.type.isBooleanType()){
//				classFile.addInstruction(new Instruction(RuntimeConstants.opc_iload));
				if(address<4){
				String str_opc = ((LocalDecl) ne.myDecl).type().getTypePrefix()+"load_" +address;
				classFile.addInstruction(new Instruction(Generator.getOpCodeFromString(str_opc)));
				}
				else
				{
					String str_opc = ((LocalDecl) ne.myDecl).type().getTypePrefix()+"load";
					classFile.addInstruction(new SimpleInstruction(Generator.getOpCodeFromString(str_opc),address));
				}


			}
			if(ne.type.isClassType())
			{
				if(address<4) {
					String str_opc = ((LocalDecl) ne.myDecl).type().getTypePrefix() + "load_" + address;
					classFile.addInstruction(new Instruction(Generator.getOpCodeFromString(str_opc)));
				}
				else
				{
					String str_opc = ((LocalDecl) ne.myDecl).type().getTypePrefix()+"load";
					classFile.addInstruction(new SimpleInstruction(Generator.getOpCodeFromString(str_opc),address));


				}


			}



		}

		if(ne.myDecl instanceof ParamDecl)
		{
			int address = ((ParamDecl) ne.myDecl).address();
			if(address<4) {
				String str_opc = ((ParamDecl) ne.myDecl).type().getTypePrefix() + "load_" + address;
				classFile.addInstruction(new Instruction(Generator.getOpCodeFromString(str_opc)));
			}

			else
			{
				String str_opc = ((ParamDecl) ne.myDecl).type().getTypePrefix()+"load";
				classFile.addInstruction(new SimpleInstruction(Generator.getOpCodeFromString(str_opc),address));


			}

			int  a= 1;


		}



		// YOUR CODE HERE

		classFile.addComment(ne, "End NameExpr");
		return null;
	}

	// NEW
	public Object visitNew(New ne) {
		println(ne.line + ": New:\tGenerating code");
		classFile.addComment(ne, "New");
//		classFile.addInstruction(new Instruction(RuntimeConstants.opc_new));
		classFile.addInstruction(new ClassRefInstruction(RuntimeConstants.opc_new,ne.getConstructorDecl().getname()));
        classFile.addInstruction(new Instruction(RuntimeConstants.opc_dup));
		ne.type().visit(this);
		ne.args().visit(this);
		String i;
		String to;
        if((ne.args().nchildren)>0 && (ne.getConstructorDecl().params().nchildren)>0) {
			if ((ne.args().children[0]) instanceof NameExpr) {
				if ((((NameExpr) ne.args().children[0]).myDecl) instanceof ParamDecl) {
					i = ((ParamDecl) ((NameExpr) ne.args().children[0]).myDecl).type().getTypePrefix();
					to = ((ParamDecl) (ne.getConstructorDecl().params().children[0])).type().getTypePrefix();
					if (i != to) {
						String opt = i + "2" + to;
						classFile.addInstruction(new Instruction(Generator.getOpCodeFromString(opt)));
					}
				}
			}
		}





//		if(ne.type().signature().contains("L"))
//
//			classFile.addInstruction(new Instruction(RuntimeConstants.opc_i2l));

		classFile.addInstruction(new MethodInvocationInstruction(RuntimeConstants.opc_invokespecial,ne.getConstructorDecl().getname(),"<init>","("+ne.getConstructorDecl().paramSignature()+")"+"V"));

		// YOUR CODE HERE

		classFile.addComment(ne, "End New");
		return null;
	}

	// RETURN STATEMENT
	public Object visitReturnStat(ReturnStat rs) {
		println(rs.line + ": ReturnStat:\tGenerating code.");
		classFile.addComment(rs, "Return Statement");
		Boolean oldRHSofAssignment = RHSofAssignment;
		RHSofAssignment = true;

		super.visitReturnStat(rs);
		RHSofAssignment = oldRHSofAssignment;
		if (rs.getType() == null) {
			classFile.addInstruction(new Instruction(RuntimeConstants.opc_return));
		} else if (rs.getType().isClassType()) {
			classFile.addInstruction(new Instruction(RuntimeConstants.opc_areturn));
		} else {
			String instString = rs.getType().getTypePrefix() + "return";
			classFile.addInstruction(new Instruction(Generator.getOpCodeFromString(instString)));
		}

		classFile.addComment(rs, "End ReturnStat");
		return null;
	}

	// STATIC INITIALIZER
	public Object visitStaticInitDecl(StaticInitDecl si) {
		println(si.line + ": StaticInit:\tGenerating code for a Static initializer.");	

		classFile.startMethod(si);
		classFile.addComment(si, "Static Initializer");
		super.visitStaticInitDecl(si);
		currentClass.visit(new GenerateFieldInits(gen,currentClass,true));

		classFile.addInstruction(new Instruction(RuntimeConstants.opc_return));

		// YOUR CODE HERE

		si.setCode(classFile.getCurrentMethodCode());
		classFile.endMethod();
		return null;
	}

	// SUPER
	public Object visitSuper(Super su) {
		println(su.line + ": Super:\tGenerating code (access).");	
		classFile.addComment(su, "Super");
		classFile.addInstruction(new Instruction(RuntimeConstants.opc_aload_0));

		// YOUR CODE HERE

		classFile.addComment(su, "End Super");
		return null;
	}

	// SWITCH STATEMENT
	public Object visitSwitchStat(SwitchStat ss) {
		println(ss.line + ": Switch Statement:\tGenerating code for Switch Statement.");
		int def = -1;
		SortedMap<Object, SwitchLabel> sm = new TreeMap<Object, SwitchLabel>();
		classFile.addComment(ss,  "Switch Statement");

		SwitchGroup sg = null;
		SwitchLabel sl = null;

		// just to make sure we can do breaks;
		boolean oldinsideSwitch = insideSwitch;
		insideSwitch = true;
		String oldBreakLabel = Generator.getBreakLabel();
		Generator.setBreakLabel("L"+gen.getLabel());

		// Generate code for the item to switch on.
		ss.expr().visit(this);	
		// Write the lookup table
		for (int i=0;i<ss.switchBlocks().nchildren; i++) {
			sg = (SwitchGroup)ss.switchBlocks().children[i];
			sg.setLabel(gen.getLabel());
			for(int j=0; j<sg.labels().nchildren;j++) {
				sl = (SwitchLabel)sg.labels().children[j];
				sl.setSwitchGroup(sg);
				if (sl.isDefault())
					def = i;
				else
					sm.put(sl.expr().constantValue(), sl);
			}
		}

		for (Iterator<Object> ii=sm.keySet().iterator(); ii.hasNext();) {
			sl = sm.get(ii.next());
		}

		// default comes last, if its not there generate an empty one.
		if (def != -1) {
			classFile.addInstruction(new LookupSwitchInstruction(RuntimeConstants.opc_lookupswitch, sm, 
					"L" + ((SwitchGroup)ss.switchBlocks().children[def]).getLabel()));
		} else {
			// if no default label was there then just jump to the break label.
			classFile.addInstruction(new LookupSwitchInstruction(RuntimeConstants.opc_lookupswitch, sm, 
					Generator.getBreakLabel()));
		}

		// Now write the code and the labels.
		for (int i=0;i<ss.switchBlocks().nchildren; i++) {
			sg = (SwitchGroup)ss.switchBlocks().children[i];
			classFile.addInstruction(new LabelInstruction(RuntimeConstants.opc_label, "L"+sg.getLabel()));
			sg.statements().visit(this);
		}

		// Put the break label in;
		classFile.addInstruction(new LabelInstruction(RuntimeConstants.opc_label, Generator.getBreakLabel()));
		insideSwitch = oldinsideSwitch;
		Generator.setBreakLabel(oldBreakLabel);
		classFile.addComment(ss, "End SwitchStat");
		return null;
	}

	// TERNARY EXPRESSION 
	public Object visitTernary(Ternary te) {
		println(te.line + ": Ternary:\tGenerating code.");
		classFile.addComment(te, "Ternary Statement");


		boolean OldStringBuilderCreated = StringBuilderCreated;
		StringBuilderCreated = false;
		String label1 = "L"+gen.getLabel();
		String label2 = "L"+ gen.getLabel();
		if (te.expr() != null) {
			te.expr().visit(this);
		}


		classFile.addInstruction(new JumpInstruction(RuntimeConstants.opc_ifeq, label1));

		if (te.trueBranch() != null) {
			te.trueBranch().visit(this);
		}
		if (te.falseBranch() != null) {
			classFile.addInstruction(new JumpInstruction(RuntimeConstants.opc_goto, label2));
		}

		classFile.addInstruction(new LabelInstruction(RuntimeConstants.opc_label, label1));

		if (te.falseBranch() != null) {
			te.falseBranch().visit(this);
			classFile.addInstruction(new LabelInstruction(RuntimeConstants.opc_label, label2));
		}


		// YOUR CODE HERE
		classFile.addComment(te, "Ternary");
		StringBuilderCreated = OldStringBuilderCreated;
		return null;
	}

	// THIS
	public Object visitThis(This th) {
		println(th.line + ": This:\tGenerating code (access).");       
		classFile.addComment(th, "This");
		classFile.addInstruction(new Instruction(RuntimeConstants.opc_aload_0));

		// YOUR CODE HERE

		classFile.addComment(th, "End This");
		return null;
	}

	// UNARY POST EXPRESSION
	public Object visitUnaryPostExpr(UnaryPostExpr up) {
		println(up.line + ": UnaryPostExpr:\tGenerating code.");
		classFile.addComment(up, "Unary Post Expression");

		if(up.expr() instanceof NameExpr)
		{
			up.expr().visit(this);
			if (up.expr().type.isIntegerType())
			{

				if(((NameExpr) up.expr()).myDecl instanceof LocalDecl)
					if(up.op().getKind() == PostOp.PLUSPLUS)
						classFile.addInstruction(new IincInstruction(RuntimeConstants.opc_iinc,((LocalDecl)((NameExpr)up.expr()).myDecl).address,1));
                    else
						classFile.addInstruction(new IincInstruction(RuntimeConstants.opc_iinc,((LocalDecl)((NameExpr)up.expr()).myDecl).address,-1));
				if(((NameExpr) up.expr()).myDecl instanceof ParamDecl)
					if(up.op().getKind() == PostOp.PLUSPLUS)
						classFile.addInstruction(new IincInstruction(RuntimeConstants.opc_iinc,((ParamDecl)((NameExpr)up.expr()).myDecl).address,1));
					else
						classFile.addInstruction(new IincInstruction(RuntimeConstants.opc_iinc,((ParamDecl)((NameExpr)up.expr()).myDecl).address,-1));

			}

			else
			{
				classFile.addInstruction(new Instruction(RuntimeConstants.opc_dup));
				String insstring = up.expr().type.getTypePrefix()+"load_1";
				classFile.addInstruction(new Instruction(Generator.getOpCodeFromString(insstring)));

				if (up.op().getKind() == PostOp.PLUSPLUS) {
					String test =up.expr().type.getTypePrefix()+"add";
					classFile.addInstruction(new Instruction(Generator.getOpCodeFromString(test)));
				} else if (up.op().getKind() == PostOp.MINUSMINUS) {
					String test =up.expr().type.getTypePrefix()+"sub";
					classFile.addInstruction(new Instruction(Generator.getOpCodeFromString(test)));
				}
			}

		}

		if(up.expr() instanceof FieldRef)
		{
			if(((FieldRef) up.expr()).myDecl.isStatic())
			{
				((FieldRef) up.expr()).target().visit(this);

//				classFile.addInstruction(new Instruction(RuntimeConstants.opc_pop));
				classFile.addInstruction(new FieldRefInstruction(RuntimeConstants.opc_getstatic,((FieldRef)up.expr()).targetType.typeName(),((FieldRef)up.expr()).myDecl.name(),((FieldRef)up.expr()).type.signature()));
                classFile.addInstruction(new Instruction(RuntimeConstants.opc_dup));


			}
			if(!(((FieldRef) up.expr()).myDecl.isStatic()))
			{
				((FieldRef) up.expr()).target().visit(this);
				classFile.addInstruction(new Instruction(RuntimeConstants.opc_dup));
				classFile.addInstruction(new FieldRefInstruction(RuntimeConstants.opc_getfield,((FieldRef)up.expr()).targetType.typeName(),((FieldRef)up.expr()).myDecl.name(),((FieldRef)up.expr()).type.signature()));
			    classFile.addInstruction(new Instruction(RuntimeConstants.opc_dup_x1));
			}
			String test =up.expr().type.getTypePrefix()+"const_1";
			classFile.addInstruction(new Instruction(Generator.getOpCodeFromString(test)));
			if (up.op().getKind() == PostOp.PLUSPLUS) {
				String add =up.expr().type.getTypePrefix()+"add";
				classFile.addInstruction(new Instruction(Generator.getOpCodeFromString(add)));
			} else if (up.op().getKind() == PostOp.MINUSMINUS) {
				String sub =up.expr().type.getTypePrefix()+"sub";
				classFile.addInstruction(new Instruction(Generator.getOpCodeFromString(sub)));
			}
			if(((FieldRef) up.expr()).myDecl.isStatic()){
				classFile.addInstruction(new FieldRefInstruction(RuntimeConstants.opc_putstatic,currentClass.name(),((FieldRef)up.expr()).myDecl.name(),((FieldRef)up.expr()).type.signature()));



			}
			else

			classFile.addInstruction(new FieldRefInstruction(RuntimeConstants.opc_putfield,((FieldRef)up.expr()).targetType.typeName(),((FieldRef)up.expr()).myDecl.name(),((FieldRef)up.expr()).type.signature()));



		}


		// YOUR CODE HERE

		classFile.addComment(up, "End UnaryPostExpr");
		return null;
	}

	// UNARY PRE EXPRESSION
	public Object visitUnaryPreExpr(UnaryPreExpr up) {
		println(up.line + ": UnaryPreExpr:\tGenerating code for " + up.op().operator() + " : " + up.expr().type.typeName() + " -> " + up.expr().type.typeName() + ".");
		classFile.addComment(up,"Unary Pre Expression");
		if(up.op().getKind() == PreOp.PLUS) {
			up.expr().visit(this);
		}
		if(up.op().getKind() == PreOp.MINUS)
		{
			up.expr().visit(this);
			String instString = up.expr().type.getTypePrefix()+"neg";
			classFile.addInstruction(new Instruction(Generator.getOpCodeFromString(instString)));
		}
		if(up.op().getKind() == PreOp.COMP)
		{
			up.expr().visit(this);
			if(up.expr().type.isIntegerType()) {
				classFile.addInstruction(new Instruction(RuntimeConstants.opc_iconst_m1));
				classFile.addInstruction(new Instruction(RuntimeConstants.opc_ixor));

			}
			else
			{
				classFile.addInstruction(new Instruction(RuntimeConstants.opc_ldc2_w));
				classFile.addInstruction(new Instruction(RuntimeConstants.opc_lxor));

			}

		}

		if(up.op().getKind() == PreOp.NOT) {
			up.expr().visit(this);
			classFile.addInstruction(new Instruction(RuntimeConstants.opc_iconst_1));
			classFile.addInstruction(new Instruction(RuntimeConstants.opc_ixor));
		}

//
		if((up.op().getKind() == PreOp.PLUSPLUS) || (up.op().getKind() == PreOp.MINUSMINUS)) {
			if (up.expr() instanceof NameExpr) {

				if (up.expr().type.isIntegerType()) {

					if (((NameExpr) up.expr()).myDecl instanceof LocalDecl)
						if (up.op().getKind() == PostOp.PLUSPLUS)
							classFile.addInstruction(new IincInstruction(RuntimeConstants.opc_iinc, ((LocalDecl) ((NameExpr) up.expr()).myDecl).address, 1));
						else
							classFile.addInstruction(new IincInstruction(RuntimeConstants.opc_iinc, ((LocalDecl) ((NameExpr) up.expr()).myDecl).address, -1));
					if (((NameExpr) up.expr()).myDecl instanceof ParamDecl)
						if (up.op().getKind() == PostOp.PLUSPLUS)
							classFile.addInstruction(new IincInstruction(RuntimeConstants.opc_iinc, ((ParamDecl) ((NameExpr) up.expr()).myDecl).address, 1));
						else
							classFile.addInstruction(new IincInstruction(RuntimeConstants.opc_iinc, ((ParamDecl) ((NameExpr) up.expr()).myDecl).address, -1));

					up.expr().visit(this);
				} else {
					up.expr().visit(this);
					String insstring = up.expr().type.getTypePrefix() + "const_1";
					classFile.addInstruction(new Instruction(Generator.getOpCodeFromString(insstring)));

					if (up.op().getKind() == PostOp.PLUSPLUS) {
						String test = up.expr().type.getTypePrefix() + "add";
						classFile.addInstruction(new Instruction(Generator.getOpCodeFromString(test)));
					} else if (up.op().getKind() == PostOp.MINUSMINUS) {
						String test = up.expr().type.getTypePrefix() + "sub";
						classFile.addInstruction(new Instruction(Generator.getOpCodeFromString(test)));

					}
				}
			}
		}
			// YOUR CODE HERE

		classFile.addComment(up, "End UnaryPreExpr");
		return null;
	}

	// WHILE STATEMENT
	public Object visitWhileStat(WhileStat ws) {
		println(ws.line + ": While Stat:\tGenerating Code.");

		classFile.addComment(ws, "While Statement");
		String label_start = "L"+gen.getLabel();
		String label_end = "L"+ gen.getLabel();

		classFile.addInstruction(new LabelInstruction(RuntimeConstants.opc_label,label_start));

		ws.expr().visit(this);
		classFile.addInstruction(new JumpInstruction(RuntimeConstants.opc_ifeq,label_end));
		ws.stat().visit(this);

		classFile.addInstruction(new JumpInstruction(RuntimeConstants.opc_goto,label_start));
		classFile.addInstruction(new LabelInstruction(RuntimeConstants.opc_label,label_end));





		// YOUR CODE HERE

		classFile.addComment(ws, "End WhileStat");	
		return null;
	}
}

