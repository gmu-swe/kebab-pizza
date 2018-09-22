package edu.gmu.swe.kp.configurator;

import edu.gmu.swe.kp.Configurator;
import edu.gmu.swe.kp.KPLifecycleParticipant;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;

import java.io.File;
import java.util.Collections;
import java.util.LinkedList;

public class PitConfigurator extends Configurator {
//	private static final String PIT_OUTPUT_DIR = System.getenv("PIT_OUTPUT_DIR");
	LinkedList<String> classFileDirs = new LinkedList<String>();
	LinkedList<String> testClassFileDirs = new LinkedList<>();

	public PitConfigurator(MavenSession session) throws MojoFailureException {
		super(session);
//		File f = new File(PIT_OUTPUT_DIR);
//		if (f.getParentFile() != null)
//			f.getParentFile().mkdirs();

		for (MavenProject p : session.getProjects()) {
			testClassFileDirs.add(p.getBuild().getTestOutputDirectory());
			classFileDirs.add(p.getBuild().getOutputDirectory());
		}

	}

	private String joinString(Iterable<String> in) {
		StringBuilder ret = new StringBuilder();
		for (String s : in) {
			ret.append(s);
			ret.append(',');
		}
		ret.deleteCharAt(ret.length() - 1);
		return ret.toString();
	}

	@Override
	public void applyConfiguration(MavenProject project, Plugin plugin, Xpp3Dom config, boolean isLastExecutionInSession) throws MojoFailureException {


		project.getProperties().setProperty("buildDirs", joinString(classFileDirs));
		project.getProperties().setProperty("testBuildDirs", joinString(testClassFileDirs));

		//Disable test execution natively
		Xpp3Dom disabled = config.getChild("skipTests");
		if (disabled == null) {
			disabled = new Xpp3Dom("skipTests");
			config.addChild(disabled);
		}
		disabled.setValue("true");


		//Add KP plugin that will run after tests are compiled to build the targetClasses and targetTests lists

		Plugin newPlug = new Plugin();
		newPlug.setArtifactId("kp-reporter-plugin");
		newPlug.setGroupId("edu.gmu.swe.kp");
		newPlug.setVersion(KPLifecycleParticipant.KP_VERSION);
		PluginExecution repExec = new PluginExecution();
		if(plugin.getArtifactId().contains("failsafe"))
		{
			repExec.setId("kp-prepare-pit-integration-tests");
			repExec.setPhase("verify");
		}
		else
		{
			repExec.setId("kp-prepare-pit-tests");
			repExec.setPhase("test");

		}
		repExec.setGoals(Collections.singletonList("preparePIT"));
		project.getBuild().addPlugin(newPlug);
		newPlug.addExecution(repExec);


		//Add PIT as a plugin
		newPlug = new Plugin();
		newPlug.setArtifactId("pitest-maven");
		newPlug.setGroupId("org.pitest");
		newPlug.setVersion("1.4.3-SNAPSHOT");
		Xpp3Dom configuration = new Xpp3Dom("configuration");

		Xpp3Dom fullMutationMatrix = new Xpp3Dom("fullMutationMatrix");
		fullMutationMatrix.setValue("true");
		configuration.addChild(fullMutationMatrix);

		Xpp3Dom failWhenNoMutations = new Xpp3Dom("failWhenNoMutations");
		failWhenNoMutations.setValue("false");
		configuration.addChild(failWhenNoMutations);

		Xpp3Dom outputFormats = new Xpp3Dom("outputFormats");
		Xpp3Dom of = new Xpp3Dom("outputFormat");
		of.setValue("XML");
		outputFormats.addChild(of);
		configuration.addChild(outputFormats);

		Xpp3Dom timestampedReports = new Xpp3Dom("timestampedReports");
		timestampedReports.setValue("false");
		configuration.addChild(timestampedReports);
		project.getBuild().addPlugin(newPlug);

		repExec = new PluginExecution();
		if (plugin.getArtifactId().contains("failsafe")) {
			repExec.setId("pit-integration-tests");
			repExec.setPhase("verify");
		} else {
			repExec.setId("pit-tests");
			repExec.setPhase("test");

		}
		repExec.setConfiguration(configuration);
		repExec.setGoals(Collections.singletonList("mutationCoverage"));
		newPlug.addExecution(repExec);

	}
}
