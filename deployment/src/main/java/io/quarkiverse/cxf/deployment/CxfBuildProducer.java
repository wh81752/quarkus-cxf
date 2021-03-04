package io.quarkiverse.cxf.deployment;

import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.GeneratedBeanBuildItem;
import io.quarkus.arc.deployment.UnremovableBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageProxyDefinitionBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;

/**
 * A class containing all producer items.
 */
class CxfBuildProducer {
    BuildProducer<FeatureBuildItem> feature;
    BuildProducer<ReflectiveClassBuildItem> reflectiveClass;
    BuildProducer<NativeImageProxyDefinitionBuildItem> proxies;
    BuildProducer<GeneratedBeanBuildItem> generatedBeans;
    BuildProducer<CxfWebServiceBuildItem> cxfWebServices;
    BuildProducer<AdditionalBeanBuildItem> additionalBeans;
    BuildProducer<UnremovableBeanBuildItem> unremovableBeans;

    public CxfBuildProducer produce(ReflectiveClassBuildItem item) {
        this.reflectiveClass.produce(item);
        return this;
    }

    public CxfBuildProducer produceReflectiveClass(String clname) {
        return this.produce(new ReflectiveClassBuildItem(true, true, clname));
    }

    public CxfBuildProducer produceReflectiveClass(AnnotationValue clname) {
        return this.produceReflectiveClass(clname.asString());
    }

    public CxfBuildProducer produceReflectiveClass(ClassInfo clname) {
        return this.produceReflectiveClass(clname.name().toString());
    }

    public CxfBuildProducer produce(CxfWebServiceBuildItem item) {
        this.cxfWebServices.produce(item);
        return this;
    }

    public CxfBuildProducer produceWebService(QuarkusCxfProcessor.WebServiceCxf ws) {
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
            this.cxfWebServices.produce(item);
            this.produceAdditionalBean(ws.impl);
        } else {
            item = new CxfWebServiceBuildItem(
                    ws.path,
                    ws.sei,
                    ws.soapBinding,
                    ws.wsNamespace,
                    ws.wsName,
                    ws.wrapperClassNames);
            this.cxfWebServices.produce(item);
        }
        return this;
    }

    public CxfBuildProducer unremovable(String clname) {
        return unremovable(new UnremovableBeanBuildItem.BeanClassNameExclusion(clname));
    }

    public CxfBuildProducer unremovable(ClassInfo clname) {
        return unremovable(clname.name().toString());
    }

    public CxfBuildProducer unremovable(UnremovableBeanBuildItem.BeanClassNameExclusion clname) {
        this.unremovableBeans.produce(new UnremovableBeanBuildItem(clname));
        return this;
    }

    public CxfBuildProducer produceProxies(String... items) {
        this.proxies.produce(new NativeImageProxyDefinitionBuildItem(items));
        return this;
    }

    public CxfBuildProducer produceFeature() {
        this.feature.produce(new FeatureBuildItem(QuarkusCxfProcessor.FEATURE_CXF));
        return this;
    }

    public CxfBuildProducer produceAdditionalBean(String clname) {
        this.additionalBeans.produce(AdditionalBeanBuildItem.unremovableOf(clname));
        return this;
    }
}
