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
    public Implementor implementor = null;
    public SEI sei;
    public String soapBinding;
    public String wsName;
    public String wsNamespace;
    public boolean isClient = true;

    /**
     * Derive a starter build item from a class annotated with @WebService.
     */
    public CxfWebServiceBuildItemBuilder(SEI sei) {
        Objects.requireNonNull(sei);
        this.sei = sei;
        // Until we know better assume SOAP 1.1
        // TODO: lookup SOAP binding
        this.soapBinding = ofNullable(soapBindingFrom(sei.classInfo)).orElse(SOAPBinding.SOAP11HTTP_BINDING);
        // Derive SEI's name from annotated class. You can override later once you
        // know better who the SEI really is.
        // Derive service name and namespace from given annotation class. Override
        // if you know better.
        this.wsName = ofNullable(wsName(sei.classInfo)).orElse("");
        this.wsNamespace = ofNullable(wsNamespace(sei.classInfo)).orElseGet(() -> {
            return deriveNamespaceFromPackage(sei.classInfo.name().toString());
        });
    }

    /**
     * Create a new builder based on an already existing webservice item, then use withXX() methods to change properties
     * according to your needs.
     */
    public CxfWebServiceBuildItemBuilder(CxfWebServiceBuildItem ws) {
        this.implementor = ws.getImplementor();
        this.sei = ws.getSei();
        this.soapBinding = ws.getSoapBinding();
        this.wsName = ws.getWsName();
        this.wsNamespace = ws.getWsNamespace();
        this.withClassNames(ws.getClassNames());
    }

    //
    // The main work horse - how to build a inmutable CxfWebServiceBuildItem instance.
    //
    public CxfWebServiceBuildItem build() {
        return new CxfWebServiceBuildItem(
                this.sei,
                this.soapBinding,
                this.wsNamespace,
                this.wsName,
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
     * Using withImplementor() changes the underlying semantics. The builder
     * represents now the given implementor and no longer the SEI. Using this
     * method more than once doesn't make sense.
     */
    public CxfWebServiceBuildItemBuilder withImplementor(Implementor impl) {
        Objects.requireNonNull(impl);
        this.implementor = impl;
        this.isClient = false;
        this.soapBinding = ofNullable(soapBindingFrom(impl.classInfo)).orElse(this.soapBinding);
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
                ", isClient=" + isClient +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        CxfWebServiceBuildItemBuilder that = (CxfWebServiceBuildItemBuilder) o;
        return isClient == that.isClient && Objects.equals(
                classNames,
                that.classNames) && Objects.equals(implementor, that.implementor) && Objects.equals(
                        sei,
                        that.sei)
                && Objects.equals(soapBinding, that.soapBinding) && Objects.equals(
                        wsName,
                        that.wsName)
                && Objects.equals(wsNamespace, that.wsNamespace);
    }

    @Override
    public int hashCode() {
        return Objects.hash(classNames, implementor, sei, soapBinding, wsName, wsNamespace, isClient);
    }
}
