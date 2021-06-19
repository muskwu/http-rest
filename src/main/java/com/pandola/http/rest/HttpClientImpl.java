package com.pandola.http.rest;

import com.pandora.utils.net.http.ssl.HttpsHostNameVerifier;
import com.pandora.utils.net.http.ssl.HttpsTrustManager;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

public class HttpClientImpl implements HttpClient {

  private int connectTimeoutMillis;

  private int readTimeoutMillis;

  /**
   * <b>IMPORTANT</b>
   * This constructor does not set any timeouts on connection.
   * It's strongly recommended to set global timeouts if you use this constructor.
   * For HotSpot JVM global timeouts can be set with
   * <i>sun.net.client.defaultConnectTimeout</i> and <i>sun.net.client.defaultReadTimeout</i>.
   *
   * @see <a href="http://docs.oracle.com/javase/8/docs/technotes/guides/net/properties.html">Java 8 Oracle Networking Properties</a>.
   */
  public HttpClientImpl() {
  }

  public HttpClientImpl(int connectTimeoutMillis, int readTimeoutMillis) {
    this.connectTimeoutMillis = connectTimeoutMillis;
    this.readTimeoutMillis = readTimeoutMillis;
  }



  @Override
  public HttpResponse sendRequest(HttpMethod method, String url, String requestBody, String contentType) {
    return this.sendRequest(method, url, requestBody, contentType, new HashMap<>());
  }




  @Override
  public HttpResponse sendRequest(HttpMethod method, String url, String requestBody, String contentType, Map<String, String> headers) {
    HttpURLConnection connection = prepareConnection(method, url, contentType, headers);
    try
    {
      connect(connection);
      return trySendRequest(method, connection, requestBody);
    } finally {
      connection.disconnect();
    }
  }

  private void connect(HttpURLConnection connection) {
    try
    {
      connection.connect();
    } catch (IOException e) {
      String msg = String.format("Failed to connect to url: %s. Reason: %s", connection.getURL(), e.getMessage()) ;
      throw new HttpCallException(msg);
    }
  }



  private HttpResponse trySendRequest(HttpMethod method, HttpURLConnection connection, String requestBody) {
    if (method != HttpMethod.GET) {
      sendRequestBody(connection, requestBody);
    }

    int status = getResponseCode(connection);

    HttpResponse response = new HttpResponse()
        .httpMethod(method)
        .url(connection.getURL().toString())
        .requestBody(requestBody)
        .code(status)
        .responseHeaders(connection.getHeaderFields());

    boolean success = status < 400;

    if (success) {
      if (isOctetStream(connection))
      {
        byte[] result = tryReadBinaryResult(connection);
        response.responseBinaryBody(result);
      }
      else
      {
        String result = tryReadResultString(connection, true);
        response.responseBody(result);
      }
    }
    else {
      String result = tryReadResultString(connection, false);
      response.responseBody(result);
    }

    return response;
  }









  private void sendRequestBody(HttpURLConnection connection, String requestBody) {
    try
    {
      DataOutputStream wr = new DataOutputStream(connection.getOutputStream());
      BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(wr, "UTF-8"));
      writer.write(requestBody);
      writer.flush();
      writer.close();
    } catch (IOException ioe) {
      logSendRequestIOException(connection, ioe, requestBody);
    }
  }





  private int getResponseCode(HttpURLConnection connection) {
    try {
      return connection.getResponseCode();
    } catch (IOException e) {
      throw new HttpCallException("Can't get response code, assuming failure", e);
    }
  }





  private String tryReadResultString(HttpURLConnection connection, boolean success) {
    try {
      return readResultString(connection, success);
    }
    catch (IOException e) {
      throw new HttpCallException(String.format(
          "Can't read response from api call to %s",
          connection.getURL()
      ), e);
    }
  }

  private byte[] tryReadBinaryResult(HttpURLConnection connection) {
    try {
      return readBinaryResult(connection);
    }
    catch (IOException e) {
      throw new HttpCallException(String.format(
          "Can't read response from api call to %s",
          connection.getURL()
      ), e);
    }
  }

  private String readResultString(HttpURLConnection connection, boolean success) throws IOException {
    InputStream is = null ;
    if (success) {
      is = connection.getInputStream();
    }
    else {
      is = connection.getErrorStream();
    }
    is = wapperInputStream(connection, is) ;
    String contentType = connection.getHeaderField("Content-Type") ;
    String charsetName = "UTF-8" ;
    if(contentType!=null && contentType.contains("Charset"))
    {
      int flag = contentType.indexOf("Charset") ;
      charsetName = contentType.substring(flag+8).trim() ;
    }
    InputStreamReader isReader = new InputStreamReader(is, charsetName);
    BufferedReader reader = new BufferedReader(isReader);

    StringBuilder result = new StringBuilder();
    String line;
    while ((line = reader.readLine()) != null) {
      result.append(line).append("\n");
    }

    return result.toString();
  }

  private byte[] readBinaryResult(HttpURLConnection connection) throws IOException {
    BufferedInputStream stream = new BufferedInputStream(connection.getInputStream());
    return StreamUtil.toByteArray(stream);
  }

  private boolean isOctetStream(HttpURLConnection connection) {
    return connection.getHeaderField("Content-Type").toLowerCase().contains("application/octet-stream");
  }

  private void logSendRequestIOException(HttpURLConnection connection, IOException ioe, String inputJson) {
    int responseCode = -1;
    try {
      responseCode = getResponseCode(connection);
    }
    catch (Exception e) {
    }

    String responseBody;
    if (isOctetStream(connection)) {
      responseBody = "Binary Content";
    }
    else {
      responseBody = tryReadResultString(connection, false);
    }
    throw new HttpCallException(String.format(
        "Failed to call api endpoint [%s], input: [%s], status: [%s], response: %s",
        connection.getURL(),
        inputJson,
        responseCode,
        responseBody
    ), ioe);
  }

  //add by musk.wu
  private InputStream wapperInputStream(HttpURLConnection connection, InputStream stream) {
    try
    {
      String rcd = connection.getContentEncoding() ;
      if(rcd != null && rcd.contains("gzip"))
      {
        stream = new GZIPInputStream(stream);
      }

      return stream ;
    } catch (Exception ex) {
      throw new IllegalStateException(ex) ;
    }
  }



  private HttpURLConnection prepareConnection(HttpMethod method, String url, String contentType, Map<String, String> headers) {
    try
    {
      HttpURLConnection conn = newHttpURLConnection(url) ;
      conn.setRequestMethod(method.name());
      conn.setRequestProperty("Content-Type", contentType);
      headers.forEach(conn::setRequestProperty);
      conn.setDoOutput(true);
      conn.setConnectTimeout(connectTimeoutMillis);
      conn.setReadTimeout(readTimeoutMillis);
      return conn;
    } catch (IOException connectionException) {
      throw new HttpCallException(String.format("Failed to connect to url: %s", url), connectionException);
    }
  }


  private static HttpURLConnection newHttpURLConnection(String url) {
    try
    {
      HttpURLConnection conn = null ;
      if(url.startsWith("https"))
      {
        conn = buildHttpsConnection(url) ;
      }
      else
      {
        URL endpoint = new URL(url);
        conn = (HttpURLConnection) endpoint.openConnection();
      }
      return conn ;
    }catch (Exception ex) {throw new IllegalStateException(ex) ; }
  }



  private static HttpURLConnection buildHttpsConnection(String url) {
    try
    {
      TrustManager[] trustManagers = { new HttpsTrustManager() };
      SSLContext sslContext = SSLContext.getInstance("SSL");
      sslContext.init(null, trustManagers, new SecureRandom());
      SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

      URL endpoint = new URL(url);
      HttpsURLConnection conn = (HttpsURLConnection) endpoint.openConnection();
      conn.setSSLSocketFactory(sslSocketFactory);
      conn.setHostnameVerifier(new HttpsHostNameVerifier());
      return conn ;
    }catch (Exception ex) { throw new IllegalStateException(ex) ; }

  }
}
