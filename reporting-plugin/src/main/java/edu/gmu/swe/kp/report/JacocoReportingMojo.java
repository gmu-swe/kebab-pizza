package edu.gmu.swe.kp.report;

import edu.gmu.swe.kp.report.jacoco.*;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.surefire.report.ReportTestSuite;
import org.apache.maven.project.MavenProject;
import org.jacoco.core.analysis.CoverageBuilder;
import org.jacoco.core.analysis.IBundleCoverage;
import org.jacoco.core.data.*;
import org.jacoco.core.internal.analysis.StringPool;
import org.jacoco.core.tools.ExecFileLoader;
import org.jacoco.maven.FileFilter;
import org.jacoco.report.ISourceFileLocator;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

@Mojo(name = "reportjacoco", defaultPhase = LifecyclePhase.VERIFY)
public class JacocoReportingMojo extends AbstractMojo {

	Log consoleLogger;
	PrintWriter fw;
	@Component
	private MavenProject project;

	private String outputFile = System.getenv("KP_JACOCO_OUTPUT_FILE");
	//	@Parameter(readonly = true, required = true, property = "debugCovFile")
//	private String covFile;
	@Parameter(readonly = true, required = true, property = "jacocoFile")
	private String jacocoFile;
	@Parameter(readonly = true, required = true)
	private String[] buildOutputDirs;
	//	@Parameter(readonly = true, required = true, property = "reportedTestsFile")
//	private String reportedTestsFile;
	@Parameter(readonly = true, required = true, property = "maven.test.failure.ignore", defaultValue = "true")
	private boolean testFailureIgnore;
	@Parameter(readonly = true, required = true, defaultValue = "false")
	private boolean isLastExec;
	private ExecFileLoader loader;
	private String curSession = null;
	private boolean doDeflaker = false;
	private String[] allSourceDirs;

	private void logInfo(String str) {
		consoleLogger.info(str);
		if (fw != null)
			fw.println("[INFO] " + str);
	}

	private void logWarn(String str) {
		consoleLogger.warn(str);
		if (fw != null)
			fw.println("[WARN] " + str);

	}

	private List<String> getAllTypes(File sourceFile) throws IOException {
		FileInputStream fis = new FileInputStream(sourceFile);
		byte[] b = new byte[(int) sourceFile.length()];
		fis.read(b);
		HashSet<ClassInfo> d = JDTAnalyzer.collectStructuralElements(b);
		LinkedList<String> ret = new LinkedList<String>();
		for (ClassInfo s : d) {
			ret.add(s.className);
		}
		return ret;
	}

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		if (outputFile == null) {
			throw new MojoFailureException("Error: please set KP_JACOCO_OUTPUT_FILE");
		}
		if (!isLastExec) {
			System.err.println("Skipping execution: will collect all coverage on last execution.");
			return;
		}
		List<ReportTestSuite> tests = null;
		try {
			consoleLogger = getLog();

			int uncoveredClasses = 0;
			int uncoveredLines = 0;
			int uncoveredMethods = 0;
			if (System.getProperty("jacoco.report") != null) {
				try {
					fw = new PrintWriter(new FileWriter(System.getProperty("diffCov.report")));
				} catch (IOException e) {
					e.printStackTrace();
				}
			}

			logInfo("------------------------------------------------------------------------");
			logInfo("JACOCO COVERAGE ANALYSIS");
			logInfo(project.getName() + ":" + project.getArtifactId());
			logInfo("------------------------------------------------------------------------");

			final HashMap<String, HashMap<String, boolean[]>> dataPerSession = new HashMap<String, HashMap<String, boolean[]>>();


//			consoleLogger.info("Using covFile: " + covFile);
			int testExecId = -1;
			try {

				File of = new File(outputFile);
				if (of.exists()) {
					System.out.println("File " + of + " already exists, bailing");
					return;
				}
				if (project != null) {
					String buildDirsProp =  project.getProperties().getProperty("allBuildDirs");
					if (buildDirsProp != null) {
						buildOutputDirs = buildDirsProp.split(",");
						System.out.println("Using build dirs: " + Arrays.toString(buildOutputDirs));
					}
					String sourceDirsProp = project.getProperties().getProperty("allSourceDirs");
					if (sourceDirsProp != null) {
						allSourceDirs = sourceDirsProp.split(",");
						logInfo("Using source dirs: " + Arrays.toString(allSourceDirs));
					}
				}

				FileOutputStream fos = new FileOutputStream(outputFile);

				final HashSet<String> allClasses = new HashSet<String>();
				for (String dir : allSourceDirs) {
					Files.walkFileTree(Paths.get(dir), new FileVisitor<Path>() {
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
							allClasses.addAll(getAllTypes(file.toFile()));
							return FileVisitResult.CONTINUE;
						}

						@Override
						public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
							return FileVisitResult.CONTINUE;
						}
					});
				}

//				if (!new File(covFile).exists()) {
//					logInfo("No test data found");
//					return;
//				}
				final StringPool stringPool = new StringPool();

				loader = new ExecFileLoader();
				loader.load(new File(jacocoFile));
				final ExecutionDataReader reader = new ExecutionDataReader(new BufferedInputStream(new FileInputStream(jacocoFile)));
//				reader.setExecutionDataVisitor(loader.getExecutionDataStore());
//				reader.setSessionInfoVisitor(loader.getSessionInfoStore());
				reader.setSessionInfoVisitor(new ISessionInfoVisitor() {

					@Override
					public void visitSessionInfo(SessionInfo info) {
						curSession = info.getId();
					}
				});
				reader.setExecutionDataVisitor(new IExecutionDataVisitor() {

					@Override
					public void visitClassExecution(ExecutionData data) {
						if (curSession != null) {
							HashMap<String, boolean[]> thisClass = dataPerSession.get(data.getName());
							if (thisClass == null) {
								thisClass = new HashMap<String, boolean[]>();
								dataPerSession.put(data.getName(), thisClass);
							}
							boolean[] thisSession = thisClass.get(curSession);
							if (thisSession == null)
								thisClass.put(curSession, data.getProbes());
							else {
								for (int i = 0; i < thisSession.length; i++) {
									thisSession[i] |= data.getProbes()[i];
								}
							}
						}
					}
				});
				reader.read();

				final JSONFormatter formatter = new JSONFormatter(fos, allClasses);
				final CoverageBuilder builder = new CoverageBuilder();
				final JacocoRunInfo info = new JacocoRunInfo();
				info.runInfoPerTest = new HashMap<String, JacocoRunInfo>();

				SessionCoverageVisitor vis = new SessionCoverageVisitor() {
					private boolean hasLines = false;
					private HashMap<Integer, HashSet<String>> coveragePerLinePerTest;

					@Override
					public void preVisitClass(String probeClass) {
						coveragePerLinePerTest = new HashMap<>();
						hasLines = false;
					}

					@Override
					public void postVisitClass(String probeClass) {
						if (hasLines)
							info.totalClasses++;
						formatter.visitFile(probeClass, coveragePerLinePerTest);
					}

					@Override
					public void visitCoverage(String testClassName, String testMethodName, String probeClass, HashSet<Integer> coveredLines, int nTotalLines, int nInsncovered, int nInsnTotal, int nDiffCovered, int nDiffLinesTotal) {
						JacocoRunInfo inf = info.runInfoPerTest.get(testClassName + "#" + testMethodName);
						String tcn = testClassName + "#" + testMethodName;
						if (inf == null) {
							inf = new JacocoRunInfo();
							info.runInfoPerTest.put(testClassName + "#" + testMethodName, inf);
						}
						inf.insnsCovered += nInsncovered;
						inf.methodsCovered++;
						inf.visitedClasses.add(probeClass);
						inf.diffLinesCovered += nDiffCovered;
						for (int line : coveredLines) {
							if (!coveragePerLinePerTest.containsKey(line))
								coveragePerLinePerTest.put(line, new HashSet<String>());
							coveragePerLinePerTest.get(line).add(tcn);
						}
					}

					@Override
					public void preVisitMethod(String probeClass, String probeMethod) {
						info.totalMethods++;
					}

					@Override
					public void endVisitMethod(String probeClass, String probeMethod, int nCovered, int nTotal, int nInsncovered, int nInsnTotal, int nDiffCovered, int nDiffLinesTotal) {
						info.linesCovered += nCovered;
						info.totalLines += nTotal;
						info.totalDiffLines += nDiffLinesTotal;
						info.diffLinesCovered += nDiffCovered;
						info.insnsCovered += nInsncovered;
						info.totalInsns += nInsnTotal;
						if (nTotal > 0)
							hasLines = true;
						if (nCovered > 0) {
							info.methodsCovered++;
							info.visitedClasses.add(probeClass);
						}
					}
				};
				final Analyzer analyzer = new Analyzer(dataPerSession, formatter, vis, stringPool);
				final org.jacoco.maven.FileFilter filter = new FileFilter(null, null);
				for (String dir : buildOutputDirs) {
					File f = new File(dir);
					if (f.isDirectory())
						for (final File file : filter.getFiles(f)) {
							analyzer.analyzeAll(file);
						}
				}

				formatter.visitEnd();

			} catch (IOException e) {
				e.printStackTrace();
				throw new MojoFailureException(e.getMessage());
			}
		} finally {
			if (fw != null)
				fw.close();

		}

	}
}
