package io.quarkiverse.cxf;

import static java.util.stream.Collectors.toList;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;

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
    public Supplier<CXFClientInfo> cxfClientInfoSupplier(
            CxfConfig cxfConfig,
            CXFServiceData wsdata) {
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

    public Handler<RoutingContext> registerServlet(
            String path,
            CxfConfig cxfConfig,
            BeanContainer beanContainer,
            Collection<CXFServiceData> wslist) {
        CxfHandler handler;
        List<String> wrapperClasses;
        List<CXFServletInfo> list = new ArrayList<>();

        handler = new CxfHandler(path, beanContainer);
        wrapperClasses = getWrappersclasses(wslist);

        //
        // wslist contains collected build-time data about webservices around.
        //

        wslist.stream()
                .map(it -> it.toServlet(cxfConfig))
                .flatMap(Collection::stream)
                .forEach(list::add);

        // All fine so far but servlets may clash by sharing the very same publish
        // path. This is considered a show stoppper.

        List<String> collisions;
        Collector<CXFServletInfo, ?, Map<String, Long>> groupingBy;

        groupingBy = Collectors.groupingBy(
                CXFServletInfo::getRelativePath,
                Collectors.counting());
        collisions = list.stream()
                .collect(groupingBy)
                .entrySet()
                .stream()
                .filter(p -> p.getValue() > 1)
                .map(Map.Entry::getKey)
                .collect(toList());

        if (!collisions.isEmpty()) {
            throw new IllegalStateException("collisions detected for services: " + String.join(",", collisions));
        }

        // All fine by now, going ahead.
        list.forEach(servlet -> {
            handler.register(servlet, wrapperClasses);
        });
        return handler;
    }

    private static List<String> getWrappersclasses(Collection<CXFServiceData> wslist) {
        return wslist.stream()
                .map(data -> data.clnames)
                .flatMap(List::stream)
                .collect(toList());
    }

}
