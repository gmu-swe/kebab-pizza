package edu.gmu.swe.kp;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.xml.Xpp3Dom;

public abstract class Configurator {
	public abstract void applyConfiguration(MavenSession session, MavenProject project, Plugin plugin, Xpp3Dom config, boolean isLastExecutionInSession) throws MojoFailureException;

	public String getListenerClass(boolean isTestNG) {
		return null;
	}
}
