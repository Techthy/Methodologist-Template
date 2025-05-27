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

public class CreateUncertaintyUtil {

    public record UncertaintyParams(
            Optional<UncertaintyKind> uncertaintyKind,
            Optional<ReducabilityLevel> reducabilityLevel,
            Optional<UncertaintyNature> uncertaintyNature,
            Optional<Boolean> isSetManually,
            Optional<OnDeleteMode> onDeleteMode) {
    }

    public static UncertaintyLocation createUncertaintyLocation(
            Optional<UncertaintyLocationType> uncertaintyLocationType,
            Optional<String> uncertaintyLocationSpecification,
            List<EObject> referencedComponents) {

        UncertaintyLocation location = UncertaintyFactory.eINSTANCE.createUncertaintyLocation();
        location.setLocation(uncertaintyLocationType.orElse(UncertaintyLocationType.PARAMETER));
        location.setSpecification(uncertaintyLocationSpecification.orElse(""));
        location.getReferencedComponents().addAll(referencedComponents);
        return location;
    }

    public static Effect createEffect(
            Optional<String> effectSpecification,
            Optional<StructuralEffectTypeRepresentation> structuralEffectTypeRepresentation,
            Optional<StochasticityEffectType> stochasticityEffectTypeRepresentation) {

        Effect effect = UncertaintyFactory.eINSTANCE.createEffect();
        effect.setSpecification(effectSpecification.orElse(""));
        effect.setRepresentation(
                structuralEffectTypeRepresentation.orElse(StructuralEffectTypeRepresentation.CONTINOUS));
        effect.setStochasticity(
                stochasticityEffectTypeRepresentation.orElse(StochasticityEffectType.PROBABILISTIC));
        return effect;
    }

    public static UncertaintyPerspective createUncertaintyPerspective(
            Optional<String> perspectiveSpecification,
            Optional<UncertaintyPerspectiveType> perspectiveType) {

        UncertaintyPerspective perspective = UncertaintyFactory.eINSTANCE.createUncertaintyPerspective();
        perspective.setSpecification(perspectiveSpecification.orElse(""));
        perspective.setPerspective(perspectiveType.orElse(UncertaintyPerspectiveType.OBJECTIVE));
        return perspective;
    }

    public static Pattern createPattern(
            Optional<PatternType> patternType) {

        Pattern pattern = UncertaintyFactory.eINSTANCE.createPattern();
        pattern.setPatternType(patternType.orElse(PatternType.PERSISTENT));
        return pattern;
    }

    public static Uncertainty createUncertainty(
            Optional<UncertaintyParams> params,
            Optional<UncertaintyLocation> location,
            Optional<Effect> effect,
            Optional<UncertaintyPerspective> perspective,
            Optional<Pattern> pattern) {
        // Perspective parameters

        UncertaintyParams uncertaintyParams = params.orElse(new UncertaintyParams(
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()));

        UncertaintyLocation uncertaintyLocation = location
                .orElseGet(() -> createUncertaintyLocation(Optional.empty(), Optional.empty(), List.of()));

        Effect uncertaintyEffect = effect.orElseGet(() -> createEffect(Optional.empty(),
                Optional.empty(), Optional.empty()));

        UncertaintyPerspective uncertaintyPerspective = perspective
                .orElseGet(() -> createUncertaintyPerspective(Optional.empty(), Optional.empty()));

        Pattern uncertaintyPattern = pattern.orElseGet(() -> createPattern(Optional.empty()));

        Uncertainty uncertainty = UncertaintyFactory.eINSTANCE.createUncertainty();
        uncertainty.setId(EcoreUtil.generateUUID());
        uncertainty.setKind(uncertaintyParams.uncertaintyKind.orElse(UncertaintyKind.BEHAVIOR_UNCERTAINTY));
        uncertainty.setReducability(uncertaintyParams.reducabilityLevel.orElse(ReducabilityLevel.UNKNOWN));
        uncertainty.setNature(uncertaintyParams.uncertaintyNature.orElse(UncertaintyNature.ALEATORY));
        uncertainty.setSetManually(uncertaintyParams.isSetManually.orElse(true));
        uncertainty.setOnDelete(uncertaintyParams.onDeleteMode.orElse(OnDeleteMode.CASCADE));
        uncertainty.setUncertaintyLocation(uncertaintyLocation);
        uncertainty.setEffect(uncertaintyEffect);
        uncertainty.setPerspective(uncertaintyPerspective);
        uncertainty.setPattern(uncertaintyPattern);

        return uncertainty;
    }

}
