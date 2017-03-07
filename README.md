This project demonstrates the new target platform validation capabilities
of the Bnd OSGi resolver that will allow developers to catch crucial dependency
resolution errors earlier during build instead of after bundles have been
deployed.  

The way it works is that in one of your maven projects you add a special bndrun
file where you list all the modules that are in your project and then use the
bnd-resolver-maven-plugin to invoke the Bnd OSGi Resolver to resolve all of
the requirements against a special bnd distro file.  This distro file is a
single jar that has all of the OSGi capabilities of your Liferay runtime
packaged into a single file.  This file can be local to the project or in a
remote maven repository.  It is recreated by the bnd distro command that works
for any OSGi runtime that has the bnd agent loaded.  See the readme in the
validation project for more information on how to create the distro file.

In this project

An OSGi bundle can express dependencies on its environment in the following
# imported packages
# required bundles
# required capabilities
# services that are used

Missing any of these can cause the bundle to not be Active or services fully
activated.  It would be preferable for developers to know this before being
deployed to the server.