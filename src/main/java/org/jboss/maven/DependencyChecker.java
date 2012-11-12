package org.jboss.maven;

/*
 * Copyright 2001-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;

/**
 * Check project dependencies
 * 
 * @goal check
 * 
 * @phase validate
 */
public class DependencyChecker extends AbstractMojo {

    private Pattern patternRedHat = Pattern.compile(".*-redhat-[0-9]");

    /**
     * Instructs the plugin to fail if found some dependency problem
     * 
     * @parameter
     */
    private boolean failBuild;

    /**
     * List of excluded Dependencies
     * 
     * @parameter
     */
    private List<String> excludes;

    /**
     * 
     * @parameter expression="${project.dependencies}"
     * 
     */
    private List<Dependency> dependencies;

    /**
     * Dependencies and its problems
     */
    private Map<Dependency, List<String>> nonConformantDependecies = new HashMap<Dependency, List<String>>();

    private Properties relocatedDependencies = new Properties();

    public void execute() throws MojoExecutionException {
        try {
            relocatedDependencies.load(this.getClass().getResourceAsStream("/mavenrelocated.properties"));
        } catch (IOException e) {
            throw new MojoExecutionException("Problem loading mavenrelocated.properties", e);
        }
        for (Dependency dependency : dependencies) {
            if (!isExcludedDependency(dependency)) {
                checkNoRedHatRelease(dependency);
                checkRelocateDependency(dependency);
            }
        }
        if (nonConformantDependecies.size() > 0) {
            printExecutionResult();
            if (failBuild) {
                throw new MojoExecutionException("Project has non conformant Depencies. Check the logs above.");
            }
        }
    }

    /**
     * Checks if the dependency was excluded in plugin configuration groupId:artifactId
     * 
     * @param dependency
     * 
     * @return true if was excluded
     */
    private boolean isExcludedDependency(Dependency dependency) {
        for (String dep : excludes) {
            String[] depsplit = dep.split(":");
            if (dependency.getGroupId().equals(depsplit[0]) && dependency.getArtifactId().equals(depsplit[1])) {
                getLog().warn(dependency + " will not be checked. Marked as excluded: " + dep);
                return true;
            }

        }
        return false;
    }

    /**
     * @param dependency
     */
    private void checkRelocateDependency(Dependency dependency) {
        String relocated = relocatedDependencies.getProperty(dependency.getGroupId());
        // If contains a relocated Dependency
        if (relocated != null) {
            String[] proposed = relocated.split("[|]");
            StringBuilder sb = new StringBuilder("You shoud replace this dependency by: \n");
            for (String dep : proposed) {
                sb.append("\t\t - " + dep);
                // Add 'or' until the last item
                if (dep != proposed[proposed.length - 1]) {
                    sb.append("\n\t\t or \n");
                }
            }
            addIssueToDepency(dependency, sb.toString());
        }
    }

    /**
     * Verifies if this dependency ends or not with -redhat-N
     * 
     * @param dependency
     */
    private void checkNoRedHatRelease(Dependency dependency) {
        if (!patternRedHat.matcher(dependency.getVersion()).matches()) {
            addIssueToDepency(dependency, "This dependency isn't a Red Hat Release");
        }

    }

    /**
     * @param dependency
     * @param string
     */
    private void addIssueToDepency(Dependency dependency, String issue) {
        if (nonConformantDependecies.get(dependency) == null) {
            nonConformantDependecies.put(dependency, new ArrayList<String>());
        }
        nonConformantDependecies.get(dependency).add(issue);

    }

    /**
     * Prints detailed infornation about non conformant dependencies and it issues
     */
    private void printExecutionResult() {
        for (Dependency dependency : nonConformantDependecies.keySet()) {
            StringBuilder sb = new StringBuilder();
            sb.append("\n- " + dependency + "\n");
            for (String issue : nonConformantDependecies.get(dependency)) {
                sb.append("\t- " + issue + "\n");
            }
            getLog().warn(sb);
        }
    }

}
