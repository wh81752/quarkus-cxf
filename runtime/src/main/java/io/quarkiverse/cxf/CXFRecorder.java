package io.quarkiverse.cxf;

import java.util.*;
import java.util.function.Supplier;

import org.jboss.logging.Logger;

import io.quarkiverse.cxf.transport.CxfHandler;
import io.quarkus.arc.runtime.BeanContainer;
import io.quarkus.runtime.annotations.Recorder;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

/**
 * CXFRecorder contains actions to be executed at runtime.
 * <p>
 * This actions recorded at deployment time and there are build-steps producing necessary byte-code to do so.
 */
@Recorder
public class CXFRecorder {
    private static final Logger LOGGER = Logger.getLogger(CXFRecorder.class);

    /**
     * Create CXFClientInfo supplier.
     * <p>
     * This method is called once per @WebService *interface*.
     */
    public Supplier<CXFClientInfo> cxfClientInfoSupplier(CxfConfig cxfConfig, CXFServiceData wsdata) {
        return this.cxfClientInfoSupplier(
                cxfConfig,
                wsdata.sei,
                wsdata.binding,
                wsdata.wsNamespace,
                wsdata.wsName,
                wsdata.clnames);
    }

    public Supplier<CXFClientInfo> cxfClientInfoSupplier(
            CxfConfig cxfConfig,
            String sei,
            String soapBinding,
            String wsNamespace,
            String wsName,
            List<String> classNames) {
        if (LOGGER.isDebugEnabled()) {
            String fmt = "recorder CXFClientInfoSupplier: sei(%s), soapBinding(%s), wsNs(%s), ws(%s), classes(%s)";
            String msg = String.format(fmt, sei, soapBinding, wsNamespace, wsName, classNames);
            LOGGER.debug(msg);
        }
        return () -> {
            final String DEFAULT_EP_ADDR = "http://localhost:8080";

            // TODO if 2 clients with same SEI but different config it will failed but strange use case
            Map<String, CxfEndpointConfig> seiToCfg = new HashMap<>();
            Map<String, String> seiToPath = new HashMap<>();

            // iterate over CXF configuration, especially known endpoints.

            for (Map.Entry<String, CxfEndpointConfig> kv : cxfConfig.endpoints.entrySet()) {
                CxfEndpointConfig v = kv.getValue();
                String k = kv.getKey();

                // what are we doing here?
                LOGGER.debug(String.format("processing: k=(%s) v=(%s)", k, v));

                // ignore if no service interface
                if (!v.serviceInterface.isPresent()) {
                    LOGGER.debug(String.format("skipping k=(%s) cause there is no service interface", k));
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

    static public class servletConfig {
        public CxfEndpointConfig config;
        public String path;

        public servletConfig(
                CxfEndpointConfig cxfEndPointConfig,
                String relativePath) {
            this.config = cxfEndPointConfig;
            this.path = relativePath;
        }
    }

    //    public void registerCXFServlet(
    //            RuntimeValue<CXFServletInfos> runtimeInfos,
    //            CxfConfig cxfConfig,
    //            CXFServiceData data
    //    ) {
    //        this.registerCXFServlet(runtimeInfos.getValue(), cxfConfig, data);
    //    }

    private void registerCXFServlet(
            CXFServletInfos infos,
            CxfConfig cxfConfig,
            CXFServiceData data) {
        //
        // Log how we are called.
        //
        if (LOGGER.isDebugEnabled()) {
            String fmt = "registerCXFServlet: sei(%s), soapBinding(%s), path(%s), impl(%s), classes(%s)";
            String msg = String.format(fmt, data.sei, data.binding, data.path, data.impl, data.clnames);
            LOGGER.debug(msg);
        }

        // path --> servlet
        Map<String, List<servletConfig>> implementorToCfg = new HashMap<>();

        // iter over all configured endpoints
        for (Map.Entry<String, CxfEndpointConfig> webServicesByPath : cxfConfig.endpoints.entrySet()) {
            CxfEndpointConfig cxfEndPointConfig;
            String relativePath;

            cxfEndPointConfig = webServicesByPath.getValue();
            relativePath = webServicesByPath.getKey();

            String cfgImplementor = cxfEndPointConfig.implementor.orElse(null);
            // this config has no implementor configured ..
            if (cfgImplementor == null) {
                continue;
            }

            List<servletConfig> lst;

            // if implementor is already configured as servlet ..
            if (implementorToCfg.containsKey(cfgImplementor)) {
                lst = implementorToCfg.get(cfgImplementor);
            } else {
                lst = new ArrayList<>();
                implementorToCfg.put(cfgImplementor, lst);
            }

            // something is wrong here ..
            lst.add(new servletConfig(cxfEndPointConfig, relativePath));
        }
        List<servletConfig> cfgs = implementorToCfg.get(data.impl);
        if (cfgs == null) {
            cfgs = new ArrayList<>();
            cfgs.add(new servletConfig(null, data.relativePath()));
        }

        for (servletConfig cfg : cfgs) {
            infos.startRoute(data, cfg);
        }
    }

    public Handler<RoutingContext> registerServlet(
            String path,
            CxfConfig cxfConfig,
            BeanContainer beanContainer,
            Collection<CXFServiceData> wslist) {
        CXFServletInfos infos = new CXFServletInfos(path);
        wslist.forEach(it -> {
            this.registerCXFServlet(infos, cxfConfig, it);
        });
        return new CxfHandler(infos, beanContainer);
    }

}
