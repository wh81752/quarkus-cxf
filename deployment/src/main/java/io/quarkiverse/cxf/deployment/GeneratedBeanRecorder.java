package io.quarkiverse.cxf.deployment;

import java.io.StringWriter;
import java.io.Writer;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.quarkus.arc.deployment.GeneratedBeanBuildItem;
import io.quarkus.bootstrap.BootstrapDebug;
import io.quarkus.gizmo.ClassOutput;

/**
 * Class Documentation
 *
 * <p>
 * What is the point of this class?
 *
 * @author geronimo1
 */
public class GeneratedBeanRecorder implements ClassOutput {
    private final Collection<GeneratedBeanBuildItem> sink;
    private final Map<String, StringWriter> sources;

    public GeneratedBeanRecorder(Collection<GeneratedBeanBuildItem> sink) {
        this.sink = sink;
        this.sources = BootstrapDebug.DEBUG_SOURCES_DIR != null ? new ConcurrentHashMap<>() : null;
    }

    public GeneratedBeanRecorder(CxfUeberBuildItem cxf) {
        this.sink = cxf.generatedBeans;
        this.sources = BootstrapDebug.DEBUG_SOURCES_DIR != null ? new ConcurrentHashMap<>() : null;
    }

    public void write(
            String className,
            byte[] bytes) {
        String source = null;
        if (this.sources != null) {
            StringWriter sw = this.sources.get(className);
            if (sw != null) {
                source = sw.toString();
            }
        }
        //
        // generate new bean out of classname and bytes.
        //
        this.sink.add(new GeneratedBeanBuildItem(className, bytes, source));
    }

    public Writer getSourceWriter(String className) {
        Writer r;
        if (this.sources != null) {
            StringWriter writer = new StringWriter();
            this.sources.put(className, writer);
            r = writer;
        } else {
            r = ClassOutput.super.getSourceWriter(className);
        }
        return r;
    }
}
