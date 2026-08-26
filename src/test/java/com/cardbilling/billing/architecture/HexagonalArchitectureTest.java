package com.cardbilling.billing.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.Architectures;

/**
 * The hexagonal rules, enforced rather than described. A build that breaks one of these fails the
 * same way a broken test does, which is the difference between "we follow this architecture" and
 * something a reader can verify from a green build.
 */
@AnalyzeClasses(packages = "com.cardbilling.billing", importOptions = ImportOption.DoNotIncludeTests.class)
class HexagonalArchitectureTest {

    private static final String DOMAIN = "com.cardbilling.billing.domain..";
    private static final String APPLICATION = "com.cardbilling.billing.application..";
    private static final String INFRASTRUCTURE = "com.cardbilling.billing.infrastructure..";
    private static final String WEB = "com.cardbilling.billing.infrastructure.web..";
    private static final String PERSISTENCE = "com.cardbilling.billing.infrastructure.persistence..";

    @ArchTest
    static final ArchRule layersDependInwardsOnly = Architectures.layeredArchitecture()
            .consideringOnlyDependenciesInAnyPackage("com.cardbilling.billing..")
            .layer("Domain").definedBy(DOMAIN)
            .layer("Application").definedBy(APPLICATION)
            .layer("Infrastructure").definedBy(INFRASTRUCTURE)
            .whereLayer("Infrastructure").mayNotBeAccessedByAnyLayer()
            .whereLayer("Application").mayOnlyBeAccessedByLayers("Infrastructure")
            .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Infrastructure");

    @ArchTest
    static final ArchRule domainDependsOnNeitherApplicationNorInfrastructure = noClasses()
            .that().resideInAPackage(DOMAIN)
            .should().dependOnClassesThat().resideInAnyPackage(APPLICATION, INFRASTRUCTURE)
            .because("the domain is the innermost layer - it knows nothing about what surrounds it");

    @ArchTest
    static final ArchRule applicationDoesNotDependOnInfrastructure = noClasses()
            .that().resideInAPackage(APPLICATION)
            .should().dependOnClassesThat().resideInAPackage(INFRASTRUCTURE)
            .because("use cases reach the outside world through their own ports, never the other way round");

    @ArchTest
    static final ArchRule controllersDoNotReachIntoPersistence = noClasses()
            .that().resideInAPackage(WEB)
            .should().dependOnClassesThat().resideInAPackage(PERSISTENCE)
            .because("controllers call use cases, never a repository directly");

    /**
     * The domain is plain Java. This is what makes the layering above meaningful rather than
     * decorative: a domain type that carried JPA annotations would be a persistence model wearing
     * a domain model's name.
     */
    @ArchTest
    static final ArchRule domainIsFreeOfFrameworks = noClasses()
            .that().resideInAPackage(DOMAIN)
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "jakarta.persistence..", "jakarta.validation..",
                    "com.fasterxml.jackson..", "io.swagger..")
            .because("domain types are pure Java - no Spring, no JPA, no serialisation concerns");

    @ArchTest
    static final ArchRule persistenceEntitiesAreNamedForWhatTheyAre = classes()
            .that().areAnnotatedWith(jakarta.persistence.Entity.class)
            .should().haveSimpleNameEndingWith("Entity")
            .andShould().resideInAPackage(PERSISTENCE)
            .because("a JPA entity should be recognisable as one, and belongs beside the mapping it serves");

    /**
     * The other direction of the same rule, so the naming can't drift the other way either: a
     * class called {@code SomethingEntity} that quietly stopped being mapped would otherwise sit
     * there looking like it still was.
     */
    @ArchTest
    static final ArchRule classesNamedEntityAreMappedEntities = classes()
            .that().haveSimpleNameEndingWith("Entity")
            .and().resideOutsideOfPackage(DOMAIN)
            .should().beAnnotatedWith(jakarta.persistence.Entity.class)
            .because("a name ending in Entity should mean the class is actually mapped");

    @ArchTest
    static final ArchRule portsAreInterfaces = classes()
            .that().resideInAPackage("com.cardbilling.billing.application.port..")
            .should().beInterfaces()
            .because("a port is a contract the infrastructure implements, not an implementation");

    @ArchTest
    static final ArchRule useCasesAreNotCalledServices = noClasses()
            .that().resideInAPackage(APPLICATION)
            .should().haveSimpleNameEndingWith("Service")
            .because("this layer is named for what each class does - CloseInvoiceCycleUseCase, not InvoiceService");
}
