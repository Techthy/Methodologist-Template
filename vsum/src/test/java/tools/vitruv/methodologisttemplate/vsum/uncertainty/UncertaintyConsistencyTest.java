package tools.vitruv.methodologisttemplate.vsum.uncertainty;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

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
import tools.vitruv.framework.views.CommittableView;
import tools.vitruv.framework.views.View;
import tools.vitruv.framework.vsum.VirtualModel;
import uncertainty.Effect;
import uncertainty.OnDeleteMode;
import uncertainty.Pattern;
import uncertainty.PatternType;
import uncertainty.ReducabilityLevel;
import uncertainty.StochasticityEffectType;
import uncertainty.StructuralEffectTypeRepresentation;
import uncertainty.Uncertainty;
import uncertainty.UncertaintyAnnotationRepository;
import uncertainty.UncertaintyKind;
import uncertainty.UncertaintyLocation;
import uncertainty.UncertaintyLocationType;
import uncertainty.UncertaintyNature;
import uncertainty.UncertaintyPerspective;
import uncertainty.UncertaintyPerspectiveType;

public class UncertaintyConsistencyTest {

	private static final Logger logger = org.slf4j.LoggerFactory
			.getLogger(UncertaintyConsistencyTest.class);

	@BeforeAll
	static void setup() {
		Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put("*",
				new XMIResourceFactoryImpl());

	}

	// Plan of the test:
	// Add uncertainty
	// update the primitive attributes of the uncertainty
	// Assert that the updated uncertainty is propagated correctly

	@Test
	void updateUncertaintyTest(@TempDir Path tempDir) {

		VirtualModel vsum = UncertaintyTestUtil.createDefaultVirtualModel(tempDir);
		// Registers a Brakesystem, CADRepository and UncertaintyAnnotationRepository
		UncertaintyTestUtil.registerRootObjects(vsum, tempDir);

		// Add a BrakeDisk that in turn (by reactions) creates a Circle
		UncertaintyTestUtil.addBrakeDiscWithDiameter(vsum, tempDir, 120);

		// Add two uncertainties to the brake disk
		CommittableView brakeAndUncertaintyView = UncertaintyTestUtil.getDefaultView(vsum,
				List.of(UncertaintyAnnotationRepository.class, Brakesystem.class))
				.withChangeRecordingTrait();
		modifyView(brakeAndUncertaintyView, (CommittableView v) -> {
			BrakeDisk brakeDisk = v.getRootObjects(Brakesystem.class).iterator().next().getBrakeComponents()
					.stream()
					.filter(BrakeDisk.class::isInstance).map(BrakeDisk.class::cast)
					.filter(d -> d.getDiameterInMM() == 120)
					.findFirst().orElseThrow();

			UncertaintyLocation uncertaintyLocation = UncertaintyTestFactory
					.createUncertaintyLocation(List.of(brakeDisk));
			uncertaintyLocation.setSpecification("FromDisk");
			Uncertainty uncertainty = UncertaintyTestFactory
					.createUncertainty(Optional.of(uncertaintyLocation));
			uncertainty.setKind(UncertaintyKind.MEASUREMENT_UNCERTAINTY);
			uncertainty.setReducability(ReducabilityLevel.FULLY_REDUCABLE);
			uncertainty.setNature(UncertaintyNature.EPISTEMIC);
			uncertainty.setOnDelete(OnDeleteMode.NO_ACTION);

			v.getRootObjects(UncertaintyAnnotationRepository.class).iterator().next()
					.getUncertainties().add(uncertainty);

			// Trigger propagation
			brakeDisk.setSpecificationType(EcoreUtil.generateUUID());

		});

		// Assert that two uncertainties now exist both having the same primitive
		// attributes
		Assertions.assertTrue(
				assertView(UncertaintyTestUtil.getDefaultView(vsum,
						List.of(UncertaintyAnnotationRepository.class)),
						(View v) -> {
							List<Uncertainty> uncertainties = v.getRootObjects(
									UncertaintyAnnotationRepository.class)
									.iterator().next().getUncertainties();
							return uncertainties.size() == 2 && uncertainties.stream()
									.allMatch(u -> u.getKind() == UncertaintyKind.MEASUREMENT_UNCERTAINTY
											&& u.getReducability() == ReducabilityLevel.FULLY_REDUCABLE
											&& u.getNature() == UncertaintyNature.EPISTEMIC
											&& u.getOnDelete() == OnDeleteMode.NO_ACTION);

						}));

		// Change the primitive attributes one uncertainty
		modifyView(UncertaintyTestUtil.getDefaultView(vsum, List.of(UncertaintyAnnotationRepository.class))
				.withChangeRecordingTrait(), (CommittableView v) -> {
					List<Uncertainty> uncertainties = v
							.getRootObjects(UncertaintyAnnotationRepository.class)
							.iterator().next().getUncertainties();
					Uncertainty firstUncertainty = uncertainties.get(0);
					firstUncertainty.setKind(UncertaintyKind.BELIEF_UNCERTAINTY);

				});

		modifyView(UncertaintyTestUtil.getDefaultView(vsum, List.of(UncertaintyAnnotationRepository.class))
				.withChangeRecordingTrait(), (CommittableView v) -> {
					List<Uncertainty> uncertainties = v
							.getRootObjects(UncertaintyAnnotationRepository.class)
							.iterator().next().getUncertainties();
					Uncertainty firstUncertainty = uncertainties.get(0);
					firstUncertainty.setReducability(ReducabilityLevel.IRREDUCIBLE);

				});

		modifyView(UncertaintyTestUtil.getDefaultView(vsum, List.of(UncertaintyAnnotationRepository.class))
				.withChangeRecordingTrait(), (CommittableView v) -> {
					List<Uncertainty> uncertainties = v
							.getRootObjects(UncertaintyAnnotationRepository.class)
							.iterator().next().getUncertainties();
					Uncertainty firstUncertainty = uncertainties.get(0);
					firstUncertainty.setNature(UncertaintyNature.ALEATORY);

				});

		modifyView(UncertaintyTestUtil.getDefaultView(vsum, List.of(UncertaintyAnnotationRepository.class))
				.withChangeRecordingTrait(), (CommittableView v) -> {
					List<Uncertainty> uncertainties = v
							.getRootObjects(UncertaintyAnnotationRepository.class)
							.iterator().next().getUncertainties();
					Uncertainty firstUncertainty = uncertainties.get(0);
					firstUncertainty.setOnDelete(OnDeleteMode.RESTRICT);

				});

		// Assert that both uncertainties now have the changed primitive attributes
		Assertions.assertTrue(
				assertView(UncertaintyTestUtil.getDefaultView(vsum,
						List.of(UncertaintyAnnotationRepository.class)),
						(View v) -> {
							List<Uncertainty> uncertainties = v.getRootObjects(
									UncertaintyAnnotationRepository.class)
									.iterator().next().getUncertainties();
							return uncertainties.size() == 2 && uncertainties.stream()
									.allMatch(u -> u.getKind() == UncertaintyKind.BELIEF_UNCERTAINTY
											&& u.getReducability() == ReducabilityLevel.IRREDUCIBLE
											&& u.getNature() == UncertaintyNature.ALEATORY
											&& u.getOnDelete() == OnDeleteMode.RESTRICT);

						}));
	}

	@Test
	void updatePatternTest(@TempDir Path tempDir) {
		VirtualModel vsum = UncertaintyTestUtil.createDefaultVirtualModel(tempDir);
		// Registers a Brakesystem, CADRepository and UncertaintyAnnotationRepository
		UncertaintyTestUtil.registerRootObjects(vsum, tempDir);

		// Add a BrakeDisk that in turn (by reactions) creates a Circle
		UncertaintyTestUtil.addBrakeDiscWithDiameter(vsum, tempDir, 120);

		// Add two uncertainties to the brake disk
		CommittableView brakeAndUncertaintyView = UncertaintyTestUtil.getDefaultView(vsum,
				List.of(UncertaintyAnnotationRepository.class, Brakesystem.class))
				.withChangeRecordingTrait();
		modifyView(brakeAndUncertaintyView, (CommittableView v) -> {
			BrakeDisk brakeDisk = v.getRootObjects(Brakesystem.class).iterator().next().getBrakeComponents()
					.stream()
					.filter(BrakeDisk.class::isInstance).map(BrakeDisk.class::cast)
					.filter(d -> d.getDiameterInMM() == 120)
					.findFirst().orElseThrow();

			UncertaintyLocation uncertaintyLocation = UncertaintyTestFactory
					.createUncertaintyLocation(List.of(brakeDisk));
			uncertaintyLocation.setSpecification("FromDisk");
			Uncertainty uncertainty = UncertaintyTestFactory
					.createUncertainty(Optional.of(uncertaintyLocation));
			Pattern pattern = UncertaintyTestFactory.createPattern();
			pattern.setPatternType(PatternType.PERSISTENT);

			v.getRootObjects(UncertaintyAnnotationRepository.class).iterator().next()
					.getUncertainties().add(uncertainty);

			// Trigger propagation
			brakeDisk.setSpecificationType(EcoreUtil.generateUUID());

		});

		// Assert that two uncertainties now exist both having the Pattern Type
		// PERSISTENT
		Assertions.assertTrue(
				assertView(UncertaintyTestUtil.getDefaultView(vsum,
						List.of(UncertaintyAnnotationRepository.class)),
						(View v) -> {
							List<Uncertainty> uncertainties = v.getRootObjects(
									UncertaintyAnnotationRepository.class)
									.iterator().next().getUncertainties();
							return uncertainties.size() == 2 && uncertainties.stream()
									.allMatch(u -> u.getPattern()
											.getPatternType() == PatternType.PERSISTENT);

						}));

		// Change the pattern of the first uncertainty to TRANSIENT

		modifyView(UncertaintyTestUtil.getDefaultView(vsum, List.of(UncertaintyAnnotationRepository.class))
				.withChangeRecordingTrait(), (CommittableView v) -> {
					List<Uncertainty> uncertainties = v
							.getRootObjects(UncertaintyAnnotationRepository.class)
							.iterator().next().getUncertainties();
					Uncertainty firstUncertainty = uncertainties.get(0);
					Pattern pattern = firstUncertainty.getPattern();
					pattern.setPatternType(PatternType.TRANSIENT);

				});

		// Assert that both uncertainties now have the Pattern Type TRANSIENT
		Assertions.assertTrue(
				assertView(UncertaintyTestUtil.getDefaultView(vsum,
						List.of(UncertaintyAnnotationRepository.class)),
						(View v) -> {
							List<Uncertainty> uncertainties = v.getRootObjects(
									UncertaintyAnnotationRepository.class)
									.iterator().next().getUncertainties();
							return uncertainties.size() == 2 && uncertainties.stream()
									.allMatch(u -> u.getPattern()
											.getPatternType() == PatternType.TRANSIENT);

						}));

	}

	@Test
	void updatePerspectiveTest(@TempDir Path tempDir) {

		VirtualModel vsum = UncertaintyTestUtil.createDefaultVirtualModel(tempDir);
		// Registers a Brakesystem, CADRepository and UncertaintyAnnotationRepository
		UncertaintyTestUtil.registerRootObjects(vsum, tempDir);

		// Add a BrakeDisk that in turn (by reactions) creates a Circle
		UncertaintyTestUtil.addBrakeDiscWithDiameter(vsum, tempDir, 120);

		// Add two uncertainties to the brake disk
		CommittableView brakeAndUncertaintyView = UncertaintyTestUtil.getDefaultView(vsum,
				List.of(UncertaintyAnnotationRepository.class, Brakesystem.class))
				.withChangeRecordingTrait();
		modifyView(brakeAndUncertaintyView, (CommittableView v) -> {
			BrakeDisk brakeDisk = v.getRootObjects(Brakesystem.class).iterator().next().getBrakeComponents()
					.stream()
					.filter(BrakeDisk.class::isInstance).map(BrakeDisk.class::cast)
					.filter(d -> d.getDiameterInMM() == 120)
					.findFirst().orElseThrow();

			UncertaintyLocation uncertaintyLocation = UncertaintyTestFactory
					.createUncertaintyLocation(List.of(brakeDisk));
			uncertaintyLocation.setSpecification("FromDisk");
			Uncertainty uncertainty = UncertaintyTestFactory
					.createUncertainty(Optional.of(uncertaintyLocation));
			UncertaintyPerspective perspective = UncertaintyTestFactory.createUncertaintyPerspective();
			perspective.setPerspective(UncertaintyPerspectiveType.OBJECTIVE);
			perspective.setSpecification("specificationOne");
			uncertainty.setPerspective(perspective);

			v.getRootObjects(UncertaintyAnnotationRepository.class).iterator().next()
					.getUncertainties().add(uncertainty);

			// Trigger propagation
			brakeDisk.setSpecificationType(EcoreUtil.generateUUID());

		});

		// Assert that two uncertainties now exist both having the same perspective
		Assertions.assertTrue(
				assertView(UncertaintyTestUtil.getDefaultView(vsum,
						List.of(UncertaintyAnnotationRepository.class)),
						(View v) -> {
							List<Uncertainty> uncertainties = v.getRootObjects(
									UncertaintyAnnotationRepository.class)
									.iterator().next().getUncertainties();
							return uncertainties.size() == 2 && uncertainties.stream()
									.allMatch(u -> u.getPerspective()
											.getPerspective() == UncertaintyPerspectiveType.OBJECTIVE
											&& u.getPerspective()
													.getSpecification()
													.equals("specificationOne"));

						}));

		// Change the perspective of the first uncertainty to SUBJECTIVE
		modifyView(UncertaintyTestUtil.getDefaultView(vsum, List.of(UncertaintyAnnotationRepository.class))
				.withChangeRecordingTrait(), (CommittableView v) -> {
					List<Uncertainty> uncertainties = v
							.getRootObjects(UncertaintyAnnotationRepository.class)
							.iterator().next().getUncertainties();
					Uncertainty firstUncertainty = uncertainties.get(0);
					UncertaintyPerspective perspective = firstUncertainty.getPerspective();
					perspective.setPerspective(UncertaintyPerspectiveType.SUBJECTIVE);

				});

		modifyView(UncertaintyTestUtil.getDefaultView(vsum, List.of(UncertaintyAnnotationRepository.class))
				.withChangeRecordingTrait(), (CommittableView v) -> {
					List<Uncertainty> uncertainties = v
							.getRootObjects(UncertaintyAnnotationRepository.class)
							.iterator().next().getUncertainties();
					Uncertainty firstUncertainty = uncertainties.get(0);
					UncertaintyPerspective perspective = firstUncertainty.getPerspective();
					perspective.setSpecification("specificationTwo");

				});

		// Assert that both uncertainties now have the perspective SUBJECTIVE and the
		// specification "specificationTwo"
		Assertions.assertTrue(
				assertView(UncertaintyTestUtil.getDefaultView(vsum,
						List.of(UncertaintyAnnotationRepository.class)),
						(View v) -> {
							List<Uncertainty> uncertainties = v.getRootObjects(
									UncertaintyAnnotationRepository.class)
									.iterator().next().getUncertainties();
							return uncertainties.size() == 2 && uncertainties.stream()
									.allMatch(u -> u.getPerspective()
											.getPerspective() == UncertaintyPerspectiveType.SUBJECTIVE
											&& u.getPerspective()
													.getSpecification()
													.equals("specificationTwo"));

						}));

	}

	@Test
	void updateEffectTest(@TempDir Path tempDir) {

		VirtualModel vsum = UncertaintyTestUtil.createDefaultVirtualModel(tempDir);
		// Registers a Brakesystem, CADRepository and UncertaintyAnnotationRepository
		UncertaintyTestUtil.registerRootObjects(vsum, tempDir);

		// Add a BrakeDisk that in turn (by reactions) creates a Circle
		UncertaintyTestUtil.addBrakeDiscWithDiameter(vsum, tempDir, 120);

		// Add two uncertainties to the brake disk
		CommittableView brakeAndUncertaintyView = UncertaintyTestUtil.getDefaultView(vsum,
				List.of(UncertaintyAnnotationRepository.class, Brakesystem.class))
				.withChangeRecordingTrait();
		modifyView(brakeAndUncertaintyView, (CommittableView v) -> {
			BrakeDisk brakeDisk = v.getRootObjects(Brakesystem.class).iterator().next().getBrakeComponents()
					.stream()
					.filter(BrakeDisk.class::isInstance).map(BrakeDisk.class::cast)
					.filter(d -> d.getDiameterInMM() == 120)
					.findFirst().orElseThrow();

			UncertaintyLocation uncertaintyLocation = UncertaintyTestFactory
					.createUncertaintyLocation(List.of(brakeDisk));
			uncertaintyLocation.setSpecification("FromDisk");
			Uncertainty uncertainty = UncertaintyTestFactory
					.createUncertainty(Optional.of(uncertaintyLocation));
			Effect effect = UncertaintyTestFactory.createEffect();
			effect.setSpecification("effectOne");
			effect.setRepresentation(StructuralEffectTypeRepresentation.CONTINOUS);
			effect.setStochasticity(StochasticityEffectType.NON_DETERMINISTIC);
			uncertainty.setEffect(effect);

			v.getRootObjects(UncertaintyAnnotationRepository.class).iterator().next()
					.getUncertainties().add(uncertainty);

			// Trigger propagation
			brakeDisk.setSpecificationType(EcoreUtil.generateUUID());

		});

		// Assert that two uncertainties now exist both having the same effect
		Assertions.assertTrue(
				assertView(UncertaintyTestUtil.getDefaultView(vsum,
						List.of(UncertaintyAnnotationRepository.class)),
						(View v) -> {
							List<Uncertainty> uncertainties = v.getRootObjects(
									UncertaintyAnnotationRepository.class)
									.iterator().next().getUncertainties();
							return uncertainties.size() == 2 && uncertainties.stream()
									.allMatch(u -> u.getEffect().getSpecification()
											.equals("effectOne")
											&& u.getEffect()
													.getRepresentation() == StructuralEffectTypeRepresentation.CONTINOUS
											&& u.getEffect()
													.getStochasticity() == StochasticityEffectType.NON_DETERMINISTIC);

						}));
		// Change specification of the effect to "effectTwo"
		modifyView(UncertaintyTestUtil.getDefaultView(vsum, List.of(UncertaintyAnnotationRepository.class))
				.withChangeRecordingTrait(), (CommittableView v) -> {
					List<Uncertainty> uncertainties = v
							.getRootObjects(UncertaintyAnnotationRepository.class)
							.iterator().next().getUncertainties();
					Uncertainty firstUncertainty = uncertainties.get(0);
					Effect effect = firstUncertainty.getEffect();
					effect.setSpecification("effectTwo");

				});

		// Change the representation of the effect to DISCRETE
		modifyView(UncertaintyTestUtil.getDefaultView(vsum, List.of(UncertaintyAnnotationRepository.class))
				.withChangeRecordingTrait(), (CommittableView v) -> {
					List<Uncertainty> uncertainties = v
							.getRootObjects(UncertaintyAnnotationRepository.class)
							.iterator().next().getUncertainties();
					Uncertainty firstUncertainty = uncertainties.get(0);
					Effect effect = firstUncertainty.getEffect();
					effect.setRepresentation(StructuralEffectTypeRepresentation.DISCRETE);

				});
		// Change the stochasticity of the effect to PROBABILISTIC
		modifyView(UncertaintyTestUtil.getDefaultView(vsum, List.of(UncertaintyAnnotationRepository.class))
				.withChangeRecordingTrait(), (CommittableView v) -> {
					List<Uncertainty> uncertainties = v
							.getRootObjects(UncertaintyAnnotationRepository.class)
							.iterator().next().getUncertainties();
					Uncertainty firstUncertainty = uncertainties.get(0);
					Effect effect = firstUncertainty.getEffect();
					effect.setStochasticity(StochasticityEffectType.PROBABILISTIC);

				});
		// Assert that both uncertainties now have the effect "effectTwo" with
		// representation DISCRETE and stochasticity PROBABILISTIC
		Assertions.assertTrue(
				assertView(UncertaintyTestUtil.getDefaultView(vsum,
						List.of(UncertaintyAnnotationRepository.class)),
						(View v) -> {
							List<Uncertainty> uncertainties = v.getRootObjects(
									UncertaintyAnnotationRepository.class)
									.iterator().next().getUncertainties();
							return uncertainties.size() == 2 && uncertainties.stream()
									.allMatch(u -> u.getEffect().getSpecification()
											.equals("effectTwo")
											&& u.getEffect()
													.getRepresentation() == StructuralEffectTypeRepresentation.DISCRETE
											&& u.getEffect()
													.getStochasticity() == StochasticityEffectType.PROBABILISTIC);

						}));
	}

	@Disabled
	@Test
	void updateLocationTest(@TempDir Path tempDir) {

		VirtualModel vsum = UncertaintyTestUtil.createDefaultVirtualModel(tempDir);
		// Registers a Brakesystem, CADRepository and UncertaintyAnnotationRepository
		UncertaintyTestUtil.registerRootObjects(vsum, tempDir);

		// Add a BrakeDisk that in turn (by reactions) creates a Circle
		UncertaintyTestUtil.addBrakeDiscWithDiameter(vsum, tempDir, 120);

		// Add two uncertainties to the brake disk
		CommittableView brakeAndUncertaintyView = UncertaintyTestUtil.getDefaultView(vsum,
				List.of(UncertaintyAnnotationRepository.class, Brakesystem.class))
				.withChangeRecordingTrait();
		modifyView(brakeAndUncertaintyView, (CommittableView v) -> {
			BrakeDisk brakeDisk = v.getRootObjects(Brakesystem.class).iterator().next().getBrakeComponents()
					.stream()
					.filter(BrakeDisk.class::isInstance).map(BrakeDisk.class::cast)
					.filter(d -> d.getDiameterInMM() == 120)
					.findFirst().orElseThrow();

			UncertaintyLocation uncertaintyLocation = UncertaintyTestFactory
					.createUncertaintyLocation(List.of(brakeDisk));
			uncertaintyLocation.setSpecification("FromDisk");
			uncertaintyLocation.setLocation(UncertaintyLocationType.ANALYSIS);

			Uncertainty uncertainty = UncertaintyTestFactory
					.createUncertainty(Optional.of(uncertaintyLocation));

			v.getRootObjects(UncertaintyAnnotationRepository.class).iterator().next()
					.getUncertainties().add(uncertainty);

			// Trigger propagation
			brakeDisk.setSpecificationType(EcoreUtil.generateUUID());

		});

		// Assert that two uncertainties now exist both having the same location
		Assertions.assertTrue(
				assertView(
						UncertaintyTestUtil.getDefaultView(vsum,
								List.of(UncertaintyAnnotationRepository.class,
										Brakesystem.class)),
						(View v) -> {
							List<Uncertainty> uncertainties = v.getRootObjects(
									UncertaintyAnnotationRepository.class)
									.iterator().next().getUncertainties();
							return uncertainties.size() == 2 && uncertainties.stream()
									.allMatch(u -> u.getUncertaintyLocation()
											.getSpecification()
											.equals("FromDisk")
											&& u.getUncertaintyLocation()
													.getLocation() == UncertaintyLocationType.ANALYSIS);

						}));

		// Change the location of the first uncertainty to DECISION_MAKING
		modifyView(UncertaintyTestUtil
				.getDefaultView(vsum, List.of(UncertaintyAnnotationRepository.class, Brakesystem.class))
				.withChangeRecordingTrait(), (CommittableView v) -> {
					List<Uncertainty> uncertainties = v
							.getRootObjects(UncertaintyAnnotationRepository.class)
							.iterator().next().getUncertainties();
					Uncertainty firstUncertainty = uncertainties.get(0);
					UncertaintyLocation uncertaintyLocation = firstUncertainty
							.getUncertaintyLocation();
					uncertaintyLocation.setLocation(UncertaintyLocationType.DECISION_MAKING);
				});

		// Change the specification of the first uncertainty to "specificationTwo"
		modifyView(UncertaintyTestUtil.getDefaultView(vsum, List.of(UncertaintyAnnotationRepository.class))

				.withChangeRecordingTrait(), (CommittableView v) -> {
					List<Uncertainty> uncertainties = v
							.getRootObjects(UncertaintyAnnotationRepository.class)
							.iterator().next().getUncertainties();
					Uncertainty firstUncertainty = uncertainties.get(0);
					UncertaintyLocation uncertaintyLocation = firstUncertainty
							.getUncertaintyLocation();
					uncertaintyLocation.setSpecification("specificationTwo");
				});

		// Assert that both uncertainties now have the location DECISION_MAKING and the
		// specification "specificationTwo"
		Assertions.assertTrue(
				assertView(UncertaintyTestUtil.getDefaultView(vsum,
						List.of(UncertaintyAnnotationRepository.class)),
						(View v) -> {
							List<Uncertainty> uncertainties = v.getRootObjects(
									UncertaintyAnnotationRepository.class)
									.iterator().next().getUncertainties();
							return uncertainties.size() == 2 && uncertainties.stream()
									.allMatch(u -> u.getUncertaintyLocation()
											.getLocation()
											.equals(UncertaintyLocationType.DECISION_MAKING)
											&& u.getUncertaintyLocation()
													.getSpecification()
													.equals("specificationTwo"));

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
