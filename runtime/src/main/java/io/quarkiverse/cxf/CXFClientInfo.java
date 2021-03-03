package io.quarkiverse.cxf;

import java.util.ArrayList;
import java.util.List;

import javax.enterprise.context.ApplicationScoped;

import org.jboss.logging.Logger;
import org.wildfly.common.annotation.Nullable;

import io.quarkus.arc.Unremovable;

@ApplicationScoped
@Unremovable
public class CXFClientInfo {
    private String sei;
    private String endpointAddress;
    private String wsdlUrl;
    private String soapBinding;
    private String wsNamespace;
    private String wsName;
    private String epNamespace;
    private String epName;
    private String username;
    private String password;
    private List<String> inInterceptors;
    private List<String> outInterceptors;
    private List<String> outFaultInterceptors;
    private List<String> inFaultInterceptors;
    private List<String> features;
    private List<String> classNames;
    private static final Logger LOGGER = Logger.getLogger(CXFClientInfo.class);

    public CXFClientInfo() {
    }

    public CXFClientInfo(
            String sei,
            String endpointAddress,
            String soapBinding,
            String wsNamespace,
            String wsName,
            List<String> classNames,
            @Nullable CxfEndpointConfig cxfEndPointConfig) {
        this.sei = sei;
        this.endpointAddress = endpointAddress;
        this.wsdlUrl = null;
        this.soapBinding = soapBinding;
        this.wsNamespace = wsNamespace;
        this.wsName = wsName;
        this.epNamespace = null;
        this.epName = null;
        this.classNames = classNames;
        this.username = null;
        this.password = null;
        this.inInterceptors = new ArrayList<>();
        this.outInterceptors = new ArrayList<>();
        this.outFaultInterceptors = new ArrayList<>();
        this.inFaultInterceptors = new ArrayList<>();
        this.features = new ArrayList<>();

        if (cxfEndPointConfig != null) {
            this.wsdlUrl = cxfEndPointConfig.wsdlPath.orElse(null);
            this.epNamespace = cxfEndPointConfig.endpointNamespace.orElse(null);
            this.epName = cxfEndPointConfig.endpointName.orElse(null);
            this.username = cxfEndPointConfig.username.orElse(null);
            this.password = cxfEndPointConfig.password.orElse(null);
        }

        addFeatures(cxfEndPointConfig);
        addInterceptors(cxfEndPointConfig);
    }

    public void init(
            String sei,
            String endpointAddress,
            String wsdlUrl,
            String soapBinding,
            String wsNamespace,
            String wsName,
            String epNamespace,
            String epName,
            String username,
            String password,
            List<String> classNames) {
        LOGGER.trace("new CXFClientInfo");
        this.sei = sei;
        this.endpointAddress = endpointAddress;
        this.wsdlUrl = wsdlUrl;
        this.soapBinding = soapBinding;
        this.wsNamespace = wsNamespace;
        this.wsName = wsName;
        this.epNamespace = epNamespace;
        this.epName = epName;
        this.classNames = classNames;
        this.username = username;
        this.password = password;
        this.inInterceptors = new ArrayList<>();
        this.outInterceptors = new ArrayList<>();
        this.outFaultInterceptors = new ArrayList<>();
        this.inFaultInterceptors = new ArrayList<>();
        this.features = new ArrayList<>();
    }

    public String getSei() {
        return sei;
    }

    public void setSei(String sei) {
        this.sei = sei;
    }

    public String getEndpointAddress() {
        return endpointAddress;
    }

    public String getWsdlUrl() {
        return wsdlUrl;
    }

    public String getSoapBinding() {
        return soapBinding;
    }

    public String getWsNamespace() {
        return wsNamespace;
    }

    public String getWsName() {
        return wsName;
    }

    public String getEpNamespace() {
        return epNamespace;
    }

    public String getEpName() {
        return epName;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public List<String> getClassNames() {
        return classNames;
    }

    public List<String> getFeatures() {
        return features;
    }

    public List<String> getInInterceptors() {
        return inInterceptors;
    }

    public List<String> getOutInterceptors() {
        return outInterceptors;
    }

    public List<String> getOutFaultInterceptors() {
        return outFaultInterceptors;
    }

    public List<String> getInFaultInterceptors() {
        return inFaultInterceptors;
    }

    public CXFClientInfo addInterceptors(@Nullable CxfEndpointConfig cxfEndPointConfig) {
        if (cxfEndPointConfig == null) {
            return this;
        }
        if (cxfEndPointConfig.inInterceptors.isPresent()) {
            this.inInterceptors.addAll(cxfEndPointConfig.inInterceptors.get());
        }
        if (cxfEndPointConfig.outInterceptors.isPresent()) {
            this.outInterceptors.addAll(cxfEndPointConfig.outInterceptors.get());
        }
        if (cxfEndPointConfig.outFaultInterceptors.isPresent()) {
            this.outFaultInterceptors.addAll(cxfEndPointConfig.outFaultInterceptors.get());
        }
        if (cxfEndPointConfig.inFaultInterceptors.isPresent()) {
            this.inFaultInterceptors.addAll(cxfEndPointConfig.inFaultInterceptors.get());
        }
        return this;
    }

    public CXFClientInfo addFeatures(@Nullable CxfEndpointConfig cxfEndPointConfig) {
        if (cxfEndPointConfig == null) {
            return this;
        }
        if (cxfEndPointConfig.features.isPresent()) {
            this.features.addAll(cxfEndPointConfig.features.get());
        }
        return this;
    }
}
