/*
 * Copyright 2014-2016 the original author or authors.
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

package org.springframework.restdocs.curl;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.restdocs.operation.Operation;
import org.springframework.restdocs.operation.OperationRequest;
import org.springframework.restdocs.operation.OperationRequestPart;
import org.springframework.restdocs.operation.Parameters;
import org.springframework.restdocs.snippet.Snippet;
import org.springframework.restdocs.snippet.TemplatedSnippet;
import org.springframework.util.Base64Utils;
import org.springframework.util.StringUtils;

/**
 * A {@link Snippet} that documents the curl command for a request.
 *
 * @author Andy Wilkinson
 * @author Paul-Christian Volkmer
 * @see CurlDocumentation#curlRequest()
 * @see CurlDocumentation#curlRequest(Map)
 */
public class CurlRequestSnippet extends TemplatedSnippet {

	private static final Set<HeaderFilter> HEADER_FILTERS;

	static {
		Set<HeaderFilter> headerFilters = new HashSet<>();
		headerFilters.add(new NamedHeaderFilter(HttpHeaders.HOST));
		headerFilters.add(new NamedHeaderFilter(HttpHeaders.CONTENT_LENGTH));
		headerFilters.add(new BasicAuthHeaderFilter());
		HEADER_FILTERS = Collections.unmodifiableSet(headerFilters);
	}

	/**
	 * Creates a new {@code CurlRequestSnippet} with no additional attributes.
	 */
	protected CurlRequestSnippet() {
		this(null);
	}

	/**
	 * Creates a new {@code CurlRequestSnippet} with the given additional
	 * {@code attributes} that will be included in the model during template rendering.
	 *
	 * @param attributes The additional attributes
	 */
	protected CurlRequestSnippet(Map<String, Object> attributes) {
		super("curl-request", attributes);
	}

	@Override
	protected Map<String, Object> createModel(Operation operation) {
		Map<String, Object> model = new HashMap<>();
		model.put("url", getUrl(operation));
		model.put("options", getOptions(operation));
		return model;
	}

	private String getUrl(Operation operation) {
		return String.format("'%s'", operation.getRequest().getUri());
	}

	private String getOptions(Operation operation) {
		StringWriter command = new StringWriter();
		PrintWriter printer = new PrintWriter(command);
		writeIncludeHeadersInOutputOption(printer);
		writeUserOptionIfNecessary(operation.getRequest(), printer);
		writeHttpMethodIfNecessary(operation.getRequest(), printer);
		writeHeaders(operation.getRequest().getHeaders(), printer);
		writePartsIfNecessary(operation.getRequest(), printer);
		writeContent(operation.getRequest(), printer);

		return command.toString();
	}

	private void writeIncludeHeadersInOutputOption(PrintWriter writer) {
		writer.print("-i");
	}

	private void writeUserOptionIfNecessary(OperationRequest request,
			PrintWriter writer) {
		List<String> headerValue = request.getHeaders().get(HttpHeaders.AUTHORIZATION);
		if (BasicAuthHeaderFilter.isBasicAuthHeader(headerValue)) {
			String credentials = BasicAuthHeaderFilter.decodeBasicAuthHeader(headerValue);
			writer.print(String.format(" -u '%s'", credentials));
		}
	}

	private void writeHttpMethodIfNecessary(OperationRequest request,
			PrintWriter writer) {
		if (!HttpMethod.GET.equals(request.getMethod())) {
			writer.print(String.format(" -X %s", request.getMethod()));
		}
	}

	private void writeHeaders(HttpHeaders headers, PrintWriter writer) {
		for (Entry<String, List<String>> entry : headers.entrySet()) {
			if (allowedHeader(entry)) {
				for (String header : entry.getValue()) {
					writer.print(String.format(" -H '%s: %s'", entry.getKey(), header));
				}
			}
		}
	}

	private boolean allowedHeader(Entry<String, List<String>> header) {
		for (HeaderFilter headerFilter : HEADER_FILTERS) {
			if (!headerFilter.allow(header.getKey(), header.getValue())) {
				return false;
			}
		}
		return true;
	}

	private void writePartsIfNecessary(OperationRequest request, PrintWriter writer) {
		for (OperationRequestPart part : request.getParts()) {
			writer.printf(" -F '%s=", part.getName());
			if (!StringUtils.hasText(part.getSubmittedFileName())) {
				writer.append(part.getContentAsString());
			}
			else {
				writer.printf("@%s", part.getSubmittedFileName());
			}
			if (part.getHeaders().getContentType() != null) {
				writer.append(";type=")
						.append(part.getHeaders().getContentType().toString());
			}

			writer.append("'");
		}
	}

	private void writeContent(OperationRequest request, PrintWriter writer) {
		String content = request.getContentAsString();
		if (StringUtils.hasText(content)) {
			writer.print(String.format(" -d '%s'", content));
		}
		else if (!request.getParts().isEmpty()) {
			for (Entry<String, List<String>> entry : request.getParameters().entrySet()) {
				for (String value : entry.getValue()) {
					writer.print(String.format(" -F '%s=%s'", entry.getKey(), value));
				}
			}
		}
		else if (isPutOrPost(request)) {
			writeContentUsingParameters(request, writer);
		}
	}

	private void writeContentUsingParameters(OperationRequest request,
			PrintWriter writer) {
		Parameters uniqueParameters = getUniqueParameters(request);
		String queryString = uniqueParameters.toQueryString();
		if (StringUtils.hasText(queryString)) {
			writer.print(String.format(" -d '%s'", queryString));
		}
	}

	private Parameters getUniqueParameters(OperationRequest request) {
		Parameters queryStringParameters = new QueryStringParser()
				.parse(request.getUri());
		Parameters uniqueParameters = new Parameters();

		for (Entry<String, List<String>> parameter : request.getParameters().entrySet()) {
			addIfUnique(parameter, queryStringParameters, uniqueParameters);
		}
		return uniqueParameters;
	}

	private void addIfUnique(Entry<String, List<String>> parameter,
			Parameters queryStringParameters, Parameters uniqueParameters) {
		if (!queryStringParameters.containsKey(parameter.getKey())) {
			uniqueParameters.put(parameter.getKey(), parameter.getValue());
		}
		else {
			List<String> candidates = parameter.getValue();
			List<String> existing = queryStringParameters.get(parameter.getKey());
			for (String candidate : candidates) {
				if (!existing.contains(candidate)) {
					uniqueParameters.add(parameter.getKey(), candidate);
				}
			}
		}
	}

	private boolean isPutOrPost(OperationRequest request) {
		return HttpMethod.PUT.equals(request.getMethod())
				|| HttpMethod.POST.equals(request.getMethod());
	}

	private interface HeaderFilter {

		boolean allow(String name, List<String> value);
	}

	private static final class BasicAuthHeaderFilter implements HeaderFilter {

		@Override
		public boolean allow(String name, List<String> value) {
			return !(HttpHeaders.AUTHORIZATION.equals(name) && isBasicAuthHeader(value));
		}

		static boolean isBasicAuthHeader(List<String> value) {
			return value != null && (!value.isEmpty())
					&& value.get(0).startsWith("Basic ");
		}

		static String decodeBasicAuthHeader(List<String> value) {
			return new String(Base64Utils.decodeFromString(value.get(0).substring(6)));
		}

	}

	private static final class NamedHeaderFilter implements HeaderFilter {

		private final String name;

		private NamedHeaderFilter(String name) {
			this.name = name;
		}

		@Override
		public boolean allow(String name, List<String> value) {
			return !this.name.equalsIgnoreCase(name);
		}

	}

}
