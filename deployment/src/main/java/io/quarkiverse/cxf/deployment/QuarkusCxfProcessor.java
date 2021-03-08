package io.quarkiverse.cxf.deployment;

import static io.quarkiverse.cxf.deployment.CxfWebServiceBuildItem.builder;
import static io.quarkus.vertx.http.deployment.RouteBuildItem.builder;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Default;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;

import org.apache.cxf.bus.extension.ExtensionManagerImpl;
import org.jboss.jandex.*;
import org.jboss.logging.Logger;

import io.quarkiverse.cxf.*;
import io.quarkus.arc.deployment.*;
import io.quarkus.arc.processor.DotNames;
import io.quarkus.builder.item.BuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.deployment.builditem.ExtensionSslNativeSupportBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.IndexDependencyBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageProxyDefinitionBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageResourceBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.deployment.builditem.nativeimage.RuntimeInitializedClassBuildItem;
import io.quarkus.gizmo.*;
import io.quarkus.vertx.http.deployment.RouteBuildItem;
import io.quarkus.vertx.http.runtime.HandlerType;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

class QuarkusCxfProcessor {

    public static final String FEATURE_CXF = "cxf";
    public static final DotName WEBSERVICE_ANNOTATION = DotName.createSimple("javax.jws.WebService");
    public static final DotName WEBSERVICE_CLIENT = DotName.createSimple("javax.xml.ws.WebServiceClient");
    public static final DotName REQUEST_WRAPPER_ANNOTATION = DotName.createSimple("javax.xml.ws.RequestWrapper");
    public static final DotName RESPONSE_WRAPPER_ANNOTATION = DotName.createSimple("javax.xml.ws.ResponseWrapper");
    public static final DotName ABSTRACT_FEATURE = DotName.createSimple("org.apache.cxf.feature.AbstractFeature");
    public static final DotName ABSTRACT_INTERCEPTOR = DotName.createSimple(
            "org.apache.cxf.phase.AbstractPhaseInterceptor");
    public static final DotName DATABINDING = DotName.createSimple("org.apache.cxf.databinding");
    public static final DotName BINDING_TYPE_ANNOTATION = DotName.createSimple("javax.xml.ws.BindingType");
    public static final DotName XML_NAMESPACE = DotName.createSimple("com.sun.xml.txw2.annotation.XmlNamespace");
    public static final DotName XML_SEE_ALSO = DotName.createSimple("javax.xml.bind.annotation.XmlSeeAlso");
    public static final Logger LOGGER = Logger.getLogger(QuarkusCxfProcessor.class);

    @BuildStep
    public void generateWSDL(
            BuildProducer<NativeImageResourceBuildItem> ressources,
            CxfBuildTimeConfig cxfBuildTimeConfig) {
        if (cxfBuildTimeConfig.wsdlPath.isPresent()) {
            for (String wsdlPath : cxfBuildTimeConfig.wsdlPath.get()) {
                ressources.produce(new NativeImageResourceBuildItem(wsdlPath));
            }
        }
    }

    @BuildStep
    void markBeansAsUnremovable(BuildProducer<UnremovableBeanBuildItem> unremovables) {
        UnremovableBeanBuildItem b1, b2;
        b1 = new UnremovableBeanBuildItem(beanInfo -> {
            String nameWithPackage = beanInfo.getBeanClass().local();
            return nameWithPackage.contains(".jaxws_asm") || nameWithPackage.endsWith("ObjectFactory");
        });
        Set<String> extensibilities = asSet(
                "io.quarkiverse.cxf.AddressTypeExtensibility",
                "io.quarkiverse.cxf.HTTPClientPolicyExtensibility",
                "io.quarkiverse.cxf.HTTPServerPolicyExtensibility",
                "io.quarkiverse.cxf.XMLBindingMessageFormatExtensibility",
                "io.quarkiverse.cxf.XMLFormatBindingExtensibility");

        b2 = new UnremovableBeanBuildItem(new UnremovableBeanBuildItem.BeanClassNamesExclusion(extensibilities));
        produce(unremovables, b1, b2);
    }

    /**
     * This build step just consumes (intermediate) CxfUeberBuildItem in order to produce all relevant other items, like
     * unremovable and additional Beans, proxies, features, reflective classes and so on.
     */
    @BuildStep
    public void produceCxfRelatedBuildItems(
            // consume
            List<CxfUeberBuildItem> items,
            // produce
            BuildProducer<FeatureBuildItem> feature,
            BuildProducer<ReflectiveClassBuildItem> reflectiveClass,
            BuildProducer<NativeImageProxyDefinitionBuildItem> proxies,
            BuildProducer<GeneratedBeanBuildItem> generatedBeans,
            BuildProducer<CxfWebServiceBuildItem> cxfWebServices,
            BuildProducer<AdditionalBeanBuildItem> additionalBeans,
            BuildProducer<UnremovableBeanBuildItem> unremovableBeans) {
        items.forEach(item -> {
            item.features.forEach(feature::produce);
            item.reflectiveClasss.forEach(reflectiveClass::produce);
            item.proxies.forEach(proxies::produce);
            item.generatedBeans.forEach(generatedBeans::produce);
            item.cxfWebServices.forEach(cxfWebServices::produce);
            item.additionalBeans.forEach(additionalBeans::produce);
            item.unremovableBeans.forEach(unremovableBeans::produce);
        });
    }

    @BuildStep
    public void build(
            CombinedIndexBuildItem combinedIndexBuildItem,
            CxfBuildTimeConfig cxfBuildTimeConfig,
            BuildProducer<CxfUeberBuildItem> bp) {
        CxfUeberBuildItem cxf;
        IndexView index;

        index = combinedIndexBuildItem.getIndex();
        cxf = new CxfUeberBuildItem();

        //
        // The provided feature.
        //
        cxf.produceFeature();

        // Register package-infos for reflection
        index.getAnnotations(XML_NAMESPACE)
                .stream()
                .map(AnnotationInstance::target)
                .map(AnnotationTarget::asClass)
                .forEach(cxf::produceReflectiveClass);

        //
        // produce reflective classes out of all known subclasses.
        //
        Stream.of(ABSTRACT_FEATURE, ABSTRACT_INTERCEPTOR, DATABINDING)
                .map(index::getAllKnownSubclasses)
                .flatMap(Collection::stream)
                .forEach(cxf::produceReflectiveClass);

        //TODO bad code it is set in loop but use outside...

        index.getAnnotations(WEBSERVICE_ANNOTATION)
                .stream()
                .filter(it -> it.target().kind() == AnnotationTarget.Kind.CLASS)
                .map(annotation -> {
                    return builder(annotation).withConfig(cxfBuildTimeConfig);
                })
                .peek(ws -> {
                    cxf.produceReflectiveClass(ws.wsClass());
                    cxf.unremovable(ws.wsClass());
                })
                .filter(CxfWebServiceBuildItemBuilder::isInterface)
                .peek(ws -> {

                    cxf.produceProxies(
                            ws.wsClass().name().toString(),
                            "javax.xml.ws.BindingProvider",
                            "java.io.Closeable",
                            "org.apache.cxf.endpoint.Client");

                    //
                    // Produce reflective classes for all annotaded classes
                    //
                    ws.wsClass()
                            .methods()
                            .stream()
                            .map((MethodInfo mi) -> asList(
                                    mi.annotation(REQUEST_WRAPPER_ANNOTATION),
                                    mi.annotation(RESPONSE_WRAPPER_ANNOTATION)))
                            .flatMap(Collection::stream)
                            .filter(Objects::nonNull)
                            .map(ai -> ai.value("className"))
                            .forEach(cxf::produceReflectiveClass);

                })
                .forEach(ws -> {
                    //List<String> wrapperClassNames = new ArrayList<>();

                    // TODO: Perhaps not correct -> wrapper classes are not known.
                    cxf.capture(ws.sei);

                    Collection<ClassInfo> implementors = implementorsOf(index, ws.sei);

                    //TODO add soap1.2 in config file
                    //if no implementor, it mean it is client
                    if (implementors == null || implementors.isEmpty()) {
                        // make new WebServiceCxf to keep things seperated from original.
                        CxfWebServiceBuildItemBuilder client = builder(ws.build());
                        client.path = cxfBuildTimeConfig.path;

                        generateCxfClientProducer(cxf, client.sei);

                        AnnotationInstance webserviceClient = findWebServiceClientAnnotation(
                                index,
                                client.wsClass().name());

                        if (webserviceClient != null) {
                            client.withClientAnnotation(webserviceClient);
                        }
                        cxf.produce(client);
                    } else {
                        cxf.produce(ws);
                        implementors.forEach(wsClass -> {
                            cxf.produce(builder(ws)
                                    .withImplementor(wsClass)
                                    .withBinding(wsClass)
                                    .build());
                        });

                    }
                });

        //
        // eventually produce my items for consumption.
        //
        bp.produce(cxf);
    }

    /**
     * This build step produces two items: o DefaultRouteBuildItem ; and o RouteBuildItem
     */
    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    public void startRoute(
            CXFRecorder recorder,
            CxfBuildTimeConfig cxfBuildTimeConfig,
            CxfConfig cxfConfig,
            // consume
            BeanContainerBuildItem beanContainer,
            List<CxfWebServiceBuildItem> cxfWebServices,
            // produce
            BuildProducer<RouteBuildItem> routes) {
        List<CXFServiceData> services;

        //
        // Prepare my webservices.
        //

        services = cxfWebServices
                .stream()
                .filter(CxfWebServiceBuildItem::IsService)
                .filter(CxfWebServiceBuildItem::hasImplementor)
                .map(CxfWebServiceBuildItem::asRuntimeData)
                .collect(toList());

        //
        // Bail out if nothing to do.
        //

        if (services.isEmpty()) {
            LOGGER.debug("no webservices defined, not setting up servlet.");
            return;
        }

        //
        // Mysterious: Get a Servlet path.
        //

        // Changed:
        // Derive (servlet path / vert.x route) from build-time config rather from
        // runtime config.
        String routepath = cxfBuildTimeConfig.path;

        //        routepath = services.stream()
        //                .map(service -> service.path)
        //                .filter(Objects::nonNull)
        //                .filter(it -> !it.isEmpty()).findAny()
        //                .orElse(null);
        //
        //        if (routepath == null) {
        //            throw new IllegalStateException("path is not defined.");
        //        }

        //
        // Create a VERTX handler for CXF services and mount it at route.
        //

        RouteBuildItem route;
        Handler<RoutingContext> handler;

        // Here we get a VERTX handler from our recorder. A vertx handler is the main interface.
        // It's about how to handle a vertex event. Such an event is a HTTP request represented
        // by an instance of class RoutingContext.
        //
        // Question(s):
        // Q1: why do we need to receive such a handler here?
        // Q2: why do we need to produce something?
        handler = recorder.registerServlet(routepath, cxfConfig, beanContainer.getValue(), services);
        route = builder()
                .route(getMappingPath(routepath))
                .handler(handler)
                .handlerType(HandlerType.BLOCKING)
                .build();
        // A2: Here we tell VERTX that we have a HTTP=Request handler. We inform VERTX the quarkus
        // way: Just lookup the build-items VERTX consumes (see https://quarkus.io/guides/all-builditems)
        // and you endup with this one:
        // https://github.com/quarkusio/quarkus/blob/master/extensions/vertx-http/deployment/src/main/java/io/quarkus
        // /vertx/http/deployment/RouteBuildItem.java
        // The core concept of VERT.x WEB goes like this:
        // 1. Provide a route and a handler. Then, on an incoming request,..
        // 2. VERT.x tries in order all routes, the first matching one wins,
        // 3. and the associated handler is applied. The handler then does
        // 4. something and ..
        // 5. eventually ends the request ; or
        // 6. continues handling by doing nothing => next handler (iff available)
        // 7. starts handling.

        // Note: There is just one route for all CXF webservices. Let's log it to
        // to inform developer what's going on.
        LOGGER.info(String.format("*** route for all WS services: route(%s)", getMappingPath(routepath)));
        routes.produce(route);
    }

    /**
     * Build step producing client beans.
     */
    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    public void startClient(
            CXFRecorder recorder,
            CxfConfig cxfConfig,
            List<CxfWebServiceBuildItem> cxfWebServices,
            BuildProducer<SyntheticBeanBuildItem> synthetics) {

        //
        // Create injectable bean per SEI-only interface, i.e. for each
        // class annotated as @WebService and without implementation.
        //
        cxfWebServices
                .stream()
                .filter(CxfWebServiceBuildItem::IsClient)
                .map(CxfWebServiceBuildItem::asRuntimeData)
                .map(cxf -> {
                    String fmt = "producing CXF client bean named '%s' for SEI %s";
                    String msg = String.format(fmt, cxf.sei, cxf.sei);
                    LOGGER.info(msg);
                    return SyntheticBeanBuildItem
                            .configure(CXFClientInfo.class)
                            .named(cxf.sei)
                            .supplier(recorder.cxfClientInfoSupplier(cxfConfig, cxf))
                            .unremovable()
                            .setRuntimeInit()
                            .done();
                }).forEach(synthetics::produce);
    }

    @BuildStep
    BeanDefiningAnnotationBuildItem additionalBeanDefiningAnnotation() {
        return new BeanDefiningAnnotationBuildItem(WEBSERVICE_ANNOTATION);
    }

    @BuildStep
    void buildResources(
            BuildProducer<NativeImageResourceBuildItem> resources,
            BuildProducer<ReflectiveClassBuildItem> reflectiveItems) {
        try {
            Enumeration<URL> urls = ExtensionManagerImpl.class.getClassLoader().getResources(
                    "META-INF/cxf/bus-extensions.txt");
            while (urls.hasMoreElements()) {
                URL url = urls.nextElement();
                try (InputStream openStream = url.openStream()) {
                    //todo set directly extension and avoid load of file at runtime
                    //List<Extension> exts = new TextExtensionFragmentParser(loader).getExtensions(is);
                    //factory.getBus().setExtension();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(openStream));
                    String line = reader.readLine();
                    while (line != null) {
                        String[] cols = line.split(":");
                        //org.apache.cxf.bus.managers.PhaseManagerImpl:org.apache.cxf.phase.PhaseManager:true
                        if (cols.length > 1) {
                            if (!"".equals(cols[0])) {
                                reflectiveItems.produce(new ReflectiveClassBuildItem(true, true, cols[0]));
                            }
                            if (!"".equals(cols[1])) {
                                reflectiveItems.produce(new ReflectiveClassBuildItem(true, true, cols[1]));
                            }
                        }
                        line = reader.readLine();
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.warn("can not open bus-extensions.txt");
        }

    }

    @BuildStep
    ExtensionSslNativeSupportBuildItem ssl() {
        return new ExtensionSslNativeSupportBuildItem(FEATURE_CXF);
    }

    @BuildStep
    void runtimeInitializedClasses(BuildProducer<RuntimeInitializedClassBuildItem> bp) {
        Stream.of(
                "io.netty.buffer.PooledByteBufAllocator",
                "io.netty.buffer.UnpooledHeapByteBuf",
                "io.netty.buffer.UnpooledUnsafeHeapByteBuf",
                "io.netty.buffer.UnpooledByteBufAllocator$InstrumentedUnpooledUnsafeHeapByteBuf",
                "io.netty.buffer.AbstractReferenceCountedByteBuf",
                "org.apache.cxf.staxutils.validation.W3CMultiSchemaFactory",
                "com.sun.xml.bind.v2.runtime.output.FastInfosetStreamWriterOutput").map(RuntimeInitializedClassBuildItem::new)
                .forEach(bp::produce);
    }

    @BuildStep
    void addDependencies(BuildProducer<IndexDependencyBuildItem> bp) {
        Stream.of(
                "org.glassfish.jaxb:txw2",
                "org.glassfish.jaxb:jaxb-runtime").map(it -> it.split(":"))
                .map(it -> new IndexDependencyBuildItem(it[0], it[1]))
                .forEach(bp::produce);
    }

    @BuildStep
    void httpProxies(
            CombinedIndexBuildItem combinedIndexBuildItem,
            BuildProducer<NativeImageProxyDefinitionBuildItem> proxies) {
        IndexView index = combinedIndexBuildItem.getIndex();
        proxies.produce(new NativeImageProxyDefinitionBuildItem("com.sun.xml.txw2.TypedXmlWriter"));
        Set<String> proxiesCreated = new HashSet<>();
        DotName typedXmlWriterDN = DotName.createSimple("com.sun.xml.txw2.TypedXmlWriter");
        // getAllKnownDirectImplementors skip interface, so I have to do it myself.
        produceRecursiveProxies(index, typedXmlWriterDN, proxies, proxiesCreated);
    }

    @BuildStep
    void seeAlso(
            CombinedIndexBuildItem combinedIndexBuildItem,
            BuildProducer<ReflectiveClassBuildItem> reflectiveItems) {
        IndexView index = combinedIndexBuildItem.getIndex();
        for (AnnotationInstance xmlSeeAlsoAnn : index.getAnnotations(XML_SEE_ALSO)) {
            AnnotationValue value = xmlSeeAlsoAnn.value();
            Type[] types = value.asClassArray();
            for (Type t : types) {
                reflectiveItems.produce(new ReflectiveClassBuildItem(false, false, t.name().toString()));
            }
        }
    }

    @BuildStep
    void httpProxies(BuildProducer<NativeImageProxyDefinitionBuildItem> proxies) {
        proxies.produce(new NativeImageProxyDefinitionBuildItem("org.apache.cxf.common.jaxb.JAXBContextProxy"));
        proxies.produce(new NativeImageProxyDefinitionBuildItem("org.apache.cxf.common.jaxb.JAXBBeanInfo"));
        proxies.produce(new NativeImageProxyDefinitionBuildItem("org.apache.cxf.common.jaxb.JAXBUtils$BridgeWrapper"));
        proxies.produce(new NativeImageProxyDefinitionBuildItem("org.apache.cxf.common.jaxb.JAXBUtils$SchemaCompiler"));
        proxies.produce(new NativeImageProxyDefinitionBuildItem("org.apache.cxf.common.util.ASMHelper$ClassWriter"));
        proxies.produce(new NativeImageProxyDefinitionBuildItem("javax.wsdl.extensions.soap.SOAPOperation"));
        proxies.produce(new NativeImageProxyDefinitionBuildItem("javax.wsdl.extensions.soap.SOAPBody"));
        proxies.produce(new NativeImageProxyDefinitionBuildItem("javax.wsdl.extensions.soap.SOAPHeader"));
        proxies.produce(new NativeImageProxyDefinitionBuildItem("javax.wsdl.extensions.soap.SOAPAddress"));
        proxies.produce(new NativeImageProxyDefinitionBuildItem("javax.wsdl.extensions.soap.SOAPBinding"));
        proxies.produce(new NativeImageProxyDefinitionBuildItem("javax.wsdl.extensions.soap.SOAPFault"));
        proxies.produce(new NativeImageProxyDefinitionBuildItem("javax.wsdl.extensions.soap.SOAPHeaderFault"));
        proxies.produce(new NativeImageProxyDefinitionBuildItem(
                "org.apache.cxf.binding.soap.wsdl.extensions.SoapBinding"));
        proxies.produce(new NativeImageProxyDefinitionBuildItem(
                "org.apache.cxf.binding.soap.wsdl.extensions.SoapAddress"));
        proxies.produce(new NativeImageProxyDefinitionBuildItem("org.apache.cxf.binding.soap.wsdl.extensions" +
                ".SoapHeader"));
        proxies.produce(new NativeImageProxyDefinitionBuildItem("org.apache.cxf.binding.soap.wsdl.extensions" +
                ".SoapBody"));
        proxies.produce(new NativeImageProxyDefinitionBuildItem("org.apache.cxf.binding.soap.wsdl.extensions" +
                ".SoapFault"));
        proxies.produce(new NativeImageProxyDefinitionBuildItem(
                "org.apache.cxf.binding.soap.wsdl.extensions.SoapOperation"));
        proxies.produce(new NativeImageProxyDefinitionBuildItem(
                "org.apache.cxf.binding.soap.wsdl.extensions.SoapHeaderFault"));
        produceProxyIfExist(proxies, "com.sun.xml.bind.marshaller.CharacterEscapeHandler");
        produceProxyIfExist(proxies, "com.sun.xml.internal.bind.marshaller.CharacterEscapeHandler");
        produceProxyIfExist(proxies, "org.glassfish.jaxb.core.marshaller.CharacterEscapeHandler");
        produceProxyIfExist(proxies, "com.sun.xml.txw2.output.CharacterEscapeHandler");
        produceProxyIfExist(proxies, "org.glassfish.jaxb.characterEscapeHandler");
        produceProxyIfExist(proxies, "org.glassfish.jaxb.marshaller.CharacterEscapeHandler");
        //proxies.produce(new NativeImageProxyDefinitionBuildItem("com.sun.xml.bind.v2.model.impl.PropertySeed"));
        //proxies.produce(new NativeImageProxyDefinitionBuildItem("com.sun.xml.bind.v2.model.core.TypeInfo"));
        proxies.produce(new NativeImageProxyDefinitionBuildItem("org.apache.cxf.common.jaxb.JAXBUtils$S2JJAXBModel"));
        proxies.produce(new NativeImageProxyDefinitionBuildItem("org.apache.cxf.common.jaxb.JAXBUtils$Options"));
        proxies.produce(new NativeImageProxyDefinitionBuildItem("org.apache.cxf.common.jaxb.JAXBUtils$JCodeModel"));
        proxies.produce(new NativeImageProxyDefinitionBuildItem("org.apache.cxf.common.jaxb.JAXBUtils$Mapping"));
        proxies.produce(new NativeImageProxyDefinitionBuildItem("org.apache.cxf.common.jaxb" +
                ".JAXBUtils$TypeAndAnnotation"));
        proxies.produce(new NativeImageProxyDefinitionBuildItem("org.apache.cxf.common.jaxb.JAXBUtils$JType"));
        proxies.produce(new NativeImageProxyDefinitionBuildItem("org.apache.cxf.common.jaxb.JAXBUtils$JPackage"));
        proxies.produce(new NativeImageProxyDefinitionBuildItem("org.apache.cxf.common.jaxb.JAXBUtils$JDefinedClass"));
    }

    @BuildStep
    void registerReflectionItems(BuildProducer<ReflectiveClassBuildItem> reflectiveItems) {
        //TODO load all bus-extensions.txt file and parse it to generate the reflective class.
        //TODO load all handler from https://github
        // .com/apache/cxf/tree/master/rt/frontend/jaxws/src/main/java/org/apache/cxf/jaxws/handler/types
        reflectiveItems.produce(new ReflectiveClassBuildItem(
                true,
                false,
                "org.apache.cxf.common.jaxb.NamespaceMapper"));

        reflectiveItems.produce(
                new ReflectiveClassBuildItem(
                        true,
                        true,
                        "org.apache.cxf.common.spi.ClassLoaderService",
                        "org.apache.cxf.common.spi" +
                                ".GeneratedClassClassLoaderCapture",
                        "org.apache.cxf.common.spi" +
                                ".ClassGeneratorClassLoader$TypeHelperClassLoader",
                        "org.apache.cxf.common.util.ASMHelper",
                        "org.apache.cxf.common.util.ASMHelperImpl",
                        "org.apache.cxf.common.spi.ClassLoaderProxyService",
                        "org.apache.cxf.common.spi.GeneratedNamespaceClassLoader",
                        "org.apache.cxf.common.spi.NamespaceClassCreator",
                        "org.apache.cxf.common.spi.NamespaceClassGenerator",
                        "org.apache.cxf.binding.corba.utils" +
                                ".CorbaFixedAnyImplClassCreatorProxyService",
                        "org.apache.cxf.binding.corba.utils" +
                                ".CorbaFixedAnyImplClassCreator",
                        "org.apache.cxf.binding.corba.utils" +
                                ".CorbaFixedAnyImplClassLoader",
                        "org.apache.cxf.binding.corba.utils" +
                                ".CorbaFixedAnyImplGenerator",
                        "org.apache.cxf.jaxb.WrapperHelperProxyService",
                        "org.apache.cxf.jaxb.WrapperHelperCreator",
                        "org.apache.cxf.jaxb.WrapperHelperClassGenerator",
                        "org.apache.cxf.jaxb.WrapperHelperClassLoader",
                        "org.apache.cxf.jaxb.FactoryClassProxyService",
                        "org.apache.cxf.jaxb.FactoryClassCreator",
                        "org.apache.cxf.jaxb.FactoryClassGenerator",
                        "org.apache.cxf.jaxb.FactoryClassLoader",
                        "org.apache.cxf.jaxws.spi.WrapperClassCreatorProxyService",
                        "org.apache.cxf.jaxws.spi.WrapperClassCreator",
                        "org.apache.cxf.jaxws.spi.WrapperClassLoader",
                        "org.apache.cxf.jaxws.spi.WrapperClassGenerator",
                        "org.apache.cxf.endpoint.dynamic" +
                                ".ExceptionClassCreatorProxyService",
                        "org.apache.cxf.endpoint.dynamic.ExceptionClassCreator",
                        "org.apache.cxf.endpoint.dynamic.ExceptionClassLoader",
                        "org.apache.cxf.endpoint.dynamic.ExceptionClassGenerator",
                        "org.apache.cxf.wsdl.ExtensionClassCreatorProxyService",
                        "org.apache.cxf.wsdl.ExtensionClassCreator",
                        "org.apache.cxf.wsdl.ExtensionClassLoader",
                        "org.apache.cxf.wsdl.ExtensionClassGenerator",
                        "io.quarkiverse.cxf.QuarkusJAXBBeanInfo",
                        "java.net.HttpURLConnection",
                        "com.sun.xml.bind.v2.schemagen.xmlschema.Schema",
                        "com.sun.xml.bind.v2.schemagen.xmlschema.package-info",
                        "com.sun.org.apache.xerces.internal.dom.DocumentTypeImpl",
                        "org.w3c.dom.DocumentType",
                        "java.lang.Throwable",
                        "java.nio.charset.Charset",
                        "com.sun.org.apache.xerces.internal.parsers" +
                                ".StandardParserConfiguration",
                        "com.sun.org.apache.xerces.internal.xni.parser" +
                                ".XMLInputSource",
                        "com.sun.org.apache.xml.internal.resolver.readers" +
                                ".XCatalogReader",
                        "com.sun.org.apache.xml.internal.resolver.readers" +
                                ".ExtendedXMLCatalogReader",
                        "com.sun.org.apache.xml.internal.resolver.Catalog",
                        "org.apache.xml.resolver.readers.OASISXMLCatalogReader",
                        "com.sun.org.apache.xml.internal.resolver.readers" +
                                ".XCatalogReader",
                        "com.sun.org.apache.xml.internal.resolver.readers" +
                                ".OASISXMLCatalogReader",
                        "com.sun.org.apache.xml.internal.resolver.readers" +
                                ".TR9401CatalogReader",
                        "com.sun.org.apache.xml.internal.resolver.readers" +
                                ".SAXCatalogReader",
                        //"com.sun.xml.txw2.TypedXmlWriter",
                        //"com.sun.codemodel.JAnnotationWriter",
                        //"com.sun.xml.txw2.ContainerElement",
                        "javax.xml.parsers.DocumentBuilderFactory",
                        "com.sun.org.apache.xerces.internal.jaxp" +
                                ".DocumentBuilderFactoryImpl",
                        "com.sun.org.apache.xml.internal.serializer.ToXMLStream",
                        "com.sun.org.apache.xerces.internal.dom.EntityImpl",
                        "org.apache.cxf.common.jaxb.JAXBUtils$S2JJAXBModel",
                        "org.apache.cxf.common.jaxb.JAXBUtils$Options",
                        "org.apache.cxf.common.jaxb.JAXBUtils$JCodeModel",
                        "org.apache.cxf.common.jaxb.JAXBUtils$Mapping",
                        "org.apache.cxf.common.jaxb.JAXBUtils$TypeAndAnnotation",
                        "org.apache.cxf.common.jaxb.JAXBUtils$JType",
                        "org.apache.cxf.common.jaxb.JAXBUtils$JPackage",
                        "org.apache.cxf.common.jaxb.JAXBUtils$JDefinedClass",
                        "com.sun.xml.bind.v2.model.nav.ReflectionNavigator",
                        "com.sun.xml.bind.v2.runtime.unmarshaller.StAXExConnector",
                        "com.sun.xml.bind.v2.runtime.unmarshaller" +
                                ".FastInfosetConnector",
                        "com.sun.xml.bind.v2.runtime.output" +
                                ".FastInfosetStreamWriterOutput",
                        "org.jvnet.staxex.XMLStreamWriterEx",
                        "com.sun.xml.bind.v2.runtime.output" +
                                ".StAXExStreamWriterOutput",
                        "org.jvnet.fastinfoset.stax" +
                                ".LowLevelFastInfosetStreamWriter",
                        "com.sun.xml.fastinfoset.stax.StAXDocumentSerializer",
                        "com.sun.xml.fastinfoset.stax.StAXDocumentParser",
                        "org.jvnet.fastinfoset.stax.FastInfosetStreamReader",
                        "org.jvnet.staxex.XMLStreamReaderEx",
                        // missing from jaxp extension
                        //GregorSamsa but which package ???
                        "com.sun.org.apache.xalan.internal.xsltc.dom" +
                                ".CollatorFactoryBase",
                        //objecttype in jaxp
                        "com.sun.org.apache.xerces.internal.impl.xs" +
                                ".XMLSchemaLoader",
                        "java.lang.Object",
                        "java.lang.String",
                        "java.math.BigInteger",
                        "java.math.BigDecimal",
                        "javax.xml.datatype.XMLGregorianCalendar",
                        "javax.xml.datatype.Duration",
                        "java.lang.Integer",
                        "java.lang.Long",
                        "java.lang.Short",
                        "java.lang.Float",
                        "java.lang.Double",
                        "java.lang.Boolean",
                        "java.lang.Byte",
                        "java.lang.StringBuffer",
                        "java.lang.Throwable",
                        "java.lang.Character",
                        "com.sun.xml.bind.api.CompositeStructure",
                        "java.net.URI",
                        "javax.xml.bind.JAXBElement",
                        "javax.xml.namespace.QName",
                        "java.awt.Image",
                        "java.io.File",
                        "java.lang.Class",
                        "java.lang.Void",
                        "java.net.URL",
                        "java.util.Calendar",
                        "java.util.Date",
                        "java.util.GregorianCalendar",
                        "java.util.UUID",
                        "javax.activation.DataHandler",
                        "javax.xml.transform.Source",
                        "com.sun.org.apache.xml.internal.serializer" +
                                ".ToXMLSAXHandler",
                        "com.sun.org.apache.xerces.internal.xni.parser" +
                                ".XMLParserConfiguration",
                        "com.sun.org.apache.xerces.internal.parsers" +
                                ".StandardParserConfiguration",
                        "com.sun.org.apache.xerces.internal.xni.parser" +
                                ".XMLInputSource",
                        "org.xml.sax.helpers.XMLReaderAdapter",
                        "org.xml.sax.helpers.XMLFilterImpl",
                        "javax.xml.validation.ValidatorHandler",
                        "org.xml.sax.ext.DefaultHandler2",
                        "org.xml.sax.helpers.DefaultHandler",
                        "com.sun.org.apache.xalan.internal.lib.Extensions",
                        "com.sun.org.apache.xalan.internal.lib.ExsltCommon",
                        "com.sun.org.apache.xalan.internal.lib.ExsltMath",
                        "com.sun.org.apache.xalan.internal.lib.ExsltSets",
                        "com.sun.org.apache.xalan.internal.lib.ExsltDatetime",
                        "com.sun.org.apache.xalan.internal.lib.ExsltStrings",
                        "com.sun.org.apache.xerces.internal.dom.DocumentImpl",
                        "com.sun.org.apache.xalan.internal.processor" +
                                ".TransformerFactoryImpl",
                        "com.sun.org.apache.xerces.internal.dom.CoreDocumentImpl",
                        "com.sun.org.apache.xerces.internal.dom.PSVIDocumentImpl",
                        "com.sun.org.apache.xpath.internal.domapi" +
                                ".XPathEvaluatorImpl",
                        "com.sun.org.apache.xerces.internal.impl.xs" +
                                ".XMLSchemaValidator",
                        "com.sun.org.apache.xerces.internal.impl.dtd" +
                                ".XMLDTDValidator",
                        "com.sun.org.apache.xml.internal.utils.FastStringBuffer",
                        "com.sun.xml.internal.stream.events.XMLEventFactoryImpl",
                        "com.sun.xml.internal.stream.XMLOutputFactoryImpl",
                        "com.sun.xml.internal.stream.XMLInputFactoryImpl",
                        "com.sun.org.apache.xerces.internal.jaxp.datatype" +
                                ".DatatypeFactoryImpl",
                        "javax.xml.stream.XMLStreamConstants",
                        "com.sun.org.apache.xalan.internal.xslt" +
                                ".XSLProcessorVersion",
                        "com.sun.org.apache.xalan.internal.processor" +
                                ".XSLProcessorVersion",
                        "com.sun.org.apache.xalan.internal.Version",
                        "com.sun.org.apache.xerces.internal.framework.Version",
                        "com.sun.org.apache.xerces.internal.impl.Version",
                        "org.apache.crimson.parser.Parser2",
                        "org.apache.tools.ant.Main",
                        "org.w3c.dom.Document",
                        "org.w3c.dom.Node",
                        "org.xml.sax.Parser",
                        "org.xml.sax.XMLReader",
                        "org.xml.sax.helpers.AttributesImpl",
                        "org.apache.cxf.common.logging.Slf4jLogger",
                        "io.quarkiverse.cxf.AddressTypeExtensibility",
                        "io.quarkiverse.cxf.CXFException",
                        "io.quarkiverse.cxf.HTTPClientPolicyExtensibility",
                        "io.quarkiverse.cxf.HTTPServerPolicyExtensibility",
                        "io.quarkiverse.cxf.XMLBindingMessageFormatExtensibility",
                        "io.quarkiverse.cxf.XMLFormatBindingExtensibility",
                        "org.apache.cxf.common.util.ReflectionInvokationHandler",
                        "com.sun.codemodel.internal.writer.FileCodeWriter",
                        "com.sun.codemodel.writer.FileCodeWriter",
                        "com.sun.xml.internal.bind.marshaller.NoEscapeHandler",
                        "com.sun.xml.internal.bind.marshaller" +
                                ".MinimumEscapeHandler",
                        "com.sun.xml.internal.bind.marshaller.DumbEscapeHandler",
                        "com.sun.xml.internal.bind.marshaller.NioEscapeHandler",
                        "com.sun.xml.bind.marshaller.NoEscapeHandler",
                        "com.sun.xml.bind.marshaller.MinimumEscapeHandler",
                        "com.sun.xml.bind.marshaller.DumbEscapeHandler",
                        "com.sun.xml.bind.marshaller.NioEscapeHandler",
                        "com.sun.tools.internal.xjc.api.XJC",
                        "com.sun.tools.xjc.api.XJC",
                        "com.sun.xml.internal.bind.api.JAXBRIContext",
                        "com.sun.xml.bind.api.JAXBRIContext",
                        "org.apache.cxf.common.util.ReflectionInvokationHandler",
                        "javax.xml.ws.wsaddressing.W3CEndpointReference",
                        "org.apache.cxf.common.jaxb.JAXBBeanInfo",
                        "javax.xml.bind.JAXBContext",
                        "com.sun.xml.bind.v2.runtime.LeafBeanInfoImpl",
                        "com.sun.xml.bind.v2.runtime.ArrayBeanInfoImpl",
                        "com.sun.xml.bind.v2.runtime.ValueListBeanInfoImpl",
                        "com.sun.xml.bind.v2.runtime.AnyTypeBeanInfo",
                        "com.sun.xml.bind.v2.runtime.JaxBeanInfo",
                        "com.sun.xml.bind.v2.runtime.ClassBeanInfoImpl",
                        "com.sun.xml.bind.v2.runtime.CompositeStructureBeanInfo",
                        "com.sun.xml.bind.v2.runtime.ElementBeanInfoImpl",
                        "com.sun.xml.bind.v2.runtime.MarshallerImpl",
                        "com.sun.xml.messaging.saaj.soap.SOAPDocumentImpl",
                        "com.sun.xml.internal.messaging.saaj.soap" +
                                ".SOAPDocumentImpl",
                        "com.sun.org.apache.xerces.internal.dom" +
                                ".DOMXSImplementationSourceImpl",
                        "javax.wsdl.Types",
                        "javax.wsdl.extensions.mime.MIMEPart",
                        "com.sun.xml.bind.v2.runtime.BridgeContextImpl",
                        "com.sun.xml.bind.v2.runtime.JAXBContextImpl",
                        "com.sun.xml.bind.subclassReplacements",
                        "com.sun.xml.bind.defaultNamespaceRemap",
                        "com.sun.xml.bind.c14n",
                        "com.sun.xml.bind.v2.model.annotation" +
                                ".RuntimeAnnotationReader",
                        "com.sun.xml.bind.XmlAccessorFactory",
                        "com.sun.xml.bind.treatEverythingNillable",
                        "com.sun.xml.bind.retainReferenceToInfo",
                        "com.sun.xml.internal.bind.subclassReplacements",
                        "com.sun.xml.internal.bind.defaultNamespaceRemap",
                        "com.sun.xml.internal.bind.c14n",
                        "org.apache.cxf.common.jaxb.SchemaCollectionContextProxy",
                        "com.sun.xml.internal.bind.v2.model.annotation" +
                                ".RuntimeAnnotationReader",
                        "com.sun.xml.internal.bind.XmlAccessorFactory",
                        "com.sun.xml.internal.bind.treatEverythingNillable",
                        "com.sun.xml.bind.marshaller.CharacterEscapeHandler",
                        "com.sun.xml.internal.bind.marshaller" +
                                ".CharacterEscapeHandler",
                        "com.sun.org.apache.xerces.internal.dom.ElementNSImpl",
                        "sun.security.ssl.SSLLogger",
                        "com.ibm.wsdl.extensions.schema.SchemaImpl",
                        //TODO add refection only if soap 1.2
                        "com.ibm.wsdl.extensions.soap12.SOAP12AddressImpl",
                        "com.ibm.wsdl.extensions.soap12.SOAP12AddressSerializer",
                        "com.ibm.wsdl.extensions.soap12.SOAP12BindingImpl",
                        "com.ibm.wsdl.extensions.soap12.SOAP12BindingSerializer",
                        "com.ibm.wsdl.extensions.soap12.SOAP12BodyImpl",
                        "com.ibm.wsdl.extensions.soap12.SOAP12BodySerializer",
                        "com.ibm.wsdl.extensions.soap12.SOAP12Constants",
                        "com.ibm.wsdl.extensions.soap12.SOAP12FaultImpl",
                        "com.ibm.wsdl.extensions.soap12.SOAP12FaultSerializer",
                        "com.ibm.wsdl.extensions.soap12.SOAP12HeaderFaultImpl",
                        "com.ibm.wsdl.extensions.soap12.SOAP12HeaderImpl",
                        "com.ibm.wsdl.extensions.soap12.SOAP12HeaderSerializer",
                        "com.ibm.wsdl.extensions.soap12.SOAP12OperationImpl",
                        "com.ibm.wsdl.extensions.soap12.SOAP12OperationSerializer",
                        "com.sun.xml.internal.bind.retainReferenceToInfo"));
        reflectiveItems.produce(new ReflectiveClassBuildItem(
                false,
                false,
                //manually added
                "org.apache.cxf.wsdl.interceptors.BareInInterceptor",
                "com.sun.msv.reader.GrammarReaderController",
                "org.apache.cxf.binding.soap.interceptor.RPCInInterceptor",
                "org.apache.cxf.wsdl.interceptors.DocLiteralInInterceptor",
                "StaxSchemaValidationInInterceptor",
                "org.apache.cxf.binding.soap.interceptor" +
                        ".SoapHeaderInterceptor",
                "org.apache.cxf.binding.soap.model.SoapHeaderInfo",
                "javax.xml.stream.XMLStreamReader",
                "java.util.List",
                "org.apache.cxf.service.model.BindingOperationInfo",
                "org.apache.cxf.binding.soap.interceptor" +
                        ".CheckFaultInterceptor",
                "org.apache.cxf.interceptor.ClientFaultConverter",
                "org.apache.cxf.binding.soap.interceptor" +
                        ".EndpointSelectionInterceptor",
                "java.io.InputStream",
                "org.apache.cxf.service.model.MessageInfo",
                "org.apache.cxf.binding.soap.interceptor" +
                        ".MustUnderstandInterceptor",
                "org.apache.cxf.interceptor.OneWayProcessorInterceptor",
                "java.io.OutputStream",
                "org.apache.cxf.binding.soap.interceptor" +
                        ".ReadHeadersInterceptor",
                "org.apache.cxf.binding.soap.interceptor" +
                        ".RPCOutInterceptor",
                "org.apache.cxf.binding.soap.interceptor" +
                        ".Soap11FaultInInterceptor",
                "org.apache.cxf.binding.soap.interceptor" +
                        ".Soap11FaultOutInterceptor",
                "org.apache.cxf.binding.soap.interceptor" +
                        ".Soap12FaultInInterceptor",
                "org.apache.cxf.binding.soap.interceptor" +
                        ".Soap12FaultOutInterceptor",
                "org.apache.cxf.binding.soap.interceptor" +
                        ".SoapActionInInterceptor",
                "org.apache.cxf.binding.soap.wsdl.extensions.SoapBody",
                "javax.wsdl.extensions.soap.SOAPBody",
                "org.apache.cxf.binding.soap.model.SoapOperationInfo",
                "org.apache.cxf.binding.soap.interceptor" +
                        ".SoapOutInterceptor$SoapOutEndingInterceptor",
                "org.apache.cxf.binding.soap.interceptor" +
                        ".SoapOutInterceptor",
                "org.apache.cxf.binding.soap.interceptor" +
                        ".StartBodyInterceptor",
                "java.lang.Exception",
                "org.apache.cxf.staxutils.W3CDOMStreamWriter",
                "javax.xml.stream.XMLStreamReader",
                "javax.xml.stream.XMLStreamWriter",
                "org.apache.cxf.common.jaxb.JAXBContextCache",
                "com.ctc.wstx.sax.WstxSAXParserFactory",
                "com.ibm.wsdl.BindingFaultImpl",
                "com.ibm.wsdl.BindingImpl",
                "com.ibm.wsdl.BindingInputImpl",
                "com.ibm.wsdl.BindingOperationImpl",
                "com.ibm.wsdl.BindingOutputImpl",
                "com.ibm.wsdl.extensions.soap.SOAPAddressImpl",
                "com.ibm.wsdl.extensions.soap.SOAPBindingImpl",
                "com.ibm.wsdl.extensions.soap.SOAPBodyImpl",
                "com.ibm.wsdl.extensions.soap.SOAPFaultImpl",
                "com.ibm.wsdl.extensions.soap.SOAPHeaderImpl",
                "com.ibm.wsdl.extensions.soap.SOAPOperationImpl",
                "com.ibm.wsdl.factory.WSDLFactoryImpl",
                "com.ibm.wsdl.FaultImpl",
                "com.ibm.wsdl.InputImpl",
                "com.ibm.wsdl.MessageImpl",
                "com.ibm.wsdl.OperationImpl",
                "com.ibm.wsdl.OutputImpl",
                "com.ibm.wsdl.PartImpl",
                "com.ibm.wsdl.PortImpl",
                "com.ibm.wsdl.PortTypeImpl",
                "com.ibm.wsdl.ServiceImpl",
                "com.ibm.wsdl.TypesImpl",
                "com.oracle.xmlns.webservices.jaxws_databinding" +
                        ".ObjectFactory",
                "com.sun.org.apache.xerces.internal.utils" +
                        ".XMLSecurityManager",
                "com.sun.org.apache.xerces.internal.utils" +
                        ".XMLSecurityPropertyManager",
                "com.sun.xml.bind.api.TypeReference",
                "com.sun.xml.bind.DatatypeConverterImpl",
                "com.sun.xml.internal.bind.api.TypeReference",
                "com.sun.xml.internal.bind.DatatypeConverterImpl",
                "com.sun.xml.ws.runtime.config.ObjectFactory",
                "ibm.wsdl.DefinitionImpl",
                "io.swagger.jaxrs.DefaultParameterExtension",
                "io.undertow.server.HttpServerExchange",
                "io.undertow.UndertowOptions",
                "java.lang.invoke.MethodHandles",
                "java.rmi.RemoteException",
                "java.rmi.ServerException",
                "java.security.acl.Group",
                "javax.enterprise.inject.spi.CDI",
                "javax.jws.Oneway",
                "javax.jws.WebMethod",
                "javax.jws.WebParam",
                "javax.jws.WebResult",
                "javax.jws.WebService",
                "javax.security.auth.login.Configuration",
                "javax.servlet.WriteListener",
                "javax.wsdl.Binding",
                "javax.wsdl.Binding",
                "javax.wsdl.BindingFault",
                "javax.wsdl.BindingFault",
                "javax.wsdl.BindingInput",
                "javax.wsdl.BindingOperation",
                "javax.wsdl.BindingOperation",
                "javax.wsdl.BindingOutput",
                "javax.wsdl.Definition",
                "javax.wsdl.Fault",
                "javax.wsdl.Import",
                "javax.wsdl.Input",
                "javax.wsdl.Message",
                "javax.wsdl.Operation",
                "javax.wsdl.Output",
                "javax.wsdl.Part",
                "javax.wsdl.Port",
                "javax.wsdl.Port",
                "javax.wsdl.PortType",
                "javax.wsdl.Service",
                "javax.wsdl.Types",
                "javax.xml.bind.annotation.XmlSeeAlso",
                "javax.xml.soap.SOAPMessage",
                "javax.xml.transform.stax.StAXSource",
                "javax.xml.ws.Action",
                "javax.xml.ws.BindingType",
                "javax.xml.ws.Provider",
                "javax.xml.ws.RespectBinding",
                "javax.xml.ws.Service",
                "javax.xml.ws.ServiceMode",
                "javax.xml.ws.soap.Addressing",
                "javax.xml.ws.soap.MTOM",
                "javax.xml.ws.soap.SOAPBinding",
                "javax.xml.ws.WebFault",
                "javax.xml.ws.WebServiceProvider",
                "net.sf.cglib.proxy.Enhancer",
                "net.sf.cglib.proxy.MethodInterceptor",
                "net.sf.cglib.proxy.MethodProxy",
                "net.sf.ehcache.CacheManager",
                "org.apache.commons.logging.LogFactory",
                "org.apache.cxf.binding.soap.SoapBinding",
                "org.apache.cxf.binding.soap.SoapFault",
                "org.apache.cxf.binding.soap.SoapHeader",
                "org.apache.cxf.binding.soap.SoapMessage",
                "org.apache.cxf.binding.xml.XMLFault",
                "org.apache.cxf.bindings.xformat.ObjectFactory",
                "org.apache.cxf.bindings.xformat.XMLBindingMessageFormat",
                "org.apache.cxf.bindings.xformat.XMLFormatBinding",
                "org.apache.cxf.bus.CXFBusFactory",
                "org.apache.cxf.bus.managers.BindingFactoryManagerImpl",
                "org.apache.cxf.interceptor.Fault",
                "org.apache.cxf.jaxb.DatatypeFactory",
                "org.apache.cxf.jaxb.JAXBDataBinding",
                "org.apache.cxf.jaxrs.utils.JAXRSUtils",
                "org.apache.cxf.jaxws.binding.soap.SOAPBindingImpl",
                "org.apache.cxf.metrics.codahale.CodahaleMetricsProvider",
                "org.apache.cxf.message.Exchange",
                "org.apache.cxf.message.ExchangeImpl",
                "org.apache.cxf.message.StringMapImpl",
                "org.apache.cxf.message.StringMap",
                "org.apache.cxf.tools.fortest.cxf523.Database",
                "org.apache.cxf.tools.fortest.cxf523.DBServiceFault",
                "org.apache.cxf.tools.fortest.withannotation.doc" +
                        ".HelloWrapped",
                "org.apache.cxf.transports.http.configuration" +
                        ".HTTPClientPolicy",
                "org.apache.cxf.transports.http.configuration" +
                        ".HTTPServerPolicy",
                "org.apache.cxf.transports.http.configuration" +
                        ".ObjectFactory",
                "org.apache.cxf.ws.addressing.wsdl.AttributedQNameType",
                "org.apache.cxf.ws.addressing.wsdl.ObjectFactory",
                "org.apache.cxf.ws.addressing.wsdl.ServiceNameType",
                "org.apache.cxf.wsdl.http.AddressType",
                "org.apache.cxf.wsdl.http.ObjectFactory",
                "org.apache.hello_world.Greeter",
                "org.apache.hello_world_soap_http.types.StringStruct",
                "org.apache.karaf.jaas.boot.principal.Group",
                "org.apache.xerces.impl.Version",
                "org.apache.yoko.orb.OB.BootManager",
                "org.apache.yoko.orb.OB.BootManagerHelper",
                "org.codehaus.stax2.XMLStreamReader2",
                "org.eclipse.jetty.jaas.spi.PropertyFileLoginModule",
                "org.eclipse.jetty.jmx.MBeanContainer",
                "org.eclipse.jetty.plus.jaas.spi.PropertyFileLoginModule",
                "org.hsqldb.jdbcDriver",
                "org.jdom.Document",
                "org.jdom.Element",
                "org.osgi.framework.Bundle",
                "org.osgi.framework.BundleContext",
                "org.osgi.framework.FrameworkUtil",
                "org.slf4j.impl.StaticLoggerBinder",
                "org.slf4j.LoggerFactory",
                "org.springframework.aop.framework.Advised",
                "org.springframework.aop.support.AopUtils",
                "org.springframework.core.io.support" +
                        ".PathMatchingResourcePatternResolver",
                "org.springframework.core.type.classreading" +
                        ".CachingMetadataReaderFactory",
                "org.springframework.osgi.io" +
                        ".OsgiBundleResourcePatternResolver",
                "org.springframework.osgi.util.BundleDelegatingClassLoader"));
    }

    @BuildStep
    NativeImageResourceBuildItem nativeImageResourceBuildItem() {
        //TODO add @HandlerChain (file) and parse it to add class loading
        return new NativeImageResourceBuildItem(
                "com/sun/xml/fastinfoset/resources/ResourceBundle.properties",
                "META-INF/cxf/bus-extensions.txt",
                "META-INF/cxf/cxf.xml",
                "META-INF/cxf/org.apache.cxf.bus.factory",
                "META-INF/services/org.apache.cxf.bus.factory",
                "META-INF/blueprint.handlers",
                "META-INF/spring.handlers",
                "META-INF/spring.schemas",
                "META-INF/jax-ws-catalog.xml",
                "OSGI-INF/metatype/workqueue.xml",
                "schemas/core.xsd",
                "schemas/blueprint/core.xsd",
                "schemas/wsdl/XMLSchema.xsd",
                "schemas/wsdl/addressing.xjb",
                "schemas/wsdl/addressing.xsd",
                "schemas/wsdl/addressing200403.xjb",
                "schemas/wsdl/addressing200403.xsd",
                "schemas/wsdl/http.xjb",
                "schemas/wsdl/http.xsd",
                "schemas/wsdl/mime-binding.xsd",
                "schemas/wsdl/soap-binding.xsd",
                "schemas/wsdl/soap-encoding.xsd",
                "schemas/wsdl/soap12-binding.xsd",
                "schemas/wsdl/swaref.xsd",
                "schemas/wsdl/ws-addr-wsdl.xjb",
                "schemas/wsdl/ws-addr-wsdl.xsd",
                "schemas/wsdl/ws-addr.xsd",
                "schemas/wsdl/wsdl.xjb",
                "schemas/wsdl/wsdl.xsd",
                "schemas/wsdl/wsrm.xsd",
                "schemas/wsdl/xmime.xsd",
                "schemas/wsdl/xml.xsd",
                "schemas/configuratio/cxf-beans.xsd",
                "schemas/configuration/extension.xsd",
                "schemas/configuration/parameterized-types.xsd",
                "schemas/configuration/security.xjb",
                "schemas/configuration/security.xsd");
    }

    //
    // Private helper methods and functions.
    //
    private String getMappingPath(String path) {
        String mappingPath;
        if (path.endsWith("/")) {
            mappingPath = path + "*";
        } else {
            mappingPath = path + "/*";
        }
        return mappingPath;
    }

    static public String getNamespaceFromPackage(String pkg) {
        //TODO XRootElement then XmlSchema then derived of package
        String[] strs = pkg.split("\\.");
        StringBuilder b = new StringBuilder("http://");
        for (int i = strs.length - 1; i >= 0; i--) {
            if (i != strs.length - 1) {
                b.append(".");
            }
            b.append(strs[i]);
        }
        b.append("/");
        return b.toString();
    }

    private void produceProxyIfExist(
            BuildProducer<NativeImageProxyDefinitionBuildItem> proxies,
            String s) {
        try {
            Class.forName(s);
            proxies.produce(new NativeImageProxyDefinitionBuildItem(s));
        } catch (ClassNotFoundException e) {
            //silent fail
        }
    }

    private void produceRecursiveProxies(
            IndexView index,
            DotName interfaceDN,
            BuildProducer<NativeImageProxyDefinitionBuildItem> proxies,
            Set<String> proxiesCreated) {
        index.getKnownDirectImplementors(interfaceDN).stream()
                .filter(classinfo -> Modifier.isInterface(classinfo.flags()))
                .map(ClassInfo::name)
                .forEach((className) -> {
                    if (!proxiesCreated.contains(className.toString())) {
                        proxies.produce(new NativeImageProxyDefinitionBuildItem(className.toString()));
                        produceRecursiveProxies(index, className, proxies, proxiesCreated);
                        proxiesCreated.add(className.toString());
                    }
                });

    }

    private AnnotationInstance findWebServiceClientAnnotation(
            IndexView index,
            DotName seiName) {
        Collection<AnnotationInstance> annotations = index.getAnnotations(WEBSERVICE_CLIENT);
        for (AnnotationInstance annotation : annotations) {
            ClassInfo targetClass = annotation.target().asClass();

            for (MethodInfo method : targetClass.methods()) {
                if (method.returnType().name().equals(seiName)) {
                    return annotation;
                }
            }
        }

        return null;
    }

    /**
     * Create Producer bean managing webservice client
     * <p>
     * The generated class will look like
     *
     * <pre>
     * public class FruitWebserviceCxfClientProducer extends AbstractCxfClientProducer {
     * &#64;ApplicationScoped
     * &#64;Produces
     * &#64;Default
     * public FruitWebService createService() {
     * return (FruitWebService) loadCxfClient ();
     * }
     */
    private void generateCxfClientProducer(
            CxfUeberBuildItem cxf,
            String sei) {
        String cxfClientProducerClassName = sei + "CxfClientProducer";
        ClassOutput classOutput = new GeneratedBeanRecorder(cxf);

        try (ClassCreator classCreator = ClassCreator.builder()
                .classOutput(classOutput)
                .className(cxfClientProducerClassName)
                .superClass(CxfClientProducer.class)
                .build()) {
            FieldCreator fieldCreator;

            classCreator.addAnnotation(ApplicationScoped.class);
            fieldCreator = classCreator.getFieldCreator("info", "io.quarkiverse.cxf.CXFClientInfo")
                    .setModifiers(Modifier.PUBLIC);

            fieldCreator.addAnnotation(AnnotationInstance.create(DotNames.NAMED, null, new AnnotationValue[] {
                    AnnotationValue.createStringValue("value", sei)
            }));

            fieldCreator.addAnnotation(
                    AnnotationInstance
                            .create(DotName.createSimple(Inject.class.getName()), null, new AnnotationValue[] {}));
            try (MethodCreator getInfoMethodCreator = classCreator.getMethodCreator(
                    "getInfo",
                    "io.quarkiverse.cxf.CXFClientInfo")) {
                getInfoMethodCreator.setModifiers(Modifier.PUBLIC);
                getInfoMethodCreator.returnValue(getInfoMethodCreator.readInstanceField(
                        fieldCreator.getFieldDescriptor(),
                        getInfoMethodCreator.getThis()));
            }
            try (MethodCreator cxfClientMethodCreator = classCreator.getMethodCreator("createService", sei)) {
                cxfClientMethodCreator.addAnnotation(ApplicationScoped.class);
                cxfClientMethodCreator.addAnnotation(Produces.class);
                cxfClientMethodCreator.addAnnotation(Default.class);

                // New configuration
                ResultHandle cxfClient = cxfClientMethodCreator.invokeVirtualMethod(
                        MethodDescriptor.ofMethod(
                                CxfClientProducer.class,
                                "loadCxfClient",
                                Object.class),
                        cxfClientMethodCreator.getThis());
                ResultHandle cxfClientCasted = cxfClientMethodCreator.checkCast(cxfClient, sei);
                cxfClientMethodCreator.returnValue(cxfClientCasted);
            }
        }
    }

    @SafeVarargs
    static private <T> Set<T> asSet(T... items) {
        return Arrays.stream(items).collect(Collectors.toSet());
    }

    @SafeVarargs
    static private <T extends BuildItem> void produce(
            BuildProducer<T> p,
            T... beans) {
        Arrays.stream(beans).forEach(p::produce);
    }

    /**
     * Return all known classes implementing given class.
     */
    private static Collection<ClassInfo> implementorsOf(
            IndexView index,
            String clazz) {
        return index.getAllKnownImplementors(DotName.createSimple(clazz));
    }

}
