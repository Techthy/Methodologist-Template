package tools.vitruv.methodologisttemplate.vsum;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import brakesystem.BrakeDisk;
import brakesystem.Brakesystem;
import brakesystem.BrakesystemFactory;
import cad.CADRepository;
import cad.CadFactory;
import cad.Circle;
import tools.vitruv.framework.views.CommittableView;
import tools.vitruv.framework.views.View;
import tools.vitruv.framework.vsum.VirtualModel;
import tools.vitruv.methodologisttemplate.vsum.uncertainty.UncertaintyTestUtil;
import uncertainty.UncertaintyAnnotationRepository;

/**
 * This class contains tests for the propagation of changes in the virtual
 * model.
 * It tests the insertion of root objects and the propagation of changes between
 * the Brakesystem and CADRepository.
 * 
 *
 */
public class PropagationTests {

	@BeforeAll
	static void setup() {
		Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put("*",
				new XMIResourceFactoryImpl());

	}

	@Test
	void RootObjectsInsertedAndPropagated(@TempDir Path tempDir) {
		VirtualModel vsum = UncertaintyTestUtil.createDefaultVirtualModel(tempDir);
		UncertaintyTestUtil.registerRootObjects(vsum, tempDir);

		Assertions.assertEquals(1,
				UncertaintyTestUtil.getDefaultView(vsum, List.of(Brakesystem.class)).getRootObjects().size());

		Assertions.assertEquals(1,
				UncertaintyTestUtil.getDefaultView(vsum, List.of(CADRepository.class)).getRootObjects().size());

		Assertions.assertEquals(1,
				UncertaintyTestUtil.getDefaultView(vsum, List.of(UncertaintyAnnotationRepository.class))
						.getRootObjects().size());

	}

	@Test
	void insertCylinderIntoCADRepositoryTest(@TempDir Path tempDir) {
		VirtualModel vsum = UncertaintyTestUtil.createDefaultVirtualModel(tempDir);
		UncertaintyTestUtil.registerRootObjects(vsum, tempDir);

		// Add a Circle to the CADRepository with a radius of 60
		modifyView(UncertaintyTestUtil.getDefaultView(vsum, List.of(CADRepository.class))
				.withChangeDerivingTrait(), (CommittableView v) -> {
					var circle = CadFactory.eINSTANCE.createCircle();
					circle.setRadius(60);
					v.getRootObjects(CADRepository.class).iterator().next().getCadElements().add(circle);
				});
		Assertions.assertTrue(
				assertView(UncertaintyTestUtil.getDefaultView(vsum,
						List.of(Brakesystem.class, CADRepository.class)),
						(View v) -> {
							BrakeDisk brakeDisk = (BrakeDisk) v
									.getRootObjects(Brakesystem.class)
									.iterator().next()
									.getBrakeComponents().get(0);
							Circle circle = (Circle) v.getRootObjects(
									CADRepository.class).iterator()
									.next()
									.getCadElements().get(0);
							return brakeDisk.getDiameterInMM() == circle.getRadius() * 2;

						}));
	}

	@Test
	void insertBrakeDiscIntoBrakesystemTest(@TempDir Path tempDir) {
		VirtualModel vsum = UncertaintyTestUtil.createDefaultVirtualModel(tempDir);
		UncertaintyTestUtil.registerRootObjects(vsum, tempDir);

		modifyView(UncertaintyTestUtil.getDefaultView(vsum, List.of(Brakesystem.class))
				.withChangeDerivingTrait(), (CommittableView v) -> {
					var brakeDisc = BrakesystemFactory.eINSTANCE.createBrakeDisk();
					brakeDisc.setDiameterInMM(120);
					v.getRootObjects(Brakesystem.class).iterator().next().getBrakeComponents().add(brakeDisc);
				});

		Assertions.assertTrue(
				assertView(UncertaintyTestUtil.getDefaultView(vsum,
						List.of(Brakesystem.class, CADRepository.class)),
						(View v) -> {
							BrakeDisk brakeDisk = (BrakeDisk) v
									.getRootObjects(Brakesystem.class)
									.iterator().next()
									.getBrakeComponents().get(0);
							Circle circle = (Circle) v.getRootObjects(
									CADRepository.class).iterator()
									.next()
									.getCadElements().get(0);
							return brakeDisk.getDiameterInMM() == circle
									.getRadius() * 2;

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
