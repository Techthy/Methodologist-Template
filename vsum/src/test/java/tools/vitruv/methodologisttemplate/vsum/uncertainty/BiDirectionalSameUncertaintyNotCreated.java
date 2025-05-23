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

public class BiDirectionalSameUncertaintyNotCreated {
	private static final Logger logger = org.slf4j.LoggerFactory
			.getLogger(BiDirectionalSameUncertaintyNotCreated.class);

	@BeforeAll
	static void setup() {
		Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put("*",
				new XMIResourceFactoryImpl());

	}

	@Test
	void biDirectionalUncertaintyPropagationBetweenBrakeDiskAndCircleSameUncertaintyNotAutomaticallyCreated(
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
			var circle = v.getRootObjects(CADRepository.class).iterator().next().getCadElements().stream()
					.filter(Circle.class::isInstance).map(Circle.class::cast)
					.filter(c -> c.getRadius() == 60)
					.findFirst().orElseThrow();

			var uncertainty = createUncertainty("FromCircle", circle);

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
			var brakeDisk = v.getRootObjects(Brakesystem.class).iterator().next().getBrakeComponents()
					.stream()
					.filter(BrakeDisk.class::isInstance).map(BrakeDisk.class::cast)
					.filter(d -> d.getDiameterInMM() == 120)
					.findFirst().orElseThrow();

			var uncertainty = createUncertainty("FromDisk", brakeDisk);

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
							var brakeDiskUncertainties = UncertaintyTestUtil.getBrakeDiskUncertainties(v);
							var circleUncertainties = UncertaintyTestUtil.getCircleUncertainties(v);

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
