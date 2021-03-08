package io.quarkiverse.cxf.deployment;

import static io.quarkiverse.cxf.deployment.QuarkusCxfProcessor.BINDING_TYPE_ANNOTATION;
import static java.util.Collections.unmodifiableList;
import static java.util.Optional.ofNullable;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

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
    public AnnotationInstance ws;
    public List<String> classNames = new ArrayList<>();
    public String implementor = null;
    public String path;
    public String sei;
    public String soapBinding;
    public String wsName;
    public String wsNamespace;
    public boolean isClient = true;

    private CxfWebServiceBuildItemBuilder() {
    }

    /**
     * Derive a starter build item from a class annotated with @WebService.
     */
    public CxfWebServiceBuildItemBuilder(AnnotationInstance ws) {
        Objects.requireNonNull(ws);
        this.ws = ws;
        // Until we know better assume SOAP 1.1
        this.soapBinding = SOAPBinding.SOAP11HTTP_BINDING;
        // Derive SEI's name from annotated class. You can override later once you
        // know better who the SEI really is.
        this.sei = this.wsClass().name().toString();
        // Use SEI as publish adress for now.
        this.path = this.sei.toLowerCase();
        // Derive service name and namespace from given annotation class. Override
        // if you know better.
        this.wsName = this.wsName();
        this.wsNamespace = this.wsNamespace();
    }

    /**
     * Create a new builder based on an already existing webservice item, then use withXX() methods to change properties
     * according to your needs.
     */
    public CxfWebServiceBuildItemBuilder(CxfWebServiceBuildItem ws) {
        this.ws = ws.getWs();
        this.withImpl(ws.getImplementor()).withClassNames(ws.getClassNames());
        this.path = ws.getPath();
        this.sei = ws.getSei();
        this.soapBinding = ws.getSoapBinding();
        this.wsName = ws.getWsName();
        this.wsNamespace = ws.getWsNamespace();
    }

    /**
     * Create a new builder based on the current status of another builder.
     */
    @SuppressWarnings("CopyConstructorMissesField")
    public CxfWebServiceBuildItemBuilder(CxfWebServiceBuildItemBuilder ws) {
        this(ws.build());
    }

    //
    // The main work horse - how to build a inmutable CxfWebServiceBuildItem instance.
    //
    public CxfWebServiceBuildItem build() {
        return new CxfWebServiceBuildItem(
                this.ws,
                this.path,
                this.sei,
                this.soapBinding,
                this.wsNamespace,
                this.wsName,
                this.classNames,
                this.hasImpl() ? this.implementor : null);
    }

    //
    // Smart and fluent with**() methods
    //
    public CxfWebServiceBuildItemBuilder withImpl(@Nullable String s) {
        this.implementor = s;
        this.isClient = !this.hasImpl();
        return this;
    }

    public CxfWebServiceBuildItemBuilder withClassNames(@Nullable Collection<String> coll) {
        this.classNames.addAll(ofNullable(coll).orElse(EMPTY));
        return this;
    }

    public CxfWebServiceBuildItemBuilder withBinding(@Nullable AnnotationInstance ai) {
        if (ai != null) {
            this.soapBinding = ai.value().asString();
        }
        return this;
    }

    public CxfWebServiceBuildItemBuilder withBinding(ClassInfo ci) {
        return withBinding(ci.classAnnotation(BINDING_TYPE_ANNOTATION));
    }

    public CxfWebServiceBuildItemBuilder withConfig(CxfBuildTimeConfig config) {
        Objects.requireNonNull(config);
        this.path = ofNullable(config.path).orElse("/");
        return this;
    }

    public CxfWebServiceBuildItemBuilder withImplementor(ClassInfo wsClass) {
        this.withImpl(wsClass.name().toString());
        this.wsName = this.implementor;

        if (this.implementor.contains(".")) {
            this.wsName = this.implementor.substring(this.implementor.lastIndexOf('.') + 1);
        }
        return this;
    }

    public CxfWebServiceBuildItemBuilder withWsName(AnnotationValue av) {
        this.wsName = av.asString();
        return this;
    }

    public CxfWebServiceBuildItemBuilder withWsNamespace(AnnotationValue av) {
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
        return this.implementor != null && !this.implementor.trim().isEmpty();
    }

    public ClassInfo wsClass() {
        return this.ws.target().asClass();
    }

    public String wsPackage() {
        return packageOf(this.wsClass());
    }

    public String wsName() {
        return ofNullable(wsName(this.ws)).orElse("");
    }

    public String wsNamespace() {
        return ofNullable(wsNamespace(this.ws)).orElseGet(() -> getNamespaceFromPackage(
                this.wsPackage()));
    }

    public boolean isInterface() {
        return Modifier.isInterface(this.ws.target().asClass().flags());
    }

    //
    // Private helper methods.
    //

    private static String getNamespaceFromPackage(String pkg) {
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

    private static @Nullable String wsName(AnnotationInstance ai) {
        return val(ai, "serviceName");
    }

    private static @Nullable String wsNamespace(AnnotationInstance ai) {
        return val(ai, "targetNamespace");
    }
}
