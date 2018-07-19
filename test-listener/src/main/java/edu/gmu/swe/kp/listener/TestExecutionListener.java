package edu.gmu.swe.kp.listener;

import edu.gmu.swe.kp.listener.MySQLLogger;
import edu.gmu.swe.kp.listener.MySQLLogger.TestResult;
import edu.gmu.swe.kp.listener.SharedHolder;
import org.junit.runner.Description;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;

public class TestExecutionListener extends RunListener {

	static MySQLLogger delegate;
	static FirebaseLogger firebase;
	static FileWriter logger;

	static {
		if (System.getProperty("diffcov.mysql") != null && System.getProperty("diffcov.mysqllight") == null) {
			delegate = MySQLLogger.instance();
			delegate.testID = Integer.valueOf(System.getProperty("diffcov.studyid"));
			if (delegate.uuid == null)
				delegate.init("DummyProject", null, "" + delegate.testID);
		}
		if (System.getenv("TRAVIS") != null) {
			//set up firebase
			System.out.println("Connecting to firebase");
			if (SharedHolder.logger == null)
				SharedHolder.logger = new FirebaseLogger();
			firebase = (FirebaseLogger) SharedHolder.logger;
		}
	}

	static {
		Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {

			@Override
			public void run() {
				try {
					logger.close();
					if (firebase != null)
						firebase.awaitExit();

				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}));
	}

	String lastTestClass = null;
	LinkedList<TestResult> methods = new LinkedList<TestResult>();
	TestResult thisMethod;
	boolean methodReported = false;
	int nErrors;
	String lastFinishedClass = null;
	String className;
	String methodName;
	TestResult res;

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
		else if (System.getProperty("deflaker.inProcessRerun") != null)
			return "deflaker.inProcessRerun$" + ret;
		else if (System.getProperty("deflaker.isInRerunFork") != null)
			return "deflaker.inForkRerun$" + ret;
		return ret;
	}

	@Override
	public void testRunStarted(Description description) throws Exception {
		if (!getClassName(description).equals(lastTestClass)) {
			//we are doing another test class
			if (res != null)
				finishedClass();
			methods.clear();
			lastTestClass = getClassName(description);
			if (lastTestClass == null || description == null || "null".equals(lastTestClass) || "classes".equals(lastTestClass))
				return;
			res = new TestResult(lastTestClass);
//			if (description != null && description.getChildren() != null && description.getChildren().size() == 1) {
//				Description child = description.getChildren().get(0);
//				long time = Long.valueOf(child.getDisplayName());
//				res.startTime = time;
//			}
		}
	}

	/**
	 * Called when an atomic test is about to be started.
	 */
	public void testStarted(Description description) throws java.lang.Exception {
		if (!getClassName(description).equals(lastTestClass)) {
			//we are doing another test class
//			System.out.println("Starting new test class");
			if (res != null)
				finishedClass();
			res = new TestResult(getClassName(description));
			lastTestClass = getClassName(description);
		}
		className = getClassName(description);
		methodName = getMethodName(description);
//		System.out.println(">>Start" + className+ "."+methodName);
		TestResult m = new TestResult(getMethodName(description));
		m.startTime = System.currentTimeMillis();
		thisMethod = m;
		methods.add(m);
		if (res.startTime == 0 && description.getChildren() != null && description.getChildren().size() == 1) {
			Description child = description.getChildren().get(0);
			long time = Long.valueOf(child.getDisplayName());
			res.startTime = time;

		}
		res.nMethods++;
	}

	@Override
	public void testFinished(Description description) throws Exception {
		if (thisMethod != null) {
			thisMethod.endTime = System.currentTimeMillis();
		}
		methodReported = true;
//		System.out.println(">>>"+description.getDisplayName() + "Finished\n");
		if (description.getChildren() != null && description.getChildren().size() == 1) {
			Description child = description.getChildren().get(0);
			long time = Long.valueOf(child.getDisplayName());
			res.finished = time;
		}
	}

	@Override
	public void testRunFinished(Result result) throws Exception {
		if (res == null)
			return;
//		res.nFailures = result.getFailureCount();
//		if (!lastTestClass.equals(lastFinishedClass))
		finishedClass();
//		lastFinishedClass = lastTestClass;
		lastTestClass = null;
		res = null;
	}

	private void finishedClass() {
		if (res.reported)
			return;

		res.reported = true;
		if (res.finished == 0)
			res.finished = System.currentTimeMillis();
		if (res.startTime == 0 && res.nMethods == 0)
			res.startTime = res.finished;

		if (firebase != null)
			firebase.log(res);

		res.methods = methods;
		methods = new LinkedList<TestResult>();
		if (delegate != null)
			synchronized (delegate.insertQueue) {
				// if (!delegate.inserter.isAlive() && delegate.senderDead) {
				// delegate.senderDead = false;
				// delegate.inserter.start();
				// }
				// System.out.println("Finished and sending" + res.name);
				if (res.name != null && !"null".equals(res.name)) {
					delegate.insertQueue.add(res);
					delegate.insertQueue.notifyAll();
				}
			}
	}

	/**
	 * Called when an atomic test fails.
	 */
	public void testFailure(Failure failure) throws java.lang.Exception {
		if (failure.getDescription().getChildren() != null && !failure.getDescription().getChildren().isEmpty()) {
			if (!getClassName(failure.getDescription()).equals(lastTestClass)) {
				thisMethod = null;
				if (res != null)
					finishedClass();
				res = new TestResult(getClassName(failure.getDescription()));
				lastTestClass = getClassName(failure.getDescription());
			}
			//Make sure that the child method was created, it almost definitely wasn't
			String methName = getMethodName(failure.getDescription().getChildren().get(0));
			boolean found = false;
			for (TestResult m : methods)
				if (m.name.equals(methName))
					found = true;
			if (!found) {
				TestResult meth = new TestResult(methName);
				meth.startTime = 0;
				meth.endTime = 0;
				meth.failed = true;
				meth.exception = failure.getTrace();
				methods.add(meth);
				res.nMethods++;
			}
		}
		if (res == null)
			return;
		res.nFailures++;
		if (thisMethod != null) {
			thisMethod.exception = failure.getTrace();
			thisMethod.endTime = System.currentTimeMillis();
			thisMethod.failed = true;
		}
		res.failed = true;
//		System.out.println(">>>"+failure.getDescription());
//		System.out.println("Failed on  " + failure.getTestHeader() + ": " + failure.getMessage() + Arrays.toString(failure.getException().getStackTrace()));
	}

	public void testAssumptionFailure(Failure failure) {
		if (res == null)
			return;
		res.nSkips++;
		if (thisMethod != null) {
			thisMethod.endTime = System.currentTimeMillis();
			thisMethod.skipped = true;
		}
		res.failed = true;
		res.stderr.append("Failed on  " + failure.getTestHeader() + ": " + failure.getMessage() + Arrays.toString(failure.getException().getStackTrace()));
	}

	/**
	 * Called when a test will not be run, generally because a test method is
	 * annotated with Ignore.
	 */
	public void testIgnored(Description description) throws java.lang.Exception {
//		System.out.println("Execution of test case ignored : " + description.getMethodName());
	}
}
