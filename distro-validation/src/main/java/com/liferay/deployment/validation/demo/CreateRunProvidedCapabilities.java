package com.liferay.deployment.validation.demo;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * @author Gregory Amerson
 */
public class CreateRunProvidedCapabilities {

	public static void main(String[] args) throws Exception {
		new CreateRunProvidedCapabilities(args[0]);
	}

	public CreateRunProvidedCapabilities(String filePath) throws Exception {
		Path path = new File(filePath).toPath();

		if (Files.notExists(path)) {
			throw new NoSuchFileException("Unable to find " + path);
		}

		if (!Files.isDirectory(path)) {
			throw new IllegalArgumentException("file is not a directory.");
		}

		Set<Path> serviceXmlFiles = findAllServiceXmlFiles(path);

		List<String> runProvidedCaps =
				serviceXmlFiles.
					stream().
					flatMap(serviceXml -> buildServicesList(serviceXml).stream()).
					map(cap -> "osgi.service;objectClass=" + cap).
					distinct().
					collect(Collectors.toList());

		StringBuilder stringBuilder = new StringBuilder();

		stringBuilder.append("-runprovidedcapabilities: \\\n");
		runProvidedCaps.stream().forEach(cap -> stringBuilder.append("\t" + cap + ",\\\n"));
		System.out.println(stringBuilder.toString().substring(0, stringBuilder.length() - 3));
	}


	private List<String> buildServicesList(Path serviceXml) {
		List<String> services = new ArrayList<>();

		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			DocumentBuilder builder = factory.newDocumentBuilder();
			Document doc = builder.parse(serviceXml.toUri().toString());
			XPathFactory xPathfactory = XPathFactory.newInstance();
			XPath xpath = xPathfactory.newXPath();
			XPathExpression expr = xpath.compile("/service-builder/@api-package-path");
			String packagePath = expr.evaluate(doc);

			if (packagePath == null || "".equals(packagePath)) {
				expr = xpath.compile("/service-builder/@package-path");
				packagePath = expr.evaluate(doc);
			}

			expr = xpath.compile("//entity");

			NodeList entities = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);

			for (int i = 0; i < entities.getLength(); i++) {
				Node entity = entities.item(i);

				String name = getAttrValue(entity, "name");
				String localService = getAttrValue(entity, "local-service");
				String remoteService = getAttrValue(entity, "remote-service");

				if (localService == null || "true".equals(localService)) {
					services.add(packagePath + ".service." + name + "LocalService");
				}

				if ("true".equals(remoteService)) {
					services.add(packagePath + ".service." + name + "Service");
				}
			}
		}
		catch (Exception e) {
		}

		return services;
	}


	private String getAttrValue(Node node, String attrName) {
		Node item = node.getAttributes().getNamedItem(attrName);

		if (item != null) {
			return item.getNodeValue();
		}

		return null;
	}


	private Set<Path> findAllServiceXmlFiles(Path dir) throws IOException {
		final Set<Path> serviceXmlFiles = new HashSet<>();

		Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult visitFile(Path file,
					BasicFileAttributes attrs) throws IOException {

				if ("service.xml".equals(file.getFileName().toString())) {
					serviceXmlFiles.add(file);
				}

				return super.visitFile(file, attrs);
			}
		});

		return serviceXmlFiles;
	}

}