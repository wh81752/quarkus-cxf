package io.quarkiverse.cxf.deployment;

import static io.quarkiverse.cxf.deployment.QuarkusCxfProcessor.WEBSERVICE_ANNOTATION;

import java.lang.reflect.Modifier;
import java.util.Objects;
import java.util.Optional;

import org.jboss.jandex.ClassInfo;

/**
 * Class Documentation
 *
 * <p>
 * What is the point of this class?
 *
 * @author geronimo1
 */
public class SEI {
    final public ClassInfo classInfo;

    public SEI(ClassInfo classInfo) {
        Objects.requireNonNull(classInfo);
        assertWebService(classInfo);
        this.classInfo = classInfo;
    }

    public static void assertWebService(ClassInfo cl) {
        Optional.ofNullable(cl.classAnnotation(WEBSERVICE_ANNOTATION))
                .orElseThrow(() -> new IllegalArgumentException("not annotated as webservice: " + cl));
    }

    public static void assertInterface(ClassInfo cl) {
        if (!Modifier.isInterface(cl.flags())) {
            throw new IllegalArgumentException("interface expected: " + cl);
        }
    }

    @Override
    public String toString() {
        return "SEI{" +
                "classInfo=" + classInfo +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        SEI sei = (SEI) o;
        return Objects.equals(classInfo, sei.classInfo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(classInfo);
    }
}
