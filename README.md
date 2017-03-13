# Bundle Validation Progressive Enhancement for Builds

*Goal:* To know at build time if the dependencies of my OSGi bundles will be satified when they are installed into the OSGi framework.

*Method:* Validate user bundles dependencies against a Liferay Distro during normal build lifecycle.

# Creating the Distro Jar

First we need to generate a distro jar file from an official Liferay deployment.

**Note:** The goal of the distro is to only include what’s provided by Liferay releases including fix packs.

**Note:** The jars being used are snapshot from the bnd cloudbees instance until the 3.4.0 release is made. After than the same will be available from Maven Central.

1. Obtain the latest bnd remote agent from bnd’s cloudbees instance by visiting this link and downloading the first jar listed: https://bndtools.ci.cloudbees.com/job/bnd.master/lastSuccessfulBuild/artifact/dist/bundles/biz/aQute/bnd/biz.aQute.remote.agent/3.4.0-SNAPSHOT/

2. Install the remote agent in Liferay by placing it into the ${liferay.home}/osgi/bundles directory

 **Note:** the remote agent by default is bound to the loopback network device and so should be safe by default.

3. Obtain the latest bnd jar from bnd’s cloudbees instance by visiting this link and downloading the first jar listed: https://bndtools.ci.cloudbees.com/job/bnd.master/lastSuccessfulBuild/artifact/dist/bundles/biz/aQute/bnd/biz.aQute.bnd/3.4.0-SNAPSHOT/

4. Create the distro bundle jar with following command

 **java -jar biz.aQute.bnd.jar remote distro -o com.liferay.distro-7.10.1.jar com.liferay.distro 7.10.1**
 It may be useful to append the Liferay fixpack version to the distro as well. You could use a qualifier for that purpose.
 E.g. 7.10.1.FP2

# Why does you need the distro?

The distro bundle contains all the metadata about the Liferay deployment, including all exported packages, and other capabilities available from the provided bundles.  We need to use that information during the build to validate our bundles against it.  This way, we will be performing the same OSGi dependency resolution that happens when a bundle is installed into an OSGi framework, but now it will happen during a normal build lifecycle.  From technical standpoint, we will need to invoke the OSGi Resolver, to have it look at the bundles we are building and resolve their requirements against a set of known capabilities (a distro) and give us the resolution, success or failure.  If it finds failure, the resolver will tell you what failed and why, thus you will know it at build time instead of deploy, thereby saving you cycles.

# Using the distro in your build

We are assuming that you are already using the bnd-maven-plugin to build your OSGi bundles.  Nothing needs to change for that process.  However, in some project in your overall maven build you need to configure a new maven plugin responsible for integrating the distro into the overall build.

1. Add the following plugin repository to the build:

 ```
 <pluginRepositories>
   <pluginRepository>
     <id>bnd-snapshots</id>
     <url>https://bndtools.ci.cloudbees.com/job/bnd.master/lastSuccessfulBuild/artifact/dist/bundles/</url>
     <layout>default</layout>
   </pluginRepository>
 </pluginRepositories>
 ```

2. Add the following bnd maven plugins as follows:

 ```
 <plugin>
   <groupId>biz.aQute.bnd</groupId>
   <artifactId>bnd-indexer-maven-plugin</artifactId>
   <version>3.4.0-SNAPSHOT</version>
   <configuration>
     <includeJar>true</includeJar>
    	<localURLs>REQUIRED</localURLs>
   </configuration>
   <executions>
   	 <execution>
    		 <id>index</id>
    		 <goals>
    			 <goal>index</goal>
    		 </goals>
    	</execution>
   </executions>
 </plugin>
 <plugin>
   <groupId>biz.aQute.bnd</groupId>
   <artifactId>bnd-resolver-maven-plugin</artifactId>
   <version>3.4.0-SNAPSHOT</version>
   <configuration>
     <failOnChanges>false</failOnChanges>
     <bndruns>
       <bndrun>distro-validation.bndrun</bndrun>
     </bndruns>
   </configuration>
   <executions>
 	   <execution>
 	 	   <id>resolve</id>
       <phase>verify</phase>
 	 	   <goals>
 		  	   <goal>resolve</goal>
 		    </goals>
     </execution>
   </executions>
 </plugin>
 ```

3. Include in this project’s pom file the top level project dependencies you wish to validate.

4. Copy the distro jar that you created previously into this project directory. Let’s assume the file is called com.liferay.distro-7.10.1.jar.

5. Create the bndrun file named in the resolver plugin’s configuration (distro-validation.bndrun) with the following content:

 ```
 -standalone: target/index.xml
 -resolve.effective: resolve, active
 -distro: com.liferay.distro-7.10.1.jar;version=file
```

 Now we need to tell the OSGi resolver the identity of the bundles required for validation. We use an additional property in the bndrun file called -runrequires using the following format.

 ```
 -runrequires: \
    osgi.identity;filter:='(osgi.identity=com.foo.provider)',\
    osgi.identity;filter:='(osgi.identity=com.foo.other)'
 ```
 where the value of osgi.identity filter (e.g. com.foo.provider) is the bsn of our OSGi bundles, one per line.

6. Finally, execute the maven verify lifecycle.
 `mvn clean verify`

# Diagnosing resolver results

If the build succeeds the resolver has successfully validated that the bundles targeted for deployment.

Failure scenarios:

Bundle cannot resolve package import
Bundle cannot resolve service reference
..                                fragment host
..                                required capability?








Notes for consideration:
If the distro jar is published to a maven repo then the reference can be <bsn>;version=’[${version},)’
Make sure the distro never resolves
Can we automate the -distro? (using a distro scope)
Can we automate the -runrequires? (using a required scope)



