package io.quarkiverse.cxf.deployment;

import static io.quarkiverse.cxf.deployment.QuarkusCxfProcessor.BINDING_TYPE_ANNOTATION;
import static io.quarkiverse.cxf.deployment.QuarkusCxfProcessor.WEBSERVICE_ANNOTATION;
import static java.util.Collections.unmodifiableList;
import static java.util.Optional.ofNullable;

import java.util.*;

import javax.xml.ws.soap.SOAPBinding;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.wildfly.common.annotation.Nullable;

/**
 * A builder-pattern class for class CxfWebServiceBuildItem.
 *
 * <p>
 * What is the point of this class?
 *
 * @author geronimo1
 */
public class CxfWebServiceBuildItemBuilder {
    static private final List<String> EMPTY = unmodifiableList(new ArrayList<>());
    public List<String> classNames = new ArrayList<>();
    public @Nullable Implementor implementor = null;
    public SEI sei;
    public String soapBinding;
    // @WebService(name="..")
    // Specifies the name of the service interface. This property is mapped to the name attribute of the
    // wsdl:portType element that defines the service's interface in a WSDL contract. The default is to
    // append PortType to the name of the implementation class.
    public @Nullable String wsName;
    // @WebService(targetNamespace="..")
    // Specifies the target namespace under which the service is defined. If this property is not specified,
    // the target namespace is derived from the package name.
    public @Nullable String wsNamespace;
    // @WebService(wsdlLocation="..")
    // Specifies the target namespace under which the service is defined. If this property is not specified,
    // the target namespace is derived from the package name.
    public @Nullable String wsdlLocation;
    // @WebService(serviceName="..")
    // Specifies the name of the published service. This property is mapped to the name attribute of the
    // wsdl:service element that defines the published service. The default is to use the name of the service's
    // implementation class. Note: Not allowed on the SEI
    public @Nullable String serviceName;
    // @WebService(endpointInterface="..")
    // Specifies the full name of the SEI that the implementation class implements. This property is only used
    // when the attribute is used on a service implementation class. Note: Not allowed on the SEI
    public @Nullable String endpointInterface;
    // @WebService(portName="..")
    // Specifies the name of the endpoint at which the service is published. This property is mapped to the
    // name attribute of the wsdl:port element that specifies the endpoint details for a published service.
    // The default is the append Port to the name of the service's implementation class. Note: Not allowed
    // on the SEI
    public @Nullable String portName;

    /**
     * Derive a starter build item from a class annotated with @WebService.
     */
    public CxfWebServiceBuildItemBuilder(SEI sei) {
        Objects.requireNonNull(sei);
        this.sei = sei;
        this.soapBinding = ofNullable(soapBindingFrom(sei.classInfo)).orElse(SOAPBinding.SOAP11HTTP_BINDING);
        this.wsName = wsName(sei.classInfo);
        this.wsNamespace = wsNamespace(sei.classInfo);
    }

    //
    // The main work horse - how to build a inmutable CxfWebServiceBuildItem instance.
    //
    public CxfWebServiceBuildItem build() {
        return new CxfWebServiceBuildItem(
                this.sei,
                this.soapBinding,
                ofNullable(this.wsNamespace).orElseGet(() -> {
                    return deriveNamespaceFromPackage(sei.classInfo.name().toString());
                }),
                ofNullable(this.wsName).orElse(""),
                this.serviceName,
                this.classNames,
                this.implementor);
    }

    //
    // Smart and fluent with**() methods
    //

    private CxfWebServiceBuildItemBuilder withClassNames(@Nullable Collection<String> coll) {
        this.classNames.addAll(ofNullable(coll).orElse(EMPTY));
        return this;
    }

    private CxfWebServiceBuildItemBuilder withBinding(@Nullable AnnotationInstance ai) {
        if (ai != null) {
            this.soapBinding = ai.value().asString();
        }
        return this;
    }

    static private @Nullable String soapBindingFrom(ClassInfo ci) {
        AnnotationInstance ai = ci.classAnnotation(BINDING_TYPE_ANNOTATION);
        return Optional.ofNullable(ai).map(it -> it.value().asString()).orElse(null);
    }

    private CxfWebServiceBuildItemBuilder withConfig(CxfBuildTimeConfig config) {
        Objects.requireNonNull(config);
        return this;
    }

    /**
     * Using withImplementor() changes the underlying semantics. The builder represents now the given implementor and no
     * longer the SEI. Using this method more than once doesn't make sense.
     */
    public CxfWebServiceBuildItemBuilder withImplementor(Implementor impl) {
        Objects.requireNonNull(impl);
        this.implementor = impl;
        this.soapBinding = ofNullable(soapBindingFrom(impl.classInfo)).orElse(this.soapBinding);
        this.serviceName = impl.serviceName();
        this.wsName = this.implementor.classInfo.name().toString();
        if (this.wsName.contains(".")) {
            this.wsName = wsName.substring(wsName.lastIndexOf('.') + 1);
        }
        return this;
    }

    private CxfWebServiceBuildItemBuilder withWsName(AnnotationValue av) {
        this.wsName = av.asString();
        return this;
    }

    private CxfWebServiceBuildItemBuilder withWsNamespace(AnnotationValue av) {
        this.wsNamespace = av.asString();
        return this;
    }

    public CxfWebServiceBuildItemBuilder withClientAnnotation(AnnotationInstance ai) {
        this.withWsName(ai.value("name"));
        this.withWsNamespace(ai.value("targetNamespace"));
        return this;
    }

    //
    // Query methods, applicable at any time.
    //
    public boolean hasImpl() {
        return this.implementor != null;
    }

    //
    // Private helper methods.
    //

    private static String deriveNamespaceFromPackage(String pkg) {
        //TODO XRootElement then XmlSchema then derived of package
        String[] strs = pkg.split("\\.");
        StringBuilder b = new StringBuilder("http://");
        for (int i = strs.length - 1; i >= 0; i--) {
            if (i != strs.length - 1) {
                b.append(".");
            }
            b.append(strs[i]);
        }
        b.append("/");
        return b.toString();
    }

    private static String packageOf(ClassInfo clazz) {
        String pkg = clazz.name().toString();
        int idx = pkg.lastIndexOf('.');
        if (idx != -1 && idx < pkg.length() - 1) {
            pkg = pkg.substring(0, idx);
        }
        return pkg;
    }

    private static @Nullable String val(
            AnnotationInstance ai,
            String name) {
        return ai.value(name) != null ? ai.value(name).asString() : null;
    }

    private static @Nullable String wsName(ClassInfo cl) {
        return val(cl.classAnnotation(WEBSERVICE_ANNOTATION), "serviceName");
    }

    private static @Nullable String wsNamespace(ClassInfo cl) {
        return val(cl.classAnnotation(WEBSERVICE_ANNOTATION), "targetNamespace");
    }

    @Override
    public String toString() {
        return "CxfWebServiceBuildItemBuilder{" +
                ", classNames=" + classNames +
                ", implementor='" + implementor + '\'' +
                ", sei='" + sei + '\'' +
                ", soapBinding='" + soapBinding + '\'' +
                ", wsName='" + wsName + '\'' +
                ", wsNamespace='" + wsNamespace + '\'' +
                ", isClient=" + hasImpl() +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        CxfWebServiceBuildItemBuilder that = (CxfWebServiceBuildItemBuilder) o;
        return Objects.equals(
                classNames,
                that.classNames) && Objects.equals(implementor, that.implementor)
                && Objects.equals(
                        sei,
                        that.sei)
                && Objects.equals(soapBinding, that.soapBinding) && Objects.equals(
                        wsName,
                        that.wsName)
                && Objects.equals(wsNamespace, that.wsNamespace);
    }

    @Override
    public int hashCode() {
        return Objects.hash(classNames, implementor, sei, soapBinding, wsName, wsNamespace);
    }
}
