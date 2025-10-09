package org.example.infrastructure;

import jakarta.xml.ws.Endpoint;

public class SoapServer {

    public static void main(String[] args) {
        String url = "http://localhost:8080/ws";
//        Endpoint.publish(url, new UnifiedSoapController());
        System.out.println("🟢 SOAP server iniciado en: " + url);
    }
}
