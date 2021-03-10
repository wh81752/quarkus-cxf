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

    public CxfUeberBuildItem addFeatureCxF() {
        this.features.add(new FeatureBuildItem(QuarkusCxfProcessor.FEATURE_CXF));
        return this;
    }

    public CxfUeberBuildItem additem(ReflectiveClassBuildItem item) {
        this.reflectiveClasss.add(item);
        return this;
    }

    public CxfUeberBuildItem addReflectiveClass(String clname) {
        return this.additem(new ReflectiveClassBuildItem(true, true, clname));
    }

    public CxfUeberBuildItem addReflectiveClass(AnnotationValue clname) {
        return this.addReflectiveClass(clname.asString());
    }

    public CxfUeberBuildItem addReflectiveClass(ClassInfo clname) {
        return this.addReflectiveClass(clname.name().toString());
    }

    public CxfUeberBuildItem addUnremovable(String clname) {
        return addUnremovable(new UnremovableBeanBuildItem.BeanClassNameExclusion(clname));
    }

    public CxfUeberBuildItem addUnremovable(ClassInfo clname) {
        return addUnremovable(clname.name().toString());
    }

    public CxfUeberBuildItem addUnremovable(UnremovableBeanBuildItem.BeanClassNameExclusion clname) {
        this.unremovableBeans.add(new UnremovableBeanBuildItem(clname));
        return this;
    }

    public CxfUeberBuildItem addProxies(String... items) {
        this.proxies.add(new NativeImageProxyDefinitionBuildItem(items));
        return this;
    }

    public CxfUeberBuildItem addAdditionalBean(String clname) {
        this.additionalBeans.add(AdditionalBeanBuildItem.unremovableOf(clname));
        return this;
    }

    public CxfUeberBuildItem addAdditionalBean(Implementor clname) {
        return this.addAdditionalBean(clname.classInfo.name().toString());
    }

    public CxfUeberBuildItem additem(CxfWebServiceBuildItem item) {
        Objects.requireNonNull(item);
        this.cxfWebServices.add(item);
        if (item.hasImplementor()) {
            this.addAdditionalBean(item.getImplementor());
        }
        return this;
    }

    public CxfUeberBuildItem additem(CxfWebServiceBuildItemBuilder item) {
        return additem(item.build());
    }

    public CxfUeberBuildItem capture(
            ClassInfo sei) {
        return capture(sei.name().toString());
    }

    public CxfUeberBuildItem capture(String sei) {
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
