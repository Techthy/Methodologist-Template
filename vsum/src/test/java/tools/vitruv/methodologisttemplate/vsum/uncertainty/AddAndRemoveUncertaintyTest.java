package tools.vitruv.methodologisttemplate.vsum.uncertainty;

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

public class AddAndRemoveUncertaintyTest {

	private static final Logger logger = org.slf4j.LoggerFactory
			.getLogger(AddAndRemoveUncertaintyTest.class);

	@BeforeAll
	static void setup() {
		Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put("*",
				new XMIResourceFactoryImpl());

	}

	// Plan of the test:
	// A BrakeDisk is manually added to the model
	// The reaction creates a corresponding Circle
	// The Uncertainty and UncertaintyLocation referencing the BrakeDisk are added
	// manually
	// A Uncertainty and UncertaintyLocation referencing the Circle are created by
	// the reaction
	// The Uncertainty which location is referencing the BrakeDisk is deleted
	// The reaction deletes the Uncertainty which location is referencing the Circle

	@Test
	void addUncertaintyAndRemove(@TempDir Path tempDir) {
		logger.debug("=== Starting test ===");
		VirtualModel vsum = UncertaintyTestUtil.createDefaultVirtualModel(tempDir);
		// Registers a Brakesystem, CADRepository and UncertaintyAnnotationRepository
		UncertaintyTestUtil.registerRootObjects(vsum, tempDir);

		// Add a BrakeDisk that in turn (by reactions) creates a Circle
		UncertaintyTestUtil.addBrakeDiscWithDiameter(vsum, tempDir, 120);

		// Add two uncertainties to the brake disk
		CommittableView brakeAndUncertaintyView = UncertaintyTestUtil.getDefaultView(vsum,
				List.of(UncertaintyAnnotationRepository.class, Brakesystem.class))
				.withChangeDerivingTrait();
		modifyView(brakeAndUncertaintyView, (CommittableView v) -> {
			var brakeDisk = v.getRootObjects(Brakesystem.class).iterator().next().getBrakeComponents()
					.stream()
					.filter(BrakeDisk.class::isInstance).map(BrakeDisk.class::cast)
					.filter(d -> d.getDiameterInMM() == 120)
					.findFirst().orElseThrow();

			var firstUncertainty = createUncertainty("FromDisk1", brakeDisk, UncertaintyKind.MEASUREMENT_UNCERTAINTY);
			var secondUncertainty = createUncertainty("FromDisk2", brakeDisk, UncertaintyKind.OCCURENCE_UNCERTAINTY);

			// Trigger propagation
			brakeDisk.setSpecificationType("propagationTest");

			v.getRootObjects(UncertaintyAnnotationRepository.class).iterator().next().getUncertainties()
					.add(firstUncertainty);
			v.getRootObjects(UncertaintyAnnotationRepository.class).iterator().next().getUncertainties()
					.add(secondUncertainty);
		});

		// Assert that four uncertainties were created
		// two belonging to the brake disk (manually) and two belonging to the circle
		// (by reaction) exist
		Assertions.assertTrue(
				assertView(UncertaintyTestUtil.getDefaultView(vsum, List.of(UncertaintyAnnotationRepository.class)),
						(View v) -> {
							var brakeDiskUncertainties = UncertaintyTestUtil.getBrakeDiskUncertainties(v);
							var circleUncertainties = UncertaintyTestUtil.getCircleUncertainties(v);
							logger.debug("brakeDiskUncertainties: " + brakeDiskUncertainties);
							logger.debug("circleUncertainties: " + circleUncertainties);

							return brakeDiskUncertainties.size() == 2 && circleUncertainties.size() == 2;
						}));

		// Delete any of the two uncertainties belonging to the brake disk
		// The reaction should delete the corresponding uncertainty belonging to the
		// circle
		// and their respective locations
		deleteBrakeDiskUncertainty(vsum);

		// Assert: There should be only two uncertainties left,
		// One belonging to the BrakeDisk the other to the Circle
		// Both the brake disk and the circle should be still present
		Assertions.assertTrue(assertView(UncertaintyTestUtil.getDefaultView(vsum,
				List.of(UncertaintyAnnotationRepository.class, Brakesystem.class, CADRepository.class)),
				(View v) -> {
					var brakeDiskUncertainties = UncertaintyTestUtil.getBrakeDiskUncertainties(v);
					var circleUncertainties = UncertaintyTestUtil.getCircleUncertainties(v);

					long brakeComponentCount = v.getRootObjects(Brakesystem.class).iterator().next()
							.getBrakeComponents().size();
					long CADElementCount = v.getRootObjects(CADRepository.class).iterator().next()
							.getCadElements().size();

					return circleUncertainties.size() == 1 && CADElementCount == 1
							&& brakeDiskUncertainties.size() == 1 && brakeComponentCount == 1;
				}));

		// Delete the remaining uncertainty belonging to the brake disk
		deleteBrakeDiskUncertainty(vsum);

		// FINAL ASSERTION: No uncertainties should exist, but the cirlce and the brake
		// disk
		// Assert: There should be only two uncertainties left, one for the BrakeDisk
		// and one for the Circle
		// Both the brake disk and the circle should be still present
		Assertions.assertTrue(assertView(UncertaintyTestUtil.getDefaultView(vsum,
				List.of(UncertaintyAnnotationRepository.class, Brakesystem.class, CADRepository.class)),
				(View v) -> {
					long uncertainties = v.getRootObjects(UncertaintyAnnotationRepository.class)
							.iterator().next()
							.getUncertainties().size();

					long brakeComponents = v.getRootObjects(Brakesystem.class).iterator().next()
							.getBrakeComponents().size();
					logger.debug("brakeComponents: " + brakeComponents);

					long CADElements = v.getRootObjects(CADRepository.class).iterator().next()
							.getCadElements().size();
					logger.debug("CADElements: " + CADElements);

					return brakeComponents == 1 && CADElements == 1 && uncertainties == 0;
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

	private void deleteBrakeDiskUncertainty(VirtualModel vsum) {
		modifyView(UncertaintyTestUtil
				.getDefaultView(vsum, List.of(UncertaintyAnnotationRepository.class, Brakesystem.class))
				.withChangeDerivingTrait(), (CommittableView v) -> {

					var uncertainties = v.getRootObjects(UncertaintyAnnotationRepository.class)
							.iterator().next()
							.getUncertainties();
					var uncertaintyToDelete = uncertainties.stream()
							.filter(u -> u.getUncertaintyLocation()
									.getReferencesComponents().stream()
									.anyMatch(c -> c instanceof BrakeDisk))
							.findFirst().orElseThrow();
					logger.debug("Deleting uncertainty: " + uncertaintyToDelete);

					v.getRootObjects(UncertaintyAnnotationRepository.class)
							.iterator().next()
							.getUncertainties().remove(uncertaintyToDelete);
					EcoreUtil.delete(uncertaintyToDelete, true);

					// Trigger propagation
					v.getRootObjects(Brakesystem.class).iterator().next()
							.getBrakeComponents().get(0).setSpecificationType(EcoreUtil.generateUUID());
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
