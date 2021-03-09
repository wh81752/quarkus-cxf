package io.quarkiverse.cxf;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jboss.logging.Logger;

/**
 * Class Documentation
 *
 * <p>
 * What is the point of this class?
 * <p>
 * TODO: add builder class make this class imutable
 *
 * @author geronimo1
 */
public class CXFServiceData {
    private static final Logger LOGGER = Logger.getLogger(CXFServiceData.class);
    final public List<String> clnames = new ArrayList<>();
    public String binding;
    public String impl;
    public String sei;
    public String wsName;
    public String wsNamespace;

    public boolean hasImpl() {
        return this.impl != null && !this.impl.equals("");
    }

    public String relativePath() {
        Objects.requireNonNull(this.sei);
        String serviceName = this.sei.toLowerCase();
        if (serviceName.contains(".")) {
            serviceName = serviceName.substring(serviceName.lastIndexOf('.') + 1);
        }
        return String.format("/%s", serviceName);
    }

    /**
     * Transform this WebService data into a collection of deployable "Servlets".
     */
    public List<CXFServletInfo> toServlet(CxfConfig cxfConfig) {
        final List<CXFServletInfo> services = new ArrayList<>();
        //
        // Log how we are called.
        //
        if (LOGGER.isDebugEnabled()) {
            String fmt = "sei(%s), soapBinding(%s), impl(%s), classes(%s)";
            String msg = String.format(fmt, this.sei, this.binding, this.impl, this.clnames);
            LOGGER.debug(msg);
        }

        List<Map.Entry<String, CxfEndpointConfig>> matching = new ArrayList<>();
        //
        // Iter over all configured endpoints
        //
        for (Map.Entry<String, CxfEndpointConfig> kv : cxfConfig.endpoints.entrySet()) {
            String wsid;
            CxfEndpointConfig cxfEndPointConfig;

            // The key identifies first and foremost a webservice by an arbitrary identifier.
            wsid = kv.getKey();
            // This is how the (virtual) webservice identified by wsid is defined.
            cxfEndPointConfig = kv.getValue();

            //
            //
            // quarkus.cxf.endpoint."{wsid}".implementor=
            // quarkus.cxf.endpoint."{wsid}".path=
            //
            // (A)
            // If .implementor is present while .path not, then {wsid} is assumed to be
            // the relpath => .path is constructed out of {wsid}.
            //
            // (B)
            // If .implementor and .path is present then {wsid} is nothing else than a
            // grouping id and not used for any further calculations.
            //
            // (C)
            // If .implementor is absent -- regardless of .path -- then {wsid} is considered
            // to be the webservice implementation class. If the path is absent, then
            // the implementation class is used as .path.
            //
            // Examples:
            // (A)
            // quarkus.cxf.endpoint."hw".implementor=io.app.ws.HelloWorldImpl
            // quarkus.cxf.endpoint."hw".path=null
            //
            // @WebService() class HelloWorldImpl {} => .path("/hw")
            // @WebService(serviceName=myhw) class HelloWorldImpl {} => .path("/myhw")
            //
            // (B)
            // quarkus.cxf.endpoint."hw".implementor=io.app.ws.HelloWorldImpl
            // quarkus.cxf.endpoint."hw".path=/foobar
            //
            // configuration wins in any case:
            // @WebService() class HelloWorldImpl {} => .path("/foobar")
            // @WebService(serviceName=myhw) class HelloWorldImpl {} => .path("/foobar")
            //
            // (C1)
            // quarkus.cxf.endpoint."hw".implementor=null
            // => error: hw not a implementor class
            //
            // (C2)
            // quarkus.cxf.endpoint."io.app.ws.HelloWorldImpl".implementor=null
            // quarkus.cxf.endpoint."io.app.ws.HelloWorldImpl".path=/foobar
            // @WebService(serviceName=myhw) class HelloWorldImpl {} => .path("/foobar")
            //
            // (C3)
            // quarkus.cxf.endpoint."io.app.ws.HelloWorldImpl".implementor=null
            // quarkus.cxf.endpoint."io.app.ws.HelloWorldImpl".path=null
            //
            // @WebService(serviceName=myhw) class HelloWorldImpl {} => .path("/myhw")
            // @WebService() class HelloWorldImpl {} => .path("/io/app/ws/HelloWorldImpl")

            //
            // Is there a configured EP matching this service by .implementor?
            //
            String cfgImplementor = cxfEndPointConfig.implementor.orElse(null);
            if (cfgImplementor != null && cfgImplementor.equals(this.impl)) {
                matching.add(kv);
                continue;
            }

            // How about the config key? Does he match this service implementor?
            if (wsid.equals(this.impl)) {
                matching.add(kv);
                continue;
            }

            // No other way how a service is identified by a config item, thus we
            // give up on this config and turn our focus on the next one..
        }

        // If this service is unconfigured translate into deployable servlet, otherwise create
        // servlet out of each matching configuration.
        if (matching.isEmpty()) {
            // Depending on (additional) config values various mount strategies could be used
            // as default. To avoid collisions, we start using the implementor's name.
            services.add(new CXFServletInfo(this, null, String.format("/%s", this.impl.trim().replace('.', '/'))));
        } else {
            matching.stream()
                    .map(kv -> {
                        CXFServletInfo servlet;

                        String wsid;
                        CxfEndpointConfig cfg;

                        wsid = kv.getKey();
                        cfg = kv.getValue();

                        // Configuration naturally changes some deployment properties of given
                        // service. Apply config on this webservice in order to get a config free
                        // deployable servlet item.

                        // Precondition: The current config value (kv) matches this service.

                        // If config key starts with "/" then key is used as relative service path.
                        if (wsid.startsWith("/")) {
                            return new CXFServletInfo(this, cfg, wsid);
                        }
                        // otherwise use key as path
                        return new CXFServletInfo(this, cfg, String.format("/%s", wsid));
                    })
                    .forEach(services::add);
        }
        return services;
    }

    @Override
    public String toString() {
        return "CXFServiceData{" +
                "clnames=" + clnames +
                ", binding='" + binding + '\'' +
                ", impl='" + impl + '\'' +
                ", sei='" + sei + '\'' +
                ", wsName='" + wsName + '\'' +
                ", wsNamespace='" + wsNamespace + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        CXFServiceData that = (CXFServiceData) o;
        return Objects.equals(clnames, that.clnames) && Objects.equals(
                binding,
                that.binding) && Objects.equals(impl, that.impl) && Objects.equals(sei, that.sei)
                && Objects
                        .equals(wsName, that.wsName)
                && Objects.equals(wsNamespace, that.wsNamespace);
    }

    @Override
    public int hashCode() {
        return Objects.hash(clnames, binding, impl, sei, wsName, wsNamespace);
    }
}
