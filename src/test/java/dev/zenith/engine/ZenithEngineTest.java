package dev.zenith.engine;

import dev.zenith.ast.ZenithFile;
import dev.zenith.parser.Parser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class ZenithEngineTest {

    @Test
    public void testCheckoutSimulationWithStateDiffAndEmail() throws Exception {
        String schemaContent = Files.readString(Paths.get("zenith-exemples/orders-commerce/schema.zen"));
        ZenithFile schema = new Parser().parse(schemaContent);

        Map<String, Object> entityData = new LinkedHashMap<>();
        entityData.put("id", "3fa85f64-5717-4562-b3fc-2c963f66afa6");
        entityData.put("status", "PENDING");
        entityData.put("totalAmount", 149.99);

        Map<String, Object> customer = new LinkedHashMap<>();
        customer.put("email", "client@example.com");
        customer.put("fullName", "Jane Doe");
        entityData.put("customer", customer);

        SimulationResult result = ZenithEngine.getInstance().simulate(
                schema,
                "Order",
                "/{id}/checkout",
                "POST",
                entityData,
                new LinkedHashMap<>(),
                new LinkedHashMap<>()
        );

        assertTrue(result.isSuccess(), "Simulation should succeed: " + result.getMessage());
        assertEquals(200, result.getStatus());
        assertTrue(result.getExecutionTimeMs() < 50, "Simulation should be ultra fast under 50ms (got " + result.getExecutionTimeMs() + "ms)");

        // Verify status mutated from PENDING to CONFIRMED
        assertTrue(result.getStateDiff().containsKey("status"), "State diff should include status mutation");
        assertEquals("PENDING", result.getStateDiff().get("status").getBeforeValue());
        assertEquals("CONFIRMED", result.getStateDiff().get("status").getAfterValue());

        // Verify email effect recorded
        assertFalse(result.getDispatchedEffects().isEmpty(), "Should record dispatched email contract");
        assertEquals("OrderConfirmation", result.getDispatchedEffects().get(0).getContract());
    }

    @Test
    public void testPayGuardRejection() throws Exception {
        String schemaContent = Files.readString(Paths.get("zenith-exemples/orders-commerce/schema.zen"));
        ZenithFile schema = new Parser().parse(schemaContent);

        // Pre-set status to CANCELLED so guard `status == OrderStatus.PENDING` fails
        Map<String, Object> entityData = new LinkedHashMap<>();
        entityData.put("id", "3fa85f64-5717-4562-b3fc-2c963f66afa6");
        entityData.put("status", "CANCELLED");

        SimulationResult result = ZenithEngine.getInstance().simulate(
                schema,
                "Order",
                "/{id}/pay",
                "POST",
                entityData,
                new LinkedHashMap<>(),
                new LinkedHashMap<>()
        );

        assertFalse(result.isSuccess(), "Simulation should fail due to guard violation");
        assertEquals(400, result.getStatus());
        assertTrue(result.getMessage().contains("Only pending orders can be paid"));
    }
}

