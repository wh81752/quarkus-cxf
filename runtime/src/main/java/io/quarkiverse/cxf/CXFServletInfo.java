package io.quarkiverse.cxf;

import java.util.ArrayList;
import java.util.List;

import org.jboss.logging.Logger;
import org.wildfly.common.annotation.Nullable;

public class CXFServletInfo {
    private final String relativePath;
    private final String path;
    private final String className;
    private final List<String> inInterceptors = new ArrayList<>();
    private final List<String> outInterceptors = new ArrayList<>();
    private final List<String> outFaultInterceptors = new ArrayList<>();
    private final List<String> inFaultInterceptors = new ArrayList<>();
    private final List<String> features = new ArrayList<>();
    private final String sei;
    private final String wsdlPath;
    private final String soapBinding;
    private final List<String> wrapperClassNames;
    private final String endpointUrl;

    private static final Logger LOGGER = Logger.getLogger(CXFServletInfo.class);

    public CXFServletInfo(
            CXFServiceData data,
            CxfEndpointConfig cfg,
            String relativePath) {
        super();
        this.path = data.path;
        this.relativePath = relativePath;
        this.className = data.impl;
        this.sei = data.sei;
        this.wsdlPath = cfg != null ? cfg.wsdlPath.orElse(null) : null;
        this.soapBinding = data.binding;
        this.wrapperClassNames = data.clnames;
        this.endpointUrl = cfg != null ? cfg.publishedEndpointUrl.orElse(null) : null;
        this.with(cfg);
    }

    public CXFServletInfo(
            String path,
            String relativePath,
            String className,
            String sei,
            String wsdlPath,
            String soapBinding,
            List<String> wrapperClassNames,
            String endpointUrl) {
        super();
        this.path = path;
        this.relativePath = relativePath;
        this.className = className;
        this.sei = sei;
        this.wsdlPath = wsdlPath;
        this.soapBinding = soapBinding;
        this.wrapperClassNames = wrapperClassNames;
        this.endpointUrl = endpointUrl;
    }

    public String getClassName() {
        return className;
    }

    public String getPath() {
        return path;
    }

    public String getRelativePath() {
        return relativePath;
    }

    public String getWsdlPath() {
        return wsdlPath;
    }

    public String getSei() {
        return sei;
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

    public String getSOAPBinding() {
        return soapBinding;
    }

    public List<String> getWrapperClassNames() {
        return wrapperClassNames;
    }

    public String getEndpointUrl() {
        return endpointUrl;
    }

    @Override
    public String toString() {
        return "Web Service " + className + " on " + path;
    }

    public CXFServletInfo with(@Nullable CxfEndpointConfig cfg) {
        if (cfg != null) {
            if (cfg.inInterceptors.isPresent()) {
                this.inInterceptors.addAll(cfg.inInterceptors.get());
            }
            if (cfg.outInterceptors.isPresent()) {
                this.outInterceptors.addAll(cfg.outInterceptors.get());
            }
            if (cfg.outFaultInterceptors.isPresent()) {
                this.outFaultInterceptors.addAll(cfg.outFaultInterceptors.get());
            }
            if (cfg.inFaultInterceptors.isPresent()) {
                this.inFaultInterceptors.addAll(cfg.inFaultInterceptors.get());
            }
            if (cfg.features.isPresent()) {
                this.features.addAll(cfg.features.get());
            }
        }
        return this;
    }
}
