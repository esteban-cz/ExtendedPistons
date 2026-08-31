package dev.estyxq.extendedpistons.assets;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

final class ModelGeometryTest {
    private static final Set<String> SIDE_FACES = Set.of("down", "up", "west", "east");

    @Test
    void shaftArmMeetsCenterWithoutOverlappingIt() throws IOException {
        assertSingleElement("piston_shaft_arm",
                new int[]{6, 6, 0}, new int[]{10, 10, 6}, SIDE_FACES);
    }

    @Test
    void headInnerArmBridgesPlateAndCenterWithoutOverlappingEither() throws IOException {
        assertSingleElement("piston_head_inner_arm",
                new int[]{6, 6, 4}, new int[]{10, 10, 6}, SIDE_FACES);
    }

    private static void assertSingleElement(String modelName, int[] expectedFrom,
                                            int[] expectedTo, Set<String> expectedFaces)
            throws IOException {
        JsonObject model = loadModel(modelName);
        JsonArray elements = model.getAsJsonArray("elements");
        assertEquals(1, elements.size(), modelName + " must contain one connector element");

        JsonObject element = elements.get(0).getAsJsonObject();
        assertArrayEquals(expectedFrom, coordinates(element.getAsJsonArray("from")));
        assertArrayEquals(expectedTo, coordinates(element.getAsJsonArray("to")));
        assertEquals(expectedFaces, element.getAsJsonObject("faces").keySet(),
                modelName + " must not render hidden coplanar end caps");
    }

    private static JsonObject loadModel(String modelName) throws IOException {
        String path = "/assets/extendedpistons/models/block/" + modelName + ".json";
        InputStream stream = ModelGeometryTest.class.getResourceAsStream(path);
        assertNotNull(stream, "Missing model resource " + path);
        try (stream; InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static int[] coordinates(JsonArray values) {
        int[] result = new int[values.size()];
        for (int index = 0; index < values.size(); index++) {
            result[index] = values.get(index).getAsInt();
        }
        return result;
    }
}
