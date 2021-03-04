package io.quarkiverse.cxf;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.jboss.logging.Logger;

public class CXFServletInfos {
    private static final Logger LOGGER = Logger.getLogger(CXFServletInfos.class);

    private final List<CXFServletInfo> infos = new ArrayList<>();
    private final String path;

    public CXFServletInfos(String path) {
        Objects.requireNonNull(path);
        this.path = path;
    }

    public Collection<CXFServletInfo> getInfos() {
        return infos;
    }

    public String getPath() {
        return path;
    }

    public List<String> getWrappersclasses() {
        Objects.requireNonNull(this.infos);
        return infos.stream()
                .map(CXFServletInfo::getWrapperClassNames)
                .flatMap(List::stream)
                .collect(Collectors.toList());
    }

    public void add(CXFServletInfo cfg) {
        infos.add(cfg);
    }

    public void startRoute(
            CXFServiceData data,
            CXFRecorder.servletConfig cfg) {
        Objects.requireNonNull(cfg);
        if (data.hasImpl()) {
            this.add(new CXFServletInfo(data, cfg.config, cfg.path));
        }
    }

    public void startRoute(CXFServiceData data) {
        if (data.hasImpl()) {
            this.add(new CXFServletInfo(data, null, data.relativePath()));
        }
    }
}
