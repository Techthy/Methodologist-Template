package tools.vitruv.methodologisttemplate.vsum;

import tools.vitruv.framework.vsum.VirtualModelBuilder;
import tools.vitruv.framework.vsum.internal.InternalVirtualModel;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import java.util.function.Function;

import org.apache.logging.log4j.core.config.plugins.PluginAliases;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import brakesystem.BrakeDisk;
import brakesystem.Brakesystem;
import brakesystem.BrakesystemFactory;
import uncertainty.ReducabilityLevel;
import uncertainty.Uncertainty;
import uncertainty.UncertaintyAnnotationRepository;
import cad.CADElement;
import cad.CADRepository;
import cad.Circle;
import cad.CadFactory;
import uncertainty.UncertaintyFactory;
import uncertainty.UncertaintyKind;
import uncertainty.UncertaintyLocationType;
import uncertainty.UncertaintyNature;
import mir.reactions.uncertainty2uncertainty.Uncertainty2uncertaintyChangePropagationSpecification;
import mir.reactions.brakesystem2cad.Brakesystem2cadChangePropagationSpecification;
import mir.reactions.cad2brakesystem.Cad2brakesystemChangePropagationSpecification;
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
  void createUncertaintyManuallyPropagateToSingleCorrespondingEntity(@TempDir Path tempDir) {
    System.out.println("****************************************************************************");
    System.out.println("createUncertaintyManuallyPropagateToSingleCorrespondingEntity Test started. \n");

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

    addBrakeDisc(vsum, tempDir);
    Assertions
        .assertTrue(assertView(getDefaultView(vsum, List.of(Brakesystem.class, CADRepository.class)), (View v) -> {
          BrakeDisk brakeDisk = (BrakeDisk) v.getRootObjects(Brakesystem.class).iterator().next()
              .getBrakeComponents().get(0);
          Circle circle = (Circle) v.getRootObjects(CADRepository.class).iterator().next().getCadElements().get(0);
          return brakeDisk.getDiameterInMM() == circle.getRadius() * 2;
        }));

    System.out.println("BrakeDisk created manually, Cricle in CAD created from reaction. \n");

    addUncertaintyAnnotationRepository(vsum, tempDir);

    Assertions.assertEquals(1,
        getDefaultView(vsum, List.of(UncertaintyAnnotationRepository.class)).getRootObjects().size());

    System.out.println("UncertaintyAnnotationRepository created manually. \n");

    addUncertainty(vsum, tempDir);

    Assertions
        .assertTrue(assertView(getDefaultView(vsum, List.of(UncertaintyAnnotationRepository.class)), (View v) -> {

          int s = v.getRootObjects(UncertaintyAnnotationRepository.class).iterator().next().getUncertainties().size();
          return s == 2;

        }));

    Assertions
        .assertTrue(assertView(getDefaultView(vsum, List.of(UncertaintyAnnotationRepository.class)), (View v) -> {

          Uncertainty uncertainty2 = v.getRootObjects(UncertaintyAnnotationRepository.class).iterator().next()
              .getUncertainties().get(1);
          EObject ref = uncertainty2.getUncertaintyLocation().getReferencesComponents().get(0);
          return ref instanceof Circle;
        }));

    System.out.println(
        "Uncertainty for brakedisk created manually, Uncertainty consistently added to circle automatically. \n");
  }

  @Test
  void createUncertaintyManuallyPropagateToSingleCorrespondingEntityNoOtherEntitiesAffected(@TempDir Path tempDir) {
    System.out.println("****************************************************************************");
    System.out.println(
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

    System.out.println("BrakeDisk diameter 50mm and 120mm created manually, Cricles in CAD created from reaction. \n");

    Assertions
        .assertTrue(assertView(getDefaultView(vsum, List.of(Brakesystem.class, CADRepository.class)), (View v) -> {
          var brakeComponents = v.getRootObjects(Brakesystem.class).iterator().next().getBrakeComponents();
          var brakeDiscs = brakeComponents.stream()
              .filter(BrakeDisk.class::isInstance)
              .map(BrakeDisk.class::cast)
              .toList();
          var circles = v.getRootObjects(CADRepository.class).iterator().next().getCadElements().stream()
              .filter(Circle.class::isInstance)
              .map(Circle.class::cast)
              .toList();

          return brakeDiscs.size() == 2 &&
              circles.size() == 2 &&
              brakeDiscs.stream().anyMatch(d -> d.getDiameterInMM() == 120) &&
              brakeDiscs.stream().anyMatch(d -> d.getDiameterInMM() == 50) &&
              circles.stream().anyMatch(c -> ((Circle) c).getRadius() == 60) &&
              circles.stream().anyMatch(c -> ((Circle) c).getRadius() == 25);
        }));

    System.out.println("BrakeDiscs and circles exist in VSUM. \n");

    // Add UncertaintyAnnotationRepository
    addUncertaintyAnnotationRepository(vsum, tempDir);

    // Add uncertainty only to the 120mm BrakeDisk
    CommittableView view = getDefaultView(vsum, List.of(UncertaintyAnnotationRepository.class, Brakesystem.class))
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
    Assertions.assertTrue(assertView(getDefaultView(vsum, List.of(UncertaintyAnnotationRepository.class)), (View v) -> {
      return v.getRootObjects(UncertaintyAnnotationRepository.class).iterator().next()
          .getUncertainties().size() == 2;
    }));

    // Assert: One uncertainty points to a BrakeDisk, and one to a Circle with
    // radius 60
    Assertions.assertTrue(assertView(getDefaultView(vsum, List.of(UncertaintyAnnotationRepository.class)), (View v) -> {
      var uncertainties = v.getRootObjects(UncertaintyAnnotationRepository.class).iterator().next().getUncertainties();

      boolean hasBrakeDisk120 = uncertainties.stream()
          .anyMatch(u -> u.getUncertaintyLocation().getReferencesComponents().stream()
              .anyMatch(c -> c instanceof BrakeDisk && ((BrakeDisk) c).getDiameterInMM() == 120));

      boolean hasCircle60 = uncertainties.stream()
          .anyMatch(u -> u.getUncertaintyLocation().getReferencesComponents().stream()
              .anyMatch(c -> c instanceof Circle && ((Circle) c).getRadius() == 60));

      boolean noCircle25 = uncertainties.stream()
          .noneMatch(u -> u.getUncertaintyLocation().getReferencesComponents().stream()
              .anyMatch(c -> c instanceof Circle && ((Circle) c).getRadius() == 25));

      return hasBrakeDisk120 && hasCircle60 && noCircle25;
    }));
  }

  @Test
  void biDirectionalUncertaintyPropagationBetweenBrakeDiskAndCircle(@TempDir Path tempDir) {
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

      v.getRootObjects(UncertaintyAnnotationRepository.class).iterator().next().getUncertainties().add(uncertainty);
    });

    // STEP 2: Add a different Uncertainty to the BrakeDisk (should propagate to
    // Circle)
    CommittableView view2 = getDefaultView(vsum, List.of(UncertaintyAnnotationRepository.class, Brakesystem.class))
        .withChangeDerivingTrait();
    modifyView(view2, (CommittableView v) -> {
      var brakeDisk = v.getRootObjects(Brakesystem.class).iterator().next().getBrakeComponents().stream()
          .filter(BrakeDisk.class::isInstance).map(BrakeDisk.class::cast)
          .filter(d -> d.getDiameterInMM() == 120)
          .findFirst().orElseThrow();

      var uncertaintyLocation = UncertaintyFactory.eINSTANCE.createUncertaintyLocation();
      uncertaintyLocation.setLocation(UncertaintyLocationType.PARAMETER);
      uncertaintyLocation.setSpecification("FromDisk");
      uncertaintyLocation.getReferencesComponents().add(brakeDisk);

      var uncertainty = UncertaintyFactory.eINSTANCE.createUncertainty();
      uncertainty.setUncertaintyLocation(uncertaintyLocation);
      uncertainty.setKind(UncertaintyKind.BELIEF_UNCERTAINTY);
      uncertainty.setReducability(ReducabilityLevel.UNKNOWN);
      uncertainty.setNature(UncertaintyNature.ALEATORY);

      // Trigger propagation
      brakeDisk.setSpecificationType(generateRandomString());

      v.getRootObjects(UncertaintyAnnotationRepository.class).iterator().next().getUncertainties().add(uncertainty);
    });

    // FINAL ASSERTION: Each of the 120mm BrakeDisk and 60-radius Circle has 2
    // uncertainties
    Assertions.assertTrue(assertView(getDefaultView(vsum, List.of(UncertaintyAnnotationRepository.class)), (View v) -> {
      var uncertainties = v.getRootObjects(UncertaintyAnnotationRepository.class).iterator().next().getUncertainties();
      System.out.println("Number of Uncertainties: " + uncertainties.size());

      long brakeDiskUncertainties = uncertainties.stream()
          .filter(u -> u.getUncertaintyLocation().getReferencesComponents().stream()
              .anyMatch(c -> c instanceof BrakeDisk && ((BrakeDisk) c).getDiameterInMM() == 120))
          .count();
      System.out.println("brakeDiskUncertainties: " + brakeDiskUncertainties);

      long circleUncertainties = uncertainties.stream()
          .filter(u -> u.getUncertaintyLocation().getReferencesComponents().stream()
              .anyMatch(c -> c instanceof Circle && ((Circle) c).getRadius() == 60))
          .count();
      System.out.println("circleUncertainties: " + circleUncertainties);

      boolean specsPresent = uncertainties.stream()
          .filter(u -> u.getUncertaintyLocation().getReferencesComponents().stream()
              .anyMatch(c -> (c instanceof BrakeDisk && ((BrakeDisk) c).getDiameterInMM() == 120) ||
                  (c instanceof Circle && ((Circle) c).getRadius() == 60)))
          .map(u -> u.getUncertaintyLocation().getSpecification())
          .collect(java.util.stream.Collectors.toSet())
          .containsAll(List.of("FromDisk", "FromCircle"));

      return brakeDiskUncertainties == 2 && circleUncertainties == 2 && specsPresent;
    }));
  }

  @Test
  void biDirectionalUncertaintyPropagationBetweenBrakeDiskAndCircleSameUncertaintyNotAutomaticallyCreated(
      @TempDir Path tempDir) {
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

      v.getRootObjects(UncertaintyAnnotationRepository.class).iterator().next().getUncertainties().add(uncertainty);
    });

    // STEP 2: Add a different Uncertainty to the BrakeDisk (should propagate to
    // Circle)
    CommittableView view2 = getDefaultView(vsum, List.of(UncertaintyAnnotationRepository.class, Brakesystem.class))
        .withChangeDerivingTrait();
    modifyView(view2, (CommittableView v) -> {
      var brakeDisk = v.getRootObjects(Brakesystem.class).iterator().next().getBrakeComponents().stream()
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

      v.getRootObjects(UncertaintyAnnotationRepository.class).iterator().next().getUncertainties().add(uncertainty);
    });

    // FINAL ASSERTION: The brakedisc has two uncertainties, one automatically
    // added, one manually added.
    // The circle has only one, as the parameters are equal => No new uncertainty
    // added automatically.
    // Attention: uncertaintyLocation is currently not part of the equal check.
    // uncertainties
    Assertions.assertTrue(assertView(getDefaultView(vsum, List.of(UncertaintyAnnotationRepository.class)), (View v) -> {
      var uncertainties = v.getRootObjects(UncertaintyAnnotationRepository.class).iterator().next().getUncertainties();
      System.out.println("Number of Uncertainties: " + uncertainties.size());

      long brakeDiskUncertainties = uncertainties.stream()
          .filter(u -> u.getUncertaintyLocation().getReferencesComponents().stream()
              .anyMatch(c -> c instanceof BrakeDisk && ((BrakeDisk) c).getDiameterInMM() == 120))
          .count();
      System.out.println("brakeDiskUncertainties: " + brakeDiskUncertainties);

      long circleUncertainties = uncertainties.stream()
          .filter(u -> u.getUncertaintyLocation().getReferencesComponents().stream()
              .anyMatch(c -> c instanceof Circle && ((Circle) c).getRadius() == 60))
          .count();
      System.out.println("circleUncertainties: " + circleUncertainties);

      boolean specsPresent = uncertainties.stream()
          .filter(u -> u.getUncertaintyLocation().getReferencesComponents().stream()
              .anyMatch(c -> (c instanceof BrakeDisk && ((BrakeDisk) c).getDiameterInMM() == 120) ||
                  (c instanceof Circle && ((Circle) c).getRadius() == 60)))
          .map(u -> u.getUncertaintyLocation().getSpecification())
          .collect(java.util.stream.Collectors.toSet())
          .containsAll(List.of("FromDisk", "FromCircle"));

      return brakeDiskUncertainties == 2 && circleUncertainties == 1 && specsPresent;
    }));
  }

  @Test
  void addUncertaintyAndRemovePropagation(@TempDir Path tempDir) {
    VirtualModel vsum = createDefaultVirtualModel(tempDir);

    CommittableView brakeSystemView = getDefaultView(vsum, List.of(Brakesystem.class))
        .withChangeDerivingTrait();
    CommittableView cadRepositoryView = getDefaultView(vsum, List.of(CADRepository.class))
        .withChangeDerivingTrait();
    CommittableView uncertaintyView = getDefaultView(vsum, List.of(UncertaintyAnnotationRepository.class))
        .withChangeDerivingTrait();

    modifyView(uncertaintyView, (CommittableView v) -> {
      v.registerRoot(
          UncertaintyFactory.eINSTANCE.createUncertaintyAnnotationRepository(),
          URI.createFileURI(tempDir.toString() + "/uncertainty.model"));

    });

    modifyView(brakeSystemView, (CommittableView v) -> {
      v.registerRoot(
          BrakesystemFactory.eINSTANCE.createBrakesystem(),
          URI.createFileURI(tempDir.toString() + "/brakesystem.model"));
    });

    addBrakeDiscWithDiameter(vsum, tempDir, 120);
    System.out.println("UncertaintyAnnotationRepository created. \n");

    CommittableView view2 = getDefaultView(vsum, List.of(UncertaintyAnnotationRepository.class, Brakesystem.class))
        .withChangeDerivingTrait();
    modifyView(view2, (CommittableView v) -> {
      var brakeDisk = v.getRootObjects(Brakesystem.class).iterator().next().getBrakeComponents().stream()
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

      v.getRootObjects(UncertaintyAnnotationRepository.class).iterator().next().getUncertainties().add(uncertainty);
    });

    System.out.println("Uncertainty created. \n");

    // Assert: Exactly two uncertainties exist (one manually added, one propagated)
    Assertions.assertTrue(assertView(getDefaultView(vsum, List.of(UncertaintyAnnotationRepository.class)), (View v) -> {
      System.out.println("Number of Uncertainties: "
          + v.getRootObjects(UncertaintyAnnotationRepository.class).iterator().next().getUncertainties().size());
      return v.getRootObjects(UncertaintyAnnotationRepository.class).iterator().next()
          .getUncertainties().size() == 2;
    }));

    CommittableView lel = getDefaultView(vsum, List.of(UncertaintyAnnotationRepository.class, Brakesystem.class))
        .withChangeDerivingTrait();
    modifyView(lel, (CommittableView v) -> {
      System.out.println("UNCERTAINTY LOCATION REF: "
          + v.getRootObjects(UncertaintyAnnotationRepository.class).iterator().next()
              .getUncertainties().get(0).getUncertaintyLocation().getReferencesComponents().get(0));

      v.getRootObjects(UncertaintyAnnotationRepository.class).iterator().next()
          .getUncertainties().remove(0);
      System.out.println("Uncertainty removed");
      v.getRootObjects(Brakesystem.class).iterator().next()
          .getBrakeComponents().get(0).setSpecificationType("test");
    });

    Assertions.assertTrue(assertView(getDefaultView(vsum,
        List.of(UncertaintyAnnotationRepository.class)), (View v) -> {
          var uncertainties = v.getRootObjects(UncertaintyAnnotationRepository.class).iterator().next()
              .getUncertainties();

          var locations = uncertainties.stream()
              .map(u -> u.getUncertaintyLocation())
              .toList();
          System.out.println("Number of Uncertainties: " + uncertainties.size());
          System.out.println("Number of locations: " + locations.size());
          return locations.size() == 0 && uncertainties.size() == 0;
        }));

    Assertions.assertTrue(assertView(getDefaultView(vsum,
        List.of(Brakesystem.class)), (View v) -> {
          var brakeComponents = v.getRootObjects(Brakesystem.class).iterator().next()
              .getBrakeComponents();
          System.out.println("Number of Uncertainties: " + brakeComponents.size());
          return brakeComponents.size() == 1;
        }));

    Assertions.assertTrue(assertView(getDefaultView(vsum,
        List.of(CADRepository.class)), (View v) -> {
          var CADElements = v.getRootObjects(CADRepository.class).iterator().next()
              .getCadElements();
          System.out.println("Number of Uncertainties: " + CADElements.size());
          return CADElements.size() == 1;
        }));

    // CommittableView uncertaintyView1 = getDefaultView(vsum,
    // List.of(UncertaintyAnnotationRepository.class))
    // .withChangeDerivingTrait();

    // modifyView(uncertaintyView1, (CommittableView v) -> {
    // // Remove the uncertainty

    // System.out.println("Removing uncertainty");
    // System.out
    // .println("Uncertainties before: " +
    // v.getRootObjects(UncertaintyAnnotationRepository.class).iterator().next()
    // .getUncertainties().get(0).getUncertaintyLocation().getReferencesComponents().get(0));

    // v.getRootObjects(UncertaintyAnnotationRepository.class).iterator().next()
    // .getUncertainties().remove(0);
    // });

    // // Assert: The uncertainty is removed from the
    // UncertaintyAnnotationRepository
    // Assertions.assertTrue(assertView(getDefaultView(vsum,
    // List.of(UncertaintyAnnotationRepository.class)), (View v) -> {
    // var uncertainties =
    // v.getRootObjects(UncertaintyAnnotationRepository.class).iterator().next().getUncertainties();
    // System.out.println("Number of Uncertainties: " + uncertainties.size());

    // // Make sure that the uncertainty is deleted
    // return uncertainties.size() == 0;
    // }));

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

  @Test
  void CADRepositoryInsertationAndPropagationTest(@TempDir Path tempDir) {
    VirtualModel vsum = createDefaultVirtualModel(tempDir);
    addCADRepository(vsum, tempDir);
    // assert that the directly added System is present
    Assertions.assertEquals(1,
        getDefaultView(vsum, List.of(CADRepository.class)).getRootObjects().size());
    Assertions.assertEquals(1,
        getDefaultView(vsum, List.of(Brakesystem.class)).getRootObjects().size());
  }

  @Test
  void insertCircleIntoCADRepositoryTest(@TempDir Path tempDir) {
    VirtualModel vsum = createDefaultVirtualModel(tempDir);
    addCADRepository(vsum, tempDir);
    addCircle(vsum, tempDir);
    Assertions
        .assertTrue(assertView(getDefaultView(vsum, List.of(Brakesystem.class, CADRepository.class)), (View v) -> {
          BrakeDisk brakeDisk = (BrakeDisk) v.getRootObjects(Brakesystem.class).iterator().next()
              .getBrakeComponents().get(0);
          Circle circle = (Circle) v.getRootObjects(CADRepository.class).iterator().next().getCadElements().get(0);
          return brakeDisk.getDiameterInMM() == circle.getRadius() * 2;

        }));
  }

  @Test
  void insertBrakeDiscIntoBrakesystemTest(@TempDir Path tempDir) {
    VirtualModel vsum = createDefaultVirtualModel(tempDir);
    addBrakesystem(vsum, tempDir);
    addBrakeDisc(vsum, tempDir);
    Assertions
        .assertTrue(assertView(getDefaultView(vsum, List.of(Brakesystem.class, CADRepository.class)), (View v) -> {
          BrakeDisk brakeDisk = (BrakeDisk) v.getRootObjects(Brakesystem.class).iterator().next()
              .getBrakeComponents().get(0);
          Circle circle = (Circle) v.getRootObjects(CADRepository.class).iterator().next().getCadElements().get(0);
          return brakeDisk.getDiameterInMM() == circle.getRadius() * 2;

        }));
  }

  private void addUncertainty(VirtualModel vsum, Path projectPath) {
    CommittableView view = getDefaultView(vsum, List.of(UncertaintyAnnotationRepository.class, Brakesystem.class))
        .withChangeDerivingTrait();
    modifyView(view, (CommittableView v) -> {
      var uncertaintyLocation = UncertaintyFactory.eINSTANCE.createUncertaintyLocation();
      uncertaintyLocation.setLocation(UncertaintyLocationType.PARAMETER);
      uncertaintyLocation.setSpecification("Diameter");
      uncertaintyLocation.getReferencesComponents()
          .add(v.getRootObjects(Brakesystem.class).iterator().next().getBrakeComponents().get(0));
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

  private void addCADRepository(VirtualModel vsum, Path projectPath) {
    CommittableView view = getDefaultView(vsum, List.of(CADRepository.class))
        .withChangeDerivingTrait();
    modifyView(view, (CommittableView v) -> {
      v.registerRoot(
          CadFactory.eINSTANCE.createCADRepository(),
          URI.createFileURI(projectPath.toString() + "/example.model"));
    });
  }

  private void addCircle(VirtualModel vsum, Path projectPath) {
    CommittableView view = getDefaultView(vsum, List.of(CADRepository.class))
        .withChangeDerivingTrait();
    modifyView(view, (CommittableView v) -> {
      var circle = CadFactory.eINSTANCE.createCircle();
      circle.setRadius(60);
      v.getRootObjects(CADRepository.class).iterator().next().getCadElements().add(circle);
    });
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
        .withUserInteractorForResultProvider(new TestUserInteraction.ResultProvider(new TestUserInteraction()))
        .withChangePropagationSpecification(new Brakesystem2cadChangePropagationSpecification())
        .withChangePropagationSpecification(new Uncertainty2uncertaintyChangePropagationSpecification())
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
