# Methodologist Template Project

<!-- TODO: Explain further what a methodolgist is and why we need this repo -->
This project is a template for the methodologists who are creating a V-SUM. 

## Getting Started

The Methodologist Template can be executed using the Maven build system. The project comes with a maven wrapper, so you can run it without installing Maven.
To build the project you can run the following command:

```bash
./mvnw clean verify
```

Verify that all tests are passing. The tests are located in the `vsum` folder.
Now you can start to modify the project to your needs. Or jump to the [Tutorial](#tutorial) section to get a quick start. First we will explain what tests are run and what they are testing.

### Tests

The tests test the vsum and its reactions.
The goal is to ensure that the reactions are keeping the model consistent.
Consider the following example taken from the `VSUMExampleTest.java` file:

```java
@Test
  void systemInsertionAndPropagationTest(@TempDir Path tempDir) {
    VirtualModel vsum = createDefaultVirtualModel(tempDir);
    addSystem(vsum, tempDir);
    // assert that the directly added System is present
    Assertions.assertEquals(1, getDefaultView(vsum, List.of(System.class)).getRootObjects().size());
    // as well as the Root that should be created by the Reactions, see templateReactions.reactions#14
    Assertions.assertEquals(1, getDefaultView(vsum, List.of(Root.class)).getRootObjects().size());
  }
```

In this testcase a system is added to the vsum. The test checks that the system is present in the view and that a root object is created by the reaction.  The reaction is defined in the `consistency` folder.

## Tutorial

For the following example make yourself familiar with the model. The model is located in the `model` folder for more information read the [Model](#model) section.

### Editing the Model and Testing Reactions

1. **Editing the Model** \
    The `model.ecore` file defines a `component` and a `device` and `server` which extend the `component` .
    Consider you're now a methodologist and you want to add a new `component` called `Router` which also extends the `component` .

2. **Keeping the Models Consistent** \
    Once you have added the `Router` to the model we now want to ensure that
    consistency is kept. Have a look at the reactions located in the `consistency` folder.
    The `ComponentInsertedIntoSystem` reaction is responsible for creating a corresponding `Entity` for each `Component` that is inserted into the system.

    The reaction defines when it is triggered, in this case when a `Component` is inserted into the system. It then calles a routine which restores the consistency. Here the routine `createAndInsertEntity` is called. With the `match` block all `components` without a matching `Entity` are selected. Then a new `Entity` is created. Afterwards all properties from the `Component` are set to the `Entity` . The `Entity` is then inserted into the `Root` . With the `addCorrespondenceBetween` method the `Component` and the `Entity` are linked.

    Since the `Router` is a `Component` it will be matched by the reaction and a corresponding `Entity` will be created. Therefore we don't need to add a new reaction for the `Router` .

3. **Adding a Test Case** \
    Now we want to ensure that the `Router` is correctly inserted into the system and that the reaction is triggered.
    For this we can use the existing test case `insertComponent` and add a new test case for the `Router` .  
    We also need to add another helper method `addRouter` that creates a `Router` and adds it to the system. For reference have a look at the `addComponent` method.
    The test case with the `addRouter` method should look like this:


    ```java
    @Test
    void insertRouter(@TempDir Path tempDir) {
        InternalVirtualModel vsum = createDefaultVirtualModel(tempDir);
        addSystem(vsum, tempDir);
        addRouter(vsum);
        Assertions.assertTrue(assertView(getDefaultView(vsum, List.of(System.class, Root.class)), (View v) -> {
        // assert that a component has been inserted, a entity has been created and that
        // both have the same name
        return v.getRootObjects(System.class).iterator().next()
            .getComponents().get(0).getName()
            .equals(v.getRootObjects(Root.class).iterator().next()
                .getEntities().get(0).getName());
        }));
    }
    ```

    This testcase asserts that a `Router` has been inserted into the system and an `Entity` has been created. It also checks that both have the same name.

### Writing a Reaction

In order to later be able to keep the links consistent we now want to add `Protocol` to the second ecore file.
This we will then keep consistent with the `Protocol` in the first ecore file using a reaction.

1. **Updating the model** \
    Add a `Protocol` class to the second ecore file.
    The `Protocol` should have a property name of type `EString` .
    Furthermore, the `Root` should have a list of `Protocol` objects.
    Once you have saved these changes to the model, don't forget to update the genmodel.

2. **Creating a Reaction** \
    Strongly inspired by the already existing `ComponentInsertedIntoSystem` reaction,
    we now want to create a reaction that creates a `Protocol` and adds it to the `Root` .
    The reaction should be triggered when a `Protocol` is inserted into the system. 
    The reaction should look like this:

    ```java
    reaction ProtocolInsertedIntoSystem {
        after element model::Protocol inserted in model::System[protocols]
        call createAndInsertProtocol(affectedEObject, newValue)
    }

    routine createAndInsertProtocol(model::System system, model::Protocol protocol) {
        match {
            require absence of model2::Protocol corresponding to protocol
            // retrieve the mRoot we added a correspondence in the createAndRegisterRoot routine in the update block (line 33 in this file)
            val mRoot = retrieve model2::Root corresponding to system
        }
        create {
            val mProtocol = new model2::Protocol
        }
        update {
        mProtocol.name = protocol.name
        mRoot.protocols.add(mProtocol)
            addCorrespondenceBetween(protocol, mProtocol)
        }
    }
    ```

3. **Adding a Test Case** \
    Now we want to ensure that the `Protocol` is correctly inserted into the system and that the reaction is triggered.
    For this we can use the existing test case `insertComponent` and add a new test case for the `Protocol` .  
    We also need to add another helper method `addProtocol` that creates a `Protocol` and adds it to the system. For reference have a look at the `addComponent` method.

Once you have done this, you can run the tests again and check that all tests are passing.

## Model

The `model` folder contains the model in the ecore format. Note that each ecore file is accompanied by a genmodel. The genmodel is used to generate the code. If you update the ecore model, you need to update the genmodel. How to edit and also (automatically) update the genmodel please refer to [this Tutorial by Lars Vogel](https://www.vogella.com/tutorials/EclipseEMF/article.html).

## Consistency

This folder contains the consistency specifications, like reactions.

## ViewType

This folder contains the definition of the view types. These are necessary to create views of the vsum.

## Vsum

This folder contains the VSUM

## Useful Links

Details about the build process and configurations can be found in the readmes of the relevant projects.

* <https://github.com/vitruv-tools/Maven-Build-Parent/blob/main/readme.md>
* <https://github.com/vitruv-tools/EMF-Template/blob/main/readme.md>
