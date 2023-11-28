package merger;
import models.HeavyEquipment;
import models.MergedRecord;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.stream.Collectors;

import static merger.MergeMarker.MERGE_MARKER_COMPARATOR;

public class Merger {
    private final Flux<MergedRecord> mergedRecordFlux;
    private final Flux<Map.Entry<Integer, EnumSet<HeavyEquipment>>> equipmentFlux;
    private final Flux<Map.Entry<Integer, String>> materialsFlux;
    private final Flux<Map.Entry<Integer, MergedRecord.PointOfContact>> contactFlux;


    public Merger(Flux<MergedRecord> mergedRecordFlux, Flux<Map.Entry<Integer, EnumSet<HeavyEquipment>>> equipmentFlux,
                  Flux<Map.Entry<Integer, String>> materialsFlux, Flux<Map.Entry<Integer, MergedRecord.PointOfContact>> contactFlux) {
        this.mergedRecordFlux = mergedRecordFlux;//
        this.equipmentFlux = equipmentFlux;
        this.materialsFlux = materialsFlux;
        this.contactFlux = contactFlux;
    }

    public Flux<MergedRecord> run() {
        Flux<MergeMarker<?>> records = mergedRecordFlux.map(MergeMarker::getMergeMarker);
        Flux<MergeMarker<?>> contacts = contactFlux.map(MergeMarker::getMergeMarker);
        Flux<MergeMarker<?>> equipment = equipmentFlux.map(MergeMarker::getMergeMarker);
        Flux<MergeMarker<?>> materials = materialsFlux.windowUntilChanged(Map.Entry::getKey)
                .flatMapSequential(Flux::collectList)
                .map(Merger::convertMaterials)
                .map(MergeMarker::getMergeMarker);

        return Flux.mergeComparing(MERGE_MARKER_COMPARATOR, records, contacts, equipment, materials)
                //.filter(marker -> !MergeMarker.isNoOp(marker))
                .windowUntilChanged(MergeMarker::getKey)
                .flatMapSequential(keyed -> keyed.reduce(MergedRecord.builder(), MergeMarker::accumulate))
                .map(MergedRecord.Builder::build);
    }

    private static Map.Entry<Integer,Map<String,Long>> convertMaterials(List<Map.Entry<Integer, String>> materials) {
        return Map.entry(materials.stream().findFirst().get().getKey(), materials.stream()
                .collect(Collectors.groupingBy(Map.Entry::getValue, Collectors.counting())));
    }

}
