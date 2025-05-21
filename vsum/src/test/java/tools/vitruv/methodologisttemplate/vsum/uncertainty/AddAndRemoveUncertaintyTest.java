package tools.vitruv.methodologisttemplate.vsum.uncertainty;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Random;
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
import brakesystem.BrakesystemFactory;
import cad.CADRepository;
import cad.Circle;
import tools.vitruv.framework.views.CommittableView;
import tools.vitruv.framework.views.View;
import tools.vitruv.framework.views.ViewTypeFactory;
import tools.vitruv.framework.vsum.VirtualModel;
import uncertainty.ReducabilityLevel;
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
		VirtualModel vsum = UncertaintyTestUtil.createDefaultVirtualModel(tempDir);
		UncertaintyTestUtil.registerRootObjects(vsum, tempDir);

		// Add a BrakeDisk that in turn (by reactions) creates a Circle
		addBrakeDiscWithDiameter(vsum, tempDir, 120);

		// Assert: No uncertainties exist
		// This is a workaround since otherwise we encouter dangling references
		// The reason for this is unknown
		Assertions.assertTrue(
				assertView(getDefaultView(vsum, List.of(UncertaintyAnnotationRepository.class)),
						(View v) -> {
							return v.getRootObjects(UncertaintyAnnotationRepository.class)
									.iterator().next()
									.getUncertainties().isEmpty();
						}));

		// Add two uncertainties to the brake disk
		CommittableView view2 = getDefaultView(vsum,
				List.of(UncertaintyAnnotationRepository.class, Brakesystem.class))
				.withChangeDerivingTrait();
		modifyView(view2, (CommittableView v) -> {
			var brakeDisk = v.getRootObjects(Brakesystem.class).iterator().next().getBrakeComponents()
					.stream()
					.filter(BrakeDisk.class::isInstance).map(BrakeDisk.class::cast)
					.filter(d -> d.getDiameterInMM() == 120)
					.findFirst().orElseThrow();

			// First uncertainty
			var uncertaintyLocation = UncertaintyFactory.eINSTANCE.createUncertaintyLocation();
			uncertaintyLocation.setLocation(UncertaintyLocationType.OUTCOME);
			uncertaintyLocation.setSpecification("FromDisk1");
			uncertaintyLocation.getReferencesComponents().add(brakeDisk);

			var uncertainty = UncertaintyFactory.eINSTANCE.createUncertainty();
			uncertainty.setUncertaintyLocation(uncertaintyLocation);
			uncertainty.setKind(UncertaintyKind.MEASUREMENT_UNCERTAINTY);
			uncertainty.setReducability(ReducabilityLevel.UNKNOWN);
			uncertainty.setNature(UncertaintyNature.ALEATORY);
			uncertainty.setSetManually(true);
			uncertainty.setId(generateRandomString());

			// Second uncertainty
			var uncertaintyLocationTwo = UncertaintyFactory.eINSTANCE.createUncertaintyLocation();
			uncertaintyLocationTwo.setLocation(UncertaintyLocationType.OUTCOME);
			uncertaintyLocationTwo.setSpecification("FromDisk2");
			uncertaintyLocationTwo.getReferencesComponents().add(brakeDisk);

			var uncertaintyTwo = UncertaintyFactory.eINSTANCE.createUncertainty();
			uncertaintyTwo.setUncertaintyLocation(uncertaintyLocationTwo);
			uncertaintyTwo.setKind(UncertaintyKind.BELIEF_UNCERTAINTY);
			uncertaintyTwo.setReducability(ReducabilityLevel.UNKNOWN);
			uncertaintyTwo.setNature(UncertaintyNature.ALEATORY);
			uncertaintyTwo.setSetManually(true);
			uncertaintyTwo.setId(generateRandomString());

			// Trigger propagation
			brakeDisk.setSpecificationType(generateRandomString());

			v.getRootObjects(UncertaintyAnnotationRepository.class).iterator().next().getUncertainties()
					.add(uncertainty);
			v.getRootObjects(UncertaintyAnnotationRepository.class).iterator().next().getUncertainties()
					.add(uncertaintyTwo);
		});

		// Assert that four uncertainties exist;
		// two belonging to the brake disk and two belonging to the circle exist
		Assertions.assertTrue(
				assertView(getDefaultView(vsum, List.of(UncertaintyAnnotationRepository.class)),
						(View v) -> {
							var uncertainties = v.getRootObjects(
									UncertaintyAnnotationRepository.class)
									.iterator().next()
									.getUncertainties();
							logger.debug("Number of Uncertainties: "
									+ uncertainties.size());

							long brakeDiskUncertainties = uncertainties.stream()
									.filter(u -> u.getUncertaintyLocation()
											.getReferencesComponents()
											.stream()
											.anyMatch(c -> c instanceof BrakeDisk
													&& ((BrakeDisk) c)
															.getDiameterInMM() == 120))
									.count();
							logger.debug("brakeDiskUncertainties: "
									+ brakeDiskUncertainties);

							long circleUncertainties = uncertainties.stream()
									.filter(u -> u.getUncertaintyLocation()
											.getReferencesComponents()
											.stream()
											.anyMatch(c -> c instanceof Circle
													&& ((Circle) c).getRadius() == 60))
									.count();
							logger.debug(
									"circleUncertainties: " + circleUncertainties);
							return brakeDiskUncertainties == 2 && circleUncertainties == 2;
						}));

		// Delete the first uncertainty
		modifyView(getDefaultView(vsum, List.of(UncertaintyAnnotationRepository.class, Brakesystem.class))
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
					logger.debug("Uncertainty location: "
							+ uncertaintyToDelete.getUncertaintyLocation());

					v.getRootObjects(UncertaintyAnnotationRepository.class)
							.iterator().next()
							.getUncertainties().remove(uncertaintyToDelete);
					EcoreUtil.delete(uncertaintyToDelete, true);

					// Trigger propagation
					v.getRootObjects(Brakesystem.class).iterator().next()
							.getBrakeComponents().get(0).setSpecificationType("test");
				});

		// Assert: There should be only two uncertainties left, one for the BrakeDisk
		// and one for the Circle
		// Both the brake disk and the circle should be still present
		Assertions.assertTrue(assertView(getDefaultView(vsum,
				List.of(UncertaintyAnnotationRepository.class, Brakesystem.class, CADRepository.class)),
				(View v) -> {
					var uncertainties = v.getRootObjects(UncertaintyAnnotationRepository.class)
							.iterator().next()
							.getUncertainties();

					long brakeDiskUncertainties = uncertainties.stream()
							.filter(u -> u.getUncertaintyLocation()
									.getReferencesComponents().stream()
									.anyMatch(c -> c instanceof BrakeDisk
											&& ((BrakeDisk) c)
													.getDiameterInMM() == 120))
							.count();
					System.out.println("brakeDiskUncertainties: " + brakeDiskUncertainties);

					long circleUncertainties = uncertainties.stream()
							.filter(u -> u.getUncertaintyLocation()
									.getReferencesComponents().stream()
									.anyMatch(c -> c instanceof Circle
											&& ((Circle) c).getRadius() == 60))
							.count();
					System.out.println("circleUncertainties: " + circleUncertainties);

					long brakeComponents = v.getRootObjects(Brakesystem.class).iterator().next()
							.getBrakeComponents().size();
					System.out.println("brakeComponents: " + brakeComponents);

					long CADElements = v.getRootObjects(CADRepository.class).iterator().next()
							.getCadElements().size();
					System.out.println("CADElements: " + CADElements);

					return circleUncertainties == 1 && brakeDiskUncertainties == 1
							&& brakeComponents == 1 && CADElements == 1;
				}));

		// Delete the second uncertainty
		modifyView(getDefaultView(vsum, List.of(UncertaintyAnnotationRepository.class, Brakesystem.class))
				.withChangeDerivingTrait(), (CommittableView v) -> {

					var uncertainties = v.getRootObjects(UncertaintyAnnotationRepository.class)
							.iterator().next()
							.getUncertainties();

					var uncertaintyToDelete = uncertainties.stream()
							.filter(u -> u.getUncertaintyLocation().getSpecification()
									.equals("FromDisk2"));

					EcoreUtil.delete(uncertaintyToDelete.findAny().get(), true);

					// Trigger propagation
					v.getRootObjects(Brakesystem.class).iterator().next()
							.getBrakeComponents().get(0).setSpecificationType("test1");
				});

		// FINAL ASSERTION: No uncertainties should exist, but the cirlce and the brake
		// disk
		// Assert: There should be only two uncertainties left, one for the BrakeDisk
		// and one for the Circle
		// Both the brake disk and the circle should be still present
		Assertions.assertTrue(assertView(getDefaultView(vsum,
				List.of(UncertaintyAnnotationRepository.class, Brakesystem.class, CADRepository.class)),
				(View v) -> {
					long uncertainties = v.getRootObjects(UncertaintyAnnotationRepository.class)
							.iterator().next()
							.getUncertainties().size();

					long brakeComponents = v.getRootObjects(Brakesystem.class).iterator().next()
							.getBrakeComponents().size();
					System.out.println("brakeComponents: " + brakeComponents);

					long CADElements = v.getRootObjects(CADRepository.class).iterator().next()
							.getCadElements().size();
					System.out.println("CADElements: " + CADElements);

					return brakeComponents == 1 && CADElements == 1 && uncertainties == 0;
				}));

	}

	private void addBrakeDiscWithDiameter(VirtualModel vsum, Path projectPath, int diameter) {
		CommittableView view = getDefaultView(vsum, List.of(Brakesystem.class))
				.withChangeDerivingTrait();
		modifyView(view, (CommittableView v) -> {
			var brakeDisc = BrakesystemFactory.eINSTANCE.createBrakeDisk();
			brakeDisc.setDiameterInMM(diameter);
			v.getRootObjects(Brakesystem.class).iterator().next().getBrakeComponents().add(brakeDisc);
		});
	}

	// See https://github.com/vitruv-tools/Vitruv/issues/717 for more information
	// about the rootTypes
	private View getDefaultView(VirtualModel vsum, Collection<Class<?>> rootTypes) {
		var selector = vsum.createSelector(ViewTypeFactory.createIdentityMappingViewType("default"));
		selector.getSelectableElements().stream()
				.filter(element -> rootTypes.stream().anyMatch(it -> it.isInstance(element)))
				.forEach(it -> selector.setSelected(it, true));
		return selector.createView();
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
