package tools.vitruv.methodologisttemplate.vsum;

import tools.vitruv.framework.vsum.VirtualModelBuilder;
import tools.vitruv.framework.vsum.internal.InternalVirtualModel;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import brakesystem.Brakesystem;
import uncertainty.UncertaintyAnnotationRepository;
import cad.CADRepository;
import uncertainty.UncertaintyFactory;
import mir.reactions.uncertainty2brakesystem.Uncertainty2brakesystemChangePropagationSpecification;
import mir.reactions.brakesystem2cad.Brakesystem2cadChangePropagationSpecification;
import tools.vitruv.change.propagation.ChangePropagationMode;
import tools.vitruv.change.testutils.TestUserInteraction;
import tools.vitruv.framework.views.CommittableView;
import tools.vitruv.framework.views.View;
import tools.vitruv.framework.views.ViewTypeFactory;
import tools.vitruv.framework.vsum.VirtualModel;

/**
 * This class provides an example how to define and use a VSUM.
 */
public class VSUMExampleTest {

  @BeforeAll
  static void setup() {
    Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put("*", new XMIResourceFactoryImpl());
  }

  @Test
  void uncertaintyAnnotationRepositoryInsertionAndPropagationTest(@TempDir Path tempDir) {
    VirtualModel vsum = createDefaultVirtualModel(tempDir);
    addUncertaintyAnnotationRepository(vsum, tempDir);
    // assert that the directly added System is present
    Assertions.assertEquals(1,
        getDefaultView(vsum, List.of(UncertaintyAnnotationRepository.class)).getRootObjects().size());
    // as well as the Root that should be created by the Reactions, see
    // templateReactions.reactions#14
    Assertions.assertEquals(1, getDefaultView(vsum, List.of(Brakesystem.class)).getRootObjects().size());
  }

  @Test
  void BrakesystemInsertationAndPropagationTest(@TempDir Path tempDir) {
    VirtualModel vsum = createDefaultVirtualModel(tempDir);
    addBrakesystem(vsum, tempDir);
    // assert that the directly added System is present
    Assertions.assertEquals(1,
        getDefaultView(vsum, List.of(Brakesystem.class)).getRootObjects().size());

    Assertions.assertEquals(1,
        getDefaultView(vsum, List.of(CADRepository.class)).getRootObjects().size());

  }

  private void addBrakesystem(VirtualModel vsum, Path projectPath) {
    CommittableView view = getDefaultView(vsum, List.of(Brakesystem.class))
        .withChangeDerivingTrait();
    modifyView(view, (CommittableView v) -> {
      v.registerRoot(
          UncertaintyFactory.eINSTANCE.createUncertaintyAnnotationRepository(),
          URI.createFileURI(projectPath.toString() + "/example.model"));
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
        .withUserInteractorForResultProvider(new TestUserInteraction.ResultProvider(new TestUserInteraction()))
        .withChangePropagationSpecifications(new Uncertainty2brakesystemChangePropagationSpecification())
        .withChangePropagationSpecification(new Brakesystem2cadChangePropagationSpecification())
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

}
