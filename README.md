# Methodologist Template Project

<!-- TODO: Explain further what a methodolgist is and why we need this repo -->
This project is a template for the methodologists who are creating a V-SUM.

## Getting Started

The Methodologist Template can be executed using the Maven build system. The project comes with a maven wrapper, so you can run it without installing Maven.
To build the project you can run the following command:

```bash
./mvnw clean verify
```

Verify that all tests are passing. Now you can start to modify the project to your needs. For demonstration purposes and to give a basic introduction to the project we describe an example scenario in the following.

### Example Scenario

* Youre the methodologist
* make familiar with the provided model
* add a new device which parent is a compenent (Modem)
* add new Test Case that ensures that the Modem is reconized correctly
* create new reaction that links the links (TODO: check with IDs?!)

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
