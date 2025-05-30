package tools.vitruv.methodologisttemplate.vsum.uncertaintyPerformance;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;

import brakesystem.BrakeDisk;
import brakesystem.Brakesystem;
import brakesystem.BrakesystemFactory;
import cad.CADRepository;
import cad.Circle;
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

	@BeforeAll
	static void setup() {
		Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put("*",
				new XMIResourceFactoryImpl());
	}

	@Test
	void performanceTestOneBrakeDisk(@TempDir Path tempDir) {
		// This test should measure the performance of the uncertainty creation without
		// change propagation.
		// It should create a single uncertainty and check the performance of the
		// system.

		InternalVirtualModel vsum = createDefaultVirtualModelWithoutReactions(tempDir);
		// Registers a Brakesystem and UncertaintyAnnotationRepository
		UncertaintyTestUtil.registerRootObjects(vsum, tempDir);

		long startTime = System.nanoTime();
		UncertaintyTestUtil.addBrakeDiscWithDiameter(vsum, tempDir, 120);

		long endTime = System.nanoTime();
		long durationMs = (endTime - startTime) / 1_000_000;
		logger.info("[1a] Execution time for single brake disk: {} ms", durationMs);

		// Assert that only one uncertainty was created
		View assertionView = UncertaintyTestUtil.getDefaultView(vsum,
				List.of(Brakesystem.class, CADRepository.class));
		Assertions.assertTrue(assertView(assertionView, (View v) -> {
			List<BrakeDisk> brakeDisks = v.getRootObjects(Brakesystem.class).iterator().next()
					.getBrakeComponents().stream().filter(BrakeDisk.class::isInstance)
					.map(BrakeDisk.class::cast).toList();
			List<Circle> circles = v.getRootObjects(CADRepository.class).iterator().next()
					.getCadElements().stream().filter(Circle.class::isInstance).map(Circle.class::cast).toList();
			return circles.size() == 1 && brakeDisks.size() == 1;
		}));
	}

	@Test
	void performanceTestOneBrakeDiskWithUncertainty(@TempDir Path tempDir) {
		// This test should measure the performance of the uncertainty creation with
		// change propagation.
		// It should create a single uncertainty and check the performance of the
		// system.

		InternalVirtualModel vsum = createDefaultVirtualModelWithReactions(tempDir);
		// Registers a Brakesystem and UncertaintyAnnotationRepository
		UncertaintyTestUtil.registerRootObjects(vsum, tempDir);

		long startTime = System.nanoTime();

		CommittableView view = UncertaintyTestUtil.getDefaultView(vsum,
				List.of(Brakesystem.class, UncertaintyAnnotationRepository.class))
				.withChangeDerivingTrait();
		modifyView(view, (CommittableView v) -> {
			var brakeDisk = BrakesystemFactory.eINSTANCE.createBrakeDisk();
			brakeDisk.setDiameterInMM(120);
			v.getRootObjects(Brakesystem.class).iterator().next().getBrakeComponents().add(brakeDisk);

			UncertaintyLocation uncertaintyLocation = UncertaintyTestFactory
					.createUncertaintyLocation(List.of(brakeDisk));
			Uncertainty uncertainty = UncertaintyTestFactory
					.createUncertainty(Optional.of(uncertaintyLocation));

			v.getRootObjects(UncertaintyAnnotationRepository.class).iterator().next()
					.getUncertainties().add(uncertainty);
		});

		long endTime = System.nanoTime();
		long durationMs = (endTime - startTime) / 1_000_000;
		logger.info("[1b] Execution time for single brake disk with uncertainty: {} ms", durationMs);

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
	void performanceTest10BrakeDisk(@TempDir Path tempDir) {
		// This test should measure the performance of the uncertainty creation without
		// change propagation.
		// It should create a single uncertainty and check the performance of the
		// system.

		InternalVirtualModel vsum = createDefaultVirtualModelWithoutReactions(tempDir);
		// Registers a Brakesystem and UncertaintyAnnotationRepository
		UncertaintyTestUtil.registerRootObjects(vsum, tempDir);

		long startTime = System.nanoTime();
		for (int i = 0; i < 10; i++) {
			// Add a brake disk with a diameter of 120 + i * 10
			UncertaintyTestUtil.addBrakeDiscWithDiameter(vsum, tempDir, 120 + i * 10);
		}

		long endTime = System.nanoTime();
		long durationMs = (endTime - startTime) / 1_000_000;
		logger.info("[2a] Execution time for creation of 10 brake disks: {} ms", durationMs);

		// Assert that only one uncertainty was created
		View assertionView = UncertaintyTestUtil.getDefaultView(vsum,
				List.of(Brakesystem.class, CADRepository.class));
		Assertions.assertTrue(assertView(assertionView, (View v) -> {
			List<BrakeDisk> brakeDisks = v.getRootObjects(Brakesystem.class).iterator().next()
					.getBrakeComponents().stream().filter(BrakeDisk.class::isInstance)
					.map(BrakeDisk.class::cast).toList();
			List<Circle> circles = v.getRootObjects(CADRepository.class).iterator().next()
					.getCadElements().stream().filter(Circle.class::isInstance).map(Circle.class::cast).toList();
			return circles.size() == 10 && brakeDisks.size() == 10;
		}));
	}

	@Test
	void performanceTest10BrakeDiskWithUncertainty(@TempDir Path tempDir) {
		// This test should measure the performance of the uncertainty creation with
		// change propagation.
		// It should create a single uncertainty and check the performance of the
		// system.

		InternalVirtualModel vsum = createDefaultVirtualModelWithReactions(tempDir);
		// Registers a Brakesystem and UncertaintyAnnotationRepository
		UncertaintyTestUtil.registerRootObjects(vsum, tempDir);

		long startTime = System.nanoTime();

		CommittableView view = UncertaintyTestUtil.getDefaultView(vsum,
				List.of(Brakesystem.class, UncertaintyAnnotationRepository.class))
				.withChangeDerivingTrait();
		modifyView(view, (CommittableView v) -> {
			for (int i = 0; i < 10; i++) {
				// Add a brake disk with a diameter of 120 + i * 10
				var brakeDisk = BrakesystemFactory.eINSTANCE.createBrakeDisk();
				brakeDisk.setDiameterInMM(120 + i * 10);
				v.getRootObjects(Brakesystem.class).iterator().next().getBrakeComponents().add(brakeDisk);

				UncertaintyLocation uncertaintyLocation = UncertaintyTestFactory
						.createUncertaintyLocation(List.of(brakeDisk));
				Uncertainty uncertainty = UncertaintyTestFactory
						.createUncertainty(Optional.of(uncertaintyLocation));

				v.getRootObjects(UncertaintyAnnotationRepository.class).iterator().next()
						.getUncertainties().add(uncertainty);
			}

		});

		long endTime = System.nanoTime();
		long durationMs = (endTime - startTime) / 1_000_000;
		logger.info("[2b] Execution time for creation of 10 brake disks with uncertainty: {} ms", durationMs);

		// Assert that two uncertainties were created
		View assertionView = UncertaintyTestUtil.getDefaultView(vsum,
				List.of(UncertaintyAnnotationRepository.class));
		Assertions.assertTrue(assertView(assertionView, (View v) -> {
			List<Uncertainty> uncertainties = v.getRootObjects(UncertaintyAnnotationRepository.class).iterator().next()
					.getUncertainties();
			return uncertainties.size() == 20;
		}));
	}

	@Test
	void performanceTest100BrakeDisk(@TempDir Path tempDir) {
		// This test should measure the performance of the uncertainty creation without
		// change propagation.
		// It should create a single uncertainty and check the performance of the
		// system.

		InternalVirtualModel vsum = createDefaultVirtualModelWithoutReactions(tempDir);
		// Registers a Brakesystem and UncertaintyAnnotationRepository
		UncertaintyTestUtil.registerRootObjects(vsum, tempDir);

		long startTime = System.nanoTime();
		for (int i = 0; i < 100; i++) {
			// Add a brake disk with a diameter of 120 + i * 10
			UncertaintyTestUtil.addBrakeDiscWithDiameter(vsum, tempDir, 120 + i * 10);
		}

		long endTime = System.nanoTime();
		long durationMs = (endTime - startTime) / 1_000_000;
		logger.info("[2a] Execution time for creation of 10 brake disks: {} ms", durationMs);

		// Assert that only one uncertainty was created
		View assertionView = UncertaintyTestUtil.getDefaultView(vsum,
				List.of(Brakesystem.class, CADRepository.class));
		Assertions.assertTrue(assertView(assertionView, (View v) -> {
			List<BrakeDisk> brakeDisks = v.getRootObjects(Brakesystem.class).iterator().next()
					.getBrakeComponents().stream().filter(BrakeDisk.class::isInstance)
					.map(BrakeDisk.class::cast).toList();
			List<Circle> circles = v.getRootObjects(CADRepository.class).iterator().next()
					.getCadElements().stream().filter(Circle.class::isInstance).map(Circle.class::cast).toList();
			return circles.size() == 100 && brakeDisks.size() == 100;
		}));
	}

	@Test
	void performanceTest100BrakeDiskWithUncertainty(@TempDir Path tempDir) {
		// This test should measure the performance of the uncertainty creation with
		// change propagation.
		// It should create a single uncertainty and check the performance of the
		// system.

		InternalVirtualModel vsum = createDefaultVirtualModelWithReactions(tempDir);
		// Registers a Brakesystem and UncertaintyAnnotationRepository
		UncertaintyTestUtil.registerRootObjects(vsum, tempDir);

		long startTime = System.nanoTime();

		CommittableView view = UncertaintyTestUtil.getDefaultView(vsum,
				List.of(Brakesystem.class, UncertaintyAnnotationRepository.class))
				.withChangeDerivingTrait();
		modifyView(view, (CommittableView v) -> {
			for (int i = 0; i < 100; i++) {
				// Add a brake disk with a diameter of 120 + i * 10
				var brakeDisk = BrakesystemFactory.eINSTANCE.createBrakeDisk();
				brakeDisk.setDiameterInMM(120 + i * 10);
				v.getRootObjects(Brakesystem.class).iterator().next().getBrakeComponents().add(brakeDisk);

				UncertaintyLocation uncertaintyLocation = UncertaintyTestFactory
						.createUncertaintyLocation(List.of(brakeDisk));
				Uncertainty uncertainty = UncertaintyTestFactory
						.createUncertainty(Optional.of(uncertaintyLocation));

				v.getRootObjects(UncertaintyAnnotationRepository.class).iterator().next()
						.getUncertainties().add(uncertainty);
			}

		});

		long endTime = System.nanoTime();
		long durationMs = (endTime - startTime) / 1_000_000;
		logger.info("[2b] Execution time for creation of 10 brake disks with uncertainty: {} ms", durationMs);

		// Assert that two uncertainties were created
		View assertionView = UncertaintyTestUtil.getDefaultView(vsum,
				List.of(UncertaintyAnnotationRepository.class));
		Assertions.assertTrue(assertView(assertionView, (View v) -> {
			List<Uncertainty> uncertainties = v.getRootObjects(UncertaintyAnnotationRepository.class).iterator().next()
					.getUncertainties();
			return uncertainties.size() == 200;
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
