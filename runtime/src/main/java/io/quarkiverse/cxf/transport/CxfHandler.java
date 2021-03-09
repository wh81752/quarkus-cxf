package io.quarkiverse.cxf.transport;

import java.io.IOException;
import java.net.URI;
import java.util.*;

import javax.enterprise.inject.Instance;
import javax.enterprise.inject.UnsatisfiedResolutionException;
import javax.enterprise.inject.spi.CDI;
import javax.servlet.ServletException;

import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.common.classloader.ClassLoaderUtils;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.feature.Feature;
import org.apache.cxf.interceptor.Interceptor;
import org.apache.cxf.jaxws.JaxWsServerFactoryBean;
import org.apache.cxf.message.Message;
import org.apache.cxf.transport.ConduitInitiatorManager;
import org.apache.cxf.transport.DestinationFactoryManager;
import org.apache.cxf.transport.http.AbstractHTTPDestination;
import org.apache.cxf.transport.http.DestinationRegistry;
import org.apache.cxf.transport.http.DestinationRegistryImpl;
import org.apache.cxf.transport.servlet.BaseUrlHelper;
import org.apache.cxf.transport.servlet.ServletController;
import org.apache.cxf.transport.servlet.servicelist.ServiceListGeneratorServlet;
import org.jboss.logging.Logger;
import org.wildfly.common.annotation.Nullable;

import io.quarkiverse.cxf.*;
import io.quarkus.arc.ManagedContext;
import io.quarkus.arc.runtime.BeanContainer;
import io.quarkus.security.identity.CurrentIdentityAssociation;
import io.quarkus.security.identity.IdentityProviderManager;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.vertx.http.runtime.CurrentVertxRequest;
import io.quarkus.vertx.http.runtime.security.QuarkusHttpUser;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;

/**
 * CxfHandler handling a VERTX routing event by invoking appropriate CXF servlet handler.
 */
public class CxfHandler implements Handler<RoutingContext> {
    private static final Logger LOGGER = Logger.getLogger(CxfHandler.class);
    private static final String ALLOWED_METHODS = "POST, GET, PUT, DELETE, HEAD, OPTIONS, TRACE";
    private ServiceListGeneratorServlet serviceListGeneratorServlet;
    private Bus bus;
    private ClassLoader loader;
    private DestinationRegistry destinationRegistry;
    private String servletPath;
    private ServletController controller;
    private BeanContainer beanContainer;
    private CurrentIdentityAssociation association;
    private IdentityProviderManager identityProviderManager;
    private CurrentVertxRequest currentVertxRequest;

    private static final Map<String, String> RESPONSE_HEADERS = new HashMap<>();

    static {
        RESPONSE_HEADERS.put("Access-Control-Allow-Origin", "*");
        RESPONSE_HEADERS.put("Access-Control-Allow-Credentials", "true");
        RESPONSE_HEADERS.put("Access-Control-Allow-Methods", ALLOWED_METHODS);
        RESPONSE_HEADERS.put("Access-Control-Allow-Headers", "Content-Type, Authorization");
        RESPONSE_HEADERS.put("Access-Control-Max-Age", "86400");
    }

    public CxfHandler() {
    }

    public CxfHandler(
            String path,
            BeanContainer beanContainer) {
        LOGGER.trace("CxfHandler created");
        this.beanContainer = beanContainer;
        Instance<CurrentIdentityAssociation> association = CDI.current().select(CurrentIdentityAssociation.class);
        this.association = association.isResolvable() ? association.get() : null;
        Instance<IdentityProviderManager> identityProviderManager = CDI.current().select(IdentityProviderManager.class);
        this.identityProviderManager = identityProviderManager.isResolvable() ? identityProviderManager.get() : null;
        this.currentVertxRequest = CDI.current().select(CurrentVertxRequest.class).get();
        this.bus = BusFactory.getDefaultBus();
        BusFactory.setDefaultBus(bus);
        this.loader = this.bus.getExtension(ClassLoader.class);

        LOGGER.trace("load destination");
        DestinationFactoryManager dfm = this.bus.getExtension(DestinationFactoryManager.class);
        this.destinationRegistry = new DestinationRegistryImpl();

        VertxDestinationFactory destinationFactory = new VertxDestinationFactory(destinationRegistry);
        dfm.registerDestinationFactory("http://cxf.apache.org/transports/quarkus", destinationFactory);
        ConduitInitiatorManager extension = bus.getExtension(ConduitInitiatorManager.class);
        extension.registerConduitInitiator("http://cxf.apache.org/transports/quarkus", destinationFactory);

        VertxServletConfig servletConfig = new VertxServletConfig();
        this.serviceListGeneratorServlet = new ServiceListGeneratorServlet(destinationRegistry, bus);
        this.serviceListGeneratorServlet.init(servletConfig);
        this.controller = new ServletController(destinationRegistry, servletConfig, serviceListGeneratorServlet);
        this.serviceListGeneratorServlet.init(new VertxServletConfig());
        this.servletPath = path;
    }

    /**
     * Register a servlet within this handler.
     */
    public CxfHandler register(
            CXFServletInfo servletInfo,
            List<String> wrapperClasses) {
        VertxDestinationFactory destinationFactory;
        JaxWsServerFactoryBean factory;

        destinationFactory = new VertxDestinationFactory(destinationRegistry);
        factory = new JaxWsServerFactoryBean(new QuarkusJaxWsServiceFactoryBean(wrapperClasses));
        factory.setDestinationFactory(destinationFactory);
        factory.setBus(bus);
        //suboptimal because done it in loop but not a real issue...
        Object instanceService = getInstance(servletInfo.getClassName());
        if (instanceService == null) {
            LOGGER.error("Cannot initialize " + servletInfo.toString());
            return this;
        }
        Class<?> seiClass = null;
        if (servletInfo.getSei() != null) {
            seiClass = loadClass(servletInfo.getSei());
            factory.setServiceClass(seiClass);
        }
        if (seiClass == null) {
            LOGGER.warn("sei not found: " + servletInfo.getSei());
        }
        factory.setAddress(servletInfo.getRelativePath());
        factory.setServiceBean(instanceService);
        if (servletInfo.getWsdlPath() != null) {
            factory.setWsdlLocation(servletInfo.getWsdlPath());
        }
        if (!servletInfo.getFeatures().isEmpty()) {
            List<Feature> features = new ArrayList<>();
            for (String feature : servletInfo.getFeatures()) {
                Feature instanceFeature = (Feature) getInstance(feature);
                features.add(instanceFeature);
            }
            factory.setFeatures(features);
        }
        if (servletInfo.getSOAPBinding() != null) {
            factory.setBindingId(servletInfo.getSOAPBinding());
        }
        if (servletInfo.getEndpointUrl() != null) {
            factory.setPublishedEndpointUrl(servletInfo.getEndpointUrl());
        }

        Server server = factory.create();
        for (String className : servletInfo.getInFaultInterceptors()) {
            Interceptor<? extends Message> interceptor = (Interceptor<? extends Message>) getInstance(className);
            server.getEndpoint().getInFaultInterceptors().add(interceptor);
        }
        for (String className : servletInfo.getInInterceptors()) {
            Interceptor<? extends Message> interceptor = (Interceptor<? extends Message>) getInstance(className);
            server.getEndpoint().getInInterceptors().add(interceptor);
        }
        for (String className : servletInfo.getOutFaultInterceptors()) {
            Interceptor<? extends Message> interceptor = (Interceptor<? extends Message>) getInstance(className);
            server.getEndpoint().getOutFaultInterceptors().add(interceptor);
        }
        for (String className : servletInfo.getOutInterceptors()) {
            Interceptor<? extends Message> interceptor = (Interceptor<? extends Message>) getInstance(className);
            server.getEndpoint().getOutInterceptors().add(interceptor);
        }

        String fmt = "WebService %s mounted at %s";
        String log = String.format(fmt, servletInfo.getClassName(),
                String.format("%s%s", this.servletPath, servletInfo.getRelativePath()));
        LOGGER.info(log);
        return this;
    }

    private @Nullable Class<?> loadClass(String className) {
        try {
            return Thread.currentThread().getContextClassLoader().loadClass(className);
        } catch (ClassNotFoundException e) {
            //silent fail
        }
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            LOGGER.warn("failed to load class " + className);
            return null;
        }
    }

    private @Nullable Object getInstance(String className) {
        Class<?> classObj = loadClass(className);
        try {
            return CDI.current().select(classObj).get();
        } catch (UnsatisfiedResolutionException e) {
            //silent fail
        }
        try {
            return classObj.getConstructor().newInstance();
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    @Override
    public void handle(RoutingContext event) {
        ClassLoaderUtils.ClassLoaderHolder origLoader = null;
        Bus origBus = null;
        try {
            if (this.loader != null) {
                origLoader = ClassLoaderUtils.setThreadContextClassloader(this.loader);
            }

            if (this.bus != null) {
                origBus = BusFactory.getAndSetThreadDefaultBus(this.bus);
            }

            process(event);
        } finally {
            if (origBus != this.bus) {
                BusFactory.setThreadDefaultBus(origBus);
            }

            if (origLoader != null) {
                origLoader.reset();
            }

        }
    }

    protected void generateNotFound(
            HttpServerRequest request,
            HttpServerResponse res) {
        res.setStatusCode(404);
        res.headers().add("Content-Type", "text/html");
        res.end("<html><body>No service was found.</body></html>");
    }

    protected void updateDestination(
            HttpServerRequest request,
            AbstractHTTPDestination d) {
        String base = getBaseURL(request);
        String ad = d.getEndpointInfo().getAddress();
        if (ad == null && d.getAddress() != null && d.getAddress().getAddress() != null) {
            ad = d.getAddress().getAddress().getValue();
            if (ad == null) {
                ad = "/";
            }
        }

        if (ad != null && !ad.startsWith("http")) {
            BaseUrlHelper.setAddress(d, base + ad);
        }

    }

    private String getBaseURL(HttpServerRequest request) {
        String reqPrefix = request.uri();
        String pathInfo = request.path();
        if (!"/".equals(pathInfo) || reqPrefix.contains(";")) {
            StringBuilder sb = new StringBuilder();
            URI uri = URI.create(reqPrefix);
            sb.append(uri.getScheme()).append("://").append(uri.getRawAuthority());
            String contextPath = request.path();
            if (contextPath != null) {
                sb.append(contextPath);
            }
            reqPrefix = sb.toString();
        }

        return reqPrefix;
    }

    private void process(RoutingContext event) {
        ManagedContext requestContext = this.beanContainer.requestContext();
        requestContext.activate();
        if (association != null) {
            QuarkusHttpUser existing = (QuarkusHttpUser) event.user();
            if (existing != null) {
                SecurityIdentity identity = existing.getSecurityIdentity();
                association.setIdentity(identity);
            } else {
                association.setIdentity(QuarkusHttpUser.getSecurityIdentity(event, identityProviderManager));
            }
        }
        currentVertxRequest.setCurrent(event);
        try {
            VertxHttpServletRequest req = new VertxHttpServletRequest(event, "", servletPath);
            VertxHttpServletResponse resp = new VertxHttpServletResponse(event);
            controller.invoke(req, resp);
            resp.end();
        } catch (ServletException se) {
            LOGGER.warn("Internal server error", se);
            event.fail(500, se);
        } catch (IOException ioe) {
            LOGGER.warn("Cannot list or instantiate web service", ioe);
            event.fail(404, ioe);
        } finally {
            if (requestContext.isActive()) {
                requestContext.terminate();
            }
        }
    }

}
