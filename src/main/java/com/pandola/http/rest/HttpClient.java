package com.pandola.http.rest;

import java.util.Map;

public interface HttpClient {

  /**
   *
   * @param method
   * @param path
   * @param requestBody
   * @param contentType
   * @return
   */
  HttpResponse sendRequest(HttpMethod method, String path, String requestBody, String contentType);


  /**
   *
   * @param method
   * @param url
   * @param requestBody
   * @param contentType
   * @param headers
   * @return
   */
  HttpResponse sendRequest(HttpMethod method, String url, String requestBody, String contentType, Map<String, String> headers);
}
