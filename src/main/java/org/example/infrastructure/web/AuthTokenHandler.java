package org.example.infrastructure.web;


import jakarta.xml.soap.SOAPFactory;
import jakarta.xml.soap.SOAPElement;
import jakarta.xml.soap.SOAPFault;
import jakarta.xml.soap.SOAPHeader;
import jakarta.xml.soap.SOAPMessage;
import jakarta.xml.ws.handler.MessageContext;
import jakarta.xml.ws.handler.soap.SOAPHandler;
import jakarta.xml.ws.handler.soap.SOAPMessageContext;
import jakarta.xml.ws.soap.SOAPFaultException;

import javax.xml.namespace.QName;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Este handler intercepta las llamadas SOAP y busca el token en el SOAP Header.
 * Para usarlo, se debe  enviar el token así en Postman:
 *
 * <soap:Header>
 *     <tns:AuthToken xmlns:tns="http://Soap.example.org/">tu_token_jwt_aqui</tns:AuthToken>
 * </soap:Header>
 *
 */
public class AuthTokenHandler implements SOAPHandler<SOAPMessageContext> {

    private static final String NAMESPACE_URI = "http://Soap.example.org/";
    private final TokenManager tokenManager;


    public AuthTokenHandler(TokenManager tokenManager) {
        this.tokenManager = tokenManager;
    }

    @Override
    public boolean handleMessage(SOAPMessageContext context) {


        Boolean isOutbound = (Boolean) context.get(MessageContext.MESSAGE_OUTBOUND_PROPERTY);
        if (!isOutbound) {
            QName operation = (QName) context.get(MessageContext.WSDL_OPERATION);
            if (operation != null) {
                String methodName = operation.getLocalPart();

                    try {
                        SOAPMessage message = context.getMessage();
                        SOAPHeader header = message.getSOAPHeader();

                        if (header == null) {
                            throw new RuntimeException("No SOAP header presente");
                        }

                        String token = extractToken(header);

                        if (token == null || token.isEmpty()) {
                            SOAPFactory soapFactory = SOAPFactory.newInstance();
                            SOAPFault fault = soapFactory.createFault("Token ausente en el header", new QName("Client.Auth"));
                            throw new SOAPFaultException(fault);

                        }

                        if (!tokenManager.validateToken(token)) {
                            throw new RuntimeException("Token inválido o expirado");
                        }

                        // Guardar el userId en el contexto para usarlo en el mé_todo
                        Long userId = tokenManager.extractUserId(token);


                        if (userId == null) {
                            throw new RuntimeException("No se pudo extraer userId del token");
                        }

                        context.put("userId", userId);
                        context.setScope("userId", MessageContext.Scope.APPLICATION);
                        System.out.println("UserId guardado en contexto: " + context.get("userId"));

                    } catch (Exception e) {
                        throw new RuntimeException("Error de autenticación: " + e.getMessage());
                    }
                }
            }

        return true;
    }

    /**
     * Extrae el token del SOAP Header.
     * Busca un elemento <AuthToken>token_aqui</AuthToken>
     */
    private String extractToken(SOAPHeader header) {
        try {
            Iterator<?> it = header.getChildElements();
            while (it.hasNext()) {
                Object next = it.next();
                if (next instanceof SOAPElement) {
                    SOAPElement element = (SOAPElement) next;
                    if ("AuthToken".equals(element.getLocalName())) {
                        return element.getTextContent();
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Error al extraer token: " + e.getMessage());
        }
        return null;
    }


    @Override public boolean handleFault(SOAPMessageContext context) { return true; }
    @Override public void close(MessageContext context) {}
    @Override
    public Set<QName> getHeaders() {
        return Set.of(new QName(NAMESPACE_URI, "AuthToken"));
    }

}
