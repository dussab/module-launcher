/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.pipes.module.launcher;

import java.io.File;
import java.net.URL;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.jar.JarFile;

import org.springframework.boot.SpringApplication;
import org.springframework.util.StringUtils;
import org.springframework.xd.module.support.ParentLastURLClassLoader;

/**
 * @author Mark Fisher
 */
public class MultiModuleLauncher {

	private static final String DEFAULT_MODULE_HOME = "/opt/spring/modules";

	public static void main(String[] args) throws Exception {
		String modules = System.getProperty("modules");
		if (modules == null) {
			modules = System.getenv("MODULES");
		}
		if (modules == null) {
			System.err.println("Either the 'modules' system property or 'MODULES' environment variable is required.");
			System.exit(1);
		}
		String moduleHome = System.getProperty("module.home");
		if (moduleHome == null) {
			moduleHome = System.getenv("MODULE_HOME");
		}
		if (moduleHome == null) {
			moduleHome = DEFAULT_MODULE_HOME;
		}
		launchModules(new File(moduleHome), StringUtils.tokenizeToStringArray(modules, ","));
	}

	private static void launchModules(File moduleHome, String... modules) {
		Executor executor = Executors.newFixedThreadPool(modules.length);
		for (String module : modules) {
			module = (module.endsWith(".jar")) ? module : module + ".jar";
			executor.execute(new ModuleLaunchTask(new File(moduleHome, module)));
		}
	}

	private static class ModuleLaunchTask implements Runnable {

		private static int SERVER_PORT_SEQUENCE = 8080;

		private final File file;

		private final int serverPort;

		ModuleLaunchTask(File file) {
			this.file = file;
			this.serverPort = SERVER_PORT_SEQUENCE++;
		}

		@Override
		public void run() {
			try {
				URL url = new URL("jar", "","file:" + file.getAbsolutePath()+"!/");
				ParentLastURLClassLoader classLoader = new ParentLastURLClassLoader(new URL[] { url }, Thread.currentThread().getContextClassLoader());
				Thread.currentThread().setContextClassLoader(classLoader);
				JarFile jarFile = new JarFile(file);
				String mainClass = jarFile.getManifest().getMainAttributes().getValue("Start-Class");
				jarFile.close();
				new SpringApplication(classLoader.loadClass(mainClass)).run("--spring.jmx.default-domain=module-" + serverPort, "--server.port=" + serverPort, "--format=yyyy-MM-dd HH:mm:ss");
			}
			catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
}