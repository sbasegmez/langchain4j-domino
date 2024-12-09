/*
 * Copyright (c) ${project.inceptionYear}-2025 Serdar Basegmez
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
