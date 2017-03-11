package ModifierChecker;

import AST.*;
import Utilities.*;
import NameChecker.*;
import TypeChecker.*;
import Utilities.Error;

public class ModifierChecker extends Visitor {

	private SymbolTable classTable;
	private ClassDecl currentClass;
	private ClassBodyDecl currentContext;
	private boolean leftHandSide = false;

	public ModifierChecker(SymbolTable classTable, boolean debug) {
		this.classTable = classTable;
		this.debug = debug;
	}

	/** Assignment */
	public Object visitAssignment(Assignment as) {
	    println(as.line + ": Visiting an assignment (Operator: " + as.op()+ ")");

		boolean oldLeftHandSide = leftHandSide;

		leftHandSide = true;
		as.left().visit(this);

		// Added 06/28/2012 - no assigning to the 'length' field of an array type
		if (as.left() instanceof FieldRef) {
			FieldRef fr = (FieldRef)as.left();
			if (fr.target().type.isArrayType() && fr.fieldName().getname().equals("length"))
				Error.error(fr,"Cannot assign a value to final variable length.");
		}

		leftHandSide = oldLeftHandSide;
		as.right().visit(this);

		return null;
	}

	/** CInvocation */
	public Object visitCInvocation(CInvocation ci) {
	    println(ci.line + ": Visiting an explicit constructor invocation (" + (ci.superConstructorCall() ? "super" : "this") + ").");

		// YOUR CODE HERE
		super.visitCInvocation(ci);

		return null;
	}

	/** ClassDecl */
	public Object visitClassDecl(ClassDecl cd) {
		println(cd.line + ": Visiting a class declaration for class '" + cd.name() + "'.");

		currentClass = cd;

		// If this class has not yet been declared public make it so.
		if (!cd.modifiers.isPublic())
			cd.modifiers.set(true, false, new Modifier(Modifier.Public));

		// If this is an interface declare it abstract!
		if (cd.isInterface() && !cd.modifiers.isAbstract())
			cd.modifiers.set(false, false, new Modifier(Modifier.Abstract));

		// If this class extends another class then make sure it wasn't declared
		// final.
		if (cd.superClass() != null)
			if (cd.superClass().myDecl.modifiers.isFinal())
				Error.error(cd, "Class '" + cd.name()
						+ "' cannot inherit from final class '"
						+ cd.superClass().typeName() + "'.");

		// YOUR CODE HERE
		super.visitClassDecl(cd);

		return null;
	}

	/** FieldDecl */
	public Object visitFieldDecl(FieldDecl fd) {
	    println(fd.line + ": Visiting a field declaration for field '" +fd.var().name() + "'.");

		// If field is not private and hasn't been declared public make it so.
		if (!fd.modifiers.isPrivate() && !fd.modifiers.isPublic())
			fd.modifiers.set(false, false, new Modifier(Modifier.Public));

		if(fd.getModifiers().isFinal())
		{
			if (fd.var().init() == null) {
				Error.error(fd,"Final field '"+fd.name()+"' in class '"+currentClass.name()+"' must be initialized.");

			}
		}

		currentContext = fd;
		super.visitFieldDecl(fd);
		currentContext = null;

		// YOUR CODE HERE

		return null;
	}

	/** FieldRef */
	public Object visitFieldRef(FieldRef fr) {
	    println(fr.line + ": Visiting a field reference '" + fr.fieldName() + "'.");

		if(fr.myDecl != null) {

			if (leftHandSide && (((FieldDecl) fr.myDecl)).getModifiers().isFinal())
				Error.error(fr, "Cannot assign a value to final field '" + ((FieldRef) fr).myDecl.name() + "'.");
			if (fr.target() instanceof NameExpr) {
				if ((((NameExpr) fr.target()).myDecl) instanceof ClassDecl) {
					if (currentContext != null) {
						if (currentContext.isStatic() && !fr.myDecl.isStatic()) {
							Error.error(fr, "non-static field '" + fr.myDecl.name() + "' cannot be referenced in a static context.");
						}
					}
				}

			}
			if(fr.target() instanceof  This)
				if(currentContext.isStatic() && !(fr.myDecl.isStatic()) && fr.rewritten)
				{
					Error.error(fr, "non-static field '" + fr.myDecl.name() + "' cannot be referenced from a static context.");
				}

			if (((FieldDecl) fr.myDecl).getModifiers().isPrivate()) {

				if(((ClassType) fr.targetType).name().toString()!= currentClass.name()){

					Error.error(fr, "field '" + fr.myDecl.name() + "' was declared 'private' and cannot be accessed outside its class.");

				}



			}
		}



		super.visitFieldRef(fr);


		// YOUR CODE HERE

		return null;
	}

	/** MethodDecl */
	public Object visitMethodDecl(MethodDecl md) {
		currentContext =md;
	    println(md.line + ": Visiting a method declaration for method '" + md.name() + "'.");
		if(currentClass.isInterface() && md.getModifiers().isFinal())
		{
			Error.error(md,"Method '"+md.getname()+"' cannot be declared final in an interface.");
		}
		if(currentClass.getModifiers().isAbstract() && md.block() == null ) {
			if (!md.getModifiers().isAbstract() && !currentClass.isInterface() ) {
				Error.error(md, "Method '" + md.getname() + "' does not have a body, or should be declared abstract.");
			}
		}
		if(md.getModifiers().isAbstract() && md.block() != null)
		{
			Error.error(md,"Abstract method '"+md.getname()+"' cannot have a body.");
		}
        if(currentClass.superClass() !=null) {
			MethodDecl mthdecl = (MethodDecl) TypeChecker.findMethod(currentClass.superClass().myDecl.allMethods,
					md.getname(), md.params(), true);
			if(mthdecl!=null && mthdecl instanceof MethodDecl)
			{
				if(!mthdecl.isStatic() && currentContext.isStatic())
				{
					Error.error(md, "Method '" + md.getname() + "' declared non-static in superclass, cannot be reimplemented static.");
				}
				if(mthdecl.isStatic() && !currentContext.isStatic())
				{
					Error.error(md, "Method '" + md.getname() + "' declared static in superclass, cannot be reimplemented non-static.");
				}
				if(mthdecl.getModifiers().isFinal()) {
					Error.error(md, "Method '" + md.getname() + "' was implemented as final in super class, cannot be reimplemented.");
				}

			}
		}
		else {
			MethodDecl mthdecl = (MethodDecl) TypeChecker.findMethod(currentClass.allMethods,
					md.getname(), md.params(), true);
		}

		int a= 5;
		super.visitMethodDecl(md);

		// YOUR CODE HERE
		currentContext=null;

		return null;
	}

	/** Invocation */
	public Object visitInvocation(Invocation in) {
	    println(in.line + ": Visiting an invocation of method '" + in.methodName() + "'.");


		if(in.targetMethod.getModifiers().isPrivate()) {
			if (currentClass.name() != in.targetMethod.getMyClass().name())
				Error.error(in, in.targetMethod.name() + "( ) has private access in '" + ((ClassType) ((ClassType) in.targetType)).myDecl.name() + "'.");
		}
		if(in.target() != null)
		{
			in.target().visit(this);
			if(in.target() instanceof NameExpr) {
				if ((((NameExpr) in.target()).myDecl) instanceof ClassDecl && !in.targetMethod.isStatic())
					Error.error(in, "non-static method '" + in.methodName().getname()
							+ "' cannot be referenced from a static context.");
			}

		}
		if(in.target()==null)
		{
			if(currentContext.isStatic() && !in.targetMethod.isStatic())
			{
				Error.error(in, "non-static method '" + in.methodName().getname()
						+ "' cannot be referenced from a static context.");
			}

		}
		in.params().visit(this);
		return null;
	}


	public Object visitNameExpr(NameExpr ne) {
	    println(ne.line + ": Visiting a name expression '" + ne.name() + "'. (Nothing to do!)");
	    return null;
	}

	/** ConstructorDecl */
	public Object visitConstructorDecl(ConstructorDecl cd) {
		currentContext = cd;
	    println(cd.line + ": visiting a constructor declaration for class '" + cd.name() + "'.");
        super.visitConstructorDecl(cd);
		currentContext = null;
		// YOUR CODE HERE

		return null;
	}

	/** New */
	public Object visitNew(New ne) {
	    println(ne.line + ": visiting a new '" + ne.type().myDecl.name() + "'.");
		if((currentClass.name()) != (ne.type().myDecl.name())) {
			if (ne.getConstructorDecl().getModifiers().isPrivate())
				Error.error(ne, ne.type().myDecl.name() + "( ) has private access in '" + ne.type().myDecl.name() + "'.");

			if(ne.type().myDecl.getModifiers().isAbstract())
				Error.error(ne,"Cannot instantiate abstract class '"+ne.type().myDecl.name()+"'.");

		}


		super.visitNew(ne);

		// YOUR CODE HERE

		return null;
	}

	/** StaticInit */
	public Object visitStaticInitDecl(StaticInitDecl si) {
		println(si.line + ": visiting a static initializer");
		super.visitStaticInitDecl(si);

		// YOUR CODE HERE

		return null;
	}

	/** Super */
	public Object visitSuper(Super su) {
		println(su.line + ": visiting a super");

		if (currentContext.isStatic())
			Error.error(su,
					"non-static variable super cannot be referenced from a static context");

		return null;
	}

	/** This */
	public Object visitThis(This th) {
		println(th.line + ": visiting a this");

		if (currentContext.isStatic())
			Error.error(th,	"non-static variable this cannot be referenced from a static context");

		return null;
	}

	/** UnaryPostExpression */
    public Object visitUnaryPostExpr(UnaryPostExpr up) {
	println(up.line + ": visiting a unary post expression with operator '" + up.op() + "'.");
		boolean old_value = leftHandSide;
		leftHandSide = true;

		super.visitUnaryPostExpr(up);
		leftHandSide =old_value;

	// YOUR CODE HERE
	return null;
    }
    
    /** UnaryPreExpr */
    public Object visitUnaryPreExpr(UnaryPreExpr up) {
	println(up.line + ": visiting a unary pre expression with operator '" + up.op() + "'.");
		boolean old_value = leftHandSide;
		leftHandSide = true;
	super.visitUnaryPreExpr(up);
		leftHandSide =old_value;
	// YOUR CODE HERE
	return null;
    }
}
