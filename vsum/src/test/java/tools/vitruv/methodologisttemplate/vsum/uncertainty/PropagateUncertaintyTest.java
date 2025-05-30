package tools.vitruv.methodologisttemplate.vsum.uncertainty;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;

import brakesystem.BrakeComponent;
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

public class PropagateUncertaintyTest {

	private static final Logger logger = org.slf4j.LoggerFactory
			.getLogger(PropagateUncertaintyTest.class);

	@BeforeAll
	static void setup() {
		Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put("*",
				new XMIResourceFactoryImpl());

	}

	// Plan of the test:
	// Add BrakeDisk and Circle (by reaction)
	// Add Uncertainty to BrakeDisk\
	// Asserts it propagates correctly to the Circle

	@Test
	// createUncertaintyManuallyPropagateToSingleCorrespondingEntity
	void propagateUncertaintyTest1(@TempDir Path tempDir) {

		VirtualModel vsum = UncertaintyTestUtil.createDefaultVirtualModel(tempDir);
		// Registers a Brakesystem, CADRepository and UncertaintyAnnotationRepository
		UncertaintyTestUtil.registerRootObjects(vsum, tempDir);
		UncertaintyTestUtil.addBrakeDiscWithDiameter(vsum, tempDir, 60);

		// Assert that the brake disk has a corresponding circle
		Assertions.assertTrue(
				assertView(UncertaintyTestUtil.getDefaultView(vsum, List.of(Brakesystem.class, CADRepository.class)),
						(View v) -> {
							BrakeDisk brakeDisk = (BrakeDisk) v
									.getRootObjects(Brakesystem.class)
									.iterator().next()
									.getBrakeComponents().get(0);
							Circle circle = (Circle) v.getRootObjects(
									CADRepository.class).iterator()
									.next()
									.getCadElements().get(0);
							return brakeDisk.getDiameterInMM() == circle
									.getRadius() * 2;
						}));

		// Add uncertainty to the brake disk which should by reaction create an
		// uncertainty referencing the circle
		CommittableView brakeAndUncertaintyView = UncertaintyTestUtil.getDefaultView(vsum,
				List.of(UncertaintyAnnotationRepository.class, Brakesystem.class))
				.withChangeDerivingTrait();
		modifyView(brakeAndUncertaintyView, (CommittableView v) -> {
			BrakeDisk brakeDisk = v.getRootObjects(Brakesystem.class).iterator().next().getBrakeComponents()
					.stream()
					.filter(BrakeDisk.class::isInstance).map(BrakeDisk.class::cast)
					.findFirst().orElseThrow();

			UncertaintyLocation uncertaintyLocation = UncertaintyTestFactory
					.createUncertaintyLocation(List.of(brakeDisk));
			Uncertainty uncertainty = UncertaintyTestFactory.createUncertainty(Optional.of(uncertaintyLocation));

			v.getRootObjects(UncertaintyAnnotationRepository.class).iterator().next()
					.getUncertainties().add(uncertainty);

			// Trigger propagation
			brakeDisk.setSpecificationType(EcoreUtil.generateUUID());

		});

		// Assert that there are two uncertainties in the
		// UncertaintyAnnotationRepository
		// Assert that one of them references the circle
		Assertions.assertTrue(
				assertView(UncertaintyTestUtil.getDefaultView(vsum,
						List.of(UncertaintyAnnotationRepository.class)),
						(View v) -> {

							int s = v.getRootObjects(
									UncertaintyAnnotationRepository.class)
									.iterator().next()
									.getUncertainties().size();

							Uncertainty circleUncertainty = v.getRootObjects(
									UncertaintyAnnotationRepository.class)
									.iterator().next()
									.getUncertainties().get(1);
							EObject ref = circleUncertainty
									.getUncertaintyLocation()
									.getReferencedComponents()
									.get(0);
							return s == 2 && ref instanceof Circle;

						}));

	}

	// Plan of the test:
	// Add two BrakeDisks with different diameters
	// Add uncertainty to one of them
	// Assert that the uncertainty propagates to the corresponding circle
	// Assert that the other circle is not affected

	@Test
	// createUncertaintyManuallyPropagateToSingleCorrespondingEntityNoOtherEntitiesAffected
	void propagateUncertaintyTest2(
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
							EList<BrakeComponent> brakeComponents = v.getRootObjects(
									Brakesystem.class).iterator()
									.next()
									.getBrakeComponents();
							List<BrakeDisk> brakeDiscs = brakeComponents.stream()
									.filter(BrakeDisk.class::isInstance)
									.map(BrakeDisk.class::cast)
									.toList();
							List<Circle> circles = v.getRootObjects(
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
					BrakeDisk targetDisk = v.getRootObjects(Brakesystem.class).iterator().next()
							.getBrakeComponents().stream().filter(BrakeDisk.class::isInstance)
							.map(BrakeDisk.class::cast)
							.filter(d -> d.getDiameterInMM() == 120)
							.findFirst().orElseThrow();

					UncertaintyLocation uncertaintyLocation = UncertaintyTestFactory.createUncertaintyLocation(
							List.of(targetDisk));
					uncertaintyLocation.setSpecification("120mm");
					Uncertainty uncertainty = UncertaintyTestFactory.createUncertainty(
							Optional.of(uncertaintyLocation));

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

							List<Uncertainty> brakeDiskUncertainties = UncertaintyTestUtil.getBrakeDiskUncertainties(v);
							List<Uncertainty> circleUncertainties = UncertaintyTestUtil.getCircleUncertainties(v);

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
