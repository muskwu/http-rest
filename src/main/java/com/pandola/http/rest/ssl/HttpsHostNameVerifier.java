package com.pandola.http.rest.ssl;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;

public class HttpsHostNameVerifier implements HostnameVerifier {

    public boolean verify(String s, SSLSession sslSession) {
        return true;
    }

}
