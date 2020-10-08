package net.coderobe.java.callsite;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;

public class Main {
	private String targetClass;
	private Method targetMethod;

	private AppClassVisitor cv;
	private ArrayList<Callee> callees = new ArrayList<Callee>();

	private static class Callee {
		String className;
		String methodName;
		String methodDesc;
		String source;
		int line;

		public Callee(String cName, String mName, String mDesc, String src, int ln) {
			className = cName;
			methodName = mName;
			methodDesc = mDesc;
			source = src;
			line = ln;
		}
	}


	private class AppMethodVisitor extends MethodVisitor {
		boolean callsTarget;
		int line;

		public AppMethodVisitor() {
			super(Opcodes.ASM4);
		}

		public void visitMethodInsn(int opcode, String owner, String name, String desc) {
			if (owner.equals(targetClass) && name.equals(targetMethod.getName())
					/*&& desc.equals(targetMethod.getDescriptor())*/) {
				callsTarget = true;
			}
		}

		public void visitCode() {
			callsTarget = false;
		}

		public void visitLineNumber(int line, Label start) {
			this.line = line;
		}

		public void visitEnd() {
			if (callsTarget)
				callees.add(new Callee(cv.className, cv.methodName, cv.methodDesc, cv.source, line));
		}
	}

	private class AppClassVisitor extends ClassVisitor {

		private AppMethodVisitor mv = new AppMethodVisitor();

		public String source;
		public String className;
		public String methodName;
		public String methodDesc;

		public AppClassVisitor() {
			super(Opcodes.ASM6);
		}

		public void visit(int version, int access, String name, String signature, String superName,
				String[] interfaces) {
			className = name;
		}

		public void visitSource(String source, String debug) {
			this.source = source;
		}

		public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
			methodName = name;
			methodDesc = desc;

			return mv;
		}
	}

	public void findCallingMethodsInJar(String jarPath, String targetClass, String targetMethodDeclaration)
			throws Exception {

		this.targetClass = targetClass;
		this.targetMethod = Method.getMethod(targetMethodDeclaration);

		this.cv = new AppClassVisitor();

		JarFile jarFile = new JarFile(jarPath);
		Enumeration<JarEntry> entries = jarFile.entries();

		while (entries.hasMoreElements()) {
			JarEntry entry = entries.nextElement();
			if (entry.getName().endsWith(".class")) {
				InputStream stream = new BufferedInputStream(jarFile.getInputStream(entry), 1024);
				try {
					ClassReader reader = new ClassReader(stream);
					reader.accept((ClassVisitor) cv, 0);
				} catch(ArrayIndexOutOfBoundsException e) {
					System.err.println("class file too large");
					System.exit(255);
				}

				stream.close();
			}
		}
		jarFile.close();
	}

	public static void main(String[] args) {
		try {
			Main app = new Main();

			if(args.length != 3) {
				System.err.println("callsite.jar by coderobe");
				System.err.println("Usage: java -jar callsite.jar <target> <classpath> <method>");
				System.err.println("Example: java -jar callsite.jar /path/to/my.jar java/io/PrintStream println");
				System.exit(255);
			}
			
			app.findCallingMethodsInJar(args[0], args[1], "void "+args[2]+"()");
			for (Callee c : app.callees) {
				System.out
						.println(c.source + ":" + c.line + " " + c.className + " " + c.methodName + " " + c.methodDesc);
			}
			
			System.exit(app.callees.size() > 0 ? 1 : 0);
		} catch (Exception x) {
			x.printStackTrace();
		}
	}

}