package pl.kaczmarek.aggregator.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import pl.kaczmarek.aggregator.AggregatorApplication;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packagesOf = AggregatorApplication.class, importOptions = {
        ImportOption.DoNotIncludeTests.class,
        ImportOption.DoNotIncludeJars.class
})
class ArchitectureTest {

    @ArchTest
    static final ArchRule servicesShouldOnlyBeAccessedByControllersOrConfig = classes()
            .that().resideInAPackage("..service..")
            .should().onlyBeAccessed().byAnyPackage("..controller..", "..service..", "..config..", "..architecture..");

    @ArchTest
    static final ArchRule controllersShouldNotBeAccessedByOtherLayers = classes()
            .that().resideInAPackage("..controller..")
            .should().onlyBeAccessed().byAnyPackage("..controller..", "..architecture..");

    @ArchTest
    static final ArchRule controllerNamingRule = classes()
            .that().resideInAPackage("..controller..")
            .should().haveSimpleNameEndingWith("Controller");

    @ArchTest
    static final ArchRule serviceNamingRule = classes()
            .that().resideInAPackage("..service..")
            .and().areNotInterfaces()
            .should().haveSimpleNameEndingWith("Service")
            .orShould().haveSimpleNameEndingWith("Impl")
            .orShould().haveSimpleNameEndingWith("Client")
            .orShould().haveSimpleNameEndingWith("Coordinator")
            .orShould().haveSimpleNameEndingWith("Mapper")
            .orShould().haveSimpleNameEndingWith("Resolver")
            .orShould().haveSimpleNameEndingWith("Instrumentation");

    @ArchTest
    static final ArchRule controllersShouldNotDependOnOtherControllers = noClasses()
            .that().resideInAPackage("..controller..")
            .should().dependOnClassesThat().resideInAPackage("..controller..");

    @ArchTest
    static final ArchRule controllersShouldBeAnnotatedWithRestController = classes()
            .that().resideInAPackage("..controller..")
            .and().haveSimpleNameEndingWith("Controller")
            .should().beAnnotatedWith("org.springframework.web.bind.annotation.RestController");

    @ArchTest
    static final ArchRule methodsInControllersShouldReturnResponseEntityOrCustomModel = classes()
            .that().resideInAPackage("..controller..")
            .should().onlyHaveDependentClassesThat().resideInAnyPackage("..controller..", "..model..", "java..", "org.springframework..");

    @ArchTest
    static final ArchRule servicesShouldBeAnnotatedWithService = classes()
            .that().resideInAPackage("..service..")
            .and().haveSimpleNameEndingWith("Service")
            .should().beAnnotatedWith("org.springframework.stereotype.Service");

    @ArchTest
    static final ArchRule springComponentsShouldNotUseFieldInjection = noClasses()
            .should().dependOnClassesThat().haveSimpleName("Autowired")
            .because("We should use constructor injection instead of field injection");

    @ArchTest
    static final ArchRule modelClassesShouldBeSerializableOrDtos = classes()
            .that().resideInAPackage("..model..")
            .should().onlyBeAccessed().byAnyPackage("..service..", "..controller..", "..model..", "..architecture..");

    @ArchTest
    static final ArchRule utilityClassesShouldHavePrivateConstructor = classes()
            .that().haveSimpleNameEndingWith("Utils")
            .or().haveSimpleNameEndingWith("Helper")
            .should().haveOnlyPrivateConstructors();

    @ArchTest
    static final ArchRule exceptionClassesShouldHaveExceptionSuffix = classes()
            .that().areAssignableTo(Throwable.class)
            .should().haveSimpleNameEndingWith("Exception");

    @ArchTest
    static final ArchRule configClassesShouldBeAnnotatedWithConfiguration = classes()
            .that().resideInAPackage("..config..")
            .should().beAnnotatedWith("org.springframework.context.annotation.Configuration")
            .orShould().beAnnotatedWith("org.springframework.boot.context.properties.ConfigurationProperties");

}
