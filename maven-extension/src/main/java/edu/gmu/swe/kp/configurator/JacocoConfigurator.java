package edu.gmu.swe.kp.configurator;

import edu.gmu.swe.kp.Configurator;
import edu.gmu.swe.kp.KPLifecycleParticipant;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;

import java.io.File;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedList;

public class JacocoConfigurator extends Configurator {

	private static final String JACOCO_REPORT_FILE = System.getenv("KP_JACOCO_OUTPUT_FILE");
	private static String JACOCO_OUTPUT_FILE = System.getenv("KP_JACOCO_EXEC_FILE");
	LinkedList<String> classFileDirs = new LinkedList<String>();
	LinkedList<String> sourceFileDirs = new LinkedList<String>();

	public JacocoConfigurator(MavenSession session) throws MojoFailureException {
		super(session);
		addJacocoPluginToProjects(session);
	}

	@Override
	public void applyConfiguration(MavenProject project, Plugin plugin, PluginExecution pluginExecution, boolean isLastExecutionPerSession) throws MojoFailureException {

		//Remove any existing jacoco dependency
		Dependency toRemove = null;
		for(Dependency d : project.getDependencies())
		{
			if(d.getGroupId().equals("org.jacoco") && d.getArtifactId().equals("org.jacoco.agent"))
				toRemove = d;
		}
		if(toRemove != null)
			project.getDependencies().remove(toRemove);
		Xpp3Dom config = (Xpp3Dom) pluginExecution.getConfiguration();
		Xpp3Dom argLine = config.getChild("argLine");
		argLine.setValue(argLine.getValue() + " @{argLine}");


		Plugin newPlug = getOrAddPlugin(project,
				"org.jacoco", "jacoco-maven-plugin", "0.7.9");

		{
			PluginExecution jacocoPrepExec = new PluginExecution();
			if (plugin.getArtifactId().contains("failsafe")) {
				jacocoPrepExec.setId("kp-prepare-jacoco-" + pluginExecution.getId());
				jacocoPrepExec.setPhase("pre-integration-test");
			} else {
				jacocoPrepExec.setId("kp-prepare-jacoco-" + pluginExecution.getId());
				jacocoPrepExec.setPhase("process-test-classes");

			}

			Xpp3Dom configuration = new Xpp3Dom("configuration");
			Xpp3Dom jacocoFile = new Xpp3Dom("destFile");
			jacocoFile.setValue(JACOCO_OUTPUT_FILE);
			configuration.addChild(jacocoFile);
			jacocoPrepExec.setConfiguration(configuration);
			jacocoPrepExec.setGoals(Collections.singletonList("prepare-agent"));
			newPlug.addExecution(jacocoPrepExec);
		}
		{
			PluginExecution jacocoReportExec = new PluginExecution();
			if (plugin.getArtifactId().contains("failsafe")) {
				jacocoReportExec.setId("kp-report-jacoco" + pluginExecution.getId());
				jacocoReportExec.setPhase("verify");
			} else {
				jacocoReportExec.setId("kp-report-jacoco-" + pluginExecution.getId());
				jacocoReportExec.setPhase("test");
			}
			Xpp3Dom configuration = new Xpp3Dom("configuration");
			Xpp3Dom jacocoFile = new Xpp3Dom("destFile");
			jacocoFile.setValue(JACOCO_OUTPUT_FILE);
			configuration.addChild(jacocoFile);
			jacocoReportExec.setConfiguration(configuration);
			jacocoReportExec.setGoals(Collections.singletonList("report"));
			newPlug.addExecution(jacocoReportExec);
		}
		{
			// Add the reporting plugin
			Plugin reportingPlugin = null;
			//Look to see if we already have added it to this project
			for (Plugin _p : project.getBuild().getPlugins()) {
				if (_p.getArtifactId().equals("kp-reporter-plugin"))
					reportingPlugin = _p;
			}
			if (reportingPlugin == null) {
				reportingPlugin = new Plugin();
				reportingPlugin.setArtifactId("kp-reporter-plugin");
				reportingPlugin.setGroupId("edu.gmu.swe.kp");
				reportingPlugin.setVersion(KPLifecycleParticipant.KP_VERSION);
				project.getBuild().addPlugin(reportingPlugin);
			}
			PluginExecution repExec = new PluginExecution();
			Xpp3Dom reportConfig = new Xpp3Dom("configuration");
			if (plugin.getArtifactId().contains("failsafe")) {
				repExec.setId("kp-report-integration-tests");
				repExec.setPhase("verify");
				Xpp3Dom isFailsafeDom = new Xpp3Dom("isFailsafe");
				isFailsafeDom.setValue("true");
				reportConfig.addChild(isFailsafeDom);
			} else {
				repExec.setId("kp-report-tests");
				repExec.setPhase("test");

			}
			repExec.setGoals(Collections.singletonList("reportjacoco"));
			Xpp3Dom configIsLastExec = new Xpp3Dom("isLastExec");
			configIsLastExec.setValue(isLastExecutionPerSession ? "true" : "false");
			reportConfig.addChild(configIsLastExec);

			Xpp3Dom jacocoFile = new Xpp3Dom("jacocoFile");
			jacocoFile.setValue(JACOCO_OUTPUT_FILE);
			reportConfig.addChild(jacocoFile);


			Xpp3Dom buildOutputDirs = new Xpp3Dom("buildOutputDirs");
			for (String s : classFileDirs) {
				Xpp3Dom buildOutputDir = new Xpp3Dom("buildOutputDir");
				buildOutputDir.setValue(s);
				buildOutputDirs.addChild(buildOutputDir);
			}
			reportConfig.addChild(buildOutputDirs);
			repExec.setConfiguration(reportConfig);
			reportingPlugin.addExecution(repExec);
		}
	}

	private void addJacocoPluginToProjects(MavenSession session) throws MojoFailureException {
		if (JACOCO_OUTPUT_FILE == null) {
			JACOCO_OUTPUT_FILE = Paths.get(session.getRequest().getBaseDirectory(), "jacoco.exec").toString();
			System.err.println("Defaulting Jacoco execution file to " + JACOCO_OUTPUT_FILE);
		}
		if (JACOCO_REPORT_FILE == null)
			throw new MojoFailureException("Please set KP_JACOCO_OUTPUT_FILE to point to where you would like the final JaCoCO report result to go");

		File f = new File(JACOCO_OUTPUT_FILE);
		if (f.exists())
			f.delete();
		if (f.getParentFile() != null)
			f.getParentFile().mkdirs();

		f = new File(JACOCO_REPORT_FILE);
		if (f.exists()) {
			System.err.println("Warning: Deleting old output file: " + f.getAbsolutePath());
			f.delete();
		}
		if (f.getParentFile() != null)
			f.getParentFile().mkdirs();

		boolean lastIsSurefire = false;
		MavenProject lastProjectWithSurefireOrFailsafe = null;

		StringBuilder classFileDirsStr = new StringBuilder();
		StringBuilder sourceFileDirsStr = new StringBuilder();

		for (MavenProject p : session.getProjects()) {
			sourceFileDirsStr.append(p.getBuild().getSourceDirectory());
			sourceFileDirsStr.append(',');
			sourceFileDirsStr.append(p.getBuild().getTestSourceDirectory());
			sourceFileDirsStr.append(',');

			classFileDirsStr.append(p.getBuild().getTestOutputDirectory());
			classFileDirsStr.append(',');
			classFileDirsStr.append(p.getBuild().getOutputDirectory());
			classFileDirsStr.append(',');
			sourceFileDirs.add(p.getBuild().getSourceDirectory());
			sourceFileDirs.add(p.getBuild().getTestSourceDirectory());
			classFileDirs.add(p.getBuild().getTestOutputDirectory());
			classFileDirs.add(p.getBuild().getOutputDirectory());
		}
		if(classFileDirsStr.length() > 0)
			classFileDirsStr.deleteCharAt(classFileDirsStr.length() -1);
		if(sourceFileDirsStr.length() > 0)
			sourceFileDirsStr.deleteCharAt(sourceFileDirsStr.length()-1);
		for (MavenProject p : session.getProjects()) {
			p.getProperties().setProperty("allBuildDirs", classFileDirsStr.toString());
			p.getProperties().put("allSourceDirs", sourceFileDirsStr.toString());
		}
	}

	@Override
	public String getListenerClass(boolean isTestNG) {
		if (isTestNG)
			return null;
		else
			return "edu.gmu.swe.kp.listener.JacocoCoverageListener";
	}
}
