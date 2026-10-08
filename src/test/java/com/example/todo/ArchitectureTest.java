package com.example.todo;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Test tự kiểm tra kiến trúc Hexagonal. Ai đó import nhầm Spring vào domain,
 * hay cho controller gọi thẳng repository, thì build đỏ ngay.
 */
@AnalyzeClasses(packages = "com.example.todo", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule domainIsPureJava = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..", "jakarta..", "..application..", "..adapter..", "..config..")
            .because("domain là lõi nghiệp vụ, không được phụ thuộc framework hay lớp ngoài");

    @ArchTest
    static final ArchRule applicationOnlyKnowsDomainAndJava = classes()
            .that().resideInAPackage("..application..")
            .should().onlyDependOnClassesThat().resideInAnyPackage(
                    "..application..", "..domain..", "java..", "org.springframework.transaction..")
            .because("application chỉ được dùng domain, Java chuẩn và @Transactional");

    @ArchTest
    static final ArchRule webDoesNotTouchPersistence = noClasses()
            .that().resideInAPackage("..adapter.in..")
            .should().dependOnClassesThat().resideInAPackage("..adapter.out..")
            .because("controller phải đi qua use case, không gọi thẳng tầng database");

    @ArchTest
    static final ArchRule webTalksToUseCasesNotServices = noClasses()
            .that().resideInAPackage("..adapter.in..")
            .should().dependOnClassesThat().resideInAPackage("..application.service..")
            .because("controller chỉ biết interface use case (cổng vào), không biết lớp service cụ thể");

    @ArchTest
    static final ArchRule securityAdapterIsOnlyUsedThroughPorts = noClasses()
            .that().resideOutsideOfPackages("..adapter.out.security..", "..config..")
            .should().dependOnClassesThat().resideInAPackage("..adapter.out.security..")
            .because("BCrypt và JWT chỉ được dùng qua PasswordHasherPort và TokenPort");

    @ArchTest
    static final ArchRule kafkaOnlyInMessagingAdapters = noClasses()
            .that().resideOutsideOfPackages("..adapter.in.messaging..", "..adapter.out.messaging..", "..config..")
            .should().dependOnClassesThat().resideInAPackage("org.springframework.kafka..")
            .because("Kafka là chi tiết hạ tầng, chỉ adapter messaging và cấu hình được biết");

    @ArchTest
    static final ArchRule webSocketOnlyInRealtimeAdapter = noClasses()
            .that().resideOutsideOfPackages("..adapter.in.realtime..", "..config..")
            .should().dependOnClassesThat().resideInAPackage("org.springframework.web.socket..")
            .because("WebSocket là chi tiết giao thức, chỉ adapter realtime và cấu hình được biết");

    @ArchTest
    static final ArchRule persistenceDoesNotTouchWeb = noClasses()
            .that().resideInAPackage("..adapter.out..")
            .should().dependOnClassesThat().resideInAPackage("..adapter.in..")
            .because("hai adapter không được biết nhau");
}
