maven-dependency-checker
========================

This maven plugin verififies the project dependencies to check:

- If this dependency is a Red Hat release (example: org.hibernate:hibernate:1.2.3-redhat-1)
- If this dependency should be replaced by a relocated (example: if they should use org.jboss.spec:javax.xml.ws:ws-api rather than javax.xml.ws:ws-api)
- If this dependency has a BOM (Bill of Material) for it

Executing from the commmand line
---
You can execute this plugin typing:

        mvn org.jboss.maven.plugins:dependency-checker:check
        

Configuring this plugin on you project 
---

Add the following to your pom.xml


        <build>
        . . .
        <plugins>
            <plugin>
                <groupId>org.jboss.maven.plugins</groupId>
                <artifactId>dependency-checker</artifactId>
                <version>1.0-SNAPSHOT</version>
                <executions>
                    <execution>
                        <phase>validate</phase>
                        <goals>
                            <goal>check</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
        . . .
        </build>


Excluding some dependencies from being checked
---

You can add the following configuration to each of you dependencies that you want to avoid checking

        <configuration>
            <excludes>
                <exclude>groupId:artifactId</exclude>
                <exclude>groupId:artifactId</exclude>
            </excludes>
        </configuration>

You can also add the `checker.excludes` parameter if running from command line. Example:

        mvn org.jboss.maven.plugins:dependency-checker:check -Dchecker.excludes=javax.xml:jaxb-impl,javax.ejb:ejb-api


Failing the build
---

You can instruct this plugin to fail the build if found some issues on the verififed dependencies. To do this add the following configuration

        <configuration>
             <failBuild>true</failBuild>
        </configuration>

You can also add the `checker.failBuild` parameter if running from commandLine:

        mvn org.jboss.maven.plugins:dependency-checker:check -Dchecker.failBuild=true

