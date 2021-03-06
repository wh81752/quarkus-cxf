package io.quarkiverse.cxf.deployment;

import java.util.List;

import io.quarkiverse.cxf.CXFServiceData;
import io.quarkus.builder.item.MultiBuildItem;

/**
 * CxfWebServiceBuildItem is instanciate for each SEI and each implementor
 * it mean that if an interface have 2 implementors
 * it generate 3 items (1 for client and 2 for implementors)
 */
public final class CxfWebServiceBuildItem extends MultiBuildItem {
    private final String sei;
    private final String soapBinding;
    private final String wsNamespace;
    private final String wsName;
    private final List<String> classNames;
    private final String implementor;
    private final String path;
    private final boolean isClient;

    public CxfWebServiceBuildItem(String path, String sei, String soapBinding, String wsNamespace,
            String wsName, List<String> classNames, String implementor) {
        this.path = path;
        this.sei = sei;
        this.soapBinding = soapBinding;
        this.wsNamespace = wsNamespace;
        this.wsName = wsName;
        this.classNames = classNames;
        this.implementor = implementor;
        this.isClient = false;
    }

    public CxfWebServiceBuildItem(String path, String sei, String soapBinding, String wsNamespace,
            String wsName, List<String> classNames) {
        this.path = path;
        this.sei = sei;
        this.soapBinding = soapBinding;
        this.wsNamespace = wsNamespace;
        this.wsName = wsName;
        this.classNames = classNames;
        this.isClient = true;
        this.implementor = "";
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
        return this.implementor != null && !this.implementor.isEmpty();
    }

    /**
     * Convert all data into a runtime-value data structure that can
     * be given to a Recorder instance for example.
     * 
     * @return
     */
    public CXFServiceData asRuntimeData() {
        CXFServiceData cxf = new CXFServiceData();
        cxf.path = this.getPath();
        cxf.sei = this.getSei();
        cxf.binding = this.getSoapBinding();
        cxf.clnames.addAll(this.getClassNames());
        cxf.wsName = this.getWsName();
        cxf.wsNamespace = this.getWsNamespace();
        return cxf;

    }

}
