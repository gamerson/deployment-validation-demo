package com.liferay.tp.impl;

import org.osgi.service.component.annotations.Component;

import com.liferay.tp.api.TPApi;

@Component
public class TPImpl implements TPApi {

	@Override
	public void doSomething() {
		System.out.println("done");
	}

}