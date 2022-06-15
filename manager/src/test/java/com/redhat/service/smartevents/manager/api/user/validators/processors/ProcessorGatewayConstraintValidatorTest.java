package com.redhat.service.smartevents.manager.api.user.validators.processors;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.hibernate.validator.constraintvalidation.HibernateConstraintValidatorContext;
import org.hibernate.validator.constraintvalidation.HibernateConstraintViolationBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.verification.VerificationMode;

import com.redhat.service.smartevents.infra.models.gateways.Action;
import com.redhat.service.smartevents.infra.models.gateways.Gateway;
import com.redhat.service.smartevents.infra.models.gateways.Source;
import com.redhat.service.smartevents.infra.models.processors.ProcessorType;
import com.redhat.service.smartevents.infra.validations.ValidationResult;
import com.redhat.service.smartevents.manager.api.models.requests.ProcessorRequest;
import com.redhat.service.smartevents.processor.GatewayConfigurator;
import com.redhat.service.smartevents.processor.validators.GatewayValidator;

import static com.redhat.service.smartevents.manager.api.user.validators.processors.ProcessorGatewayConstraintValidator.GATEWAY_CLASS_PARAM;
import static com.redhat.service.smartevents.manager.api.user.validators.processors.ProcessorGatewayConstraintValidator.GATEWAY_PARAMETERS_MISSING_ERROR;
import static com.redhat.service.smartevents.manager.api.user.validators.processors.ProcessorGatewayConstraintValidator.GATEWAY_PARAMETERS_NOT_VALID_ERROR;
import static com.redhat.service.smartevents.manager.api.user.validators.processors.ProcessorGatewayConstraintValidator.GATEWAY_TYPE_MISSING_ERROR;
import static com.redhat.service.smartevents.manager.api.user.validators.processors.ProcessorGatewayConstraintValidator.MISSING_GATEWAY_ERROR;
import static com.redhat.service.smartevents.manager.api.user.validators.processors.ProcessorGatewayConstraintValidator.MULTIPLE_GATEWAY_ERROR;
import static com.redhat.service.smartevents.manager.api.user.validators.processors.ProcessorGatewayConstraintValidator.SOURCE_PROCESSOR_WITH_TRANSFORMATION_ERROR;
import static com.redhat.service.smartevents.manager.api.user.validators.processors.ProcessorGatewayConstraintValidator.TYPE_PARAM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProcessorGatewayConstraintValidatorTest {

    public static String TEST_ACTION_TYPE = "TestAction";
    public static String TEST_SOURCE_TYPE = "TestSource";
    public static String TEST_PARAM_NAME = "test-param-name";
    public static String TEST_PARAM_VALUE = "test-param-value";

    ProcessorGatewayConstraintValidator constraintValidator;

    @Mock
    GatewayConfigurator gatewayConfiguratorMock;
    @Mock
    GatewayValidator validatorMock;
    @Mock
    HibernateConstraintValidatorContext validatorContextMock;
    @Mock
    HibernateConstraintViolationBuilder builderMock;

    @BeforeEach
    public void beforeEach() {
        lenient().when(validatorMock.isValid(any(Action.class))).thenReturn(ValidationResult.valid());
        lenient().when(validatorMock.isValid(any(Source.class))).thenReturn(ValidationResult.valid());

        lenient().when(gatewayConfiguratorMock.getValidator(any(String.class))).thenReturn(validatorMock);

        lenient().when(validatorContextMock.buildConstraintViolationWithTemplate(any(String.class))).thenReturn(builderMock);
        lenient().when(validatorContextMock.unwrap(HibernateConstraintValidatorContext.class)).thenReturn(validatorContextMock);

        constraintValidator = new ProcessorGatewayConstraintValidator(gatewayConfiguratorMock);
    }

    @Test
    void isValid_noGatewaysIsNotValid() {
        ProcessorRequest p = new ProcessorRequest();

        assertThat(constraintValidator.isValid(p, validatorContextMock)).isFalse();
        verify(gatewayConfiguratorMock, never()).getValidator(any());
        verify(validatorMock, never()).isValid(any());
        verifyErrorMessage(MISSING_GATEWAY_ERROR);
    }

    @Test
    void isValid_multipleGatewaysIsNotValid() {
        ProcessorRequest p = new ProcessorRequest();
        p.setAction(buildTestAction());
        p.setSource(buildTestSource());

        assertThat(constraintValidator.isValid(p, validatorContextMock)).isFalse();
        verify(gatewayConfiguratorMock, never()).getValidator(any());
        verify(validatorMock, never()).isValid(any());
        verifyErrorMessage(MULTIPLE_GATEWAY_ERROR);
    }

    @ParameterizedTest
    @MethodSource("gateways")
    void isValid(Gateway gateway) {
        ProcessorRequest p = buildTestRequest(gateway);

        assertThat(constraintValidator.isValid(p, validatorContextMock)).isTrue();
        verifyGetValidatorCall(times(1), gateway.getType());
        verifyIsValidCall(times(1));
    }

    @ParameterizedTest
    @MethodSource("gateways")
    void isValid_gatewayWithNullParametersIsNotValid(Gateway gateway) {
        gateway.setParameters(null);
        ProcessorRequest p = buildTestRequest(gateway);

        assertThat(constraintValidator.isValid(p, validatorContextMock)).isFalse();
        verifyGetValidatorCall(never(), ArgumentMatchers::anyString);
        verifyIsValidCall(never());
        verifyErrorMessage(GATEWAY_PARAMETERS_MISSING_ERROR, gateway, false);
    }

    @ParameterizedTest
    @MethodSource("gateways")
    void isValid_gatewayWithEmptyParamsIsValid(Gateway gateway) {
        gateway.setMapParameters(new HashMap<>());
        ProcessorRequest p = buildTestRequest(gateway);

        assertThat(constraintValidator.isValid(p, validatorContextMock)).isTrue();
        verifyGetValidatorCall(times(1), gateway.getType());
        verifyIsValidCall(times(1));
    }

    @ParameterizedTest
    @MethodSource("gateways")
    void isValid_nullGatewayTypeIsNotValid(Gateway gateway) {
        gateway.setType(null);
        ProcessorRequest p = buildTestRequest(gateway);

        assertThat(constraintValidator.isValid(p, validatorContextMock)).isFalse();
        verifyGetValidatorCall(never(), ArgumentMatchers::anyString);
        verifyIsValidCall(never());
        verifyErrorMessage(GATEWAY_TYPE_MISSING_ERROR, gateway, false);
    }

    @ParameterizedTest
    @MethodSource("gateways")
    void isValid_messageFromGatewayValidatorAddedOnFailure(Gateway gateway) {
        String testErrorMessage = "This is a test error message returned from validator";
        lenient().when(validatorMock.isValid(any())).thenReturn(ValidationResult.invalid(testErrorMessage));

        ProcessorRequest p = buildTestRequest(gateway);

        assertThat(constraintValidator.isValid(p, validatorContextMock)).isFalse();
        verifyGetValidatorCall(times(1), gateway.getType());
        verifyIsValidCall(times(1));
        verifyErrorMessage(testErrorMessage);
    }

    @ParameterizedTest
    @MethodSource("gateways")
    void isValid_noMessageFromValidatorGenericMessageAdded(Gateway gateway) {
        lenient().when(validatorMock.isValid(any())).thenReturn(ValidationResult.invalid());

        ProcessorRequest p = buildTestRequest(gateway);

        assertThat(constraintValidator.isValid(p, validatorContextMock)).isFalse();
        verifyGetValidatorCall(times(1), gateway.getType());
        verifyIsValidCall(times(1));
        verifyErrorMessage(GATEWAY_PARAMETERS_NOT_VALID_ERROR, gateway, true);
    }

    @ParameterizedTest
    @MethodSource("gateways")
    void isValid_sourceWithTransformationIsNotValid(Gateway gateway) {
        ProcessorRequest p = buildTestRequest(gateway);
        p.setTransformationTemplate("template");

        if (p.getType() == ProcessorType.SOURCE) {
            assertThat(constraintValidator.isValid(p, validatorContextMock)).isFalse();
            verifyGetValidatorCall(never(), ArgumentMatchers::anyString);
            verifyIsValidCall(never());
            verifyErrorMessage(SOURCE_PROCESSOR_WITH_TRANSFORMATION_ERROR);
        } else {
            assertThat(constraintValidator.isValid(p, validatorContextMock)).isTrue();
            verifyGetValidatorCall(times(1), gateway.getType());
            verifyIsValidCall(times(1));
        }
    }

    private void verifyGetValidatorCall(VerificationMode mode, String argument) {
        verifyGetValidatorCall(mode, () -> argument);
    }

    private void verifyGetValidatorCall(VerificationMode mode, Supplier<String> argumentSupplier) {
        verify(gatewayConfiguratorMock, mode).getValidator(argumentSupplier.get());
    }

    private void verifyIsValidCall(VerificationMode mode) {
        verify(validatorMock, mode).isValid(any());
    }

    private void verifyErrorMessage(String expectedMessage) {
        ArgumentCaptor<String> messageCap = ArgumentCaptor.forClass(String.class);

        verify(validatorContextMock).disableDefaultConstraintViolation();
        verify(validatorContextMock).buildConstraintViolationWithTemplate(messageCap.capture());
        verify(builderMock).addConstraintViolation();

        assertThat(messageCap.getValue()).isEqualTo(expectedMessage);
    }

    private void verifyErrorMessage(String expectedMessage, Gateway gateway, boolean verifyTypeParam) {
        verifyErrorMessage(expectedMessage);
        verify(validatorContextMock).addMessageParameter(GATEWAY_CLASS_PARAM, gateway.getClass().getSimpleName());
        if (verifyTypeParam) {
            verify(validatorContextMock).addMessageParameter(TYPE_PARAM, gateway.getType());
        }
    }

    private static Stream<Arguments> gateways() {
        return Stream.of(buildTestAction(), buildTestSource()).map(Arguments::of);
    }

    private static Action buildTestAction() {
        Action action = new Action();
        action.setType(TEST_ACTION_TYPE);
        Map<String, String> params = new HashMap<>();
        params.put(TEST_PARAM_NAME, TEST_PARAM_VALUE);
        action.setMapParameters(params);
        return action;
    }

    private static Source buildTestSource() {
        Source source = new Source();
        source.setType(TEST_SOURCE_TYPE);
        Map<String, String> params = new HashMap<>();
        params.put(TEST_PARAM_NAME, TEST_PARAM_VALUE);
        source.setMapParameters(params);
        return source;
    }

    private static ProcessorRequest buildTestRequest(Gateway gateway) {
        ProcessorRequest p = new ProcessorRequest();
        if (gateway instanceof Action) {
            p.setAction((Action) gateway);
        }
        if (gateway instanceof Source) {
            p.setSource((Source) gateway);
        }
        return p;
    }
}