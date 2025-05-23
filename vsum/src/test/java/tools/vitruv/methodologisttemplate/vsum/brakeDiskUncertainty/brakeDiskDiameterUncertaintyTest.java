package tools.vitruv.methodologisttemplate.vsum.brakeDiskUncertainty;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;

import brakesystem.BrakeDisk;
import brakesystem.Brakesystem;
import tools.vitruv.framework.views.CommittableView;
import tools.vitruv.framework.views.View;
import tools.vitruv.framework.vsum.VirtualModel;
import tools.vitruv.methodologisttemplate.vsum.uncertainty.AddAndRemoveUncertaintyTest;
import tools.vitruv.methodologisttemplate.vsum.uncertainty.UncertaintyTestUtil;
import uncertainty.Effect;
import uncertainty.ReducabilityLevel;
import uncertainty.Uncertainty;
import uncertainty.UncertaintyAnnotationRepository;
import uncertainty.UncertaintyFactory;
import uncertainty.UncertaintyKind;
import uncertainty.UncertaintyLocationType;
import uncertainty.UncertaintyNature;

public class brakeDiskDiameterUncertaintyTest {
    private static final Logger logger = org.slf4j.LoggerFactory
            .getLogger(AddAndRemoveUncertaintyTest.class);

    @BeforeAll
    static void setup() {
        Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put("*",
                new XMIResourceFactoryImpl());

    }

    @Disabled
    @Test
    void changeBrakeDiskDiameterWithUncertainty(@TempDir Path tempDir) {
        VirtualModel vsum = UncertaintyTestUtil.createDefaultVirtualModel(tempDir);
        UncertaintyTestUtil.registerRootObjects(vsum, tempDir);

        UncertaintyTestUtil.addBrakeDiscWithDiameter(vsum, tempDir, 100);

        modifyView(UncertaintyTestUtil.getDefaultView(vsum,
                List.of(UncertaintyAnnotationRepository.class, Brakesystem.class)).withChangeDerivingTrait(),
                (CommittableView v) -> {
                    var brakeDisk = v.getRootObjects(Brakesystem.class).iterator().next().getBrakeComponents()
                            .stream()
                            .filter(BrakeDisk.class::isInstance).map(BrakeDisk.class::cast)
                            .filter(d -> d.getDiameterInMM() == 100)
                            .findFirst().orElseThrow();

                    var uncertainty = createUncertainty("FromDisk", brakeDisk);

                    v.getRootObjects(UncertaintyAnnotationRepository.class).iterator().next()
                            .getUncertainties().add(uncertainty);

                });

        // // Assert that the uncertainty is added to the brake disk
        // Assertions.assertTrue(assertView(v, (View view) -> {
        // return view.getElementsOfType(Uncertainty.class).contains(uncertainty);
        // }));

        // // Assert that the uncertainty is added to the CAD
        // Assertions.assertTrue(assertView(v, (View view) -> {
        // return view.getElementsOfType(Uncertainty.class).contains(uncertainty);
        // }));

    }

    private Uncertainty createUncertainty(String uncertaintyLocationSpecification, EObject object) {
        var uncertaintyLocation = UncertaintyFactory.eINSTANCE.createUncertaintyLocation();
        uncertaintyLocation.setLocation(UncertaintyLocationType.OUTCOME);
        uncertaintyLocation.setSpecification(uncertaintyLocationSpecification);
        uncertaintyLocation.getReferencesComponents().add(object);

        Effect effect = UncertaintyFactory.eINSTANCE.createEffect();

        var uncertainty = UncertaintyFactory.eINSTANCE.createUncertainty();
        uncertainty.setUncertaintyLocation(uncertaintyLocation);
        uncertainty.setKind(UncertaintyKind.MEASUREMENT_UNCERTAINTY);
        uncertainty.setReducability(ReducabilityLevel.UNKNOWN);
        uncertainty.setNature(UncertaintyNature.ALEATORY);
        uncertainty.setSetManually(true);
        uncertainty.setId(EcoreUtil.generateUUID());
        return uncertainty;
    }

    // These functions are only for convience, as they make the code a bit better
    // readable
    private void modifyView(CommittableView view, Consumer<CommittableView> modificationFunction) {
        modificationFunction.accept(view);
        view.commitChanges();
    }

    private boolean assertView(View view, Function<View, Boolean> viewAssertionFunction) {
        return viewAssertionFunction.apply(view);
    }

}
