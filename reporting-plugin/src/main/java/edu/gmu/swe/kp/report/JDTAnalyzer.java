package edu.gmu.swe.kp.report;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.file.Paths;
import java.util.*;

public class JDTAnalyzer {

	public static HashSet<ClassInfo> collectStructuralElements(byte[] src) throws FileNotFoundException, UnsupportedEncodingException {

		ASTParser p = ASTParser.newParser(AST.JLS8);
		p.setBindingsRecovery(false);
		p.setResolveBindings(false);
		p.setUnitName("App.java");
		p.setEnvironment(new String[0], new String[0], new String[0], true);
		Map<String, String> options = JavaCore.getOptions();
		JavaCore.setComplianceOptions(JavaCore.VERSION_1_8, options);
		p.setCompilerOptions(options);
		p.setKind(ASTParser.K_COMPILATION_UNIT);

		p.setSource(new String(src, "UTF-8").toCharArray()); //TODO fix char encodings
		final CompilationUnit root = (CompilationUnit) p.createAST(null);
		for (IProblem prob : root.getProblems()) {
			System.out.println(prob);
		}

		final HashSet<ClassInfo> ret = new HashSet<ClassInfo>();
//		ASTRewrite rewriter = ASTRewrite.create(root.getAST());

		root.accept(new ASTVisitor() {
			ClassInfo thisClass = null;

			String packageName;

			@Override
			public boolean visit(PackageDeclaration node) {
				packageName = node.getName().getFullyQualifiedName();
				return super.visit(node);
			}

			@Override
			public void endVisit(TypeDeclaration node) {
				thisClass = thisClass.parent;
				super.endVisit(node);
			}

			@Override
			public boolean visit(EmptyStatement node) {
				return super.visit(node);
			}

			@Override
			public boolean visit(AnonymousClassDeclaration node) {
				String name = thisClass.className + "$" + thisClass.anonCounter;
				thisClass.anonCounter++;
				ClassInfo newThisClass = new ClassInfo();
				newThisClass.className = name;
				newThisClass.parent = thisClass;
				thisClass = newThisClass;
				LinkedList<Edit> editsThisType = new LinkedList<Edit>();
				int startLine = root.getLineNumber(node.getStartPosition());
				int endLine = root.getLineNumber(node.getStartPosition() + node.getLength());
				ret.add(thisClass);

				return super.visit(node);
			}

			@Override
			public void endVisit(AnonymousClassDeclaration node) {
				thisClass = thisClass.parent;
				super.endVisit(node);
			}

			@Override
			public void endVisit(EnumDeclaration node) {
				thisClass = thisClass.parent;
				super.endVisit(node);
			}

			@Override
			public boolean visit(EnumDeclaration node) {
				String name = (packageName == null ? "" : packageName + ".") + node.getName().toString();
				name = name.replace('.', '/');
				if (thisClass == null) {
					thisClass = new ClassInfo();
					thisClass.edits = new LinkedList<Edit>();
					thisClass.className = name.replace('.', '/');
					ret.add(thisClass);
				}
				if (thisClass.className != null && !thisClass.className.equals(name)) {
					ClassInfo newThisClass = new ClassInfo();
					name = thisClass.className + "$" + node.getName().toString();
					newThisClass.className = name;
					thisClass.innerClasses.add(newThisClass);
					newThisClass.parent = thisClass;
					thisClass = newThisClass;
					LinkedList<Edit> editsThisType = new LinkedList<Edit>();
					int startLine = root.getLineNumber(node.getStartPosition());
					int endLine = root.getLineNumber(node.getStartPosition() + node.getLength());
					ret.add(thisClass);
				}
				if (thisClass.className == null)
					thisClass.className = name.replace('.', '/');
				return super.visit(node);
			}

			@Override
			public boolean visit(TypeDeclaration node) {
				String name = (packageName == null ? "" : packageName + ".") + node.getName().toString();
				name = name.replace('.', '/');
				if (thisClass == null) {
					// Root element
					thisClass = new ClassInfo();
					thisClass.className = name;
					ret.add(thisClass);
				}
				if (thisClass.className != null && !thisClass.className.equals(name)) {
					ClassInfo newThisClass = new ClassInfo();
					name = thisClass.className + "$" + node.getName().toString();
					newThisClass.className = name;
					thisClass.innerClasses.add(newThisClass);
					newThisClass.parent = thisClass;
					thisClass = newThisClass;
					LinkedList<Edit> editsThisType = new LinkedList<Edit>();
					int startLine = root.getLineNumber(node.getStartPosition());
					int endLine = root.getLineNumber(node.getStartPosition() + node.getLength());
					ret.add(thisClass);
				}
				if (node.getSuperclassType() != null)
					thisClass.superName = node.getSuperclassType().toString();
				thisClass.className = name;
				return super.visit(node);
			}

			@Override
			public boolean visit(FieldDeclaration node) {
				for (Object c : node.fragments()) {
					if (c instanceof VariableDeclarationFragment) {
						VariableDeclarationFragment f = (VariableDeclarationFragment) c;
						Expression initializer = f.getInitializer();
						FieldInfo fi = new FieldInfo();
						fi.name = f.getName().toString();
						fi.desc = node.getType().toString();
						if (initializer != null)
							fi.init = initializer.toString();
						if (thisClass != null)
							thisClass.fields.add(fi);
					}

				}
				return true;
			}

			String toDesc(String binaryName) {
				if (binaryName.length() == 1)
					return binaryName;
				else if (binaryName.charAt(0) == '[')
					return binaryName.replace('.', '/');// +";";
				else
					return "L" + binaryName.replace('.', '/') + ";";
			}

			@Override
			public boolean visit(MethodDeclaration node) {
				StringBuffer fq = new StringBuffer();
				String name;
				if (node.isConstructor())
					name = "<init>";
				else
					name = node.getName().toString();
				fq.append('(');
				boolean hasParams = false;
				for (Object p : node.parameters()) {
					SingleVariableDeclaration d = (SingleVariableDeclaration) p;
					ITypeBinding b = d.getType().resolveBinding();
					if (b != null)
						fq.append(toDesc(b.getBinaryName()));
					else
						fq.append(toDesc(d.getType().toString()));
					hasParams = true;
				}
				if (hasParams) {
					fq.deleteCharAt(fq.length() - 1);
				}
				fq.append(')');
				if (node.isConstructor())
					fq.append('V');
				else {
					if (node.getReturnType2() == null) {
						fq.append('V');
					} else {
						ITypeBinding b = node.getReturnType2().resolveBinding();
						if (b != null)
							fq.append(toDesc(b.getBinaryName()));
						else
							fq.append(toDesc(node.getReturnType2().toString()));
					}
				}
				if (thisClass != null)
					thisClass.methods.add(new ClassInfo.MethodInfo(fq.toString(), name, thisClass.className));
//				System.out.println(fq.toString());
//				System.out.println(node);
				return true;
			}

		});
		return ret;
	}

}
