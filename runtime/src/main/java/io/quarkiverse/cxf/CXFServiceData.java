package io.quarkiverse.cxf;

import java.util.List;
import java.util.Objects;

/**
 * Class Documentation
 *
 * <p>
 * What is the point of this class?
 *
 * @author geronimo1
 */
public class CXFServiceData {
    public String sei;
    public String path;
    public String impl;
    public String binding;
    public List<String> clnames;

    public boolean hasImpl() {
        return this.impl != null && !this.impl.equals("");
    }

    public String relativePath() {
        Objects.requireNonNull(this.sei);
        String serviceName = this.sei.toLowerCase();
        if (serviceName.contains(".")) {
            serviceName = serviceName.substring(serviceName.lastIndexOf('.') + 1);
        }
        return String.format("/%s", serviceName);
    }
}
