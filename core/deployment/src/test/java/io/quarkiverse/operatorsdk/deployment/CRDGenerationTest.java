package io.quarkiverse.operatorsdk.deployment;

import static io.quarkiverse.operatorsdk.deployment.CRDGeneration.shouldApply;
import static io.quarkiverse.operatorsdk.deployment.CRDGeneration.shouldGenerate;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.quarkus.runtime.LaunchMode;

class CRDGenerationTest {

    public static Stream<Arguments> shouldGenerateShouldWork() {
        return Stream.of(
                // default cases
                Arguments.of(Optional.empty(), Optional.empty(), LaunchMode.NORMAL, true),
                Arguments.of(Optional.empty(), Optional.empty(), LaunchMode.DEVELOPMENT, true),
                Arguments.of(Optional.empty(), Optional.empty(), LaunchMode.TEST, true),
                Arguments.of(Optional.of(false), Optional.empty(), LaunchMode.TEST, true),
                Arguments.of(Optional.of(false), Optional.empty(), LaunchMode.DEVELOPMENT, true),
                Arguments.of(Optional.of(false), Optional.empty(), LaunchMode.NORMAL, false),
                Arguments.of(Optional.of(true), Optional.empty(), LaunchMode.TEST, true),
                Arguments.of(Optional.of(true), Optional.empty(), LaunchMode.DEVELOPMENT, true),
                Arguments.of(Optional.of(true), Optional.empty(), LaunchMode.NORMAL, true),
                Arguments.of(Optional.empty(), Optional.of(true), LaunchMode.NORMAL, true),
                Arguments.of(Optional.empty(), Optional.of(true), LaunchMode.DEVELOPMENT, true),
                Arguments.of(Optional.empty(), Optional.of(true), LaunchMode.TEST, true),
                // explicit apply should override generate, except in normal mode
                Arguments.of(Optional.of(false), Optional.of(true), LaunchMode.TEST, true),
                Arguments.of(Optional.of(false), Optional.of(true), LaunchMode.DEVELOPMENT, true),
                Arguments.of(Optional.of(false), Optional.of(true), LaunchMode.NORMAL, false),
                Arguments.of(Optional.of(true), Optional.of(true), LaunchMode.TEST, true),
                Arguments.of(Optional.of(true), Optional.of(true), LaunchMode.DEVELOPMENT, true),
                Arguments.of(Optional.of(true), Optional.of(true), LaunchMode.NORMAL, true),
                // dev or test mode should generate by default unless explicitly forbidden
                Arguments.of(Optional.empty(), Optional.of(false), LaunchMode.NORMAL, true),
                Arguments.of(Optional.empty(), Optional.of(false), LaunchMode.DEVELOPMENT, true),
                Arguments.of(Optional.empty(), Optional.of(false), LaunchMode.TEST, true),
                Arguments.of(Optional.of(false), Optional.of(false), LaunchMode.TEST, false),
                Arguments.of(Optional.of(false), Optional.of(false), LaunchMode.DEVELOPMENT, false),
                Arguments.of(Optional.of(false), Optional.of(false), LaunchMode.NORMAL, false),
                Arguments.of(Optional.of(true), Optional.of(false), LaunchMode.TEST, true),
                Arguments.of(Optional.of(true), Optional.of(false), LaunchMode.DEVELOPMENT, true),
                Arguments.of(Optional.of(true), Optional.of(false), LaunchMode.NORMAL, true));
    }

    public static Stream<Arguments> shouldApplyShouldWork() {
        return Stream.of(
                // default cases
                Arguments.of(Optional.empty(), LaunchMode.DEVELOPMENT, true),
                Arguments.of(Optional.empty(), LaunchMode.TEST, true),

                // should never apply in normal mode regardless of explicit value
                Arguments.of(Optional.empty(), LaunchMode.NORMAL, false),
                Arguments.of(Optional.of(true), LaunchMode.NORMAL, false),
                Arguments.of(Optional.of(false), LaunchMode.NORMAL, false),

                // explicit value should be in effect
                Arguments.of(Optional.of(true), LaunchMode.DEVELOPMENT, true),
                Arguments.of(Optional.of(true), LaunchMode.TEST, true),
                Arguments.of(Optional.of(false), LaunchMode.DEVELOPMENT, false),
                Arguments.of(Optional.of(false), LaunchMode.TEST, false));
    }

    @ParameterizedTest
    @MethodSource
    void shouldGenerateShouldWork(Optional<Boolean> configuredGenerate, Optional<Boolean> configuredApply, LaunchMode mode,
            boolean expected) {
        assertEquals(expected, shouldGenerate(configuredGenerate, configuredApply, mode));
    }

    @ParameterizedTest
    @MethodSource
    void shouldApplyShouldWork(Optional<Boolean> configuredApply, LaunchMode mode, boolean expected) {
        assertEquals(expected, shouldApply(configuredApply, mode));
    }
}