package tools.vitruv.methodologisttemplate.vsum.uncertainty;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.util.EcoreUtil;

import brakesystem.BrakeDisk;
import brakesystem.Brakesystem;
import brakesystem.BrakesystemFactory;
import cad.CADRepository;
import cad.Circle;
import mir.reactions.brakesystem2cad.Brakesystem2cadChangePropagationSpecification;
import mir.reactions.cad2brakesystem.Cad2brakesystemChangePropagationSpecification;
import mir.reactions.uncertainty2cad.Uncertainty2cadChangePropagationSpecification;
import mir.reactions.uncertainty2uncertainty.Uncertainty2uncertaintyChangePropagationSpecification;
import tools.vitruv.change.propagation.ChangePropagationMode;
import tools.vitruv.change.testutils.TestUserInteraction;
import tools.vitruv.framework.views.CommittableView;
import tools.vitruv.framework.views.View;
import tools.vitruv.framework.views.ViewTypeFactory;
import tools.vitruv.framework.vsum.VirtualModel;
import tools.vitruv.framework.vsum.VirtualModelBuilder;
import tools.vitruv.framework.vsum.internal.InternalVirtualModel;
import uncertainty.Effect;
import uncertainty.OnDeleteMode;
import uncertainty.Pattern;
import uncertainty.PatternType;
import uncertainty.ReducabilityLevel;
import uncertainty.StochasticityEffectType;
import uncertainty.StructuralEffectTypeRepresentation;
import uncertainty.Uncertainty;
import uncertainty.UncertaintyAnnotationRepository;
import uncertainty.UncertaintyFactory;
import uncertainty.UncertaintyKind;
import uncertainty.UncertaintyLocation;
import uncertainty.UncertaintyLocationType;
import uncertainty.UncertaintyNature;
import uncertainty.UncertaintyPerspective;
import uncertainty.UncertaintyPerspectiveType;

public class UncertaintyTestUtil {

	private UncertaintyTestUtil() {
		// Utility class
	}

	public static InternalVirtualModel createDefaultVirtualModel(Path projectPath) {
		InternalVirtualModel model = new VirtualModelBuilder()
				.withStorageFolder(projectPath)
				.withUserInteractorForResultProvider(
						new TestUserInteraction.ResultProvider(new TestUserInteraction()))
				.withChangePropagationSpecification(new Brakesystem2cadChangePropagationSpecification())
				.withChangePropagationSpecification(
						new Uncertainty2uncertaintyChangePropagationSpecification())
				.withChangePropagationSpecification(new Cad2brakesystemChangePropagationSpecification())
				.withChangePropagationSpecification(new Uncertainty2cadChangePropagationSpecification())
				.buildAndInitialize();
		model.setChangePropagationMode(ChangePropagationMode.TRANSITIVE_CYCLIC);
		return model;
	}

	// Registers a Brakesystem, CADRepository and UncertaintyAnnotationRepository
	public static void registerRootObjects(VirtualModel virtualModel, Path filePath) {
		CommittableView view = getDefaultView(virtualModel,
				List.of(Brakesystem.class, CADRepository.class, UncertaintyAnnotationRepository.class))
				.withChangeDerivingTrait();
		modifyView(view, (CommittableView v) -> {
			v.registerRoot(
					UncertaintyFactory.eINSTANCE
							.createUncertaintyAnnotationRepository(),
					org.eclipse.emf.common.util.URI
							.createFileURI(filePath.toString() + "/uncertainty.model"));

			v.registerRoot(
					BrakesystemFactory.eINSTANCE.createBrakesystem(),
					URI.createFileURI(filePath.toString() + "/brakesystem.model"));
		});

	}

	private static void modifyView(CommittableView view, Consumer<CommittableView> modificationFunction) {
		modificationFunction.accept(view);
		view.commitChanges();
	}

	// See https://github.com/vitruv-tools/Vitruv/issues/717 for more information
	// about the rootTypes
	public static View getDefaultView(VirtualModel vsum, Collection<Class<?>> rootTypes) {
		var selector = vsum.createSelector(ViewTypeFactory.createIdentityMappingViewType("default"));
		selector.getSelectableElements().stream()
				.filter(element -> rootTypes.stream().anyMatch(it -> it.isInstance(element)))
				.forEach(it -> selector.setSelected(it, true));
		return selector.createView();
	}

	public static void addBrakeDiscWithDiameter(VirtualModel vsum, Path projectPath, int diameter) {
		CommittableView view = getDefaultView(vsum, List.of(Brakesystem.class))
				.withChangeDerivingTrait();
		modifyView(view, (CommittableView v) -> {
			var brakeDisc = BrakesystemFactory.eINSTANCE.createBrakeDisk();
			brakeDisc.setDiameterInMM(diameter);
			v.getRootObjects(Brakesystem.class).iterator().next().getBrakeComponents().add(brakeDisc);
		});
	}

	public static List<Uncertainty> getBrakeDiskUncertainties(View view) {
		return view.getRootObjects(UncertaintyAnnotationRepository.class)
				.iterator().next()
				.getUncertainties().stream()
				.filter(u -> u.getUncertaintyLocation()
						.getReferencedComponents().stream()
						.anyMatch(c -> c instanceof BrakeDisk))
				.toList();
	}

	public static List<Uncertainty> getCircleUncertainties(View view) {
		return view.getRootObjects(UncertaintyAnnotationRepository.class)
				.iterator().next()
				.getUncertainties().stream()
				.filter(u -> u.getUncertaintyLocation()
						.getReferencedComponents().stream()
						.anyMatch(c -> c instanceof Circle))
				.toList();
	}

	public static Uncertainty createUncertainty(
			// Uncertainty parameters
			Optional<UncertaintyKind> uncertaintyKind,
			Optional<ReducabilityLevel> reducabilityLevel,
			Optional<UncertaintyNature> uncertaintyNature,
			Optional<Boolean> isSetManually,
			Optional<OnDeleteMode> onDeleteMode,
			// UncertaintyLocation parameters
			Optional<UncertaintyLocationType> uncertaintyLocationType,
			Optional<String> uncertaintyLocationSpecification,
			List<EObject> referencedComponents,
			// Effect parameters
			Optional<String> effectSpecification,
			Optional<StructuralEffectTypeRepresentation> structuralEffectTypeRepresentation,
			Optional<StochasticityEffectType> stochasticityEffectTypeRepresentation,
			// Perspective parameters
			Optional<String> perspectiveSpecification,
			Optional<UncertaintyPerspectiveType> perspectiveType,
			// Patern parameters
			Optional<PatternType> patternType) {

		UncertaintyLocation location = UncertaintyFactory.eINSTANCE.createUncertaintyLocation();
		location.setLocation(uncertaintyLocationType.orElse(UncertaintyLocationType.PARAMETER));
		location.setSpecification(uncertaintyLocationSpecification.orElse(""));
		location.getReferencedComponents().addAll(referencedComponents);

		Effect effect = UncertaintyFactory.eINSTANCE.createEffect();
		effect.setSpecification(effectSpecification.orElse(""));
		effect.setRepresentation(
				structuralEffectTypeRepresentation.orElse(StructuralEffectTypeRepresentation.CONTINOUS));
		effect.setStochasticity(
				stochasticityEffectTypeRepresentation.orElse(StochasticityEffectType.PROBABILISTIC));

		UncertaintyPerspective perspective = UncertaintyFactory.eINSTANCE
				.createUncertaintyPerspective();
		perspective.setSpecification(perspectiveSpecification.orElse(""));
		perspective.setPerspective(perspectiveType.orElse(UncertaintyPerspectiveType.OBJECTIVE));

		Pattern pattern = UncertaintyFactory.eINSTANCE.createPattern();
		pattern.setPatternType(patternType.orElse(PatternType.PERSISTENT));

		Uncertainty uncertainty = UncertaintyFactory.eINSTANCE.createUncertainty();
		uncertainty.setId(EcoreUtil.generateUUID());
		uncertainty.setKind(uncertaintyKind.orElse(UncertaintyKind.BEHAVIOR_UNCERTAINTY));
		uncertainty.setReducability(reducabilityLevel.orElse(ReducabilityLevel.UNKNOWN));
		uncertainty.setNature(uncertaintyNature.orElse(UncertaintyNature.ALEATORY));
		uncertainty.setSetManually(isSetManually.orElse(true));
		uncertainty.setOnDelete(onDeleteMode.orElse(OnDeleteMode.CASCADE));
		uncertainty.setUncertaintyLocation(location);
		uncertainty.setEffect(effect);
		uncertainty.setPerspective(perspective);
		uncertainty.setPattern(pattern);

		return uncertainty;
	}

}
