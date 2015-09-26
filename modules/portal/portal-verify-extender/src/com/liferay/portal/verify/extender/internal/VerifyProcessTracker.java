/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.portal.verify.extender.internal;

import com.liferay.osgi.service.tracker.map.ServiceTrackerMap;
import com.liferay.osgi.service.tracker.map.ServiceTrackerMapFactory;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.util.RunnableUtil;
import com.liferay.portal.output.stream.container.OutputStreamContainer;
import com.liferay.portal.output.stream.container.OutputStreamContainerFactory;
import com.liferay.portal.output.stream.container.OutputStreamContainerFactoryTracker;
import com.liferay.portal.verify.VerifyException;
import com.liferay.portal.verify.VerifyProcess;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;

import java.util.Set;

import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;

/**
 * @author Miguel Pastor
 * @author Raymond Augé
 * @author Carlos Sierra Andrés
 */
@Component(
	immediate = true,
	property = {
		"osgi.command.function=execute", "osgi.command.function=executeAll",
		"osgi.command.function=list", "osgi.command.function=reports",
		"osgi.command.scope=verify"
	},
	service = {VerifyProcessTracker.class}
)
public class VerifyProcessTracker {

	public void execute(final String verifierName) {
		executeVerifier(verifierName, null, "verify-" + verifierName);
	}

	public void execute(String verifierName, String outputStreamProviderName) {
		executeVerifier(
			verifierName, outputStreamProviderName, "verify-" + verifierName);
	}

	public void executeAll() {
		Set<String> keySet = _verifiers.keySet();

		for (String verifierName : keySet) {
			executeVerifier(verifierName, null, "verify-" + verifierName);
		}
	}

	public void executeAll(String outputStreamProviderName) {
		Set<String> keySet = _verifiers.keySet();

		for (String verifierName : keySet) {
			executeVerifier(
				verifierName, outputStreamProviderName,
				"verify-" + verifierName);
		}
	}

	public void list() {
		for (String key : _verifiers.keySet()) {
			show(key);
		}
	}

	public void show(String verifierName) {
		VerifyProcess verifyProcess = getVerifyProcess(verifierName);

		if (verifyProcess != null) {
			System.out.println("Registered verifier: " + verifierName);
		}
		else {
			System.out.println(
				"No verifier registered with name: " + verifierName);
		}
	}

	public void showReports() {
		Set<String> outputStreamProviderNames =
			_outputStreamContainerFactoryTracker.
				getOutputStreamContainerFactoryNames();

		for (String s : outputStreamProviderNames) {
			System.out.println(s);
		}
	}

	@Activate
	protected void activate(BundleContext bundleContext) {
		try {
			_verifiers = ServiceTrackerMapFactory.singleValueMap(
				bundleContext, VerifyProcess.class, "verifier.name");

			_verifiers.open();
		}
		catch (InvalidSyntaxException ise) {
			throw new IllegalStateException(ise);
		}
	}

	@Deactivate
	protected void deactivate() {
		_verifiers.close();
	}

	@Reference
	protected void setOutputStreamTracker(
		OutputStreamContainerFactoryTracker
			outputStreamContainerFactoryTracker) {

		_outputStreamContainerFactoryTracker =
			outputStreamContainerFactoryTracker;
	}

	protected void executeVerifier(
		final String verifierName, String outputStreamContainerFactoryName,
		String outputStreamName) {

		OutputStreamContainerFactory outputStreamContainerFactory = null;

		if (outputStreamContainerFactoryName != null) {
			_outputStreamContainerFactoryTracker.
				getOutputStreamContainerFactory(
					outputStreamContainerFactoryName);
		}
		else {
			outputStreamContainerFactory = _outputStreamContainerFactoryTracker.
				getOutputStreamContainerFactory();
		}

		OutputStreamContainer outputStreamContainer =
			outputStreamContainerFactory.create(outputStreamName);

		final OutputStream outputStream =
			outputStreamContainer.getOutputStream();

		RunnableUtil.runWithSwappedSystemOut(
			new Runnable() {

				@Override
				public void run() {
					executeVerifierByName(verifierName, outputStream);
				}

			}, outputStream);

		close(outputStream);
	}

	protected void executeVerifierByName(
		String verifierName, OutputStream outputStream) {

		PrintWriter printWriter = new PrintWriter(outputStream, true);

		printWriter.println("Executing " + verifierName);

		VerifyProcess verifyProcess = getVerifyProcess(verifierName);

		try {
			verifyProcess.verify();
		}
		catch (VerifyException ve) {
			_log.error(ve, ve);
		}
	}

	protected VerifyProcess getVerifyProcess(String verifierName) {
		VerifyProcess verifyProcess = _verifiers.getService(verifierName);

		if (verifyProcess == null) {
			throw new IllegalArgumentException(
				"Verifier with name " + verifierName + " is not registered");
		}

		return verifyProcess;
	}

	protected void close(OutputStream outputStream) {
		try {
			outputStream.close();
		}
		catch (IOException ioe) {
			throw new RuntimeException(ioe);
		}
	}

	private static final Log _log = LogFactoryUtil.getLog(
		VerifyProcessTracker.class);

	private OutputStreamContainerFactoryTracker
		_outputStreamContainerFactoryTracker;
	private ServiceTrackerMap<String, VerifyProcess> _verifiers;

}