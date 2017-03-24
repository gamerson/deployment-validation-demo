# Bundle Validation Progressive Enhancement for Builds

*Goal:* To know at build time if the dependencies of my OSGi bundles will be satified when they are installed into the OSGi framework.

*Method:* Validate user bundles dependencies against a Liferay Distro during normal build lifecycle.

# Creating the Distro Jar

First we need a distro jar from an officical Liferay deployment.  Currently in this project I have included both 7.0.2 and 7.10.1  But if you have a more recent Liferay deployment you will need to follow the procedure below to generate a new distro jar.

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

# Why do you need the distro?

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

```
<dependencies>
  <dependency>
    <groupId>com.liferay</groupId>
    <artifactId>demo-api</artifactId>
    <version>1.0.0-SNAPSHOT</version>
  </dependency>
  <dependency>
    <groupId>com.liferay</groupId>
    <artifactId>demo-portlet</artifactId>
    <version>1.0.0-SNAPSHOT</version>
  </dependency>
  <dependency>
    <groupId>com.liferay</groupId>
    <artifactId>demo-rule</artifactId>
    <version>1.0.0-SNAPSHOT</version>
  </dependency>
  <dependency>
    <groupId>com.liferay</groupId>
    <artifactId>demo-fragment</artifactId>
    <version>1.0.0-SNAPSHOT</version>
  </dependency>
  <dependency>
    <groupId>com.liferay</groupId>
    <artifactId>demo-impl</artifactId>
    <version>1.0.0-SNAPSHOT</version>
  </dependency>
</dependencies>
```

4. Copy the distro jar that you created previously into this project directory. Let’s assume the file is called com.liferay.distro-7.10.1.jar.

5. Create the bndrun file named in the resolver plugin’s configuration (distro-validation.bndrun) with the following content:

 ```
 -standalone: target/index.xml
 -resolve.effective: resolve, active
 -distro: com.liferay.distro-7.10.1.jar;version=file
```

 Now we need to tell the OSGi resolver the identity of the bundles required for validation. We use an additional property in the bndrun file called -runrequires using the following format. In general you want to add all of top level modules that you are building and deploying to Liferay.  You don't need to list all dependencies here, since the resolver will include transitive dependencies in its operation.

 ```
 -runrequires: \
    osgi.identity;filter:='(osgi.identity=com.foo.provider)',\
    osgi.identity;filter:='(osgi.identity=com.foo.other)'
 ```
 where the value of osgi.identity filter (e.g. com.foo.provider) is the bsn of our OSGi bundles, one per line.

6. Finally, execute the maven verify lifecycle.
 `mvn clean verify`

When the maven build gets to the distro-validation module, the bnd plugins that we have configured will begin the verification process which is the following:
1. generates an OSGi index of all bundles that are a part of this multi-module maven build
2. performs a `resolve` task using the Bnd OSGi Resolver based on the input we gave it in the distro-validation.bndrun file
3. If everything goes well (all bundles OSGi requirements were met) then the distro-validation module build will succeed.
4. If something went wrong and the resolver was not able to find all of the requirements it will through an error with the missing requirements.

If you are interested in seeing the resolver detect these errors, continue onto the Demo section next to walk through a few scenerios that you are likely to encounter.

# Demo

This repository contains some sample projects that can be used to demostrate the validation progressive enhancement.  In the example projects we have the following projects:

 * demo-api (contains a simple OSGi service interface)
 * demo-impl (contains an implementation of demo-api
 * demo-portlet (mvc portlet with jsps that depends on demo-api)
 * demo-fragment (references a portal jar to attach a fragment to (JSP override)
 * demo-rule (audience targeting rule)

Right now the way we have things setup the validation will succeed, which you can test with `mvn clean verify`  But that isn't very interesting, what is more interesting is to make some changes to see it fail and then we can see how it is actually working.

## Change requirements to see validation in action

Now lets make a series of changes to these example projects to demostrate the various validation capabilities of the OSGi resolver.

### Validate imported packages exist

Suppose that you have a bundle that has some dependency that is configued in the pom and then your OSGi code compiles against it, so your OSGi bundle is going to import various packages at runtime.  The resolver will verify that the packages existing in either your projects or in the Liferay distro.

1. Open the [distro-validation/distro-validation.bndrun] file and add the `demo-rule` module to the validation rules (runrequires).
 ```
 -runrequires: \
    osgi.identity;filter:='(osgi.identity=com.liferay.demo.rule)',\
    osgi.identity;filter:='(osgi.identity=com.liferay.demo.api)',\
    osgi.identity;filter:='(osgi.identity=com.liferay.demo.impl)',\
    osgi.identity;filter:='(osgi.identity=com.liferay.demo.fragment)',\
    osgi.identity;filter:='(osgi.identity=com.liferay.demo.portlet)'
 ```
2. run the build `mvn clean verify`
 You should see the following error
 ```
Unable to resolve com.liferay.demo.rule version=1.0.0.201703132112: missing requirement com.liferay.content.targeting.anonymous.users.model; version=[2.0.0,3.0.0)]
 ```
 This means that even though we are compiling and building the demo-rule bundle no problem (we have dependencies declared in pom) in our target runtime distro, those capabilities for those package imports aren't there (the audience targeting bundles were deploy when we built our distro-7.0.2.jar) thus we get that error.

3. To resolve this problem you must deploy audience targeting application to DXP and then recapture the distro information and save into the file.

### Validate that service implementations exist

So in many of your DS components you are likely adding references to services like
```
@Reference DemoApi demoApi;
@Reference UserLocalService;
```

This resolution process can now check for missing service dependencies and alert you that there is no available implementation that will make that API available.

To see this in action do the following:

1. Edit the DemoImpl.java file and comment out the `@Component` annotation.

2. Run the build `mvn clean verify`

Notice that you get the following error.
```
Unable to resolve com.liferay.demo.portlet version=1.0.0.201703231910: missing requirement objectClass=com.liferay.demo.api.DemoApi
```
This means that the OSGi resolver was not able to find any capability that provided the service interface of DemoApi.

3. Now put the `@Component` back, so that DemoAPi has at least one implementor

5. Re-run the build. `mvn clean verify`

Resolve error should be fixed now.

### Validate fragment hosts exist

If you have a OSGi fragment, you likely want to ensure that the Fragment-Host that it will be bound to exists in your targeted deployment.

1. Edit the [modules/demo-fragment/bnd.bnd] file and modify the Fragment-Host version to the following:

 ```
 Fragment-Host: com.liferay.bookmarks.web;bundle-version="[1.0.13,1.0.14)"
 ```
2. Rerun the build `mvn clean verify`  You will see the following errors:
 ```
Unable to resolve com.liferay.demo.fragment version=1.0.0.201703132212: missing requirement com.liferay.bookmarks.web; version=[1.0.14,1.0.15)]
 ```

 This means that you are depending on a newer Fragment-Host that what is available in your distro `com.liferay.distro-7.0.2.jar`.

 But the fragment-host we want to bind to does exist, however it does exist in DXP 7.10.1 so that means it would be available if we were usng the `com.liferay.distro-7.10.1.jar`, so we need to update our distro accordingly.

3. Modify the [distro-validation/distro-validation.bndrun] file, edit the `-distro` command to the following:
 ```
 -distro: com.liferay.distro-7.10.1.jar;version=file
 ```
4. Rerun the build and notice that there are now no errors.

# Notes for consideration:
* If the distro jar is published to a maven repo then the reference can be <bsn>;version=’[${version},)’
* Make sure the distro never resolves
* Can we automate the -distro? (using a distro scope)
* Can we automate the -runrequires? (using a required scope)



