package tools.vitruv.methodologisttemplate.vsum.uncertaintyPerformance;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

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
import mir.reactions.brakesystem2brakesystem.Brakesystem2brakesystemChangePropagationSpecification;
import mir.reactions.brakesystem2cad.Brakesystem2cadChangePropagationSpecification;
import mir.reactions.cad2brakesystem.Cad2brakesystemChangePropagationSpecification;
import mir.reactions.uncertainty2cad.Uncertainty2cadChangePropagationSpecification;
import mir.reactions.uncertainty2uncertainty.Uncertainty2uncertaintyChangePropagationSpecification;
import tools.vitruv.change.propagation.ChangePropagationMode;
import tools.vitruv.change.testutils.TestUserInteraction;
import tools.vitruv.framework.views.CommittableView;
import tools.vitruv.framework.views.View;
import tools.vitruv.framework.vsum.VirtualModelBuilder;
import tools.vitruv.framework.vsum.internal.InternalVirtualModel;
import tools.vitruv.methodologisttemplate.vsum.uncertainty.UncertaintyTestFactory;
import tools.vitruv.methodologisttemplate.vsum.uncertainty.UncertaintyTestUtil;
import uncertainty.Uncertainty;
import uncertainty.UncertaintyAnnotationRepository;
import uncertainty.UncertaintyLocation;

public class uncertaintyPerformanceTest {
    private static final Logger logger = org.slf4j.LoggerFactory
            .getLogger(uncertaintyPerformanceTest.class);

    private static final int NUMBER_OF_ELEMENTS_ADDED = 10;

    @BeforeAll
    static void setup() {
        Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put("*",
                new XMIResourceFactoryImpl());
        logger.info("Running with {} elements added to the model", NUMBER_OF_ELEMENTS_ADDED);
    }

    @Test
    void performanceTestSingleNoProp(@TempDir Path tempDir) {
        // This test should measure the performance of the uncertainty creation without
        // change propagation.
        // It should create a single uncertainty and check the performance of the
        // system.

        InternalVirtualModel vsum = createDefaultVirtualModelWithoutReactions(tempDir);
        // Registers a Brakesystem and UncertaintyAnnotationRepository
        UncertaintyTestUtil.registerRootObjects(vsum, tempDir);
        UncertaintyTestUtil.addBrakeDiscWithDiameter(vsum, tempDir, 120);

        long startTime = System.nanoTime();

        CommittableView view = UncertaintyTestUtil.getDefaultView(vsum,
                List.of(Brakesystem.class, UncertaintyAnnotationRepository.class))
                .withChangeDerivingTrait();
        modifyView(view, (CommittableView v) -> {
            BrakeDisk brakeDisk = v.getRootObjects(Brakesystem.class).iterator().next().getBrakeComponents()
                    .stream()
                    .filter(BrakeDisk.class::isInstance).map(BrakeDisk.class::cast)
                    .filter(d -> d.getDiameterInMM() == 120)
                    .findFirst().orElseThrow();

            UncertaintyLocation uncertaintyLocation = UncertaintyTestFactory
                    .createUncertaintyLocation(List.of(brakeDisk));
            Uncertainty uncertainty = UncertaintyTestFactory
                    .createUncertainty(Optional.of(uncertaintyLocation));

            v.getRootObjects(UncertaintyAnnotationRepository.class).iterator().next()
                    .getUncertainties().add(uncertainty);

            // Trigger propagation
            brakeDisk.setSpecificationType(EcoreUtil.generateUUID());
        });

        long endTime = System.nanoTime();
        long durationMs = (endTime - startTime) / 1_000_000;
        logger.info("[1a] Execution time for single uncertainty creation NO propagation: {} ms", durationMs);

        // Assert that only one uncertainty was created
        View assertionView = UncertaintyTestUtil.getDefaultView(vsum,
                List.of(UncertaintyAnnotationRepository.class));
        Assertions.assertTrue(assertView(assertionView, (View v) -> {
            List<Uncertainty> uncertainties = UncertaintyTestUtil.getBrakeDiskUncertainties(v);
            return uncertainties.size() == 1;
        }));
    }

    @Test
    void performanceTestSingleWithProp(@TempDir Path tempDir) {
        // This test should measure the performance of the uncertainty creation with
        // change propagation.
        // It should create a single uncertainty and check the performance of the
        // system.

        InternalVirtualModel vsum = createDefaultVirtualModelWithReactions(tempDir);
        // Registers a Brakesystem and UncertaintyAnnotationRepository
        UncertaintyTestUtil.registerRootObjects(vsum, tempDir);
        UncertaintyTestUtil.addBrakeDiscWithDiameter(vsum, tempDir, 120);

        long startTime = System.nanoTime();

        CommittableView view = UncertaintyTestUtil.getDefaultView(vsum,
                List.of(Brakesystem.class, UncertaintyAnnotationRepository.class))
                .withChangeDerivingTrait();
        modifyView(view, (CommittableView v) -> {
            BrakeDisk brakeDisk = v.getRootObjects(Brakesystem.class).iterator().next().getBrakeComponents()
                    .stream()
                    .filter(BrakeDisk.class::isInstance).map(BrakeDisk.class::cast)
                    .filter(d -> d.getDiameterInMM() == 120)
                    .findFirst().orElseThrow();

            UncertaintyLocation uncertaintyLocation = UncertaintyTestFactory
                    .createUncertaintyLocation(List.of(brakeDisk));
            Uncertainty uncertainty = UncertaintyTestFactory
                    .createUncertainty(Optional.of(uncertaintyLocation));

            v.getRootObjects(UncertaintyAnnotationRepository.class).iterator().next()
                    .getUncertainties().add(uncertainty);

            // Trigger propagation
            brakeDisk.setSpecificationType(EcoreUtil.generateUUID());
        });

        long endTime = System.nanoTime();
        long durationMs = (endTime - startTime) / 1_000_000;
        logger.info("[1b] Execution time for single uncertainty creation WITH propagation: {} ms", durationMs);

        // Assert that two uncertainties were created
        View assertionView = UncertaintyTestUtil.getDefaultView(vsum,
                List.of(UncertaintyAnnotationRepository.class));
        Assertions.assertTrue(assertView(assertionView, (View v) -> {
            List<Uncertainty> uncertainties = v.getRootObjects(UncertaintyAnnotationRepository.class).iterator().next()
                    .getUncertainties();
            return uncertainties.size() == 2;
        }));
    }

    @Test
    void performanceTestMultipleNoProp(@TempDir Path tempDir) {
        // This test should measure the performance of the uncertainty creation without
        // change propagation.
        // It should create multiple uncertainties and check the performance of the
        // system.

        InternalVirtualModel vsum = createDefaultVirtualModelWithoutReactions(tempDir);
        // Registers a Brakesystem and UncertaintyAnnotationRepository
        UncertaintyTestUtil.registerRootObjects(vsum, tempDir);
        for (int i = 0; i < NUMBER_OF_ELEMENTS_ADDED; i++) {
            UncertaintyTestUtil.addBrakeDiscWithDiameter(vsum, tempDir, 120 + i * 10);
        }

        long startTime = System.nanoTime();

        CommittableView view = UncertaintyTestUtil.getDefaultView(vsum,
                List.of(Brakesystem.class, UncertaintyAnnotationRepository.class))
                .withChangeDerivingTrait();
        modifyView(view, (CommittableView v) -> {
            BrakeDisk brakeDisk = v.getRootObjects(Brakesystem.class).iterator().next().getBrakeComponents()
                    .stream()
                    .filter(BrakeDisk.class::isInstance).map(BrakeDisk.class::cast)
                    .filter(d -> d.getDiameterInMM() == 120)
                    .findFirst().orElseThrow();

            UncertaintyLocation uncertaintyLocation = UncertaintyTestFactory
                    .createUncertaintyLocation(List.of(brakeDisk));
            Uncertainty uncertainty = UncertaintyTestFactory
                    .createUncertainty(Optional.of(uncertaintyLocation));

            v.getRootObjects(UncertaintyAnnotationRepository.class).iterator().next()
                    .getUncertainties().add(uncertainty);

            // Trigger propagation
            brakeDisk.setSpecificationType(EcoreUtil.generateUUID());
        });

        long endTime = System.nanoTime();
        long durationMs = (endTime - startTime) / 1_000_000;
        logger.info("[2a] Execution time for two uncertainties creation NO propagation: {} ms", durationMs);

        // Assert that only one uncertainty was created
        View assertionView = UncertaintyTestUtil.getDefaultView(vsum,
                List.of(UncertaintyAnnotationRepository.class));
        Assertions.assertTrue(assertView(assertionView, (View v) -> {
            List<Uncertainty> uncertainties = UncertaintyTestUtil.getBrakeDiskUncertainties(v);
            return uncertainties.size() == 1;
        }));
    }

    @Test
    void performanceTestMultipleWithProp(@TempDir Path tempDir) {
        // This test should measure the performance of the uncertainty creation with
        // change propagation.
        // It should create multiple uncertainties and check the performance of the
        // system.

        InternalVirtualModel vsum = createDefaultVirtualModelWithReactions(tempDir);
        // Registers a Brakesystem and UncertaintyAnnotationRepository
        UncertaintyTestUtil.registerRootObjects(vsum, tempDir);
        for (int i = 0; i < NUMBER_OF_ELEMENTS_ADDED; i++) {
            UncertaintyTestUtil.addBrakeDiscWithDiameter(vsum, tempDir, 120 + i * 10);
        }

        long startTime = System.nanoTime();

        CommittableView view = UncertaintyTestUtil.getDefaultView(vsum,
                List.of(Brakesystem.class, UncertaintyAnnotationRepository.class))
                .withChangeDerivingTrait();
        modifyView(view, (CommittableView v) -> {
            BrakeDisk brakeDisk = v.getRootObjects(Brakesystem.class).iterator().next().getBrakeComponents()
                    .stream()
                    .filter(BrakeDisk.class::isInstance).map(BrakeDisk.class::cast)
                    .filter(d -> d.getDiameterInMM() == 120)
                    .findFirst().orElseThrow();

            UncertaintyLocation uncertaintyLocation = UncertaintyTestFactory
                    .createUncertaintyLocation(List.of(brakeDisk));
            Uncertainty uncertainty = UncertaintyTestFactory
                    .createUncertainty(Optional.of(uncertaintyLocation));

            v.getRootObjects(UncertaintyAnnotationRepository.class).iterator().next()
                    .getUncertainties().add(uncertainty);

            // Trigger propagation
            brakeDisk.setSpecificationType(EcoreUtil.generateUUID());
        });

        long endTime = System.nanoTime();
        long durationMs = (endTime - startTime) / 1_000_000;
        logger.info("[2b] Execution time for two uncertainties components creation WITH propagation: {} ms",
                durationMs);

        // Assert that two uncertainties were created
        View assertionView = UncertaintyTestUtil.getDefaultView(vsum,
                List.of(UncertaintyAnnotationRepository.class));
        Assertions.assertTrue(assertView(assertionView, (View v) -> {
            List<Uncertainty> uncertainties = v.getRootObjects(UncertaintyAnnotationRepository.class).iterator().next()
                    .getUncertainties();
            return uncertainties.size() == 2;
        }));
    }

    @Test
    void performanceTestMultipleCorrespondingNoProp(@TempDir Path tempDir) {
        // This test should measure the performance of the uncertainty creation without
        // change propagation.
        // It should create multiple uncertainties and check the performance of the
        // system.

        InternalVirtualModel vsum = createDefaultVirtualModelWithoutReactions(tempDir);
        // Registers a Brakesystem and UncertaintyAnnotationRepository
        UncertaintyTestUtil.registerRootObjects(vsum, tempDir);
        for (int i = 0; i < NUMBER_OF_ELEMENTS_ADDED; i++) {
            UncertaintyTestUtil.addBrakeDiscWithDiameter(vsum, tempDir, 120 + i * 10);
        }

        long startTime = System.nanoTime();

        CommittableView view = UncertaintyTestUtil.getDefaultView(vsum,
                List.of(Brakesystem.class, UncertaintyAnnotationRepository.class))
                .withChangeDerivingTrait();
        modifyView(view, (CommittableView v) -> {
            BrakeDisk brakeDisk = v.getRootObjects(Brakesystem.class).iterator().next().getBrakeComponents()
                    .stream()
                    .filter(BrakeDisk.class::isInstance).map(BrakeDisk.class::cast)
                    .filter(d -> d.getDiameterInMM() == 120)
                    .findFirst().orElseThrow();

            UncertaintyLocation uncertaintyLocation = UncertaintyTestFactory
                    .createUncertaintyLocation(List.of(brakeDisk));
            Uncertainty uncertainty = UncertaintyTestFactory
                    .createUncertainty(Optional.of(uncertaintyLocation));

            v.getRootObjects(UncertaintyAnnotationRepository.class).iterator().next()
                    .getUncertainties().add(uncertainty);

            // Trigger propagation
            brakeDisk.setSpecificationType(EcoreUtil.generateUUID());
        });

        long endTime = System.nanoTime();
        long durationMs = (endTime - startTime) / 1_000_000;
        logger.info("[3a] Execution time for one uncertainty creation NO propagation: {} ms", durationMs);

        // Assert that only one uncertainty was created
        View assertionView = UncertaintyTestUtil.getDefaultView(vsum,
                List.of(UncertaintyAnnotationRepository.class));
        Assertions.assertTrue(assertView(assertionView, (View v) -> {
            List<Uncertainty> uncertainties = UncertaintyTestUtil.getBrakeDiskUncertainties(v);
            return uncertainties.size() == 1;
        }));
    }

    @Test
    void performanceTestMultipleCorrespondingWithProp(@TempDir Path tempDir) {
        // This test should measure the performance of the uncertainty creation with
        // change propagation.
        // It should create multiple uncertainties and check the performance of the
        // system.

        InternalVirtualModel vsum = createDefaultVirtualModelWithReactionsWithTestReactions(tempDir);
        // Registers a Brakesystem and UncertaintyAnnotationRepository
        UncertaintyTestUtil.registerRootObjects(vsum, tempDir);
        for (int i = 0; i < NUMBER_OF_ELEMENTS_ADDED; i++) {
            UncertaintyTestUtil.addBrakeDiscWithDiameter(vsum, tempDir, 120 + i * 10);
        }

        long startTime = System.nanoTime();

        CommittableView view = UncertaintyTestUtil.getDefaultView(vsum,
                List.of(Brakesystem.class, UncertaintyAnnotationRepository.class))
                .withChangeDerivingTrait();
        modifyView(view, (CommittableView v) -> {
            BrakeDisk brakeDisk = v.getRootObjects(Brakesystem.class).iterator().next().getBrakeComponents()
                    .stream()
                    .filter(BrakeDisk.class::isInstance).map(BrakeDisk.class::cast)
                    .filter(d -> d.getDiameterInMM() == 120)
                    .findFirst().orElseThrow();

            UncertaintyLocation uncertaintyLocation = UncertaintyTestFactory
                    .createUncertaintyLocation(List.of(brakeDisk));
            Uncertainty uncertainty = UncertaintyTestFactory
                    .createUncertainty(Optional.of(uncertaintyLocation));

            v.getRootObjects(UncertaintyAnnotationRepository.class).iterator().next()
                    .getUncertainties().add(uncertainty);

            // Trigger propagation
            brakeDisk.setSpecificationType(EcoreUtil.generateUUID());
        });

        long endTime = System.nanoTime();
        long durationMs = (endTime - startTime) / 1_000_000;
        logger.info("[3b] Execution time for {} uncertainty creation WITH propagation: {} ms",
                NUMBER_OF_ELEMENTS_ADDED * 2, durationMs);

        // Assert that two uncertainties were created
        View assertionView = UncertaintyTestUtil.getDefaultView(vsum,
                List.of(UncertaintyAnnotationRepository.class));
        Assertions.assertTrue(assertView(assertionView, (View v) -> {
            List<Uncertainty> uncertainties = v.getRootObjects(UncertaintyAnnotationRepository.class).iterator().next()
                    .getUncertainties();
            return uncertainties.size() == NUMBER_OF_ELEMENTS_ADDED * 2; // NUMBER_OF_ELEMENTS_ADDED uncertainties from
                                                                         // the brake disks, each with a propagated
            // uncertainty
        }));
    }

    private InternalVirtualModel createDefaultVirtualModelWithoutReactions(Path projectPath) {
        InternalVirtualModel model = new VirtualModelBuilder()
                .withStorageFolder(projectPath)
                .withUserInteractorForResultProvider(
                        new TestUserInteraction.ResultProvider(new TestUserInteraction()))
                // Add propagation rules for brakesystem and cad, but not for
                // uncertainty, to be fair and models are comparable
                .withChangePropagationSpecification(new Brakesystem2cadChangePropagationSpecification())
                .withChangePropagationSpecification(new Cad2brakesystemChangePropagationSpecification())
                .buildAndInitialize();
        model.setChangePropagationMode(ChangePropagationMode.TRANSITIVE_CYCLIC);
        return model;
    }

    private InternalVirtualModel createDefaultVirtualModelWithReactions(Path projectPath) {
        InternalVirtualModel model = new VirtualModelBuilder()
                .withStorageFolder(projectPath)
                .withUserInteractorForResultProvider(
                        new TestUserInteraction.ResultProvider(new TestUserInteraction()))
                .withChangePropagationSpecification(new Brakesystem2cadChangePropagationSpecification())
                .withChangePropagationSpecification(
                        new Uncertainty2uncertaintyChangePropagationSpecification())
                .withChangePropagationSpecification(new Cad2brakesystemChangePropagationSpecification())
                .withChangePropagationSpecification(new Uncertainty2cadChangePropagationSpecification())
                .buildAndInitialize();
        model.setChangePropagationMode(ChangePropagationMode.TRANSITIVE_CYCLIC);
        return model;
    }

    private InternalVirtualModel createDefaultVirtualModelWithReactionsWithTestReactions(Path projectPath) {
        InternalVirtualModel model = new VirtualModelBuilder()
                .withStorageFolder(projectPath)
                .withUserInteractorForResultProvider(
                        new TestUserInteraction.ResultProvider(new TestUserInteraction()))
                .withChangePropagationSpecification(new Brakesystem2cadChangePropagationSpecification())
                .withChangePropagationSpecification(
                        new Uncertainty2uncertaintyChangePropagationSpecification())
                .withChangePropagationSpecification(new Cad2brakesystemChangePropagationSpecification())
                .withChangePropagationSpecification(new Uncertainty2cadChangePropagationSpecification())
                .withChangePropagationSpecification(new Brakesystem2brakesystemChangePropagationSpecification())
                .buildAndInitialize();
        model.setChangePropagationMode(ChangePropagationMode.TRANSITIVE_CYCLIC);
        return model;
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
