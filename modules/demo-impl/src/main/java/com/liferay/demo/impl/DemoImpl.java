package com.liferay.demo.impl;

import org.osgi.service.component.annotations.Component;

import com.liferay.demo.api.DemoApi;

@Component
public class DemoImpl implements DemoApi {

	@Override
	public void doSomething() {
		System.out.println("did something");
	}

}