package tools.vitruv.methodologisttemplate.vsum.brakeDiskUncertainty;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;

import brakesystem.BrakeDisk;
import brakesystem.Brakesystem;
import cad.CADRepository;
import cad.Circle;
import tools.vitruv.framework.views.CommittableView;
import tools.vitruv.framework.views.View;
import tools.vitruv.framework.vsum.VirtualModel;
import tools.vitruv.methodologisttemplate.vsum.uncertainty.AddAndRemoveUncertaintyTest;
import tools.vitruv.methodologisttemplate.vsum.uncertainty.UncertaintyTestUtil;
import uncertainty.Effect;
import uncertainty.EffectType;
import uncertainty.ReducabilityLevel;
import uncertainty.Uncertainty;
import uncertainty.UncertaintyAnnotationRepository;
import uncertainty.UncertaintyFactory;
import uncertainty.UncertaintyKind;
import uncertainty.UncertaintyLocationType;
import uncertainty.UncertaintyNature;

public class BrakeDiskDiameterUncertaintyTest {
        private static final Logger logger = org.slf4j.LoggerFactory
                        .getLogger(AddAndRemoveUncertaintyTest.class);

        @BeforeAll
        static void setup() {
                Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put("*",
                                new XMIResourceFactoryImpl());

        }

        @Test
        void changeBrakeDiskDiameterWithUncertainty(@TempDir Path tempDir) {
                VirtualModel vsum = UncertaintyTestUtil.createDefaultVirtualModel(tempDir);
                UncertaintyTestUtil.registerRootObjects(vsum, tempDir);

                UncertaintyTestUtil.addBrakeDiscWithDiameter(vsum, tempDir, 200);

                // Assert that brake disk with diameter 200 and circle with radius 100 are
                // present
                Assertions.assertTrue(assertView(UncertaintyTestUtil.getDefaultView(vsum,
                                List.of(CADRepository.class, Brakesystem.class)), (View view) -> {
                                        boolean circlePresent = view.getRootObjects(CADRepository.class).iterator()
                                                        .next().getCadElements()
                                                        .stream()
                                                        .filter(Circle.class::isInstance).map(Circle.class::cast)
                                                        .filter(d -> d.getRadius() == 100)
                                                        .findFirst().isPresent();
                                        logger.debug("Circle present: " + circlePresent);
                                        boolean brakeDiskPresent = view.getRootObjects(Brakesystem.class).iterator()
                                                        .next()
                                                        .getBrakeComponents()
                                                        .stream()
                                                        .filter(BrakeDisk.class::isInstance).map(BrakeDisk.class::cast)
                                                        .filter(d -> d.getDiameterInMM() == 200)
                                                        .findFirst().isPresent();
                                        logger.debug("BrakeDisk present: " + brakeDiskPresent);
                                        return circlePresent;
                                }));

                modifyView(UncertaintyTestUtil.getDefaultView(vsum,
                                List.of(UncertaintyAnnotationRepository.class, Brakesystem.class))
                                .withChangeDerivingTrait(),
                                (CommittableView v) -> {
                                        var brakeDisk = v.getRootObjects(Brakesystem.class).iterator().next()
                                                        .getBrakeComponents()
                                                        .stream()
                                                        .filter(BrakeDisk.class::isInstance).map(BrakeDisk.class::cast)
                                                        .filter(d -> d.getDiameterInMM() == 200)
                                                        .findFirst().orElseThrow();

                                        var uncertainty = createUncertainty("FromDisk", brakeDisk);

                                        // Trigger propagation
                                        brakeDisk.setSpecificationType("propagationTest");

                                        v.getRootObjects(UncertaintyAnnotationRepository.class).iterator().next()
                                                        .getUncertainties().add(uncertainty);

                                });

                // Assert that the diameter of the circle is changed to 98
                // (half of the expectation)
                Assertions.assertTrue(assertView(UncertaintyTestUtil.getDefaultView(vsum,
                                List.of(CADRepository.class)), (View view) -> {
                                        return view.getRootObjects(CADRepository.class).iterator().next()
                                                        .getCadElements()
                                                        .stream()
                                                        .filter(Circle.class::isInstance).map(Circle.class::cast)
                                                        .filter(d -> d.getRadius() == 98)
                                                        .findFirst().isPresent();
                                }));

        }

        private Uncertainty createUncertainty(String uncertaintyLocationSpecification, EObject object) {
                var uncertaintyLocation = UncertaintyFactory.eINSTANCE.createUncertaintyLocation();
                uncertaintyLocation.setLocation(UncertaintyLocationType.OUTCOME);
                uncertaintyLocation.setSpecification(uncertaintyLocationSpecification);
                uncertaintyLocation.getReferencesComponents().add(object);

                Effect effect = UncertaintyFactory.eINSTANCE.createEffect();
                effect.setSpecification("N=(196,5)");
                effect.getEffectTypes().addAll(List.of(EffectType.STOCHASTIC, EffectType.CONTINUOUS));

                var uncertainty = UncertaintyFactory.eINSTANCE.createUncertainty();
                uncertainty.setUncertaintyLocation(uncertaintyLocation);
                uncertainty.setKind(UncertaintyKind.BELIEF_UNCERTAINTY);
                uncertainty.setReducability(ReducabilityLevel.UNKNOWN);
                uncertainty.setNature(UncertaintyNature.ALEATORY);
                uncertainty.setSetManually(true);
                uncertainty.setId(EcoreUtil.generateUUID());
                uncertainty.setEffect(effect);
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
