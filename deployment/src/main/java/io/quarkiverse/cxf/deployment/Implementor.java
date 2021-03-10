package io.quarkiverse.cxf.deployment;

import static io.quarkiverse.cxf.deployment.SEI.assertWebService;

import java.lang.reflect.Modifier;
import java.util.Objects;

import org.jboss.jandex.ClassInfo;

/**
 * Class Documentation
 *
 * <p>
 * What is the point of this class?
 *
 * @author geronimo1
 */
public class Implementor {
    final public ClassInfo classInfo;

    public Implementor(ClassInfo classInfo) {
        Objects.requireNonNull(classInfo);
        assertWebService(classInfo);
        this.classInfo = classInfo;
        if (Modifier.isInterface(classInfo.flags())) {
            throw new IllegalArgumentException("interface expected: " + classInfo);
        }
    }

    @Override
    public String toString() {
        return "Implementor{" +
                "classInfo=" + classInfo +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Implementor that = (Implementor) o;
        return Objects.equals(classInfo, that.classInfo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(classInfo);
    }
}
