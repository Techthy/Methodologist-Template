package tools.vitruv.methodologisttemplate.vsum.uncertainty;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;
import java.util.function.Function;

import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.EcoreUtil;
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
import tools.vitruv.change.propagation.ChangePropagationMode;
import tools.vitruv.change.testutils.TestUserInteraction;
import tools.vitruv.framework.views.CommittableView;
import tools.vitruv.framework.views.View;
import tools.vitruv.framework.views.ViewTypeFactory;
import tools.vitruv.framework.vsum.VirtualModel;
import tools.vitruv.framework.vsum.VirtualModelBuilder;
import tools.vitruv.framework.vsum.internal.InternalVirtualModel;
import uncertainty.*;

import mir.reactions.brakesystem2cad.Brakesystem2cadChangePropagationSpecification;
import mir.reactions.cad2brakesystem.Cad2brakesystemChangePropagationSpecification;
import mir.reactions.uncertainty2uncertainty.Uncertainty2uncertaintyChangePropagationSpecification;

public class AddAndRemoveUncertaintyWithManuallyTest {
    private static final Logger logger = org.slf4j.LoggerFactory
            .getLogger(AddAndRemoveUncertaintyWithManuallyTest.class);

    @BeforeAll
    static void setup() {
        Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put("*",
                new XMIResourceFactoryImpl());

    }

    @Test
    void addAndRemoveUncertaintyWithManualEdit(@TempDir Path tempDir) {
        System.out.println("****************************************************************************");
        System.out.println("addAndRemoveUncertaintyWithManualEdit Test started. \n");

        VirtualModel vsum = createDefaultVirtualModel(tempDir);

        // Prepare models and insert their respective root objects
        modifyView(getDefaultView(vsum, List.of(UncertaintyAnnotationRepository.class))
                .withChangeDerivingTrait(), (CommittableView v) -> {
                    v.registerRoot(
                            UncertaintyFactory.eINSTANCE
                                    .createUncertaintyAnnotationRepository(),
                            URI.createFileURI(tempDir.toString() + "/uncertainty.model"));

                });
        // Reaction will create a CADRepository
        modifyView(getDefaultView(vsum, List.of(Brakesystem.class)).withChangeDerivingTrait(),
                (CommittableView v) -> {
                    v.registerRoot(
                            BrakesystemFactory.eINSTANCE.createBrakesystem(),
                            URI.createFileURI(tempDir.toString() + "/brakesystem.model"));
                });

        // Add a BrakeDisk that in turn (by reactions) creates a Circle
        addBrakeDiscWithDiameter(vsum, tempDir, 120);

        // Assert: No uncertainties exist
        // This is a workaround since otherwise we encouter dangling references
        // The reason for this is unknown
        Assertions.assertTrue(
                assertView(getDefaultView(vsum, List.of(UncertaintyAnnotationRepository.class)),
                        (View v) -> {
                            return v.getRootObjects(UncertaintyAnnotationRepository.class)
                                    .iterator().next()
                                    .getUncertainties().isEmpty();
                        }));

        // Add two uncertainties to the brake disk
        CommittableView view2 = getDefaultView(vsum,
                List.of(UncertaintyAnnotationRepository.class, Brakesystem.class))
                .withChangeDerivingTrait();
        modifyView(view2, (CommittableView v) -> {
            var brakeDisk = v.getRootObjects(Brakesystem.class).iterator().next().getBrakeComponents()
                    .stream()
                    .filter(BrakeDisk.class::isInstance).map(BrakeDisk.class::cast)
                    .filter(d -> d.getDiameterInMM() == 120)
                    .findFirst().orElseThrow();

            // First uncertainty
            var uncertaintyLocation = UncertaintyFactory.eINSTANCE.createUncertaintyLocation();
            uncertaintyLocation.setLocation(UncertaintyLocationType.OUTCOME);
            uncertaintyLocation.setSpecification("FromDisk");
            uncertaintyLocation.getReferencesComponents().add(brakeDisk);

            var uncertainty = UncertaintyFactory.eINSTANCE.createUncertainty();
            uncertainty.setUncertaintyLocation(uncertaintyLocation);
            uncertainty.setKind(UncertaintyKind.MEASUREMENT_UNCERTAINTY);
            uncertainty.setReducability(ReducabilityLevel.IRREDUCIBLE);
            uncertainty.setNature(UncertaintyNature.ALEATORY);
            uncertainty.setSetManually(true);
            uncertainty.setId(generateRandomString());

            v.getRootObjects(UncertaintyAnnotationRepository.class).iterator().next()
                    .getUncertainties().add(uncertainty);

            // Trigger propagation
            brakeDisk.setSpecificationType(generateRandomString());

        });

        // Assert that two uncertainties exist;
        // One for the brake disk and one for the circle exist
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
                            return brakeDiskUncertainties == 1 && circleUncertainties == 1;
                        }));

        // Manually edit the circle Uncertainty
        CommittableView view3 = getDefaultView(vsum,
                List.of(UncertaintyAnnotationRepository.class))
                .withChangeDerivingTrait();
        modifyView(view3, (CommittableView v) -> {
            var circleUncertainty = v.getRootObjects(UncertaintyAnnotationRepository.class).iterator()
                    .next()
                    .getUncertainties().stream()
                    .filter(u -> u.getUncertaintyLocation().getReferencesComponents().stream()
                            .anyMatch(c -> c instanceof Circle
                                    && ((Circle) c).getRadius() == 60))
                    .findFirst().orElseThrow();

            circleUncertainty.setKind(UncertaintyKind.BELIEF_UNCERTAINTY);
            circleUncertainty.setSetManually(true);
            circleUncertainty.getUncertaintyLocation().setSpecification("FromCircle");

        });

        // Assert that there are only two uncertaintyLocations
        Assertions.assertTrue(
                assertView(getDefaultView(vsum, List.of(UncertaintyAnnotationRepository.class)),
                        (View v) -> {
                            var uncertainties = v.getRootObjects(
                                    UncertaintyAnnotationRepository.class)
                                    .iterator().next()
                                    .getUncertainties();
                            var uncertaintyLocations = uncertainties.stream()
                                    .map(u -> u.getUncertaintyLocation())
                                    .toList();

                            return uncertainties.size() == 2
                                    && uncertaintyLocations.size() == 2;

                        }));

        // Delete the uncertainty belonging to the brake disk
        modifyView(getDefaultView(vsum, List.of(UncertaintyAnnotationRepository.class, Brakesystem.class))
                .withChangeDerivingTrait(), (CommittableView v) -> {

                    var uncertainties = v.getRootObjects(UncertaintyAnnotationRepository.class)
                            .iterator().next()
                            .getUncertainties();
                    var uncertaintyToDelete = uncertainties.stream()
                            .filter(u -> u.getUncertaintyLocation()
                                    .getReferencesComponents().stream()
                                    .anyMatch(c -> c instanceof BrakeDisk))
                            .findFirst().orElseThrow();

                    var uncertaintyLocations = uncertainties.stream()
                            .map(u -> u.getUncertaintyLocation())
                            .toList();

                    System.out.println("Uncertainties: " + uncertainties);
                    System.out.println("UncertaintyLocations: " + uncertaintyLocations);

                    System.out.println("------");

                    Resource res = v.getRootObjects(UncertaintyAnnotationRepository.class)
                            .iterator().next()
                            .eResource();
                    List<EObject> allObjs = new java.util.ArrayList<>();
                    for (EObject root : res.getContents()) {
                        TreeIterator<EObject> it = root.eAllContents();
                        while (it.hasNext()) {
                            EObject obj = it.next();
                            allObjs.add(obj);
                            // process obj
                        }
                    }

                    System.out.println("All EObjects in resource:");
                    allObjs.forEach(obj -> System.out.println(obj));

                    System.out.println("------");

                    System.out.println("Uncertainty to delete: " + uncertaintyToDelete);
                    System.out
                            .println("UncertaintyLocation to delete: "
                                    + uncertaintyToDelete.getUncertaintyLocation());

                    EcoreUtil.delete(uncertaintyToDelete, true);

                    // Trigger propagation
                    v.getRootObjects(Brakesystem.class).iterator().next()
                            .getBrakeComponents().get(0).setSpecificationType("test");
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
