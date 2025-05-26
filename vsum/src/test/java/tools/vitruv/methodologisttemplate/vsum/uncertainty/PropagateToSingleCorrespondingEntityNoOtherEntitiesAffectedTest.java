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

public class PropagateToSingleCorrespondingEntityNoOtherEntitiesAffectedTest {

	private static final Logger logger = org.slf4j.LoggerFactory
			.getLogger(PropagateToSingleCorrespondingEntityNoOtherEntitiesAffectedTest.class);

	@BeforeAll
	static void setup() {
		Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put("*",
				new XMIResourceFactoryImpl());

	}

	@Test
	void createUncertaintyManuallyPropagateToSingleCorrespondingEntityNoOtherEntitiesAffected(
			@TempDir Path tempDir) {

		// Create a new Virtual Model
		VirtualModel vsum = UncertaintyTestUtil.createDefaultVirtualModel(tempDir);
		// Registers a Brakesystem, CADRepository and UncertaintyAnnotationRepository
		UncertaintyTestUtil.registerRootObjects(vsum, tempDir);

		// Add two brake disks with different diameters
		UncertaintyTestUtil.addBrakeDiscWithDiameter(vsum, tempDir, 120);
		UncertaintyTestUtil.addBrakeDiscWithDiameter(vsum, tempDir, 50);

		// Assert that the the two brake disks and their corresponding circles exist
		Assertions.assertTrue(
				assertView(UncertaintyTestUtil.getDefaultView(vsum,
						List.of(Brakesystem.class, CADRepository.class)),
						(View v) -> {
							var brakeComponents = v.getRootObjects(
									Brakesystem.class).iterator()
									.next()
									.getBrakeComponents();
							var brakeDiscs = brakeComponents.stream()
									.filter(BrakeDisk.class::isInstance)
									.map(BrakeDisk.class::cast)
									.toList();
							var circles = v.getRootObjects(
									CADRepository.class).iterator()
									.next().getCadElements()
									.stream()
									.filter(Circle.class::isInstance)
									.map(Circle.class::cast)
									.toList();

							return brakeDiscs.size() == 2
									&& circles.size() == 2 &&
									brakeDiscs.stream().anyMatch(d -> d.getDiameterInMM() == 120)
									&& brakeDiscs.stream().anyMatch(d -> d.getDiameterInMM() == 50)
									&& circles.stream().anyMatch(c -> ((Circle) c)
											.getRadius() == 60)
									&& circles.stream().anyMatch(c -> ((Circle) c)
											.getRadius() == 25);
						}));

		// Add uncertainty only to the 120mm BrakeDisk
		modifyView(UncertaintyTestUtil.getDefaultView(vsum,
				List.of(UncertaintyAnnotationRepository.class, Brakesystem.class))
				.withChangeDerivingTrait(), (CommittableView v) -> {
					var targetDisk = v.getRootObjects(Brakesystem.class).iterator().next()
							.getBrakeComponents().stream().filter(BrakeDisk.class::isInstance)
							.map(BrakeDisk.class::cast)
							.filter(d -> d.getDiameterInMM() == 120)
							.findFirst().orElseThrow();

					var uncertainty = createUncertainty("120mm Disk", targetDisk);

					// Hack to make the change propagate
					targetDisk.setSpecificationType(EcoreUtil.generateUUID());

					v.getRootObjects(UncertaintyAnnotationRepository.class).iterator().next()
							.getUncertainties().add(uncertainty);
				});

		// Assert: Exactly two uncertainties exist (one manually added, one propagated)
		Assertions.assertTrue(
				assertView(UncertaintyTestUtil.getDefaultView(vsum, List.of(UncertaintyAnnotationRepository.class)),
						(View v) -> {
							return v.getRootObjects(UncertaintyAnnotationRepository.class)
									.iterator().next()
									.getUncertainties().size() == 2;
						}));

		// Assert: One uncertainty points to a BrakeDisk, and one to a Circle with
		// radius 60
		Assertions.assertTrue(
				assertView(UncertaintyTestUtil.getDefaultView(vsum, List.of(UncertaintyAnnotationRepository.class)),
						(View v) -> {

							var brakeDiskUncertainties = UncertaintyTestUtil.getBrakeDiskUncertainties(v);
							var circleUncertainties = UncertaintyTestUtil.getCircleUncertainties(v);

							boolean hasBrakeDisk120 = brakeDiskUncertainties.stream()
									.anyMatch(u -> u.getUncertaintyLocation()
											.getReferencedComponents()
											.stream()
											.anyMatch(c -> ((BrakeDisk) c).getDiameterInMM() == 120));

							boolean hasCircle60 = circleUncertainties.stream()
									.anyMatch(u -> u.getUncertaintyLocation()
											.getReferencedComponents()
											.stream()
											.anyMatch(c -> ((Circle) c).getRadius() == 60));

							boolean noCircle25 = circleUncertainties.stream()
									.noneMatch(u -> u.getUncertaintyLocation()
											.getReferencedComponents()
											.stream()
											.anyMatch(c -> ((Circle) c).getRadius() == 25));

							return hasBrakeDisk120 && hasCircle60 && noCircle25;
						}));

	}

	private Uncertainty createUncertainty(String uncertaintyLocationSpecification, EObject object) {
		var uncertaintyLocation = UncertaintyFactory.eINSTANCE.createUncertaintyLocation();
		uncertaintyLocation.setLocation(UncertaintyLocationType.OUTCOME);
		uncertaintyLocation.setSpecification(uncertaintyLocationSpecification);
		uncertaintyLocation.getReferencedComponents().add(object);

		var uncertainty = UncertaintyFactory.eINSTANCE.createUncertainty();
		uncertainty.setUncertaintyLocation(uncertaintyLocation);
		uncertainty.setKind(UncertaintyKind.MEASUREMENT_UNCERTAINTY);
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

}
