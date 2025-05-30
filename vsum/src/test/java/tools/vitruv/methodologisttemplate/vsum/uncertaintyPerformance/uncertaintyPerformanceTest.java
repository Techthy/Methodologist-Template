package tools.vitruv.methodologisttemplate.vsum.uncertaintyPerformance;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;

import brakesystem.BrakeDisk;
import brakesystem.Brakesystem;
import brakesystem.BrakesystemFactory;
import tools.vitruv.change.propagation.ChangePropagationMode;
import tools.vitruv.change.testutils.TestUserInteraction;
import tools.vitruv.framework.views.CommittableView;
import tools.vitruv.framework.views.View;
import tools.vitruv.framework.vsum.VirtualModel;
import tools.vitruv.framework.vsum.VirtualModelBuilder;
import tools.vitruv.framework.vsum.internal.InternalVirtualModel;
import tools.vitruv.methodologisttemplate.vsum.uncertainty.UncertaintyTestFactory;
import tools.vitruv.methodologisttemplate.vsum.uncertainty.UncertaintyTestUtil;
import uncertainty.Uncertainty;
import uncertainty.UncertaintyAnnotationRepository;
import uncertainty.UncertaintyFactory;
import uncertainty.UncertaintyLocation;

public class uncertaintyPerformanceTest {
    private static final Logger logger = org.slf4j.LoggerFactory
            .getLogger(uncertaintyPerformanceTest.class);

    @BeforeAll
    static void setup() {
        Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put("*",
                new XMIResourceFactoryImpl());

    }

    @Test
    void performanceTest(@TempDir Path tempDir) {
        // This test should measure the performance of the uncertainty propagation
        // and the handling of large models with uncertainties.
        // It should create a large number of uncertainties and check the performance
        // of the system.
        // The actual implementation will depend on the specific requirements and
        // setup of the test environment.

        // Example: Create a large number of uncertainties and measure the time taken
        // to propagate changes.

        // Assertions can be added to check if the performance is within acceptable
        // limits.

        InternalVirtualModel vsumWithoutReactions = createDefaultVirtualModelWithoutReactions(tempDir);
        // Registers a Brakesystem and UncertaintyAnnotationRepository
        registerRootObjects(vsumWithoutReactions, tempDir);
        UncertaintyTestUtil.addBrakeDiscWithDiameter(vsumWithoutReactions, tempDir, 120);

        long startTime = System.nanoTime();

        CommittableView view1 = UncertaintyTestUtil.getDefaultView(vsumWithoutReactions,
                List.of(Brakesystem.class, UncertaintyAnnotationRepository.class))
                .withChangeDerivingTrait();
        modifyView(view1, (CommittableView v) -> {
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
        logger.info("Execution time for uncertainty creation NO propagation: {} ms", durationMs);

        // Assert that only one uncertainty was created
        View assertionView1 = UncertaintyTestUtil.getDefaultView(vsumWithoutReactions,
                List.of(UncertaintyAnnotationRepository.class));
        assertView(assertionView1, (View v) -> {
            List<Uncertainty> uncertainties = UncertaintyTestUtil.getBrakeDiskUncertainties(v);
            return uncertainties.size() == 1;
        });

        // SECOND PART: Now we test the same scenario, but with change propagation
        // enabled.
        // This should lead to the creation of two uncertainties, one for the original
        // uncertainty and one for the propagated change.
        // We will measure the time taken for this operation as well.
        // Create a new VirtualModel with change propagation enabled
        // and register the same root objects again.

        InternalVirtualModel vsum = UncertaintyTestUtil.createDefaultVirtualModel(tempDir);
        // Registers a Brakesystem and UncertaintyAnnotationRepository

        startTime = System.nanoTime();

        CommittableView view2 = UncertaintyTestUtil.getDefaultView(vsum,
                List.of(Brakesystem.class, UncertaintyAnnotationRepository.class))
                .withChangeDerivingTrait();
        modifyView(view2, (CommittableView v) -> {
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

        endTime = System.nanoTime();
        durationMs = (endTime - startTime) / 1_000_000;
        logger.info("Execution time for uncertainty creation WITH propagation: {} ms", durationMs);

        // Assert that two uncertainties were created
        View assertionView2 = UncertaintyTestUtil.getDefaultView(vsum,
                List.of(UncertaintyAnnotationRepository.class));
        assertView(assertionView2, (View v) -> {
            List<Uncertainty> uncertainties = UncertaintyTestUtil.getBrakeDiskUncertainties(v);
            return uncertainties.size() == 2;
        });

    }

    public InternalVirtualModel createDefaultVirtualModelWithoutReactions(Path projectPath) {
        InternalVirtualModel model = new VirtualModelBuilder()
                .withStorageFolder(projectPath)
                .withUserInteractorForResultProvider(
                        new TestUserInteraction.ResultProvider(new TestUserInteraction()))
                .buildAndInitialize();
        model.setChangePropagationMode(ChangePropagationMode.TRANSITIVE_CYCLIC);
        return model;
    }

    public void registerRootObjects(VirtualModel virtualModel, Path filePath) {
        CommittableView view = UncertaintyTestUtil.getDefaultView(virtualModel,
                List.of(Brakesystem.class, UncertaintyAnnotationRepository.class))
                .withChangeDerivingTrait();
        modifyView(view, (CommittableView v) -> {
            v.registerRoot(
                    UncertaintyFactory.eINSTANCE
                            .createUncertaintyAnnotationRepository(),
                    org.eclipse.emf.common.util.URI
                            .createFileURI(filePath.toString() + "/uncertainty.model"));

            v.registerRoot(
                    BrakesystemFactory.eINSTANCE.createBrakesystem(),
                    URI.createFileURI(filePath.toString() + "/brakesystem.model"));
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
