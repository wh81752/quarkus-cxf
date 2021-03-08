package io.quarkiverse.cxf.deployment;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.common.spi.GeneratedClassClassLoaderCapture;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.logging.Logger;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.GeneratedBeanBuildItem;
import io.quarkus.arc.deployment.UnremovableBeanBuildItem;
import io.quarkus.builder.item.MultiBuildItem;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.nativeimage.NativeImageProxyDefinitionBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ReflectiveClassBuildItem;
import io.quarkus.gizmo.ClassOutput;

/**
 * A collective group of BuildItems around CXF WebService.
 *
 * <p>
 * A single builditem containing all builditems necessary to produce webservices.
 *
 * @author wh81752
 */
public final class CxfUeberBuildItem extends MultiBuildItem {
    public static final Logger LOGGER = Logger.getLogger(CxfUeberBuildItem.class);

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
        Objects.requireNonNull(item);
        this.cxfWebServices.add(item);
        if (item.hasImplementor()) {
            this.produceAdditionalBean(item.getImplementor());
        }
        return this;
    }

    public CxfUeberBuildItem produce(CxfWebServiceBuildItemBuilder item) {
        return produce(item.build());
    }

    public CxfUeberBuildItem capture(
            String sei) {
        List<String> wrapperClassNames = new ArrayList<>();
        ClassOutput classOutput = new GeneratedBeanRecorder(this);
        QuarkusCapture c = new QuarkusCapture(classOutput);
        QuarkusJaxWsServiceFactoryBean jaxwsFac = new QuarkusJaxWsServiceFactoryBean();
        Bus bus = BusFactory.getDefaultBus();
        jaxwsFac.setBus(bus);
        bus.setExtension(c, GeneratedClassClassLoaderCapture.class);
        //TODO here add all class
        try {
            jaxwsFac.setServiceClass(Thread.currentThread().getContextClassLoader().loadClass(sei));
            jaxwsFac.create();
            //  TODO: what to do with wrapperClassNames??
            //noinspection CollectionAddAllCanBeReplacedWithConstructor
            wrapperClassNames.addAll(jaxwsFac.getWrappersClassNames());
        } catch (ClassNotFoundException e) {
            LOGGER.error("failed to load WS class : " + sei);
        } finally {
            // nothing
        }
        return this;
    }

}
