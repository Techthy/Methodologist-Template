package tools.vitruv.methodologisttemplate.vsum.uncertainty;

import java.util.List;
import java.util.Optional;

import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.util.EcoreUtil;

import uncertainty.Effect;
import uncertainty.OnDeleteMode;
import uncertainty.Pattern;
import uncertainty.PatternType;
import uncertainty.ReducabilityLevel;
import uncertainty.StochasticityEffectType;
import uncertainty.StructuralEffectTypeRepresentation;
import uncertainty.Uncertainty;
import uncertainty.UncertaintyFactory;
import uncertainty.UncertaintyKind;
import uncertainty.UncertaintyLocation;
import uncertainty.UncertaintyLocationType;
import uncertainty.UncertaintyNature;
import uncertainty.UncertaintyPerspective;
import uncertainty.UncertaintyPerspectiveType;
import uncertainty.UncertaintySource;
import uncertainty.UncertaintySourceType;

public class UncertaintyTestFactory {

	public static UncertaintyLocation createUncertaintyLocation(List<EObject> referencedComponents) {
		UncertaintyLocation location = UncertaintyFactory.eINSTANCE.createUncertaintyLocation();
		location.setLocation(UncertaintyLocationType.PARAMETER);
		location.setSpecification("Location specification");
		location.getReferencedComponents().addAll(referencedComponents);
		return location;
	}

	public static UncertaintyLocation createUncertaintyLocationWithLocationType(List<EObject> referencedComponents, UncertaintyLocationType locationtype) {
		UncertaintyLocation location = UncertaintyFactory.eINSTANCE.createUncertaintyLocation();
		location.setLocation(locationtype);
		location.setSpecification("Location specification");
		location.getReferencedComponents().addAll(referencedComponents);
		return location;
	}

	public static Effect createEffect() {
		Effect effect = UncertaintyFactory.eINSTANCE.createEffect();
		effect.setSpecification("Effect specification");
		effect.setRepresentation(StructuralEffectTypeRepresentation.CONTINOUS);
		effect.setStochasticity(StochasticityEffectType.PROBABILISTIC);
		return effect;
	}

	public static UncertaintyPerspective createUncertaintyPerspective() {
		UncertaintyPerspective perspective = UncertaintyFactory.eINSTANCE.createUncertaintyPerspective();
		perspective.setSpecification("Perspective specification");
		perspective.setPerspective(UncertaintyPerspectiveType.OBJECTIVE);
		return perspective;
	}

	public static Pattern createPattern() {
		Pattern pattern = UncertaintyFactory.eINSTANCE.createPattern();
		pattern.setPatternType(PatternType.PERSISTENT);
		return pattern;
	}

	public static UncertaintySource createUncertaintySource(UncertaintySourceType sourcetype) {
		UncertaintySource source = UncertaintyFactory.eINSTANCE.createUncertaintySource();
		source.setSource(sourcetype);
		return source;
	}

	public static Uncertainty createUncertainty(Optional<UncertaintyLocation> location) {

		UncertaintyLocation uncertaintyLocation = location
				.orElseGet(() -> createUncertaintyLocation(List.of()));

		Uncertainty uncertainty = UncertaintyFactory.eINSTANCE.createUncertainty();
		uncertainty.setId(EcoreUtil.generateUUID());
		uncertainty.setKind(UncertaintyKind.BEHAVIOR_UNCERTAINTY);
		uncertainty.setReducability(ReducabilityLevel.UNKNOWN);
		uncertainty.setNature(UncertaintyNature.ALEATORY);
		uncertainty.setSetManually(true);
		uncertainty.setOnDelete(OnDeleteMode.CASCADE);
		uncertainty.setUncertaintyLocation(uncertaintyLocation);
		uncertainty.setEffect(createEffect());
		uncertainty.setPerspective(createUncertaintyPerspective());
		uncertainty.setPattern(createPattern());

		return uncertainty;
	}

	/**
	 * 
	 * Helper
	 * 
	 * @param location UncertaintyLocation object, that references the actual EObject location of Uncertainty
	 * @return Uncertainty object as specified in related work.
	 */
	public static Uncertainty createUncertainty1CameraComponent(Optional<UncertaintyLocation> location) {

		UncertaintyLocation uncertaintyLocation = location
				.orElseGet(() -> createUncertaintyLocation(List.of()));

		Uncertainty uncertainty = UncertaintyFactory.eINSTANCE.createUncertainty();
		uncertainty.setId(EcoreUtil.generateUUID());
		uncertainty.setKind(UncertaintyKind.MEASUREMENT_UNCERTAINTY);
		uncertainty.setReducability(ReducabilityLevel.IRREDUCIBLE);
		uncertainty.setNature(UncertaintyNature.ALEATORY);
		uncertainty.setSetManually(true);
		uncertainty.setOnDelete(OnDeleteMode.CASCADE);
		uncertainty.setUncertaintyLocation(uncertaintyLocation);
		uncertainty.setSource(createUncertaintySource(UncertaintySourceType.NOISE_IN_SENSING));

		return uncertainty;
	}

	/**
	 * 
	 * Helper
	 * 
	 * @param location UncertaintyLocation object, that references the actual EObject location of Uncertainty
	 * @return Uncertainty object as specified in related work.
	 */
	public static Uncertainty createUncertainty2CameraObjectRecognition(Optional<UncertaintyLocation> location) {

		UncertaintyLocation uncertaintyLocation = location
				.orElseGet(() -> createUncertaintyLocation(List.of()));

		Uncertainty uncertainty = UncertaintyFactory.eINSTANCE.createUncertainty();
		uncertainty.setId(EcoreUtil.generateUUID());
		uncertainty.setKind(UncertaintyKind.BEHAVIOR_UNCERTAINTY);
		uncertainty.setReducability(ReducabilityLevel.PARTIALLY_REDUCIBLE);
		uncertainty.setNature(UncertaintyNature.EPISTEMIC);
		uncertainty.setSetManually(true);
		uncertainty.setOnDelete(OnDeleteMode.CASCADE);
		uncertainty.setUncertaintyLocation(uncertaintyLocation);
		uncertainty.setSource(createUncertaintySource(UncertaintySourceType.MODEL));

		return uncertainty;
	}

	/**
	 * 
	 * Helper
	 * 
	 * @param location UncertaintyLocation object, that references the actual EObject location of Uncertainty
	 * @return Uncertainty object as specified in related work.
	 */
	public static Uncertainty createUncertainty3CameraObjectRecognition(Optional<UncertaintyLocation> location) {

		UncertaintyLocation uncertaintyLocation = location
				.orElseGet(() -> createUncertaintyLocation(List.of()));

		Uncertainty uncertainty = UncertaintyFactory.eINSTANCE.createUncertainty();
		uncertainty.setId(EcoreUtil.generateUUID());
		uncertainty.setKind(UncertaintyKind.MEASUREMENT_UNCERTAINTY);
		uncertainty.setReducability(ReducabilityLevel.PARTIALLY_REDUCIBLE);
		uncertainty.setNature(UncertaintyNature.EPISTEMIC);
		uncertainty.setSetManually(true);
		uncertainty.setOnDelete(OnDeleteMode.CASCADE);
		uncertainty.setUncertaintyLocation(uncertaintyLocation);
		uncertainty.setSource(createUncertaintySource(UncertaintySourceType.ENVIRONMENT));

		return uncertainty;
	}

}
