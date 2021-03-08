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

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        CXFServiceData that = (CXFServiceData) o;
        return Objects.equals(clnames, that.clnames) && Objects.equals(
                binding,
                that.binding) && Objects.equals(impl, that.impl) && Objects.equals(path, that.path)
                && Objects
                        .equals(sei, that.sei)
                && Objects.equals(wsName, that.wsName) && Objects.equals(
                        wsNamespace,
                        that.wsNamespace);
    }

    @Override
    public int hashCode() {
        return Objects.hash(clnames, binding, impl, path, sei, wsName, wsNamespace);
    }

    @Override
    public String toString() {
        return "CXFServiceData{" +
                "clnames=" + clnames +
                ", binding='" + binding + '\'' +
                ", impl='" + impl + '\'' +
                ", path='" + path + '\'' +
                ", sei='" + sei + '\'' +
                ", wsName='" + wsName + '\'' +
                ", wsNamespace='" + wsNamespace + '\'' +
                '}';
    }
}
