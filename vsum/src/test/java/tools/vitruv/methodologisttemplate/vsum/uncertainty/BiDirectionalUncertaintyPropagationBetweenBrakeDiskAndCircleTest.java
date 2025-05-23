package tools.vitruv.methodologisttemplate.vsum.uncertainty;

import java.nio.file.Path;
import java.util.List;
import java.util.Random;
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
import uncertainty.ReducabilityLevel;
import uncertainty.Uncertainty;
import uncertainty.UncertaintyAnnotationRepository;
import uncertainty.UncertaintyFactory;
import uncertainty.UncertaintyKind;
import uncertainty.UncertaintyLocationType;
import uncertainty.UncertaintyNature;

public class BiDirectionalUncertaintyPropagationBetweenBrakeDiskAndCircleTest {
	private static final Logger logger = org.slf4j.LoggerFactory
			.getLogger(BiDirectionalUncertaintyPropagationBetweenBrakeDiskAndCircleTest.class);

	@BeforeAll
	static void setup() {
		Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put("*",
				new XMIResourceFactoryImpl());

	}

	// Plan of the test:
	// Add brake disk and circle
	// Add uncertainty to the circle this should propagate to the brake disk
	// Add uncertainty to the brake disk this should propagate to the circle
	// Assert that both uncertainties are propagated and created a corresponding
	// uncertainty
	@Test
	void biDirectionalUncertaintyPropagationBetweenBrakeDiskAndCircleSameUncertaintyNotAutomaticallyCreated(
			@TempDir Path tempDir) {
		VirtualModel vsum = UncertaintyTestUtil.createDefaultVirtualModel(tempDir);
		// Registers a Brakesystem, CADRepository and UncertaintyAnnotationRepository
		UncertaintyTestUtil.registerRootObjects(vsum, tempDir);

		// Add BrakeDisk
		UncertaintyTestUtil.addBrakeDiscWithDiameter(vsum, tempDir, 120);

		// STEP 1: Add Uncertainty to the Circle (should propagate to BrakeDisk)
		CommittableView uncertaintyCADView = UncertaintyTestUtil.getDefaultView(vsum,
				List.of(UncertaintyAnnotationRepository.class, CADRepository.class))
				.withChangeDerivingTrait();
		modifyView(uncertaintyCADView, (CommittableView v) -> {
			var circle = v.getRootObjects(CADRepository.class).iterator().next().getCadElements().stream()
					.filter(Circle.class::isInstance).map(Circle.class::cast)
					.filter(c -> c.getRadius() == 60)
					.findFirst().orElseThrow();

			var uncertainty = createUncertainty("FromCircle", circle, UncertaintyKind.OCCURENCE_UNCERTAINTY);

			// Make sure something changes to trigger propagation
			circle.setIdentifier(generateRandomString());

			v.getRootObjects(UncertaintyAnnotationRepository.class).iterator().next()
					.getUncertainties().add(uncertainty);
		});

		// STEP 2: Add a different Uncertainty to the BrakeDisk (should propagate to
		// Circle)
		CommittableView uncertaintyBrakesystemView = UncertaintyTestUtil.getDefaultView(vsum,
				List.of(UncertaintyAnnotationRepository.class, Brakesystem.class))
				.withChangeDerivingTrait();
		modifyView(uncertaintyBrakesystemView, (CommittableView v) -> {
			var brakeDisk = v.getRootObjects(Brakesystem.class).iterator().next().getBrakeComponents()
					.stream()
					.filter(BrakeDisk.class::isInstance).map(BrakeDisk.class::cast)
					.filter(d -> d.getDiameterInMM() == 120)
					.findFirst().orElseThrow();

			var uncertainty = createUncertainty("FromDisk", brakeDisk, UncertaintyKind.MEASUREMENT_UNCERTAINTY);

			// Trigger propagation
			brakeDisk.setSpecificationType(generateRandomString());

			v.getRootObjects(UncertaintyAnnotationRepository.class).iterator().next().getUncertainties()
					.add(uncertainty);
		});

		// FINAL ASSERTION: Each of the 120mm BrakeDisk and 60-radius Circle has 2
		// uncertainties
		Assertions.assertTrue(
				assertView(UncertaintyTestUtil.getDefaultView(vsum, List.of(UncertaintyAnnotationRepository.class)),
						(View v) -> {

							var circleUncertainties = UncertaintyTestUtil.getCircleUncertainties(v);
							var brakeDiskUncertainties = UncertaintyTestUtil.getBrakeDiskUncertainties(v);

							long brakeUncertaintiesCount = brakeDiskUncertainties.stream()
									.filter(u -> u.getUncertaintyLocation()
											.getReferencesComponents()
											.stream().anyMatch(c -> ((BrakeDisk) c).getDiameterInMM() == 120))
									.count();

							long circleUncertaintiesCount = circleUncertainties.stream()
									.filter(u -> u.getUncertaintyLocation()
											.getReferencesComponents()
											.stream()
											.anyMatch(c -> ((Circle) c).getRadius() == 60))
									.count();

							boolean fromCirclePresent = brakeDiskUncertainties.stream()
									.anyMatch(u -> u.getUncertaintyLocation().getSpecification()
											.contains("FromCircle"));

							boolean fromDiskPresent = circleUncertainties.stream()
									.anyMatch(u -> u.getUncertaintyLocation().getSpecification()
											.contains("FromDisk"));

							return brakeUncertaintiesCount == 2 && circleUncertaintiesCount == 2
									&& fromCirclePresent && fromDiskPresent;
						}));

	}

	private Uncertainty createUncertainty(String uncertaintyLocationSpecification, EObject object,
			UncertaintyKind kind) {
		var uncertaintyLocation = UncertaintyFactory.eINSTANCE.createUncertaintyLocation();
		uncertaintyLocation.setLocation(UncertaintyLocationType.OUTCOME);
		uncertaintyLocation.setSpecification(uncertaintyLocationSpecification);
		uncertaintyLocation.getReferencesComponents().add(object);

		var uncertainty = UncertaintyFactory.eINSTANCE.createUncertainty();
		uncertainty.setUncertaintyLocation(uncertaintyLocation);
		uncertainty.setKind(kind);
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

	private static String generateRandomString() {
		String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
		int length = 5;

		Random random = new Random();
		StringBuilder sb = new StringBuilder(length);

		for (int i = 0; i < length; i++) {
			sb.append(characters.charAt(random.nextInt(characters.length())));
		}
		System.out.println("Random String: " + sb.toString());
		return sb.toString();
	}
}
