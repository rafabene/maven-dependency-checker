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

import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactResolutionRequest;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.repository.RepositorySystem;
import org.apache.maven.settings.Proxy;
import org.jboss.jdf.stacks.client.StacksClient;
import org.jboss.jdf.stacks.model.BomVersion;
import org.jboss.maven.dependency.MavenDependency;
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
     * @parameter expression="${project}"
     */
    private MavenProject project;

    /**
     * @component
     */
    private RepositorySystem repositorySystem;

    /**
     * List of Remote Repositories used by the resolver
     * 
     * @parameter expression="${project.remoteArtifactRepositories}"
     * @required
     */
    protected java.util.List<ArtifactRepository> remoteRepos;

    /**
     * Location of the local repository.
     * 
     * @parameter expression="${localRepository}"
     * @required
     */
    protected org.apache.maven.artifact.repository.ArtifactRepository localRepository;

    /**
     * Instructs the plugin to fail if found some dependency problem
     * 
     * @parameter expression="${checker.failBuild}"
     */
    private boolean failBuild;

    /**
     * List of excluded Dependencies
     * 
     * @parameter expression="${checker.excludes}"
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
     * List of all managed Dependencies from each BOM (Bill of Materials)
     */
    private Map<MavenDependency, List<BomVersion>> managedDependencies = new HashMap<MavenDependency, List<BomVersion>>();

    /**
     * Read model from pom.xml
     */
    private Model model;

    /**
     * Read any needed information that is used by this plugin
     * 
     * @throws MojoExecutionException
     * 
     */
    private void setupResources() throws MojoExecutionException {
        MavenXpp3Reader reader = new MavenXpp3Reader();
        try {
            // Gets a new model from pom.xml to determine what version has been informed or not
            this.model = reader.read(new FileReader(project.getFile()));
        } catch (Exception e) {
            throw new MojoExecutionException("Problem reading " + project.getFile(), e);
        }
        // Load the relocated dependencies
        try {
            relocatedDependencies.load(this.getClass().getResourceAsStream("/mavenrelocated.properties"));
        } catch (IOException e) {
            throw new MojoExecutionException("Problem loading mavenrelocated.properties", e);
        }
        // Uses stacks-client to query all available BOMs
        Proxy proxy = proxies.size() == 0 ? null : proxies.get(0);
        StacksClient sc = new StacksClient(new MavenStacksConfiguration(proxy), new MavenStacksMessages(getLog()));
        List<BomVersion> boms = sc.getStacks().getAvailableBomVersions();
        for (BomVersion bomVersion : boms) {
            readBOMArtifact(bomVersion, bomVersion.getBom().getGroupId(), bomVersion.getBom().getArtifactId(),
                    bomVersion.getVersion());
        }
    }

    /**
     * @param bomVersion
     * @param groupId
     * @param artifactId
     * @param version
     * @throws MojoExecutionException
     * 
     */
    private void readBOMArtifact(BomVersion bomVersion, String groupId, String artifactId, String version)
            throws MojoExecutionException {
        Artifact pomArtifact = repositorySystem.createArtifact(groupId, artifactId, version, "", "pom");
        ArtifactResolutionRequest arr = new ArtifactResolutionRequest();
        arr.setArtifact(pomArtifact).setRemoteRepositories(remoteRepos).setLocalRepository(localRepository);
        repositorySystem.resolve(arr);
        try {
            readBOM(bomVersion, pomArtifact);
        } catch (Exception e) {
            throw new MojoExecutionException("Can't read the POM " + pomArtifact, e);
        }

    }

    /**
     * @param bomVersion
     * @param pomArtifact
     * @throws Exception
     */
    private void readBOM(BomVersion bomVersion, Artifact pomArtifact) throws Exception {
        if (pomArtifact.getFile().exists()) {
            MavenXpp3Reader reader = new MavenXpp3Reader();
            Model model = reader.read(new FileReader(pomArtifact.getFile()));
            // recursive parent search
            if (model.getParent() != null) {
                Parent p = model.getParent();
                readBOMArtifact(bomVersion, p.getGroupId(), p.getArtifactId(), p.getVersion());
            }
            if (model.getDependencyManagement() != null) {
                for (Dependency dep : model.getDependencyManagement().getDependencies()) {
                    MavenDependency mvnDependency = new MavenDependency(dep.getGroupId(), dep.getArtifactId());
                    if (managedDependencies.get(mvnDependency) == null) {
                        managedDependencies.put(mvnDependency, new ArrayList<BomVersion>());
                    }
                    managedDependencies.get(mvnDependency).add(bomVersion);
                }
            }
        } else {
            String msg = String
                    .format("BOM %s (from jdf-stacks) was not found. Maybe you need to setup a EAP/WFK repository on your settings.xml",
                            pomArtifact);
            getLog().warn(msg);
        }
    }

    public void execute() throws MojoExecutionException {
        setupResources();
        // Process each Dependency
        for (Dependency dependency : dependencies) {
            if (!isExcludedDependency(dependency)) {
                checkNoRedHatRelease(dependency); // feature 1
                checkRelocateDependency(dependency); // feature 2
                checkIfHasBomForIt(dependency); // feature 3
            }
        }
        // Prints the result
        if (nonConformantDependecies.size() > 0) {
            printExecutionResult();
            // Should fail the build?
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
     * @throws MojoExecutionException
     */
    private void checkIfHasBomForIt(Dependency dependency) throws MojoExecutionException {
        // Only check dependencies that has declared version
        if (hasDeclaredVersion(dependency)) {
            MavenDependency mvnDependency = new MavenDependency(dependency.getGroupId(), dependency.getArtifactId());
            // If it has managed Dependency in any BOM
            if (managedDependencies.get(mvnDependency) != null) {
                StringBuilder sb = new StringBuilder("Declared version [" + dependency.getVersion() + "]. ");
                sb.append("You should remove the version and use the following BOM: \n");
                List<BomVersion> boms = managedDependencies.get(mvnDependency);
                for (BomVersion bomVersion : boms) {
                    sb.append(String.format("\t\t - %s:%s:%s", bomVersion.getBom().getGroupId(), bomVersion.getBom()
                            .getArtifactId(), bomVersion.getVersion()));
                    // Add 'or' until the last item
                    if (bomVersion != boms.get(boms.size() - 1)) {
                        sb.append("\n\t\t or \n");
                    }
                }
                addIssueToDepency(dependency, sb.toString());
            }
        }
    }

    /**
     * Search Model declared dependencies to see if it has a declared version
     * 
     * @param dependency dependency to check if has declared dependency
     * 
     * @return true if has declared dependency
     * @throws MojoExecutionException
     */
    private boolean hasDeclaredVersion(Dependency dependency) throws MojoExecutionException {
        for (Dependency dep : model.getDependencies()) {
            if (dep.getGroupId().equals(dependency.getGroupId()) && dep.getArtifactId().equals(dependency.getArtifactId())) {
                return dep.getVersion() != null;
            }
        }
        return false;
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
            if ((dependency.getGroupId().contains(key) || dependency.getArtifactId().contains(key))) {
                String relocated = relocatedDependencies.getProperty(key);
                // if this groupdId isn't already relocated
                if (!relocated.contains(dependency.getGroupId())) {
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
    }

    /**
     * Verifies if this dependency ends or not with -redhat-N
     * 
     * @param dependency
     */
    private void checkNoRedHatRelease(Dependency dependency) {
        if (!patternRedHat.matcher(dependency.getVersion()).matches()) {
            addIssueToDepency(dependency, "This dependency isn't a Red Hat Release (-redhat-N suffix)");
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
