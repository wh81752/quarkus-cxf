package io.quarkiverse.it.cxf;

import javax.annotation.Resource;
import javax.inject.Inject;
import javax.jws.WebParam;
import javax.jws.WebService;
import javax.xml.ws.BindingType;
import javax.xml.ws.WebServiceContext;

@WebService(endpointInterface = "io.quarkiverse.it.cxf.GreetingWebService", serviceName = "GreetingWebService")
@BindingType(javax.xml.ws.soap.SOAPBinding.SOAP12HTTP_BINDING)
public class GreetingWebServiceImpl implements GreetingWebService {
    @Resource
    WebServiceContext context;

    @Inject
    public HelloResource helloResource;

    @Override
    public String reply(@WebParam(name = "text") String text) {
        return helloResource.getHello() + text;
    }

    @Override
    public String ping(@WebParam(name = "text") String text) {
        return helloResource.getHello() + text;
    }

    @Override
    public String getUserName() {
        return context.getUserPrincipal().getName();
    }

}
