package io.quarkiverse.cxf;

import java.util.ArrayList;
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
    public List<String> clnames = new ArrayList<>();
    public String binding;
    public String impl;
    public String path;
    public String sei;
    public String wsName;
    public String wsNamespace;

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
