package edu.gmu.swe.kp;

import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.lifecycle.internal.LifecycleDebugLogger;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.util.xml.Xpp3Dom;

import java.util.HashSet;
import java.util.LinkedList;

@Component(role = AbstractMavenLifecycleParticipant.class, hint = "kp-ext")
public class KPLifecycleParticipant extends AbstractMavenLifecycleParticipant {
	private static final String ARGLINE_FLAGS = System.getenv("KP_ARGLINE");
	private static final String ADDL_TEST_DEPS = System.getenv("KP_DEPENDENCIES");

	private static final boolean RECORD_TESTS = System.getenv("KP_RECORD_TESTS") != null;
	static HashSet<String> disabledPlugins = new HashSet<String>();

	static {
		disabledPlugins.add("maven-enforcer-plugin");
		disabledPlugins.add("license-maven-plugin");
		disabledPlugins.add("maven-duplicate-finder-plugin");
		disabledPlugins.add("apache-rat-plugin");
		disabledPlugins.add("cobertura-maven-plugin");
		disabledPlugins.add("jacoco-maven-plugin");
		disabledPlugins.add("maven-dependency-versions-check-plugin");
		disabledPlugins.add("duplicate-finder-maven-plugin");
	}

	private LifecycleDebugLogger logger;

	private void removeAnnoyingPlugins(MavenProject proj) {
		LinkedList<Plugin> plugsToRemove = new LinkedList<Plugin>();

		for (Plugin p : proj.getBuildPlugins()) {
			if (disabledPlugins.contains(p.getArtifactId())) {
				plugsToRemove.add(p);
				System.out.println("Warning: KebabPizza disabling incompatible " + p.getGroupId() + ":" + p.getArtifactId() + " from " + proj.getArtifactId());
			}
			if (System.getProperty("diffcov.mysql") != null) {
				//fix for checkstyle in evaluation
				if (p.getArtifactId().equals("maven-antrun-plugin") && proj.getName().contains("checkstyle")) {
					PluginExecution del = null;
					for (PluginExecution pe : p.getExecutions()) {
						if (pe.getId().equals("ant-phase-verify"))
							del = pe;
					}
					if (del != null)
						p.getExecutions().remove(del);
				}
			}
		}
		proj.getBuildPlugins().removeAll(plugsToRemove);

		//Also, fix terrible junit deps
		for (Dependency d : proj.getDependencies()) {
			if ("junit".equals(d.getGroupId()) && "junit".equals(d.getArtifactId())) {
				if ("4.2".equals(d.getVersion()) || "4.5".equals(d.getVersion()) || "4.4".equals(d.getVersion()) || "4.3".equals(d.getVersion()) || d.getVersion().startsWith("3"))
					d.setVersion("4.6");
			}
		}
	}

	public void addSurefireLoggerWithoutCoverage(MavenProject project, boolean doFailsafe) throws MojoFailureException {
		Plugin p = null;
		for (Plugin o : project.getBuildPlugins()) {
			if (!doFailsafe && o.getArtifactId().equals("maven-surefire-plugin") && o.getGroupId().equals("org.apache.maven.plugins"))
				p = o;
			else if (doFailsafe && o.getArtifactId().equals("maven-failsafe-plugin") && o.getGroupId().equals("org.apache.maven.plugins"))
				p = o;
		}

		if (p == null)
			return;
		boolean testNG = false;
		for (Dependency d : project.getDependencies()) {
			if (d.getGroupId().equals("org.testng"))
				testNG = true;
		}
		String version = p.getVersion();
		if (version != null) {
			try {
				version = version.substring(2);
				if (!"18.1".equals(version) && !"19.1".equals(version) && !version.startsWith("20")) {
					int vers = Integer.valueOf(version);
					if (vers < 17)
						p.setVersion("2.18");
				}
			} catch (NumberFormatException ex) {
				p.setVersion("2.18");
			}
		}
		Dependency d = new Dependency();
		d.setArtifactId("kp-test-listener");
		d.setGroupId("edu.gmu.swe.kp");
		d.setVersion("1.0-SNAPSHOT");
		d.setScope("test");
		project.getDependencies().add(d);
		if(ADDL_TEST_DEPS != null){
			for(String dep : ADDL_TEST_DEPS.split(",")){
				String[] dat = dep.split(":");
				d = new Dependency();
				d.setGroupId(dat[0]);
				d.setArtifactId(dat[1]);
				d.setVersion(dat[2]);
				d.setScope("test");
				project.getDependencies().add(d);
			}
		}
		for (PluginExecution pe : p.getExecutions()) {

			Xpp3Dom config = (Xpp3Dom) pe.getConfiguration();
			if (config == null)
				config = new Xpp3Dom("configuration");
			injectConfig(config, testNG, false);
			p.setConfiguration(config);
			pe.setConfiguration(config);
		}
		p.getDependencies().clear();
	}

	void injectConfig(Xpp3Dom config, boolean testNG, boolean forkPerTest) throws MojoFailureException {

		Xpp3Dom argLine = config.getChild("argLine");
		if (argLine == null) {
			argLine = new Xpp3Dom("argLine");
			argLine.setValue("");
			config.addChild(argLine);
		}
		argLine.setValue(argLine.getValue().replace("${surefireArgLine}", ""));
		if (argLine != null && argLine.getValue().equals("${argLine}"))
			argLine.setValue("'-XX:OnOutOfMemoryError=kill -9 %p' ");
		else if (argLine != null) {
			argLine.setValue("'-XX:OnOutOfMemoryError=kill -9 %p' " + argLine.getValue().replace("@{argLine}", "").replace("${argLine}", "").replace("${test.opts.coverage}", ""));
		}

		//Now fix if we wanted jacoco or cobertura
		if (ARGLINE_FLAGS != null)
			argLine.setValue(ARGLINE_FLAGS + " " + argLine.getValue());
		Xpp3Dom parallel = config.getChild("parallel");
		if (parallel != null)
			parallel.setValue("none");
		// Fork is either not present (default fork once, reuse), or is fork
		// once, reuse fork
		Xpp3Dom properties = config.getChild("properties");
		if (properties == null) {
			properties = new Xpp3Dom("properties");
			config.addChild(properties);
		}
		if(RECORD_TESTS) {
			Xpp3Dom prop = new Xpp3Dom("property");
			properties.addChild(prop);
			Xpp3Dom propName = new Xpp3Dom("name");
			propName.setValue("listener");
			Xpp3Dom propValue = new Xpp3Dom("value");
			if(testNG)
				propValue.setValue("edu.gmu.swe.kp.listener.TestNGExecutionListener");
			else
				propValue.setValue("edu.gmu.swe.kp.listener.TestExecutionListener");


			prop.addChild(propName);
			prop.addChild(propValue);
		}


		Xpp3Dom testFailureIgnore = config.getChild("testFailureIgnore");
		if (testFailureIgnore != null) {
			testFailureIgnore.setValue("true");
		} else {
			testFailureIgnore = new Xpp3Dom("testFailureIgnore");
			testFailureIgnore.setValue("true");
			config.addChild(testFailureIgnore);
		}

		Xpp3Dom vars = config.getChild("systemPropertyVariables");
		if (vars == null) {
			vars = new Xpp3Dom("systemPropertyVariables");
			config.addChild(vars);
		}
	}

	@Override
	public void afterProjectsRead(MavenSession session) throws MavenExecutionException {
		for (MavenProject p : session.getProjects()) {
			removeAnnoyingPlugins(p);
			try {
				addSurefireLoggerWithoutCoverage(p,false);
			} catch (MojoFailureException e) {
				e.printStackTrace();
			}
			try {
				addSurefireLoggerWithoutCoverage(p,true);
			} catch (MojoFailureException e) {
				e.printStackTrace();
			}
		}

	}

}
