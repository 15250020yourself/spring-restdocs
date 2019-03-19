/*
 * Copyright 2014-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.restdocs.snippet;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;

import org.springframework.restdocs.RestDocumentationContext;
import org.springframework.util.PropertyPlaceholderHelper;
import org.springframework.util.PropertyPlaceholderHelper.PlaceholderResolver;

/**
 * Standard implementation of {@link WriterResolver}.
 *
 * @author Andy Wilkinson
 */
public final class StandardWriterResolver implements WriterResolver {

	private String encoding = "UTF-8";

	private final PlaceholderResolver placeholderResolver;

	private final PropertyPlaceholderHelper propertyPlaceholderHelper = new PropertyPlaceholderHelper(
			"{", "}");

	/**
	 * Creates a new {@code StandardWriterResolver} that will use the given
	 * {@code placeholderResolver} to resolve any placeholders in the
	 * {@code operationName}.
	 *
	 * @param placeholderResolver the placeholder resolver
	 */
	public StandardWriterResolver(PlaceholderResolver placeholderResolver) {
		this.placeholderResolver = placeholderResolver;
	}

	@Override
	public Writer resolve(String operationName, String snippetName,
			RestDocumentationContext context) throws IOException {
		File outputFile = resolveFile(this.propertyPlaceholderHelper.replacePlaceholders(
				operationName, this.placeholderResolver), snippetName + ".adoc", context);

		if (outputFile != null) {
			createDirectoriesIfNecessary(outputFile);
			return new OutputStreamWriter(new FileOutputStream(outputFile),
					this.encoding);
		}
		else {
			return new OutputStreamWriter(System.out, this.encoding);
		}
	}

	@Override
	public void setEncoding(String encoding) {
		this.encoding = encoding;
	}

	File resolveFile(String outputDirectory, String fileName,
			RestDocumentationContext context) {
		File outputFile = new File(outputDirectory, fileName);
		if (!outputFile.isAbsolute()) {
			outputFile = makeRelativeToConfiguredOutputDir(outputFile, context);
		}
		return outputFile;
	}

	private File makeRelativeToConfiguredOutputDir(File outputFile,
			RestDocumentationContext context) {
		File configuredOutputDir = context.getOutputDirectory();
		if (configuredOutputDir != null) {
			return new File(configuredOutputDir, outputFile.getPath());
		}
		return null;
	}

	private void createDirectoriesIfNecessary(File outputFile) {
		File parent = outputFile.getParentFile();
		if (!parent.isDirectory() && !parent.mkdirs()) {
			throw new IllegalStateException(
					"Failed to create directory '" + parent + "'");
		}
	}
}
