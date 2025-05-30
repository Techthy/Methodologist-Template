package tools.vitruv.methodologisttemplate.vsum.uncertainty;

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
import cad.CADRepository;
import cad.Circle;
import tools.vitruv.framework.views.CommittableView;
import tools.vitruv.framework.views.View;
import tools.vitruv.framework.vsum.VirtualModel;
import uncertainty.Uncertainty;
import uncertainty.UncertaintyAnnotationRepository;
import uncertainty.UncertaintyLocation;

public class PropagateUncertaintyOnlyIfNotExistsTest {
	private static final Logger logger = org.slf4j.LoggerFactory
			.getLogger(PropagateUncertaintyOnlyIfNotExistsTest.class);

	@BeforeAll
	static void setup() {
		Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put("*",
				new XMIResourceFactoryImpl());

	}

	// Plan of the test:
	// Add brake disk and circle
	// Add uncertainty to the circle this should propagate to the brake disk
	// Add (property wise) SAME uncertainty to the brake disk this should NOT
	// propagate to the circle
	// Assert that both uncertainties are propagated and created a corresponding
	// uncertainty

	@Test
	// biDirectionalUncertaintyPropagationBetweenBrakeDiskAndCircleSameUncertaintyNotAutomaticallyCreated
	void propagateUncertaintyOnlyIfNotExistsTest(
			@TempDir Path tempDir) {
		VirtualModel vsum = UncertaintyTestUtil.createDefaultVirtualModel(tempDir);
		UncertaintyTestUtil.registerRootObjects(vsum, tempDir);

		// Add BrakeDisk
		UncertaintyTestUtil.addBrakeDiscWithDiameter(vsum, tempDir, 120);

		// STEP 1: Add Uncertainty to the Circle (should propagate to BrakeDisk)
		CommittableView uncertaintyCADView = UncertaintyTestUtil.getDefaultView(vsum,
				List.of(UncertaintyAnnotationRepository.class, CADRepository.class))
				.withChangeDerivingTrait();
		modifyView(uncertaintyCADView, (CommittableView v) -> {
			Circle circle = v.getRootObjects(CADRepository.class).iterator().next().getCadElements().stream()
					.filter(Circle.class::isInstance).map(Circle.class::cast)
					.filter(c -> c.getRadius() == 60)
					.findFirst().orElseThrow();

			UncertaintyLocation uncertaintyLocation = UncertaintyTestFactory.createUncertaintyLocation(List.of(circle));
			uncertaintyLocation.setSpecification("FromCircle");
			Uncertainty uncertainty = UncertaintyTestFactory.createUncertainty(Optional.of(uncertaintyLocation));

			// Make sure something changes to trigger propagation
			circle.setIdentifier(EcoreUtil.generateUUID());

			v.getRootObjects(UncertaintyAnnotationRepository.class).iterator().next().getUncertainties()
					.add(uncertainty);
		});

		// STEP 2: Add a different Uncertainty to the BrakeDisk (should propagate to
		// Circle)
		CommittableView view2 = UncertaintyTestUtil.getDefaultView(vsum,
				List.of(UncertaintyAnnotationRepository.class, Brakesystem.class))
				.withChangeDerivingTrait();
		modifyView(view2, (CommittableView v) -> {
			BrakeDisk brakeDisk = v.getRootObjects(Brakesystem.class).iterator().next().getBrakeComponents()
					.stream()
					.filter(BrakeDisk.class::isInstance).map(BrakeDisk.class::cast)
					.filter(d -> d.getDiameterInMM() == 120)
					.findFirst().orElseThrow();

			UncertaintyLocation uncertaintyLocation = UncertaintyTestFactory
					.createUncertaintyLocation(List.of(brakeDisk));
			uncertaintyLocation.setSpecification("FromDisk");
			Uncertainty uncertainty = UncertaintyTestFactory.createUncertainty(Optional.of(uncertaintyLocation));

			// Trigger propagation
			brakeDisk.setSpecificationType(EcoreUtil.generateUUID());

			v.getRootObjects(UncertaintyAnnotationRepository.class).iterator().next().getUncertainties()
					.add(uncertainty);
		});

		// FINAL ASSERTION: The brakedisc has two uncertainties, one automatically
		// added, one manually added.
		// The circle has only one, as the parameters are equal => No new uncertainty
		// added automatically.
		// Attention: uncertaintyLocation is currently not part of the equal check.
		// uncertainties
		Assertions.assertTrue(
				assertView(UncertaintyTestUtil.getDefaultView(vsum, List.of(UncertaintyAnnotationRepository.class)),
						(View v) -> {
							List<Uncertainty> brakeDiskUncertainties = UncertaintyTestUtil.getBrakeDiskUncertainties(v);
							List<Uncertainty> circleUncertainties = UncertaintyTestUtil.getCircleUncertainties(v);

							boolean fromCirclePresent = brakeDiskUncertainties.stream()
									.anyMatch(u -> u.getUncertaintyLocation().getSpecification()
											.contains("FromCircle"));

							boolean fromDiskPresent = circleUncertainties.stream()
									.noneMatch(u -> u.getUncertaintyLocation().getSpecification()
											.contains("FromDisk"));

							return brakeDiskUncertainties.size() == 2 && circleUncertainties.size() == 1
									&& fromCirclePresent && fromDiskPresent;
						}));
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
