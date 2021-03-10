package io.quarkiverse.cxf.deployment;

import static java.util.Collections.unmodifiableList;
import static java.util.Optional.ofNullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.wildfly.common.annotation.Nullable;

import io.quarkiverse.cxf.CXFServiceData;
import io.quarkus.builder.item.MultiBuildItem;

/**
 * CxfWebServiceBuildItem is instanciate for each SEI and each implementor it mean that if an interface have 2
 * implementors it generate 3 items (1 for client and 2 for implementors)
 */

public final class CxfWebServiceBuildItem extends MultiBuildItem {
    static private final List<String> EMPTY = unmodifiableList(new ArrayList<>());
    private final List<String> classNames = new ArrayList<>();
    private final Implementor implementor;
    private final SEI sei;
    private final String soapBinding;
    private final String wsName;
    private final String wsNamespace;
    private final boolean isClient;

    public CxfWebServiceBuildItem(
            SEI sei,
            String soapBinding,
            String wsNamespace,
            String wsName,
            @Nullable List<String> classNames,
            @Nullable Implementor implementor) {
        Objects.requireNonNull(sei);
        Objects.requireNonNull(soapBinding);
        Objects.requireNonNull(wsNamespace);
        Objects.requireNonNull(wsName);
        this.classNames.addAll(Optional.of(classNames).orElse(EMPTY));
        this.implementor = ofNullable(implementor).orElse(null);
        this.isClient = (implementor == null);
        this.sei = sei;
        this.soapBinding = soapBinding;
        this.wsName = wsName;
        this.wsNamespace = wsNamespace;
    }

    public SEI getSei() {
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

    public Implementor getImplementor() {
        return implementor;
    }

    public boolean IsClient() {
        return isClient;
    }

    public boolean IsService() {
        return !this.IsClient();
    }

    public boolean hasImplementor() {
        return this.implementor != null;
    }

    /**
     * Convert all data into a runtime-value data structure that can be given to a Recorder instance for example.
     */
    public CXFServiceData asRuntimeData() {
        CXFServiceData cxf = new CXFServiceData();
        cxf.binding = this.getSoapBinding();
        cxf.clnames.addAll(this.getClassNames());
        cxf.impl = ofNullable(this.getImplementor()).map(impl -> impl.classInfo.name().toString()).orElse(null);
        cxf.sei = this.getSei().classInfo.name().toString();
        cxf.wsName = this.getWsName();
        cxf.wsNamespace = this.getWsNamespace();
        return cxf;

    }

    public static CxfWebServiceBuildItemBuilder builder(SEI ws) {
        return new CxfWebServiceBuildItemBuilder(ws);
    }

    //    public static CxfWebServiceBuildItemBuilder builder(CxfWebServiceBuildItem ws) {
    //        return new CxfWebServiceBuildItemBuilder(ws);
    //    }
    //
    //    public static CxfWebServiceBuildItemBuilder builder(CxfWebServiceBuildItemBuilder ws) {
    //        return new CxfWebServiceBuildItemBuilder(ws.build());
    //    }

}
