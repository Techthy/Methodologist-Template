package tools.vitruv.methodologisttemplate.vsum.uncertainty;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.Assertions;
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
import uncertainty.OnDeleteMode;
import uncertainty.Uncertainty;
import uncertainty.UncertaintyAnnotationRepository;
import uncertainty.UncertaintyLocation;

public class OnDeleteTest {

        private static final Logger logger = org.slf4j.LoggerFactory
                        .getLogger(OnDeleteTest.class);

        @BeforeAll
        static void setup() {
                Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put("*",
                                new XMIResourceFactoryImpl());

        }

        // Plan of the test:
        // BrakeDisk and Circle are added to the model
        // Uncertainty is added to BrakeDisk with OnDeleteMode.NO_ACTION
        // This should propagate to the Circle
        // BrakeDisk uncertainty is deleted
        // Since the OnDeleteMode is NO_ACTION, the Circle uncertainty should
        // still exist

        @Disabled
        @Test
        // AddAndRemoveUncertaintyWithOnDeleteNoActionTest
        void onDeleteNoActionTest(@TempDir Path tempDir) {
                logger.info("Starting AddAndRemoveUncertaintyWithOnDeleteNoActionTest");
                VirtualModel vsum = UncertaintyTestUtil.createDefaultVirtualModel(tempDir);
                // Registers a Brakesystem, CADRepository and UncertaintyAnnotationRepository
                UncertaintyTestUtil.registerRootObjects(vsum, tempDir);

                // Add a BrakeDisk that in turn (by reactions) creates a Circle
                UncertaintyTestUtil.addBrakeDiscWithDiameter(vsum, tempDir, 120);

                // Add uncertainty to the brake disk
                CommittableView brakeAndUncertaintyView = UncertaintyTestUtil.getDefaultView(vsum,
                                List.of(UncertaintyAnnotationRepository.class, Brakesystem.class))
                                .withChangeRecordingTrait();
                modifyView(brakeAndUncertaintyView, (CommittableView v) -> {
                        BrakeDisk brakeDisk = v.getRootObjects(Brakesystem.class).iterator().next().getBrakeComponents()
                                        .stream()
                                        .filter(BrakeDisk.class::isInstance).map(BrakeDisk.class::cast)
                                        .filter(d -> d.getDiameterInMM() == 120)
                                        .findFirst().orElseThrow();

                        UncertaintyLocation uncertaintyLocation = UncertaintyTestFactory
                                        .createUncertaintyLocation(List.of(brakeDisk));
                        uncertaintyLocation.setSpecification("FromDisk");
                        Uncertainty uncertainty = UncertaintyTestFactory
                                        .createUncertainty(Optional.of(uncertaintyLocation));
                        uncertainty.setOnDelete(OnDeleteMode.NO_ACTION);

                        v.getRootObjects(UncertaintyAnnotationRepository.class).iterator().next()
                                        .getUncertainties().add(uncertainty);

                        // Trigger propagation
                        brakeDisk.setSpecificationType(EcoreUtil.generateUUID());

                });

                // Assert that the uncertainties are created correctly
                Assertions.assertTrue(
                                assertView(UncertaintyTestUtil.getDefaultView(vsum,
                                                List.of(UncertaintyAnnotationRepository.class)),
                                                (View v) -> {
                                                        List<Uncertainty> brakeDiskUncertainties = UncertaintyTestUtil
                                                                        .getBrakeDiskUncertainties(v);
                                                        List<Uncertainty> circleUncertainties = UncertaintyTestUtil
                                                                        .getCircleUncertainties(v);

                                                        return circleUncertainties.size() == 1
                                                                        && brakeDiskUncertainties.size() == 1;
                                                }));

                // Delete the uncertainty belonging to the brake disk
                deleteBrakeDiskUncertainty(vsum);

                // Assert that the uncertainty belonging to the brake disk is deleted
                Assertions.assertTrue(assertView(UncertaintyTestUtil.getDefaultView(vsum,
                                List.of(UncertaintyAnnotationRepository.class)),
                                (View v) -> {
                                        List<Uncertainty> brakeDiskUncertainties = UncertaintyTestUtil
                                                        .getBrakeDiskUncertainties(v);
                                        List<Uncertainty> circleUncertainties = UncertaintyTestUtil
                                                        .getCircleUncertainties(v);
                                        return circleUncertainties.size() == 1 && brakeDiskUncertainties.isEmpty();
                                }));

        }

        // Plan of the test:
        // BrakeDisk and Circle are added to the model
        // Uncertainty is added to BrakeDisk with OnDeleteMode.RESTRICT
        // This should propagate to the Circle
        // BrakeDisk uncertainty is deleted
        // Since the OnDeleteMode is RESTRICT both uncertainties should still exist

        @Disabled
        @Test
        // AddAndRemoveUncertaintyWithOnDeleteRestrictTest
        void onDeleteRestrictTest(@TempDir Path tempDir) {
                logger.info("Starting AddAndRemoveUncertaintyWithOnDeleteRestrictTest");
                VirtualModel vsum = UncertaintyTestUtil.createDefaultVirtualModel(tempDir);
                // Registers a Brakesystem, CADRepository and UncertaintyAnnotationRepository
                UncertaintyTestUtil.registerRootObjects(vsum, tempDir);

                // Add a BrakeDisk that in turn (by reactions) creates a Circle
                UncertaintyTestUtil.addBrakeDiscWithDiameter(vsum, tempDir, 120);

                // Add uncertainty to the brake disk
                CommittableView brakeAndUncertaintyView = UncertaintyTestUtil.getDefaultView(vsum,
                                List.of(UncertaintyAnnotationRepository.class, Brakesystem.class))
                                .withChangeRecordingTrait();
                modifyView(brakeAndUncertaintyView, (CommittableView v) -> {
                        BrakeDisk brakeDisk = v.getRootObjects(Brakesystem.class).iterator().next().getBrakeComponents()
                                        .stream()
                                        .filter(BrakeDisk.class::isInstance).map(BrakeDisk.class::cast)
                                        .filter(d -> d.getDiameterInMM() == 120)
                                        .findFirst().orElseThrow();

                        UncertaintyLocation uncertaintyLocation = UncertaintyTestFactory
                                        .createUncertaintyLocation(List.of(brakeDisk));
                        uncertaintyLocation.setSpecification("FromDisk");
                        Uncertainty uncertainty = UncertaintyTestFactory
                                        .createUncertainty(Optional.of(uncertaintyLocation));
                        uncertainty.setOnDelete(OnDeleteMode.RESTRICT);

                        v.getRootObjects(UncertaintyAnnotationRepository.class).iterator().next()
                                        .getUncertainties().add(uncertainty);

                        // Trigger propagation
                        brakeDisk.setSpecificationType(EcoreUtil.generateUUID());

                });

                // Assert that the uncertainties are created correctly
                Assertions.assertTrue(
                                assertView(UncertaintyTestUtil.getDefaultView(vsum,
                                                List.of(UncertaintyAnnotationRepository.class)),
                                                (View v) -> {
                                                        List<Uncertainty> brakeDiskUncertainties = UncertaintyTestUtil
                                                                        .getBrakeDiskUncertainties(v);
                                                        List<Uncertainty> circleUncertainties = UncertaintyTestUtil
                                                                        .getCircleUncertainties(v);

                                                        return circleUncertainties.size() == 1
                                                                        && brakeDiskUncertainties.size() == 1;
                                                }));

                // Delete the uncertainty belonging to the brake disk
                // deleteBrakeDiskUncertainty(vsum);

                // Assert that the uncertainty belonging to the brake disk is deleted
                Assertions.assertTrue(assertView(UncertaintyTestUtil.getDefaultView(vsum,
                                List.of(UncertaintyAnnotationRepository.class)),
                                (View v) -> {
                                        List<Uncertainty> brakeDiskUncertainties = UncertaintyTestUtil
                                                        .getBrakeDiskUncertainties(v);
                                        List<Uncertainty> circleUncertainties = UncertaintyTestUtil
                                                        .getCircleUncertainties(v);
                                        return circleUncertainties.size() == 1 && brakeDiskUncertainties.size() == 1;
                                }));

        }

        // Helper methods for creating and deleting uncertainties

        private void deleteBrakeDiskUncertainty(VirtualModel vsum) {
                modifyView(UncertaintyTestUtil
                                .getDefaultView(vsum, List.of(UncertaintyAnnotationRepository.class, Brakesystem.class))
                                .withChangeRecordingTrait(), (CommittableView v) -> {

                                        EList<Uncertainty> uncertainties = v
                                                        .getRootObjects(UncertaintyAnnotationRepository.class)
                                                        .iterator().next()
                                                        .getUncertainties();
                                        Uncertainty uncertaintyToDelete = uncertainties.stream()
                                                        .filter(u -> u.getUncertaintyLocation()
                                                                        .getReferencedComponents().stream()
                                                                        .anyMatch(c -> c instanceof BrakeDisk))
                                                        .findFirst().orElseThrow();
                                        logger.debug("Deleting uncertainty: " + uncertaintyToDelete);

                                        v.getRootObjects(UncertaintyAnnotationRepository.class)
                                                        .iterator().next()
                                                        .getUncertainties().remove(uncertaintyToDelete);
                                        EcoreUtil.delete(uncertaintyToDelete, true);

                                        // Trigger propagation
                                        v.getRootObjects(Brakesystem.class).iterator().next()
                                                        .getBrakeComponents().get(0)
                                                        .setSpecificationType(EcoreUtil.generateUUID());
                                });
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
