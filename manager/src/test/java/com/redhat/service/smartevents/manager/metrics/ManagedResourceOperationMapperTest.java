package com.redhat.service.smartevents.manager.metrics;

import java.time.ZonedDateTime;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.redhat.service.smartevents.infra.models.dto.ManagedResourceStatus;
import com.redhat.service.smartevents.infra.models.dto.ManagedResourceStatusUpdateDTO;
import com.redhat.service.smartevents.manager.metrics.ManagedResourceOperationMapper.ManagedResourceOperation;
import com.redhat.service.smartevents.manager.models.ManagedResource;

import static org.assertj.core.api.Assertions.assertThat;

public class ManagedResourceOperationMapperTest {

    @ParameterizedTest
    @MethodSource("inferenceTestData")
    void testInference(ManagedResourceStatus managedResourceStatus,
            boolean isManagedResourcePublished,
            ManagedResourceStatus updateStatus,
            ManagedResourceOperation operation) {
        ManagedResource managedResource = new ManagedResource();
        managedResource.setStatus(managedResourceStatus);
        managedResource.setPublishedAt(isManagedResourcePublished ? ZonedDateTime.now() : null);

        ManagedResourceStatusUpdateDTO updateDTO = new ManagedResourceStatusUpdateDTO();
        updateDTO.setStatus(updateStatus);

        assertThat(ManagedResourceOperationMapper.inferOperation(managedResource, updateDTO)).isEqualTo(operation);
    }

    private static Stream<Arguments> inferenceTestData() {
        Object[][] arguments = {
                { ManagedResourceStatus.READY, false, ManagedResourceStatus.READY, ManagedResourceOperation.UNDETERMINED },
                { ManagedResourceStatus.PREPARING, false, ManagedResourceStatus.READY, ManagedResourceOperation.CREATE },
                { ManagedResourceStatus.PROVISIONING, false, ManagedResourceStatus.READY, ManagedResourceOperation.CREATE },
                { ManagedResourceStatus.PREPARING, false, ManagedResourceStatus.FAILED, ManagedResourceOperation.FAILED_CREATE },
                { ManagedResourceStatus.PROVISIONING, false, ManagedResourceStatus.FAILED, ManagedResourceOperation.FAILED_CREATE },
                { ManagedResourceStatus.PREPARING, true, ManagedResourceStatus.READY, ManagedResourceOperation.UPDATE },
                { ManagedResourceStatus.PROVISIONING, true, ManagedResourceStatus.READY, ManagedResourceOperation.UPDATE },
                { ManagedResourceStatus.PREPARING, true, ManagedResourceStatus.FAILED, ManagedResourceOperation.FAILED_UPDATE },
                { ManagedResourceStatus.PROVISIONING, true, ManagedResourceStatus.FAILED, ManagedResourceOperation.FAILED_UPDATE },
                { ManagedResourceStatus.DEPROVISION, false, ManagedResourceStatus.DELETED, ManagedResourceOperation.DELETE },
                { ManagedResourceStatus.DELETING, false, ManagedResourceStatus.DELETED, ManagedResourceOperation.DELETE },
                { ManagedResourceStatus.DEPROVISION, false, ManagedResourceStatus.FAILED, ManagedResourceOperation.FAILED_DELETE },
                { ManagedResourceStatus.DELETING, false, ManagedResourceStatus.FAILED, ManagedResourceOperation.FAILED_DELETE }
        };
        return Stream.of(arguments).map(Arguments::of);
    }

}