package io.quarkiverse.cxf.deployment;

import static java.util.Collections.*;
import static java.util.Optional.ofNullable;

import java.util.*;

import org.jboss.jandex.AnnotationInstance;
import org.wildfly.common.annotation.Nullable;

import io.quarkiverse.cxf.CXFServiceData;
import io.quarkus.builder.item.MultiBuildItem;

/**
 * CxfWebServiceBuildItem is instanciate for each SEI and each implementor it mean that if an interface have 2
 * implementors it generate 3 items (1 for client and 2 for implementors)
 */
public final class CxfWebServiceBuildItem extends MultiBuildItem {
    volatile public AnnotationInstance ws;
    private final List<String> classNames = new ArrayList<>();
    private final String implementor;
    private final String path;
    private final String sei;
    private final String soapBinding;
    private final String wsName;
    private final String wsNamespace;
    private final boolean isClient;

    public CxfWebServiceBuildItem(
            String path,
            String sei,
            String soapBinding,
            String wsNamespace,
            String wsName,
            @Nullable List<String> classNames,
            @Nullable String implementor) {
        Objects.requireNonNull(path);
        Objects.requireNonNull(sei);
        Objects.requireNonNull(soapBinding);
        Objects.requireNonNull(wsNamespace);
        Objects.requireNonNull(wsName);
        this.classNames.addAll(Optional.of(classNames).orElse(EMPTY_LIST));
        this.implementor = ofNullable(implementor).orElse("");
        this.isClient = ofNullable(implementor).map(it -> false).orElse(true);
        this.path = path;
        this.sei = sei;
        this.soapBinding = soapBinding;
        this.wsName = wsName;
        this.wsNamespace = wsNamespace;
    }

    public CxfWebServiceBuildItem(
            String path,
            String sei,
            String soapBinding,
            String wsNamespace,
            String wsName,
            List<String> classNames) {
        this(path, sei, soapBinding, wsNamespace, wsName, classNames, null);
    }

    public String getPath() {
        return path;
    }

    public String getSei() {
        return sei;
    }

    public String getSoapBinding() {
        return soapBinding;
    }

    public String getWsName() {
        return wsName;
    }

    public List<String> getClassNames() {
        return classNames;
    }

    public String getWsNamespace() {
        return wsNamespace;
    }

    public String getImplementor() {
        return implementor;
    }

    public boolean IsClient() {
        return isClient;
    }

    public boolean IsService() {
        return !this.IsClient();
    }

    public boolean hasImplementor() {
        return this.implementor != null && !this.implementor.isEmpty() && !(this.implementor.trim().isEmpty());
    }

    /**
     * Convert all data into a runtime-value data structure that can be given to a Recorder instance for example.
     *
     * @return
     */
    public CXFServiceData asRuntimeData() {
        CXFServiceData cxf = new CXFServiceData();
        cxf.binding = this.getSoapBinding();
        cxf.clnames.addAll(this.getClassNames());
        cxf.impl = this.getImplementor();
        cxf.path = this.getPath();
        cxf.sei = this.getSei();
        cxf.wsName = this.getWsName();
        cxf.wsNamespace = this.getWsNamespace();
        return cxf;

    }

    public static CxfWebServiceBuildItemBuilder builder(AnnotationInstance ws) {
        return new CxfWebServiceBuildItemBuilder(ws);
    }

    public static CxfWebServiceBuildItemBuilder builder(CxfWebServiceBuildItem ws) {
        return new CxfWebServiceBuildItemBuilder(ws);
    }

    public static CxfWebServiceBuildItemBuilder builder(CxfWebServiceBuildItemBuilder ws) {
        return new CxfWebServiceBuildItemBuilder(ws.build());
    }

}
