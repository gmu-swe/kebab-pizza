package edu.gmu.swe.kp.report;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.objectweb.asm.ClassReader;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.Scanner;

@Mojo(name = "preparePIT", defaultPhase = LifecyclePhase.VERIFY)
public class PitPreparingMojo extends AbstractMojo {

	@Component
	private MavenProject project;

	@Parameter(readonly = true, required = true, property = "buildDirs")
	private String buildOutputDirs;
	@Parameter(readonly = true, required = true)
	private String testsToRunFile;

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {

		System.out.println("Scanning build directories: " + buildOutputDirs);
		/*
		Identify the list of classes to mutate
		 */
		HashSet<String> classesToMutate = new HashSet<>();
		for (String s : buildOutputDirs.split(",")) {
			classesToMutate.addAll(collectTypes(s, false));
		}

//		HashSet<String> testClasses = new HashSet<>();
//		for (String s : buildTestOutputDirs.split(",")) {
//			testClasses.addAll(collectTypes(s, true));
//		}

		project.getProperties().setProperty("targetClasses",(System.getenv("PIT_TARGET_CLASSES") != null ? System.getenv("PIT_TARGET_CLASSES") : joinString(classesToMutate)));

		if(System.getenv("PIT_TEST") != null && System.getenv("PIT_TEST").contains("#")){
			String target = System.getenv("PIT_TEST");
			StringBuilder targetTests = new StringBuilder();
			StringBuilder targetMethods = new StringBuilder();
			for(String each : target.split(",")){
				String[] d = each.split("#");
				targetTests.append(d[0]);
				targetTests.append(',');
				targetMethods.append(d[1]);
				targetMethods.append(',');
			}
			String targetTestsStr = targetTests.substring(0, targetTests.length() - 1);
			String targetMethodsStr = targetMethods.substring(0, targetMethods.length() - 1);
			project.getProperties().setProperty("targetTests", targetTestsStr);
			project.getProperties().setProperty("includedTestMethods", targetMethodsStr);

		} else {
			project.getProperties().setProperty("targetTests", System.getenv("PIT_TEST"));
		}
	}

	private String readAllLines(String path){
		StringBuilder sb = new StringBuilder();
		try {
			if(! new File(path).exists())
				return "";
			Scanner s = new Scanner(new File(path));
			while (s.hasNextLine()) {
				sb.append(s.nextLine());
				sb.append(',');
			}
		} catch(IOException ex) {
			ex.printStackTrace();
		}
		if (sb.length() > 0) {
			sb.deleteCharAt(sb.length() - 1);
		}
		return sb.toString();
	}

	private String joinString(Iterable<String> in) {
		StringBuilder ret = new StringBuilder();
		for (String s : in) {
			ret.append(s);
			ret.append(',');
		}
		if(ret.length() > 0)
			ret.deleteCharAt(ret.length() - 1);
		return ret.toString();
	}

	private String getAllTypes(File inputFile) throws IOException {
		ClassReader cr = new ClassReader(new FileInputStream(inputFile));
		return cr.getClassName().replace("/",".");
	}

	private HashSet<String> collectTypes(String path, final boolean onlyTests) {
		final HashSet<String> ret = new HashSet<>();

		try {
			Files.walkFileTree(Paths.get(path), new FileVisitor<Path>() {
				@Override
				public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					if (file.toString().endsWith(".class")) {	// Only handle class files (ends with .class)
						String str = getAllTypes(file.toFile());
						if (!onlyTests || str.endsWith("Test") || str.startsWith("Test") || str.endsWith("ITCase"))
							ret.add(str);
					}
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
					return FileVisitResult.CONTINUE;
				}
			});
		} catch (IOException e) {
			e.printStackTrace();
		}
		return ret;
	}
}
