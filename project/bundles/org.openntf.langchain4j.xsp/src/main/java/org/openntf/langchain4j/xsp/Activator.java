package org.openntf.langchain4j.xsp;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

public class Activator implements BundleActivator {

	public void start(BundleContext context) throws Exception {
		System.out.println("Starting langchain4j-wrapper");
	}

	public void stop(BundleContext context) throws Exception {
		System.out.println("Stopping langchain4j-wrapper");
	}

}
