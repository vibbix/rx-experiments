import com.fasterxml.jackson.core.type.TypeReference;
import json.JSONDecoder;
import merger.Merger;
import models.HeavyEquipment;
import models.MergedRecord;

import java.util.EnumSet;
import java.util.Map;

public class Main {
    private static final TypeReference<MergedRecord> mergedRecordTypeReference = new TypeReference<>(){};
    private static final TypeReference<Map.Entry<Integer, EnumSet<HeavyEquipment>>> EQUIPMENT_STREAM_TYPEREF = new TypeReference<>() {};
    private static final TypeReference<Map.Entry<Integer, String>> mapIntStr = new TypeReference<>() {};
    private static final TypeReference<Map.Entry<Integer, MergedRecord.PointOfContact>> mapIntContact = new TypeReference<>(){};
    public static void main(String[] args) {
        JSONDecoder decoder = new JSONDecoder();
        var mergedRecordFlux = decoder.createFluxReader("/input/main.json", mergedRecordTypeReference);
        var equipmentFlux = decoder.createFluxReader("/input/equipment.json", EQUIPMENT_STREAM_TYPEREF);
        var materialsFlux = decoder.createFluxReader("/input/materials.json", mapIntStr);
        var contactFlux = decoder.createFluxReader("/input/poc.json", mapIntContact);

        Merger merger = new Merger(mergedRecordFlux, equipmentFlux, materialsFlux, contactFlux);

        merger.run()
                .log("main")
                .blockLast();
    }
}
