package edu.gmu.swe.kp.listener;

import org.jacoco.agent.rt.IAgent;
import org.jacoco.agent.rt.RT;
import org.junit.runner.Description;
import org.junit.runner.notification.RunListener;

import java.io.IOException;

public class JacocoCoverageListener extends RunListener {
	public static boolean DUMP_PER_TEST_CLASS = System.getenv("KP_JACOCO_PER_CLASS") != null;
	String lastTestClass = null;
	String className;
	String methodName;

	private String getMethodName(Description desc) {
		if (desc == null)
			return "null";
		if (desc.getMethodName() == null)
			return desc.getDisplayName();
		else
			return desc.getMethodName();
	}

	private String getClassName(Description desc) {
		if (desc == null)
			return "null";
		String ret;
		if (desc.getClassName() == null)
			ret = desc.getTestClass().getName();
		else
			ret = desc.getClassName();
		if (ret == null || ret.equals("null") || ret.equals("classes"))
			return ret;
		else if (System.getProperty("kp.inProcessRerun") != null)
			return "kp.inProcessRerun$" + ret;
		else if (System.getProperty("kp.isInRerunFork") != null)
			return "kp.inForkRerun$" + ret;
		return ret;
	}

	@Override
	public void testStarted(Description description) throws Exception {
		if (DUMP_PER_TEST_CLASS) {

			className = getClassName((description));
			if (!className.equals(lastTestClass)) {
				boolean isFirst = lastTestClass == null;
				lastTestClass = className;
				if (lastTestClass == null || description == null || "null".equals(lastTestClass) || "classes".equals(lastTestClass))
					return;

				IAgent agent = RT.getAgent();
				agent.setSessionId(lastTestClass + "#all");
				try {
					if (!isFirst)
						agent.dump(true);
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				agent.setSessionId(className + "#all");
			}
		}
	}
}
