package merger;

import models.HeavyEquipment;
import models.MergedRecord;
import reactor.util.annotation.NonNull;
import reactor.util.annotation.Nullable;

import java.util.*;
import java.util.function.BiConsumer;


public sealed interface MergeMarker<T> permits MergeMarker.FieldApplier, MergeMarker.MergedRecordMarker, MergeMarker.NoOpMarker {

    static Comparator<MergeMarker<?>> MERGE_MARKER_COMPARATOR = Comparator.comparingInt(MergeMarker::getKey);

    @Nullable
    T getValue();

    @NonNull
    int getKey();

    default boolean hasValue() {
        return this.getValue() != null;
    }

    static MergedRecord.Builder accumulate(MergedRecord.Builder builder, MergeMarker<?> marker) {
        return marker.apply(builder);
    }

    MergedRecord.Builder apply(MergedRecord.Builder builder);

    static boolean isNoOp(MergeMarker marker) {
        return marker instanceof NoOpMarker;
    }

    static MergeMarker<?> getMergeMarker(Object inType) {
        return switch (inType) {
            case MergedRecord mr -> new MergedRecordMarker(mr);
            //case Map.Entry<Integer, MergedRecord.PointOfContact> won't work cause of type erasure
            case Map.Entry<?, ?> entry -> switch (entry.getValue()) {
                case MergedRecord.PointOfContact ignored -> {
                    yield new FieldApplier<>((Map.Entry<Integer, MergedRecord.PointOfContact>) entry,
                            MergedRecord.Builder::setPointOfContact);
                }
                case Set<?> set -> {
                    if (!set.isEmpty()) {
                        yield new FieldApplier<>((Map.Entry<Integer, Set<HeavyEquipment>>) entry,
                                MergedRecord.Builder::setRequiredEquipment);
                    }
                    yield new NoOpMarker((Map.Entry<Integer,?>)entry);
                }
                case Map map -> {
                    if (!map.isEmpty()) {
                        yield new FieldApplier<>((Map.Entry<Integer, Map<String,Long>>) entry,
                                MergedRecord.Builder::setRequiredMaterials);
                    }
                    yield new NoOpMarker((Map.Entry<Integer,?>)entry);
                }
                //case null -> throw new NullPointerException("Value type is null");
                default -> new NoOpMarker((Map.Entry<Integer,?>)entry);
            };
            default -> throw new IllegalStateException("Unmatched type: %s".formatted(inType));
        };
    }

    final class FieldApplier<T> implements MergeMarker<T> {
        private final Map.Entry<Integer, T> entry;
        private final BiConsumer<MergedRecord.Builder, T> applyFn;

        public FieldApplier(Map.Entry<Integer, T> entry, BiConsumer<MergedRecord.Builder, T> applyFn) {
            this.entry = entry;
            this.applyFn = applyFn;
        }

        @Override
        public T getValue() {
            return this.entry.getValue();
        }

        @Override
        public int getKey() {
            return this.entry.getKey();
        }

        @Override
        public MergedRecord.Builder apply(MergedRecord.Builder builder) {
            this.applyFn.accept(builder, getValue());
            return builder;
        }
    }


    final class MergedRecordMarker implements MergeMarker<MergedRecord> {
        private final MergedRecord record;

        public MergedRecordMarker(MergedRecord record) {
            this.record = record;
        }

        @Override
        public MergedRecord getValue() {
            return this.record;
        }

        @Override
        public int getKey() {
            return record.id();
        }

        @Override
        public MergedRecord.Builder apply(MergedRecord.Builder builder) {
            return builder.apply(getValue().mutate());
        }
    }

    final class NoOpMarker implements MergeMarker<Void> {
        private final int key;

        public NoOpMarker(int key) {
            this.key = key;
        }

        public NoOpMarker(Map.Entry<Integer, ?> entry) {
            this.key = entry.getKey();
        }

        @Override
        public Void getValue() {
            return null;
        }

        @Override
        public int getKey() {
            return key;
        }

        @Override
        public boolean hasValue() {
            return false;
        }

        @Override
        public MergedRecord.Builder apply(MergedRecord.Builder builder) {
            return builder;
        }
    }
}
