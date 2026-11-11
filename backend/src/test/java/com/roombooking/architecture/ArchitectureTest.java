package com.roombooking.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Package-by-feature boundaries. Each feature lives in {@code com.roombooking.<feature>} with:
 *
 * <ul>
 *   <li>{@code domain}: the rules, in plain Java with no framework;
 *   <li>{@code web}: controllers implementing the interfaces generated from the Contract;
 *   <li>{@code persistence}: adapters to the database.
 * </ul>
 */
@AnalyzeClasses(packages = "com.roombooking", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String CONTRACT = "com.roombooking.contract..";

    @ArchTest
    static final ArchRule domainIsPlainJava = classes()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .onlyDependOnClassesThat()
            .resideInAnyPackage("java..", "..domain..")
            .because("domain rules live in plain classes, free of frameworks and of the Contract");

    @ArchTest
    static final ArchRule domainDoesNotKnowItsAdapters = noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..web..", "..persistence..");

    @ArchTest
    static final ArchRule onlyWebUsesTheContract = noClasses()
            .that()
            .resideOutsideOfPackages("..web..", CONTRACT)
            .should()
            .dependOnClassesThat()
            .resideInAPackage(CONTRACT)
            .because("the generated Contract types stay at the HTTP edge");

    @ArchTest
    static final ArchRule webAndPersistenceDoNotTalk = noClasses()
            .that()
            .resideInAPackage("..web..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..persistence..")
            .because("web reaches persistence only through the domain");

    @ArchTest
    static final ArchRule controllersLiveInWeb = classes()
            .that()
            .areAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
            .should()
            .resideInAPackage("..web..");

    @ArchTest
    static final ArchRule featuresAreFreeOfCycles =
            slices().matching("com.roombooking.(*)..").should().beFreeOfCycles();
}
