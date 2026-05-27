package com.mycompany.msr.amis.api.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mycompany.msr.amis.api.dto.sync.SyncQueueRecordRequest;
import org.junit.jupiter.api.Test;

class SyncValidationServiceTest {

    private final SyncValidationService service = new SyncValidationService();

    @Test
    void acceptsDistributionPayloadWithItems() {
        ObjectNode payload = JsonNodeFactory.instance.objectNode();
        payload.putArray("items")
                .addObject()
                .put("assetCode", "MSR-LAP-001")
                .put("assignedTo", "Officer One")
                .put("phone", "123456")
                .put("nid", "NID-1");

        var issues = service.validateRecord(new SyncQueueRecordRequest(
                "DISTRIBUTION", "1", "CREATE", "sync-1", "", payload, null
        ));

        assertThat(issues).isEmpty();
    }

    @Test
    void acceptsReturnPayloadWithItems() {
        ObjectNode payload = JsonNodeFactory.instance.objectNode();
        payload.put("equipmentType", "Laptop");
        payload.putArray("items")
                .addObject()
                .put("originalAssetCode", "MSR-LAP-001")
                .put("returnedBy", "Officer One")
                .put("phone", "123456")
                .put("nid", "NID-1")
                .put("condition", "Good");

        var issues = service.validateRecord(new SyncQueueRecordRequest(
                "RETURN", "1", "CREATE", "sync-2", "", payload, null
        ));

        assertThat(issues).isEmpty();
    }

    @Test
    void acceptsEquipmentStatusWhenSerialNumberIsPresent() {
        ObjectNode payload = JsonNodeFactory.instance.objectNode();
        payload.put("serialNumber", "SN-001");
        payload.put("status", "RETIRED");

        var issues = service.validateRecord(new SyncQueueRecordRequest(
                "EQUIPMENT", "SN-001", "STATUS", "sync-3", "", payload, null
        ));

        assertThat(issues).isEmpty();
    }
}
