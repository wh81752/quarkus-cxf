package io.quarkiverse.cxf.deployment;

import org.apache.cxf.common.spi.GeneratedClassClassLoaderCapture;

import io.quarkus.gizmo.ClassOutput;

/**
 * Class Documentation
 *
 * <p>
 * What is the point of this class?
 *
 * @author geronimo1
 */
class QuarkusCapture implements GeneratedClassClassLoaderCapture {
    private final ClassOutput classOutput;

    QuarkusCapture(ClassOutput classOutput) {
        this.classOutput = classOutput;

    }

    @Override
    public void capture(
            String name,
            byte[] bytes) {
        classOutput.getSourceWriter(name);
        QuarkusCxfProcessor.LOGGER.trace("capture generation of " + name);
        classOutput.write(name, bytes);
    }
}
