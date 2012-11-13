package org.jboss.maven;

/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
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
import org.apache.maven.settings.Proxy;
import org.jboss.jdf.stacks.client.StacksClient;
import org.jboss.jdf.stacks.model.Bom;
import org.jboss.maven.stacks.MavenStacksConfiguration;
import org.jboss.maven.stacks.MavenStacksMessages;

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
    private List<String> excludes = new ArrayList<String>();

    /**
     * Holds all project dependencies
     * 
     * @parameter expression="${project.dependencies}"
     * 
     */
    private List<Dependency> dependencies = new ArrayList<Dependency>();

    /**
     * Proxy Host from Settings.xml
     * 
     * @parameter expression="${settings.proxies}"
     * 
     */
    private List<Proxy> proxies;
    
    /**
     * Dependencies and its problems
     */
    private Map<Dependency, List<String>> nonConformantDependecies = new HashMap<Dependency, List<String>>();

    /**
     * List of all relocated dependencies
     */
    private Properties relocatedDependencies = new Properties();

    /**
     * List of all available BOM from JDF Stacks
     */
    private List<Bom> boms = new ArrayList<Bom>();
    
    /**
     * Read any needed information that is used by this plugin
     * 
     * @throws MojoExecutionException 
     * 
     */
    private void setupResources() throws MojoExecutionException {
        //Load the relocated dependencies
        try {
            relocatedDependencies.load(this.getClass().getResourceAsStream("/mavenrelocated.properties"));
        } catch (IOException e) {
            throw new MojoExecutionException("Problem loading mavenrelocated.properties", e);
        }
        //Uses stacks-client to query all available BOMs
        Proxy proxy = proxies.size() == 0?null:proxies.get(0);
        StacksClient sc = new StacksClient(new MavenStacksConfiguration(proxy), new MavenStacksMessages(getLog()));
        boms = sc.getStacks().getAvailableBoms();
    }

    
    public void execute() throws MojoExecutionException {
        setupResources();
        //Process each Dependency
        for (Dependency dependency : dependencies) {
            if (!isExcludedDependency(dependency)) {
                checkNoRedHatRelease(dependency);
                checkRelocateDependency(dependency);
                checkIfHasBomForIt(dependency);
            }
        }
        //Prints the result
        if (nonConformantDependecies.size() > 0) {
            printExecutionResult();
            //Should fail the build?
            if (failBuild) {
                throw new MojoExecutionException("Project has non conformant Depencies. Check the logs above.");
            }
        }
    }


    /**
     * 
     * Check if this dependency has a BOM (Bill of Materials) for it
     * 
     * @param dependency
     */
    private void checkIfHasBomForIt(Dependency dependency) {
       
        
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
        for (Object o : relocatedDependencies.keySet()) {
            String key = (String) o;
            // Check the if groupdId or artifactId contains any relocated dependency
            if (dependency.getGroupId().contains(key) || dependency.getArtifactId().contains(key)) {
                String relocated = relocatedDependencies.getProperty(key);
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
