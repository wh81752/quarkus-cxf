package io.quarkiverse.cxf;

import static java.lang.String.format;
import static java.util.Optional.ofNullable;

import java.util.*;

import org.jboss.logging.Logger;
import org.wildfly.common.annotation.Nullable;

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
    public @Nullable String serviceName;

    public boolean hasImpl() {
        return this.impl != null && !this.impl.equals("");
    }

    public String relativePath() {
        Objects.requireNonNull(this.sei);
        String serviceName = this.sei.toLowerCase();
        if (serviceName.contains(".")) {
            serviceName = serviceName.substring(serviceName.lastIndexOf('.') + 1);
        }
        return format("/%s", serviceName);
    }

    /**
     * Transform this WebService data into a collection of deployable "Servlets".
     */
    public List<CXFServletInfo> toServlet(CxfConfig cxfConfig) {
        final List<CXFServletInfo> services = new ArrayList<>();

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
            // Is there a configured EP matching this service by .implementor?
            //
            String cfgImplementor = cxfEndPointConfig.implementor.orElse(null);
            if (cfgImplementor != null && cfgImplementor.equals(this.impl)) {
                matching.add(kv);
                continue;
            }
            //
            // How about the config key? Does he match this service implementor?
            //
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
            // as default.
            // changed: To avoid collisions, we start using the implementor's name instead of SEI name.
            //
            // If "serviceName" is present, the go ahead with that name otherwise use Implementor's name.
            String servicepath = ofNullable(this.serviceName)
                    .orElseGet(() -> this.impl.trim().replace('.', '/'));
            services.add(new CXFServletInfo(this, null, format("/%s", servicepath)));
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

                        // The servicepath is given by the current configitems's key.

                        if (wsid.startsWith("/")) {
                            return new CXFServletInfo(this, cfg, wsid);
                        }
                        String servicepath;
                        servicepath = Optional.ofNullable(this.serviceName).orElse(wsid);
                        if (!servicepath.startsWith("/")) {
                            servicepath = format("/%s", servicepath);
                        }
                        return new CXFServletInfo(this, cfg, servicepath);
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
