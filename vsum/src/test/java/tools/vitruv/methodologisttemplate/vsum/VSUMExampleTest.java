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
import uncertainty.UncertaintyFactory;
import mir.reactions.model2Model2.Model2Model2ChangePropagationSpecification;
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
  void systemInsertionAndPropagationTest(@TempDir Path tempDir) {
    VirtualModel vsum = createDefaultVirtualModel(tempDir);
    addUncertaintyAnnotationRepository(vsum, tempDir);
    // assert that the directly added System is present
    Assertions.assertEquals(1, getDefaultView(vsum, List.of(System.class)).getRootObjects().size());
    // as well as the Root that should be created by the Reactions, see
    // templateReactions.reactions#14
    Assertions.assertEquals(1, getDefaultView(vsum, List.of(Root.class)).getRootObjects().size());
  }

  @Test
  void insertComponent(@TempDir Path tempDir) {
    InternalVirtualModel vsum = createDefaultVirtualModel(tempDir);
    addSystem(vsum, tempDir);
    addComponent(vsum);
    Assertions.assertTrue(assertView(getDefaultView(vsum, List.of(System.class, Root.class)), (View v) -> {
      // assert that a component has been inserted, a entity has been created and that
      // both have the same name
      // Note: to make the test result easier to understand, these different effects
      // should be tested one by one
      return v.getRootObjects(System.class).iterator().next()
          .getComponents().get(0).getName()
          .equals(v.getRootObjects(Root.class).iterator().next()
              .getEntities().get(0).getName());
    }));
  }

  @Test
  void renameComponent(@TempDir Path tempDir) {
    final String newName = "newName";
    VirtualModel vsum = createDefaultVirtualModel(tempDir);
    addSystem(vsum, tempDir);
    addComponent(vsum);
    modifyView(getDefaultView(vsum, List.of(System.class)).withChangeDerivingTrait(), (CommittableView v) -> {
      // change the name of the component
      v.getRootObjects(System.class).iterator().next().getComponents().get(0).setName(newName);
    });
    Assertions.assertTrue(assertView(getDefaultView(vsum, List.of(System.class, Root.class)), (View v) -> {
      // assert that the renaming worked on the component as well as the corresponding
      // entity
      return v.getRootObjects(System.class).iterator().next()
          .getComponents().get(0).getName().equals(newName)
          && v.getRootObjects(Root.class).iterator().next()
              .getEntities().get(0).getName().equals(newName);
    }));
  }

  @Test
  void deleteComponent(@TempDir Path tempDir) {
    VirtualModel vsum = createDefaultVirtualModel(tempDir);
    addSystem(vsum, tempDir);
    addComponent(vsum);
    modifyView(getDefaultView(vsum, List.of(System.class)).withChangeDerivingTrait(), (CommittableView v) -> {
      v.getRootObjects(System.class).iterator().next().getComponents().remove(0);
    });
    Assertions.assertTrue(assertView(getDefaultView(vsum, List.of(System.class, Root.class)), (View v) -> {
      // assert that the deletion of the component worked and that the corresponding
      // entity also got deleted
      return v.getRootObjects(System.class).iterator().next().getComponents().isEmpty()
          && v.getRootObjects(Root.class).iterator().next().getEntities().isEmpty();
    }));
  }

  @Test
  void insertRouter(@TempDir Path tempDir) {
    InternalVirtualModel vsum = createDefaultVirtualModel(tempDir);
    addSystem(vsum, tempDir);
    addRouter(vsum);
    Assertions.assertTrue(assertView(getDefaultView(vsum, List.of(System.class, Root.class)), (View v) -> {
      // assert that a router has been inserted, a entity has been created and that
      // both have the same name
      return v.getRootObjects(System.class).iterator().next()
          .getComponents().get(0).getName()
          .equals(v.getRootObjects(Root.class).iterator().next()
              .getEntities().get(0).getName());
    }));
  }

  @Test
  void insertProtocol(@TempDir Path tempDir) {
    InternalVirtualModel vsum = createDefaultVirtualModel(tempDir);
    addSystem(vsum, tempDir);
    addProtocol(vsum);
    Assertions.assertTrue(assertView(getDefaultView(vsum, List.of(System.class, Root.class)), (View v) -> {
      // assert that a protocol has been inserted, a entity has been created and that
      // both have the same name
      return v.getRootObjects(System.class).iterator().next()
          .getProtocols().get(0).getName()
          .equals(v.getRootObjects(Root.class).iterator().next()
              .getProtocols().get(0).getName());
    }));
  }

  @Test
  void insertLink(@TempDir Path tempDir) {
    InternalVirtualModel vsum = createDefaultVirtualModel(tempDir);
    addSystem(vsum, tempDir);
    addComponent(vsum);
    addComponent(vsum);
    addProtocol(vsum);
    // add a link between the two components and the protocol
    addLink(vsum);
    Assertions.assertTrue(assertView(getDefaultView(vsum, List.of(System.class, Root.class)), (View v) -> {
      // assert that a link with the same name has been inserted into root
      return v.getRootObjects(System.class).iterator().next()
          .getLinks().get(0).getName()
          .equals(v.getRootObjects(Root.class).iterator().next()
              .getLinks().get(0)
              .getName());
    }));
    Assertions.assertTrue(assertView(getDefaultView(vsum, List.of(System.class, Root.class)), (View v) -> {
      // assert that a two protocols corresponding to the respective links have the
      // same name
      return v.getRootObjects(System.class).iterator().next()
          .getLinks().get(0).getProtocol().getName()
          .equals(v.getRootObjects(Root.class).iterator().next()
              .getLinks().get(0).getProtocol().getName());
    }));
    Assertions.assertTrue(assertView(getDefaultView(vsum, List.of(System.class, Root.class)), (View v) -> {
      // assert that the components belonging to the link in the system and the
      // corresponding entities in the root have the same name
      return v.getRootObjects(System.class).iterator().next()
          .getLinks().get(0).getComponents().get(0).getName()
          .equals(v.getRootObjects(Root.class).iterator().next()
              .getLinks().get(0).getEntities().get(0).getName())
          && v.getRootObjects(System.class).iterator().next()
              .getLinks().get(0).getComponents().get(1).getName()
              .equals(v.getRootObjects(Root.class).iterator().next()
                  .getLinks().get(0).getEntities().get(1).getName());
    }));
  }

  private void addSystem(VirtualModel vsum, Path projectPath) {
    CommittableView view = getDefaultView(vsum, List.of(System.class)).withChangeDerivingTrait();
    modifyView(view, (CommittableView v) -> {
      v.registerRoot(
          UncertaintyFactory.eINSTANCE.createUncertaintyAnnotationRepository(),
          URI.createFileURI(projectPath.toString() + "/example.model"));
    });
  }

  private void addRouter(VirtualModel vsum) {
    CommittableView view = getDefaultView(vsum, List.of(System.class)).withChangeDerivingTrait();
    modifyView(view, (CommittableView v) -> {
      var router = ModelFactory.eINSTANCE.createRouter();
      router.setName("specialname");
      v.getRootObjects(System.class).iterator().next().getComponents().add(router);
    });
  }

  private void addProtocol(VirtualModel vsum) {
    CommittableView view = getDefaultView(vsum, List.of(System.class)).withChangeDerivingTrait();
    modifyView(view, (CommittableView v) -> {
      var protocol = ModelFactory.eINSTANCE.createProtocol();
      protocol.setName("specialProtocolName");
      v.getRootObjects(System.class).iterator().next().getProtocols().add(protocol);
    });
  }

  private void addLink(VirtualModel vsum) {
    CommittableView view = getDefaultView(vsum, List.of(System.class)).withChangeDerivingTrait();
    modifyView(view, (CommittableView v) -> {
      var link = ModelFactory.eINSTANCE.createLink();
      link.setName("specialLinkname");
      link.getComponents().addAll(v.getRootObjects(System.class).iterator().next().getComponents());
      link.setProtocol(v.getRootObjects(System.class).iterator().next().getProtocols().get(0));
      v.getRootObjects(System.class).iterator().next().getLinks().add(link);
    });
  }

  private InternalVirtualModel createDefaultVirtualModel(Path projectPath) {
    InternalVirtualModel model = new VirtualModelBuilder()
        .withStorageFolder(projectPath)
        .withUserInteractorForResultProvider(new TestUserInteraction.ResultProvider(new TestUserInteraction()))
        .withChangePropagationSpecifications(new Model2Model2ChangePropagationSpecification())
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
