package org.openntf.langchain4j.xsp;

import com.ibm.xsp.library.AbstractXspLibrary;

public class LcXspLibrary extends AbstractXspLibrary {

    public LcXspLibrary() {
    }

    public String getLibraryId() {
        return Activator.class.getPackage()
                              .getName() + ".library";
    }

    @Override
    public String getPluginId() {
        return Activator.class.getPackage()
                              .getName();
    }

    @Override
    public String[] getDependencies() {
        return new String[]{
        };
    }

    @Override
    public String[] getFacesConfigFiles() {
        return new String[]{
                "META-INF/langchain4j-faces-config.xml",
        };
    }

    @Override
    public String[] getXspConfigFiles() {
        return new String[]{
                "META-INF/langchain4j.xsp-config",
        };
    }

}
