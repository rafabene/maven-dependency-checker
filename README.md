maven-dependency-checker
========================

This maven plugin verififies the project dependencies to check:

- If this dependency is a Red Hat release (example: org.hibernate:hibernate:1.2.3-redhat-1)
- if this dependency should be replaced by a relocated (example: if they should use org.jboss.spec:javax.xml.ws:ws-api rather than javax.xml.ws:ws-api)

Configuring this plugin on you project 
---

Add the following to your pom.xml


        <build>
        . . .
        <plugins>
            <plugin>
                <groupId>org.jboss.maven</groupId>
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


Failing the build
---

You can instruct this plugin to fail the build if found some issues on the verififed dependencies. To do this add the following configuration

        <configuration>
             <failBuild>true</failBuild>
        </configuration>



