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
    private final @Nullable Implementor implementor;
    private final SEI sei;
    private final String soapBinding;
    private final String wsName;
    private final String wsNamespace;
    private final @Nullable String serviceName;

    /**
     * Although public better to use CxfWebServiceBuildItemBuilder for building.
     */
    public CxfWebServiceBuildItem(
            SEI sei,
            String soapBinding,
            String wsNamespace,
            String wsName,
            @Nullable String serviceName,
            @Nullable List<String> classNames,
            @Nullable Implementor implementor) {
        Objects.requireNonNull(sei);
        Objects.requireNonNull(soapBinding);
        Objects.requireNonNull(wsNamespace);
        Objects.requireNonNull(wsName);
        this.classNames.addAll(Optional.of(classNames).orElse(EMPTY));
        this.implementor = implementor;
        this.sei = sei;
        this.soapBinding = soapBinding;
        this.wsName = wsName;
        this.wsNamespace = wsNamespace;
        this.serviceName = serviceName;
        // PostConditions:
        if (this.hasImplementor()) {
            Objects.requireNonNull(this.serviceName);
        }
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

    public @Nullable Implementor getImplementor() {
        return implementor;
    }

    public @Nullable String getServiceName() {
        return this.serviceName;
    }

    public boolean IsClient() {
        return (implementor == null);
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
        cxf.serviceName = this.getServiceName();
        return cxf;

    }

    /**
     * Create a WebService item out of a class previously identified as SEI.
     */
    public static CxfWebServiceBuildItemBuilder builder(SEI sei) {
        return new CxfWebServiceBuildItemBuilder(sei);
    }

    /**
     * Use this function to get builder representing the Implementor of an SEI. This is
     * just a convenience function for
     * 
     * <pre>
     * builder(sei).withImplementor(impl)
     * </pre>
     */
    public static CxfWebServiceBuildItemBuilder implbuilder(Implementor impl, SEI sei) {
        return builder(sei).withImplementor(impl);
    }

}
