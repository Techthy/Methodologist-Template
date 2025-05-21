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
		logger.info(
				"createUncertaintyManuallyPropagateToSingleCorrespondingEntityNoOtherEntitiesAffected Test started. \n");

		VirtualModel vsum = createDefaultVirtualModel(tempDir);

		System.out.println("vsum defaultVirtualModel created.");

		// Prepare models.
		addBrakesystem(vsum, tempDir);

		// Assert, that Brakesystem and CAD Repository are created in both models.
		Assertions.assertEquals(1,
				getDefaultView(vsum, List.of(Brakesystem.class)).getRootObjects().size());

		Assertions.assertEquals(1,
				getDefaultView(vsum, List.of(CADRepository.class)).getRootObjects().size());

		System.out.println("Brakesystem created manually, CADRepository created from reaction. \n");

		// Add two brakediscs now
		addBrakeDiscWithDiameter(vsum, tempDir, 120);
		addBrakeDiscWithDiameter(vsum, tempDir, 50);

		System.out.println(
				"BrakeDisk diameter 50mm and 120mm created manually, Cricles in CAD created from reaction. \n");

		Assertions
				.assertTrue(
						assertView(getDefaultView(vsum,
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

									return brakeDiscs.size() == 2 &&
											circles.size() == 2 &&
											brakeDiscs.stream().anyMatch(
													d -> d.getDiameterInMM() == 120)
											&&
											brakeDiscs.stream().anyMatch(
													d -> d.getDiameterInMM() == 50)
											&&
											circles.stream().anyMatch(
													c -> ((Circle) c)
															.getRadius() == 60)
											&&
											circles.stream().anyMatch(
													c -> ((Circle) c)
															.getRadius() == 25);
								}));

		System.out.println("BrakeDiscs and circles exist in VSUM. \n");

		// Add UncertaintyAnnotationRepository
		addUncertaintyAnnotationRepository(vsum, tempDir);

		// Add uncertainty only to the 120mm BrakeDisk
		CommittableView view = getDefaultView(vsum,
				List.of(UncertaintyAnnotationRepository.class, Brakesystem.class))
				.withChangeDerivingTrait();

		modifyView(view, (CommittableView v) -> {
			var targetDisk = v.getRootObjects(Brakesystem.class).iterator().next()
					.getBrakeComponents().stream().filter(BrakeDisk.class::isInstance)
					.map(BrakeDisk.class::cast)
					.filter(d -> d.getDiameterInMM() == 120)
					.findFirst().orElseThrow();

			var uncertaintyLocation = UncertaintyFactory.eINSTANCE.createUncertaintyLocation();
			uncertaintyLocation.setLocation(UncertaintyLocationType.PARAMETER);
			uncertaintyLocation.setSpecification("Diameter");
			uncertaintyLocation.getReferencesComponents().add(targetDisk);

			var uncertaintyObj = UncertaintyFactory.eINSTANCE.createUncertainty();
			uncertaintyObj.setUncertaintyLocation(uncertaintyLocation);
			uncertaintyObj.setKind(UncertaintyKind.MEASUREMENT_UNCERTAINTY);
			uncertaintyObj.setReducability(ReducabilityLevel.UNKNOWN);
			uncertaintyObj.setNature(UncertaintyNature.ALEATORY);

			// Hack to make the change propagate
			targetDisk.setSpecificationType(generateRandomString());

			v.getRootObjects(UncertaintyAnnotationRepository.class).iterator().next()
					.getUncertainties().add(uncertaintyObj);
		});

		// Assert: Exactly two uncertainties exist (one manually added, one propagated)
		Assertions.assertTrue(
				assertView(getDefaultView(vsum, List.of(UncertaintyAnnotationRepository.class)),
						(View v) -> {
							return v.getRootObjects(UncertaintyAnnotationRepository.class)
									.iterator().next()
									.getUncertainties().size() == 2;
						}));

		// Assert: One uncertainty points to a BrakeDisk, and one to a Circle with
		// radius 60
		Assertions.assertTrue(
				assertView(getDefaultView(vsum, List.of(UncertaintyAnnotationRepository.class)),
						(View v) -> {
							var uncertainties = v.getRootObjects(
									UncertaintyAnnotationRepository.class)
									.iterator().next()
									.getUncertainties();

							boolean hasBrakeDisk120 = uncertainties.stream()
									.anyMatch(u -> u.getUncertaintyLocation()
											.getReferencesComponents()
											.stream()
											.anyMatch(c -> c instanceof BrakeDisk
													&& ((BrakeDisk) c)
															.getDiameterInMM() == 120));

							boolean hasCircle60 = uncertainties.stream()
									.anyMatch(u -> u.getUncertaintyLocation()
											.getReferencesComponents()
											.stream()
											.anyMatch(c -> c instanceof Circle
													&& ((Circle) c).getRadius() == 60));

							boolean noCircle25 = uncertainties.stream()
									.noneMatch(u -> u.getUncertaintyLocation()
											.getReferencesComponents()
											.stream()
											.anyMatch(c -> c instanceof Circle
													&& ((Circle) c).getRadius() == 25));

							return hasBrakeDisk120 && hasCircle60 && noCircle25;
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

	private void addUncertainty(VirtualModel vsum, Path projectPath) {
		CommittableView view = getDefaultView(vsum,
				List.of(UncertaintyAnnotationRepository.class, Brakesystem.class))
				.withChangeDerivingTrait();
		modifyView(view, (CommittableView v) -> {
			var uncertaintyLocation = UncertaintyFactory.eINSTANCE.createUncertaintyLocation();
			uncertaintyLocation.setLocation(UncertaintyLocationType.PARAMETER);
			uncertaintyLocation.setSpecification("Diameter");
			uncertaintyLocation.getReferencesComponents()
					.add(v.getRootObjects(Brakesystem.class).iterator().next().getBrakeComponents()
							.get(0));
			var uncertaintyObj = UncertaintyFactory.eINSTANCE.createUncertainty();
			uncertaintyObj.setUncertaintyLocation(uncertaintyLocation);
			uncertaintyObj.setKind(UncertaintyKind.MEASUREMENT_UNCERTAINTY);
			uncertaintyObj.setReducability(ReducabilityLevel.UNKNOWN);
			uncertaintyObj.setNature(UncertaintyNature.ALEATORY);
			// Workaround for now (Problem is that I cannot reference the Brakesystem with
			// Change Deriving traits and then change nothing)
			// On the other hand I cannot just checkout another view as the references then
			// do not match.
			String randomtype = generateRandomString();
			v.getRootObjects(Brakesystem.class).iterator().next()
					.getBrakeComponents().get(0).setSpecificationType(randomtype);
			v.getRootObjects(UncertaintyAnnotationRepository.class).iterator().next()
					.getUncertainties().add(uncertaintyObj);
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
