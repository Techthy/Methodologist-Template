package tools.vitruv.methodologisttemplate.vsum.uncertainty;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import java.util.function.Function;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
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
import mir.reactions.brakesystem2cad.Brakesystem2cadChangePropagationSpecification;
import mir.reactions.cad2brakesystem.Cad2brakesystemChangePropagationSpecification;
import mir.reactions.uncertainty2uncertainty.Uncertainty2uncertaintyChangePropagationSpecification;
import tools.vitruv.change.propagation.ChangePropagationMode;
import tools.vitruv.change.testutils.TestUserInteraction;
import tools.vitruv.framework.views.CommittableView;
import tools.vitruv.framework.views.View;
import tools.vitruv.framework.views.ViewTypeFactory;
import tools.vitruv.framework.vsum.VirtualModel;
import tools.vitruv.framework.vsum.VirtualModelBuilder;
import tools.vitruv.framework.vsum.internal.InternalVirtualModel;
import uncertainty.ReducabilityLevel;
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
		logger.info("test started. \n");
		VirtualModel vsum = createDefaultVirtualModel(tempDir);
		addBrakesystem(vsum, tempDir);
		addBrakeDiscWithDiameter(vsum, tempDir, 120);
		addUncertaintyAnnotationRepository(vsum, tempDir);

		// STEP 1: Add Uncertainty to the Circle (should propagate to BrakeDisk)
		CommittableView view1 = getDefaultView(vsum,
				List.of(UncertaintyAnnotationRepository.class, CADRepository.class, Brakesystem.class))
				.withChangeDerivingTrait();
		modifyView(view1, (CommittableView v) -> {
			var circle = v.getRootObjects(CADRepository.class).iterator().next().getCadElements().stream()
					.filter(Circle.class::isInstance).map(Circle.class::cast)
					.filter(c -> c.getRadius() == 60)
					.findFirst().orElseThrow();

			var uncertaintyLocation = UncertaintyFactory.eINSTANCE.createUncertaintyLocation();
			uncertaintyLocation.setLocation(UncertaintyLocationType.PARAMETER);
			uncertaintyLocation.setSpecification("FromCircle");
			uncertaintyLocation.getReferencesComponents().add(circle);

			var uncertainty = UncertaintyFactory.eINSTANCE.createUncertainty();
			uncertainty.setUncertaintyLocation(uncertaintyLocation);
			uncertainty.setKind(UncertaintyKind.MEASUREMENT_UNCERTAINTY);
			uncertainty.setReducability(ReducabilityLevel.UNKNOWN);
			uncertainty.setNature(UncertaintyNature.ALEATORY);

			// Make sure something changes to trigger propagation
			circle.setIdentifier(generateRandomString());

			v.getRootObjects(UncertaintyAnnotationRepository.class).iterator().next().getUncertainties()
					.add(uncertainty);
		});

		// STEP 2: Add a different Uncertainty to the BrakeDisk (should propagate to
		// Circle)
		CommittableView view2 = getDefaultView(vsum,
				List.of(UncertaintyAnnotationRepository.class, Brakesystem.class))
				.withChangeDerivingTrait();
		modifyView(view2, (CommittableView v) -> {
			var brakeDisk = v.getRootObjects(Brakesystem.class).iterator().next().getBrakeComponents()
					.stream()
					.filter(BrakeDisk.class::isInstance).map(BrakeDisk.class::cast)
					.filter(d -> d.getDiameterInMM() == 120)
					.findFirst().orElseThrow();

			var uncertaintyLocation = UncertaintyFactory.eINSTANCE.createUncertaintyLocation();
			uncertaintyLocation.setLocation(UncertaintyLocationType.PARAMETER);
			uncertaintyLocation.setSpecification("FromDisk");
			uncertaintyLocation.getReferencesComponents().add(brakeDisk);

			var uncertainty = UncertaintyFactory.eINSTANCE.createUncertainty();
			uncertainty.setUncertaintyLocation(uncertaintyLocation);
			uncertainty.setKind(UncertaintyKind.MEASUREMENT_UNCERTAINTY);
			uncertainty.setReducability(ReducabilityLevel.UNKNOWN);
			uncertainty.setNature(UncertaintyNature.ALEATORY);

			// Trigger propagation
			brakeDisk.setSpecificationType(generateRandomString());

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
				assertView(getDefaultView(vsum, List.of(UncertaintyAnnotationRepository.class)),
						(View v) -> {
							var uncertainties = v.getRootObjects(
									UncertaintyAnnotationRepository.class)
									.iterator().next()
									.getUncertainties();
							System.out.println("Number of Uncertainties: "
									+ uncertainties.size());

							long brakeDiskUncertainties = uncertainties.stream()
									.filter(u -> u.getUncertaintyLocation()
											.getReferencesComponents()
											.stream()
											.anyMatch(c -> c instanceof BrakeDisk
													&& ((BrakeDisk) c)
															.getDiameterInMM() == 120))
									.count();
							System.out.println("brakeDiskUncertainties: "
									+ brakeDiskUncertainties);

							long circleUncertainties = uncertainties.stream()
									.filter(u -> u.getUncertaintyLocation()
											.getReferencesComponents()
											.stream()
											.anyMatch(c -> c instanceof Circle
													&& ((Circle) c).getRadius() == 60))
									.count();
							System.out.println(
									"circleUncertainties: " + circleUncertainties);

							boolean specsPresent = uncertainties.stream()
									.filter(u -> u.getUncertaintyLocation()
											.getReferencesComponents()
											.stream()
											.anyMatch(
													c -> (c instanceof BrakeDisk
															&& ((BrakeDisk) c)
																	.getDiameterInMM() == 120)
															||
															(c instanceof Circle
																	&& ((Circle) c).getRadius() == 60)))
									.map(u -> u.getUncertaintyLocation()
											.getSpecification())
									.collect(java.util.stream.Collectors.toSet())
									.containsAll(List.of("FromDisk", "FromCircle"));

							return brakeDiskUncertainties == 2 && circleUncertainties == 1
									&& specsPresent;
						}));
	}

	private void addBrakesystem(VirtualModel vsum, Path projectPath) {
		CommittableView view = getDefaultView(vsum, List.of(Brakesystem.class))
				.withChangeDerivingTrait();
		modifyView(view, (CommittableView v) -> {
			v.registerRoot(
					BrakesystemFactory.eINSTANCE.createBrakesystem(),
					URI.createFileURI(projectPath.toString() + "/example.model"));
		});
	}

	private void addBrakeDisc(VirtualModel vsum, Path projectPath) {
		CommittableView view = getDefaultView(vsum, List.of(Brakesystem.class))
				.withChangeDerivingTrait();
		modifyView(view, (CommittableView v) -> {
			var brakeDisc = BrakesystemFactory.eINSTANCE.createBrakeDisk();
			brakeDisc.setDiameterInMM(120);
			v.getRootObjects(Brakesystem.class).iterator().next().getBrakeComponents().add(brakeDisc);
		});
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

	private void addUncertaintyAnnotationRepository(VirtualModel vsum, Path projectPath) {
		CommittableView view = getDefaultView(vsum, List.of(UncertaintyAnnotationRepository.class))
				.withChangeDerivingTrait();
		modifyView(view, (CommittableView v) -> {
			v.registerRoot(
					UncertaintyFactory.eINSTANCE.createUncertaintyAnnotationRepository(),
					URI.createFileURI(projectPath.toString() + "/example.model"));
		});
	}

	private InternalVirtualModel createDefaultVirtualModel(Path projectPath) {
		InternalVirtualModel model = new VirtualModelBuilder()
				.withStorageFolder(projectPath)
				.withUserInteractorForResultProvider(
						new TestUserInteraction.ResultProvider(new TestUserInteraction()))
				.withChangePropagationSpecification(new Brakesystem2cadChangePropagationSpecification())
				.withChangePropagationSpecification(
						new Uncertainty2uncertaintyChangePropagationSpecification())
				.withChangePropagationSpecification(new Cad2brakesystemChangePropagationSpecification())
				.buildAndInitialize();
		model.setChangePropagationMode(ChangePropagationMode.TRANSITIVE_CYCLIC);
		return model;
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
