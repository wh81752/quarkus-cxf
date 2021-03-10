package io.quarkiverse.cxf.deployment;

import static io.quarkiverse.cxf.deployment.QuarkusCxfProcessor.BINDING_TYPE_ANNOTATION;
import static io.quarkiverse.cxf.deployment.QuarkusCxfProcessor.WEBSERVICE_ANNOTATION;
import static java.util.Optional.ofNullable;

import java.lang.reflect.Modifier;
import java.util.Objects;

import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.wildfly.common.annotation.Nullable;

/**
 * This class represents a class or an interface annotated with @WebService.
 *
 * @author wh81752
 */
public class WebService {
    final public ClassInfo classInfo;

    public WebService(ClassInfo classInfo) {
        this.classInfo = classInfo;
        Objects.requireNonNull(classInfo);
        if (!isWebService()) {
            throw new IllegalArgumentException("not annotated as webservice: " + this.classInfo);
        }
    }

    public boolean isInterface() {
        return Modifier.isInterface(this.classInfo.flags());
    }

    public boolean isAbstract() {
        return Modifier.isAbstract(this.classInfo.flags());
    }

    public boolean isClass() {
        return !isInterface();
    }

    public boolean isWebService() {
        return classAnnotation(WEBSERVICE_ANNOTATION) != null;
    }

    /**
     * Return any annotation on this class/interface.
     */
    public @Nullable AnnotationInstance classAnnotation(DotName dotName) {
        return this.classInfo.classAnnotation(dotName);
    }

    /**
     * Return @WebService annotation.
     */
    public AnnotationInstance wsAnnotation() {
        return classAnnotation(WEBSERVICE_ANNOTATION);
    }

    public @Nullable AnnotationValue wsAnnotation(String property) {
        return ofNullable(wsAnnotation()).map(ai -> ai.value(property)).orElse(null);
    }

    public String className() {
        return this.classInfo.simpleName();
    }

    public @Nullable String soapBinding() {
        return ofNullable(this.classAnnotation(BINDING_TYPE_ANNOTATION))
                .map(AnnotationInstance::value)
                .map(AnnotationValue::asString)
                .orElse(null);
    }

    public String serviceName() {
        return ofNullable(this.wsAnnotation("serviceName")).map(AnnotationValue::asString).orElseGet(this::className);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        WebService ws = (WebService) o;
        return Objects.equals(classInfo, ws.classInfo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(classInfo);
    }

}
