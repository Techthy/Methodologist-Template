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

public class PropagateToSingeCorrespondingEntityTest {

	private static final Logger logger = org.slf4j.LoggerFactory
			.getLogger(PropagateToSingeCorrespondingEntityTest.class);

	@BeforeAll
	static void setup() {
		Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put("*",
				new XMIResourceFactoryImpl());

	}

	@Test
	void createUncertaintyManuallyPropagateToSingleCorrespondingEntity(@TempDir Path tempDir) {

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
			var brakeDisk = v.getRootObjects(Brakesystem.class).iterator().next().getBrakeComponents()
					.stream()
					.filter(BrakeDisk.class::isInstance).map(BrakeDisk.class::cast)
					.findFirst().orElseThrow();

			var uncertainty = createUncertainty("Diameter", brakeDisk);

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
									.getReferencesComponents()
									.get(0);
							return s == 2 && ref instanceof Circle;

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
