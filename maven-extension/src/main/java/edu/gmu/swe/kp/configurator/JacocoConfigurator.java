package edu.gmu.swe.kp.configurator;

import edu.gmu.swe.kp.Configurator;
import edu.gmu.swe.kp.KPLifecycleParticipant;
import org.apache.maven.Maven;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;

public class JacocoConfigurator extends Configurator {

	private static final String JACOCO_OUTPUT_FILE = System.getenv("KP_JACOCO_EXEC_FILE");

	public JacocoConfigurator(MavenSession session) throws MojoFailureException {
		super(session);
		addJacocoPluginToProjects(session);
	}

	@Override
	public void applyConfiguration(MavenProject project, Plugin plugin, PluginExecution pluginExecution, boolean isLastExecutionPerSession) throws MojoFailureException {

		Xpp3Dom config = (Xpp3Dom) pluginExecution.getConfiguration();
		Xpp3Dom argLine = config.getChild("argLine");
		argLine.setValue(argLine.getValue() + " @{argLine}");

		// Add the reporting plugin
		Plugin reportingPlugin = null;
		//Look to see if we already have added it to this project
		for(Plugin _p : project.getBuild().getPlugins())
		{
			if(_p.getArtifactId().equals("kp-reporter-plugin"))
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
		if(plugin.getArtifactId().contains("failsafe"))
		{
			repExec.setId("kp-report-integration-tests");
			repExec.setPhase("verify");
			Xpp3Dom isFailsafeDom = new Xpp3Dom("isFailsafe");
			isFailsafeDom.setValue("true");
			reportConfig.addChild(isFailsafeDom);
		}
		else
		{
			repExec.setId("kp-report-tests");
			repExec.setPhase("test");

		}
		repExec.setGoals(Collections.singletonList("reportjacoco"));
		Xpp3Dom configIsLastExec = new Xpp3Dom("isLastExec");
		configIsLastExec.setValue(isLastExecutionPerSession ? "true":"false");
		reportConfig.addChild(configIsLastExec);

		Xpp3Dom jacocoFile = new Xpp3Dom("jacocoFile");
		jacocoFile.setValue(JACOCO_OUTPUT_FILE);
		reportConfig.addChild(jacocoFile);


		Xpp3Dom buildOutputDirs = new Xpp3Dom("buildOutputDirs");
		for(String s: classFileDirs)
		{
			Xpp3Dom buildOutputDir = new Xpp3Dom("buildOutputDir");
			buildOutputDir.setValue(s);
			buildOutputDirs.addChild(buildOutputDir);
		}
		reportConfig.addChild(buildOutputDirs);
		repExec.setConfiguration(reportConfig);
		reportingPlugin.addExecution(repExec);
	}

	LinkedList<String> classFileDirs = new LinkedList<String>();
	LinkedList<String> sourceFileDirs = new LinkedList<String>();
	private void addJacocoPluginToProjects(MavenSession session) throws MojoFailureException {
		if (JACOCO_OUTPUT_FILE == null)
			throw new MojoFailureException("Please set KP_JACOCO_EXEC_FILE to point to where you would like the JaCoCo output to go");
		File f = new File(JACOCO_OUTPUT_FILE);
		if (f.exists())
			f.delete();
		if (f.getParentFile() != null)
			f.getParentFile().mkdirs();

		boolean lastIsSurefire = false;
		MavenProject lastProjectWithSurefireOrFailsafe = null;

		for (MavenProject p : session.getProjects()) {
			sourceFileDirs.add(p.getBuild().getSourceDirectory());
			sourceFileDirs.add(p.getBuild().getTestSourceDirectory());
			classFileDirs.add(p.getBuild().getTestOutputDirectory());
			classFileDirs.add(p.getBuild().getOutputDirectory());
		}
		for(MavenProject p : session.getProjects()){
			p.getProperties().put("allBuildDirs", classFileDirs);
			p.getProperties().put("allSourceDirs", sourceFileDirs);
			Plugin newPlug = new Plugin();
			newPlug.setArtifactId("jacoco-maven-plugin");
			newPlug.setGroupId("org.jacoco");
			newPlug.setVersion("0.7.9");
			Xpp3Dom configuration = new Xpp3Dom("configuration");
			Xpp3Dom jacocoFile = new Xpp3Dom("destFile");
			jacocoFile.setValue(JACOCO_OUTPUT_FILE);
			configuration.addChild(jacocoFile);
			newPlug.setConfiguration(configuration);
			p.getBuild().addPlugin(newPlug);
		}
	}

	@Override
	public String getListenerClass(boolean isTestNG) {
		if(isTestNG)
			return null;
		else
			return "edu.gmu.swe.kp.listener.JacocoCoverageListener";
	}
}
