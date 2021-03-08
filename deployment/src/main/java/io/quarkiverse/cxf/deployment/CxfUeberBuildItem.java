package io.quarkiverse.cxf.deployment;

import java.util.ArrayList;
import java.util.List;

import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.GeneratedBeanBuildItem;
import io.quarkus.arc.deployment.UnremovableBeanBuildItem;
import io.quarkus.builder.item.MultiBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageProxyDefinitionBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;

/**
 * A collective group of BuildItems around CXF WebService.
 *
 * <p>
 * A single builditem containing all builditems necessary to produce webservices.
 *
 * @author wh81752
 */
public final class CxfUeberBuildItem extends MultiBuildItem {
    final public List<FeatureBuildItem> features = new ArrayList<>();
    final public List<ReflectiveClassBuildItem> reflectiveClasss = new ArrayList<>();
    final public List<NativeImageProxyDefinitionBuildItem> proxies = new ArrayList<>();
    final public List<GeneratedBeanBuildItem> generatedBeans = new ArrayList<>();
    final public List<CxfWebServiceBuildItem> cxfWebServices = new ArrayList<>();
    final public List<AdditionalBeanBuildItem> additionalBeans = new ArrayList<>();
    final public List<UnremovableBeanBuildItem> unremovableBeans = new ArrayList<>();

    public CxfUeberBuildItem produceFeature() {
        this.features.add(new FeatureBuildItem(QuarkusCxfProcessor.FEATURE_CXF));
        return this;
    }

    public CxfUeberBuildItem produce(ReflectiveClassBuildItem item) {
        this.reflectiveClasss.add(item);
        return this;
    }

    public CxfUeberBuildItem produceReflectiveClass(String clname) {
        return this.produce(new ReflectiveClassBuildItem(true, true, clname));
    }

    public CxfUeberBuildItem produceReflectiveClass(AnnotationValue clname) {
        return this.produceReflectiveClass(clname.asString());
    }

    public CxfUeberBuildItem produceReflectiveClass(ClassInfo clname) {
        return this.produceReflectiveClass(clname.name().toString());
    }

    public CxfUeberBuildItem unremovable(String clname) {
        return unremovable(new UnremovableBeanBuildItem.BeanClassNameExclusion(clname));
    }

    public CxfUeberBuildItem unremovable(ClassInfo clname) {
        return unremovable(clname.name().toString());
    }

    public CxfUeberBuildItem unremovable(UnremovableBeanBuildItem.BeanClassNameExclusion clname) {
        this.unremovableBeans.add(new UnremovableBeanBuildItem(clname));
        return this;
    }

    public CxfUeberBuildItem produceProxies(String... items) {
        this.proxies.add(new NativeImageProxyDefinitionBuildItem(items));
        return this;
    }

    public CxfUeberBuildItem produceAdditionalBean(String clname) {
        this.additionalBeans.add(AdditionalBeanBuildItem.unremovableOf(clname));
        return this;
    }

    public CxfUeberBuildItem produce(CxfWebServiceBuildItem item) {
        this.cxfWebServices.add(item);
        return this;
    }

    public CxfUeberBuildItem produceWebService(QuarkusCxfProcessor.WebServiceCxf ws) {
        CxfWebServiceBuildItem item;
        if (ws.hasImpl()) {
            item = new CxfWebServiceBuildItem(
                    ws.path,
                    ws.sei,
                    ws.soapBinding,
                    ws.wsNamespace,
                    ws.wsName,
                    ws.wrapperClassNames,
                    ws.impl);
            this.produce(item);
            this.produceAdditionalBean(ws.impl);
        } else {
            item = new CxfWebServiceBuildItem(
                    ws.path,
                    ws.sei,
                    ws.soapBinding,
                    ws.wsNamespace,
                    ws.wsName,
                    ws.wrapperClassNames);
            this.produce(item);
        }
        return this;
    }

}
