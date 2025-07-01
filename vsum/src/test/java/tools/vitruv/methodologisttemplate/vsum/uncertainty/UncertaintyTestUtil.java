package tools.vitruv.methodologisttemplate.vsum.uncertainty;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

import org.eclipse.emf.common.util.URI;

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
import uncertainty.Uncertainty;
import uncertainty.UncertaintyAnnotationRepository;
import uncertainty.UncertaintyFactory;

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
				.withChangeRecordingTrait();
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

	// Registers anUncertaintyAnnotationRepository
	public static void registerUncertaintyAnnotationRepositoryAsRoot(VirtualModel virtualModel, Path filePath) {
		CommittableView view = getDefaultView(virtualModel,
				List.of(UncertaintyAnnotationRepository.class))
				.withChangeRecordingTrait();
		modifyView(view, (CommittableView v) -> {
			v.registerRoot(
					UncertaintyFactory.eINSTANCE
							.createUncertaintyAnnotationRepository(),
					org.eclipse.emf.common.util.URI
							.createFileURI(filePath.toString() + "/uncertainty.model"));
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
				.withChangeRecordingTrait();
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

}
