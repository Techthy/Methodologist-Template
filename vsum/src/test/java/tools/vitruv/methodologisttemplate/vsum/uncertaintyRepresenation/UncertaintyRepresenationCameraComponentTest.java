package tools.vitruv.methodologisttemplate.vsum.uncertainty;

import java.nio.file.Path;
import java.util.ArrayList;
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
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;

import brakesystem.BrakeDisk;
import brakesystem.Brakesystem;
import cad.CADRepository;
import tools.vitruv.framework.views.CommittableView;
import tools.vitruv.framework.views.View;
import tools.vitruv.framework.vsum.VirtualModel;
import tools.vitruv.methodologisttemplate.vsum.uncertainty.AddAndRemoveUncertaintyTest;
import tools.vitruv.methodologisttemplate.vsum.uncertainty.UncertaintyTestFactory;
import tools.vitruv.methodologisttemplate.vsum.uncertainty.UncertaintyTestUtil;
import uncertainty.Uncertainty;
import uncertainty.UncertaintyAnnotationRepository;
import uncertainty.UncertaintyKind;
import uncertainty.UncertaintyLocation;
import uncertainty.UncertaintyLocationType;

public class UncertaintyRepresenationCameraComponentTest {

	private static final Logger logger = org.slf4j.LoggerFactory
			.getLogger(UncertaintyRepresenationCameraComponentTest.class);

	@BeforeAll
	static void setup() {
		Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put("*",
				new XMIResourceFactoryImpl());
		logger.info("setup complete.");

	}

	// Plan of the test:
	// Checkout uncertainty view
	// Create three uncertainties corresponding to C1 evaluation goal from related
	// work
	// Check if uncertainties have specified characertistics.

	@Test
	void createUncertaintyForCameraComponent(@TempDir Path tempDir) {
		VirtualModel vsum = UncertaintyTestUtil.createDefaultVirtualModel(tempDir);
		// Registers a Brakesystem, CADRepository and UncertaintyAnnotationRepository
		UncertaintyTestUtil.registerUncertaintyAnnotationRepositoryAsRoot(vsum, tempDir);

		// Add two uncertainties to the brake disk
		CommittableView uncertaintyView = UncertaintyTestUtil.getDefaultView(vsum,
				List.of(UncertaintyAnnotationRepository.class))
				.withChangeRecordingTrait();
		modifyView(uncertaintyView, (CommittableView v) -> {
			UncertaintyLocation firstLocation = UncertaintyTestFactory.createUncertaintyLocationWithLocationType(
					new ArrayList<EObject>(), UncertaintyLocationType.OUTCOME);
			firstLocation.setSpecification("referenceCameraComponent");
			UncertaintyLocation secondLocation = UncertaintyTestFactory.createUncertaintyLocationWithLocationType(
					new ArrayList<EObject>(), UncertaintyLocationType.OUTCOME);
			secondLocation.setSpecification("referenceCameraComponent");
			UncertaintyLocation thirdLocation = UncertaintyTestFactory.createUncertaintyLocationWithLocationType(
					new ArrayList<EObject>(), UncertaintyLocationType.INPUTS);
			thirdLocation.setSpecification("referenceCameraComponent");
			Uncertainty firstUncertainty = UncertaintyTestFactory.createUncertainty(
					Optional.of(firstLocation));
			Uncertainty secondUncertainty = UncertaintyTestFactory.createUncertainty(
					Optional.of(secondLocation));
			Uncertainty thirdUncertainty = UncertaintyTestFactory.createUncertainty(
					Optional.of(secondLocation));

			v.getRootObjects(UncertaintyAnnotationRepository.class).iterator().next().getUncertainties()
					.add(firstUncertainty);
			v.getRootObjects(UncertaintyAnnotationRepository.class).iterator().next().getUncertainties()
					.add(secondUncertainty);
			v.getRootObjects(UncertaintyAnnotationRepository.class).iterator().next().getUncertainties()
					.add(thirdUncertainty);
		});

		Assertions.assertTrue(
				assertView(UncertaintyTestUtil.getDefaultView(vsum, List.of(UncertaintyAnnotationRepository.class)),
						(View v) -> {
							List<Uncertainty> allUncertainties = v.getRootObjects(UncertaintyAnnotationRepository.class)
									.iterator().next().getUncertainties();
							logger.info("Initial uncertainties: " + allUncertainties);
							return allUncertainties.size() == 3;
						}));

	}

	private void deleteBrakeDiskUncertainty(VirtualModel vsum) {
		modifyView(UncertaintyTestUtil
				.getDefaultView(vsum, List.of(UncertaintyAnnotationRepository.class, Brakesystem.class))
				.withChangeRecordingTrait(), (CommittableView v) -> {

					EList<Uncertainty> uncertainties = v.getRootObjects(UncertaintyAnnotationRepository.class)
							.iterator().next()
							.getUncertainties();
					Uncertainty uncertaintyToDelete = uncertainties.stream()
							.filter(u -> u.getUncertaintyLocation()
									.getReferencedComponents().stream()
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
