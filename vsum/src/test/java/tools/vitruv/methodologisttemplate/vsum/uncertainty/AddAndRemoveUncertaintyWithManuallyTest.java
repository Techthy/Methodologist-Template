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

public class AddAndRemoveUncertaintyWithManuallyTest {
	private static final Logger logger = org.slf4j.LoggerFactory
			.getLogger(AddAndRemoveUncertaintyWithManuallyTest.class);

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
	// The Uncertainty referencing the Circle is created by the reaction
	// (setManually is therefore false)
	// This Uncertainty is then edited and the setManually flag is set to true
	// The Uncertainty referencing the BrakeDisk is deleted
	// The Uncertainty referencing the Circle is NOT deleted (since it was modified
	// manually)

	@Test
	void addAndRemoveUncertaintyWithManualEdit(@TempDir Path tempDir) {

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

			var uncertainty = createUncertainty("FromDisk", brakeDisk);

			v.getRootObjects(UncertaintyAnnotationRepository.class).iterator().next()
					.getUncertainties().add(uncertainty);

			// Trigger propagation
			brakeDisk.setSpecificationType(EcoreUtil.generateUUID());

		});

		// Assert that the uncertainty belonging to the brake disk exists and is set
		// manually
		// Assert that the uncertainty belonging to the circle exists and is NOT set
		// manually
		Assertions.assertTrue(
				assertView(UncertaintyTestUtil.getDefaultView(vsum, List.of(UncertaintyAnnotationRepository.class)),
						(View v) -> {
							List<Uncertainty> brakeDiskUncertainties = UncertaintyTestUtil.getBrakeDiskUncertainties(v);
							List<Uncertainty> circleUncertainties = UncertaintyTestUtil.getCircleUncertainties(v);
							Uncertainty brakeDiskUncertainty = brakeDiskUncertainties.get(0);
							Uncertainty circleUncertainty = circleUncertainties.get(0);

							return circleUncertainties.size() == 1 && !circleUncertainty.isSetManually()
									&& brakeDiskUncertainties.size() == 1 && brakeDiskUncertainty.isSetManually();
						}));

		// Manually edit the circle Uncertainty
		modifyView(UncertaintyTestUtil.getDefaultView(vsum,
				List.of(UncertaintyAnnotationRepository.class))
				.withChangeDerivingTrait(), (CommittableView v) -> {
					var circleUncertainty = v.getRootObjects(UncertaintyAnnotationRepository.class).iterator()
							.next()
							.getUncertainties().stream()
							.filter(u -> u.getUncertaintyLocation().getReferencesComponents().stream()
									.anyMatch(c -> c instanceof Circle
											&& ((Circle) c).getRadius() == 60))
							.findFirst().orElseThrow();

					circleUncertainty.setKind(UncertaintyKind.BELIEF_UNCERTAINTY);
					circleUncertainty.setSetManually(true);
					circleUncertainty.getUncertaintyLocation().setSpecification("FromCircle");

				});

		// Assert that all uncertainties are set manually
		Assertions.assertTrue(
				assertView(UncertaintyTestUtil.getDefaultView(vsum, List.of(UncertaintyAnnotationRepository.class)),
						(View v) -> {
							var uncertainties = v.getRootObjects(
									UncertaintyAnnotationRepository.class)
									.iterator().next()
									.getUncertainties();
							logger.debug("Number of Uncertainties: "
									+ uncertainties.size());

							return uncertainties.stream()
									.allMatch(Uncertainty::isSetManually);
						}));

		// Delete the uncertainty belonging to the brake disk
		deleteBrakeDiskUncertainty(vsum);

		// Assert that the uncertainty belonging to the brake disk does not exist
		// Assert that the uncertainty belonging to the circle exists
		Assertions.assertTrue(
				assertView(UncertaintyTestUtil.getDefaultView(vsum, List.of(UncertaintyAnnotationRepository.class)),
						(View v) -> {
							var brakeDiskUncertainties = UncertaintyTestUtil.getBrakeDiskUncertainties(v);
							var circleUncertainties = UncertaintyTestUtil.getCircleUncertainties(v);

							return brakeDiskUncertainties.size() == 0 && circleUncertainties.size() == 1;
						}));

	}

	private Uncertainty createUncertainty(String uncertaintyLocationSpecification, EObject object) {
		var uncertaintyLocation = UncertaintyFactory.eINSTANCE.createUncertaintyLocation();
		uncertaintyLocation.setLocation(UncertaintyLocationType.OUTCOME);
		uncertaintyLocation.setSpecification(uncertaintyLocationSpecification);
		uncertaintyLocation.getReferencesComponents().add(object);

		var uncertainty = UncertaintyFactory.eINSTANCE.createUncertainty();
		uncertainty.setUncertaintyLocation(uncertaintyLocation);
		uncertainty.setKind(UncertaintyKind.MEASUREMENT_UNCERTAINTY);
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
