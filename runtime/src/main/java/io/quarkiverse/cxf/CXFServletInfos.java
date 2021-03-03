package io.quarkiverse.cxf;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.jboss.logging.Logger;

public class CXFServletInfos {
    private static final Logger LOGGER = Logger.getLogger(CXFServletInfos.class);

    private final List<CXFServletInfo> infos;
    private String path = null;

    public CXFServletInfos() {
        LOGGER.trace("new CXFServletInfos");
        infos = new ArrayList<>();
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

    public void setPath(String path) {
        this.path = path;
    }

    public void startRoute(
            CXFServiceData data,
            CXFRecorder.servletConfig cfg) {
        Objects.requireNonNull(cfg);
        if (data.hasImpl()) {
            LOGGER.trace("register CXF Servlet info");
            this.add(new CXFServletInfo(data, cfg.config, cfg.path));
        }
    }

    public void startRoute(CXFServiceData data) {
        if (data.hasImpl()) {
            LOGGER.trace("register CXF Servlet info");
            this.add(new CXFServletInfo(data, null, data.relativePath()));
        }
    }
}
