package com.pandola.http.rest;

import java.util.Map;

public interface HttpClient {

  HttpResponse sendRequest(HttpMethod method, String path, String input, String contentType);

  HttpResponse sendRequest(HttpMethod method, String url, String input, String contentType, Map<String, String> headers);
}
