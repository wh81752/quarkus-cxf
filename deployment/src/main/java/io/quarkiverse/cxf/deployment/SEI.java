package io.quarkiverse.cxf.deployment;

import org.jboss.jandex.ClassInfo;

/**
 * A class representing a @WebService's service endpoint (SEI).
 *
 * <p>
 * This immutable class ensures certain @WebService SEI properties of the Class {@code classInfo} wrapped:
 * <ul>
 * <li>The class is annotated with @WebService</li>
 * <li>The class is an interface</li>
 * </ul>
 *
 * @author wh81752
 */
public class SEI extends WebService {
    public SEI(ClassInfo classInfo) {
        super(classInfo);
        if (!isInterface()) {
            throw new IllegalArgumentException("not an interface class: " + this.classInfo);
        }
    }

    @Override
    public String toString() {
        return "SEI{" +
                "classInfo=" + classInfo +
                '}';
    }

}
