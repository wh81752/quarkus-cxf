package io.quarkiverse.cxf;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.jboss.logging.Logger;

import io.quarkiverse.cxf.transport.CxfHandler;
import io.quarkus.arc.runtime.BeanContainer;
import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

@Recorder
public class CXFRecorder {
    private static final Logger LOGGER = Logger.getLogger(CXFRecorder.class);

    /**
     * Create CXFClientInfo supplier.
     *
     * @param sei
     * @param cxfConfig
     * @param soapBinding
     * @param wsNamespace
     * @param wsName
     * @param classNames
     * @return
     */
    public Supplier<CXFClientInfo> cxfClientInfoSupplier(
            String sei,
            CxfConfig cxfConfig,
            String soapBinding,
            String wsNamespace,
            String wsName,
            List<String> classNames) {
        LOGGER.trace("recorder CXFClientInfoSupplier");
        return () -> {
            final String DEFAULT_EP_ADDR = "http://localhost:8080";

            // TODO if 2 clients with same SEI but different config it will failed but strange use case
            Map<String, CxfEndpointConfig> seiToCfg = new HashMap<>();
            Map<String, String> seiToPath = new HashMap<>();

            for (Map.Entry<String, CxfEndpointConfig> kv : cxfConfig.endpoints.entrySet()) {
                CxfEndpointConfig v = kv.getValue();
                String k = kv.getKey();

                // ignore if no service interface
                if (!v.serviceInterface.isPresent()) {
                    continue;
                }

                String cfgSei = v.serviceInterface.get();

                seiToCfg.put(cfgSei, v);
                seiToPath.put(cfgSei, k);
            }

            CxfEndpointConfig cxfEndPointConfig = seiToCfg.get(sei);
            String relativePath = seiToPath.get(sei);

            String endpointAddress = DEFAULT_EP_ADDR;

            if (cxfEndPointConfig != null) {
                endpointAddress = cxfEndPointConfig.clientEndpointUrl.orElse(DEFAULT_EP_ADDR);
            }
            // default is sei name without package
            if (relativePath == null) {
                String serviceName = sei.toLowerCase();
                if (serviceName.contains(".")) {
                    serviceName = serviceName.substring(serviceName.lastIndexOf('.') + 1);
                }
                relativePath = "/" + serviceName;
            }
            if (!relativePath.equals("/") && !relativePath.equals("")) {
                endpointAddress = endpointAddress.endsWith("/")
                        ? endpointAddress.substring(0, endpointAddress.length() - 1)
                        : endpointAddress;
                endpointAddress = relativePath.startsWith("/") ? endpointAddress + relativePath
                        : endpointAddress + "/" + relativePath;
            }

            return new CXFClientInfo(
                    sei,
                    endpointAddress,
                    soapBinding,
                    wsNamespace,
                    wsName,
                    classNames,
                    cxfEndPointConfig);
        };
    }

    public void registerCXFServlet(
            RuntimeValue<CXFServletInfos> runtimeInfos,
            String path,
            String sei,
            CxfConfig cxfConfig,
            String soapBinding,
            List<String> wrapperClassNames,
            String wsImplementor) {
        CXFServletInfos infos = runtimeInfos.getValue();
        Map<String, CxfEndpointConfig> implementorToCfg = new HashMap<>();
        Map<String, String> implementorToPath = new HashMap<>();
        for (Map.Entry<String, CxfEndpointConfig> webServicesByPath : cxfConfig.endpoints.entrySet()) {
            CxfEndpointConfig cxfEndPointConfig = webServicesByPath.getValue();
            String relativePath = webServicesByPath.getKey();
            if (!cxfEndPointConfig.implementor.isPresent()) {
                continue;
            }
            String cfgImplementor = cxfEndPointConfig.implementor.get();
            implementorToCfg.put(cfgImplementor, cxfEndPointConfig);
            implementorToPath.put(cfgImplementor, relativePath);
        }
        CxfEndpointConfig cxfEndPointConfig = implementorToCfg.get(wsImplementor);
        String relativePath = implementorToPath.get(wsImplementor);
        if (relativePath == null) {
            String serviceName = sei.toLowerCase();
            if (serviceName.contains(".")) {
                serviceName = serviceName.substring(serviceName.lastIndexOf('.') + 1);
            }
            relativePath = "/" + serviceName;
        }
        if (wsImplementor != null && !wsImplementor.equals("")) {
            CXFServletInfo cfg = new CXFServletInfo(
                    path,
                    relativePath,
                    wsImplementor,
                    sei,
                    cxfEndPointConfig != null ? cxfEndPointConfig.wsdlPath.orElse(null) : null,
                    soapBinding,
                    wrapperClassNames,
                    cxfEndPointConfig != null ? cxfEndPointConfig.publishedEndpointUrl.orElse(null) : null);
            if (cxfEndPointConfig != null && cxfEndPointConfig.inInterceptors.isPresent()) {
                cfg.getInInterceptors().addAll(cxfEndPointConfig.inInterceptors.get());
            }
            if (cxfEndPointConfig != null && cxfEndPointConfig.outInterceptors.isPresent()) {
                cfg.getOutInterceptors().addAll(cxfEndPointConfig.outInterceptors.get());
            }
            if (cxfEndPointConfig != null && cxfEndPointConfig.outFaultInterceptors.isPresent()) {
                cfg.getOutFaultInterceptors().addAll(cxfEndPointConfig.outFaultInterceptors.get());
            }
            if (cxfEndPointConfig != null && cxfEndPointConfig.inFaultInterceptors.isPresent()) {
                cfg.getInFaultInterceptors().addAll(cxfEndPointConfig.inFaultInterceptors.get());
            }
            if (cxfEndPointConfig != null && cxfEndPointConfig.features.isPresent()) {
                cfg.getFeatures().addAll(cxfEndPointConfig.features.get());
            }
            LOGGER.trace("register CXF Servlet info");
            infos.add(cfg);
        }
    }

    public RuntimeValue<CXFServletInfos> createInfos() {
        CXFServletInfos infos = new CXFServletInfos();
        return new RuntimeValue<>(infos);
    }

    public Handler<RoutingContext> initServer(
            RuntimeValue<CXFServletInfos> infos,
            BeanContainer beanContainer) {
        LOGGER.trace("init server");
        return new CxfHandler(infos.getValue(), beanContainer);
    }

    public void setPath(
            RuntimeValue<CXFServletInfos> infos,
            String path) {
        infos.getValue().setPath(path);
    }
}
