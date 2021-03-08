package io.quarkiverse.cxf.deployment;

import org.apache.cxf.common.spi.GeneratedClassClassLoaderCapture;

import io.quarkus.gizmo.ClassOutput;

/**
 * A capturing class that can be injected into a CXF service as feature.
 *
 * <p>
 * This class acts as adapter for recording class-loading. Every class
 * loaded will be written to ClassOutput.
 * </p>
 *
 * <p>
 * Injecting in CXF is a simple as:
 * </p>
 * 
 * <pre>
 *    ClassOutput sink = .. ; // setup a sink to record classes
 *    QuarkusCapture cap = new QuarkusCapture(sink);
 *    Bus bus = BusFactory.getDefaultBus();
 *    bus.setExtension(c, GeneratedClassClassLoaderCapture.class);
 *    // now create CXF webservice(s) ..
 *    // do something with collected items in sink
 * </pre>
 *
 *
 * @author geronimo1
 */
class QuarkusCapture implements GeneratedClassClassLoaderCapture {
    private final ClassOutput classOutput;

    QuarkusCapture(ClassOutput classOutput) {
        this.classOutput = classOutput;

    }

    @Override
    public void capture(String name, byte[] bytes) {
        QuarkusCxfProcessor.LOGGER.trace("capture generation of " + name);
        classOutput.getSourceWriter(name);
        classOutput.write(name, bytes);
    }
}
