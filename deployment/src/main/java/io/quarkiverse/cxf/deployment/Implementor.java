package io.quarkiverse.cxf.deployment;

import org.jboss.jandex.ClassInfo;

/**
 * A class representing a @WebService implementor.
 *
 * <p>
 * This immutable class ensures certain @WebService Implementor properties of the Class {@code classInfo }wrapped:
 * <ul>
 * <li>The class is annotated with @WebService</li>
 * <li>The class is _not_ an interface</li>
 * </ul>
 *
 * @author wh81752
 */
public class Implementor extends WebService {
    public Implementor(ClassInfo classInfo) {
        super(classInfo);
        if (!isClass()) {
            throw new IllegalArgumentException("interface cannot be an implementor: " + classInfo);
        }
    }

    @Override
    public String toString() {
        return "Implementor{" +
                "classInfo=" + classInfo +
                '}';
    }

}
