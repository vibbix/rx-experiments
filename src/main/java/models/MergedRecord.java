package models;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.*;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MergedRecord(int id, String siteName, String address, PointOfContact contact,
                           Map<String, Long> requiredMaterials, Collection<HeavyEquipment> requiredEquipment) {
    public MergedRecord {
        Objects.requireNonNull(siteName);
        Objects.requireNonNull(address);
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder mutate() {
        var builder =  new Builder()
                .setID(id)
                .setSiteName(siteName)
                .setAddress(address)
                .setPointOfContact(contact);
        if (requiredEquipment != null && !requiredEquipment.isEmpty()) {
            builder.setRequiredEquipment(EnumSet.copyOf(requiredEquipment));
        }
        if (requiredMaterials != null && !requiredMaterials.isEmpty()) {
            builder.setRequiredMaterials(requiredMaterials);
        }
        return builder;
    }

    public static class Builder {
        private Integer ID;
        private String siteName;
        private String address;
        private PointOfContact pointOfContact;
        private Map<String, Long> requiredMaterials;
        private EnumSet<HeavyEquipment> requiredEquipment;

        public Builder() {
            this.requiredMaterials = new HashMap<>();
            this.requiredEquipment = EnumSet.noneOf(HeavyEquipment.class);
        }

        /**
         * Applies all non-null fields from other to this builder
         * @param other the builder to merge from
         * @return this builder
         */
        public Builder apply(Builder other) {
            if (other.ID != null) {
                this.ID = other.ID;
            }
            if (other.siteName != null) {
                this.siteName = other.siteName;
            }
            if (other.address != null) {
                this.address = other.address;
            }
            if (other.pointOfContact != null) {
                this.pointOfContact = other.pointOfContact;
            }
            if (other.requiredMaterials != null) {
                this.requiredMaterials = new HashMap<>(requiredMaterials);
            }
            if (other.requiredEquipment != null) {
                this.requiredEquipment = other.requiredEquipment.clone();
            }
            return this;
        }


        public Builder setID(Integer ID) {
            this.ID = ID;
            return this;
        }

        public Builder setSiteName(String siteName) {
            this.siteName = siteName;
            return this;
        }

        public Builder setAddress(String address) {
            this.address = address;
            return this;
        }

        public Builder setPointOfContact(PointOfContact pointOfContact) {
            this.pointOfContact = pointOfContact;
            return this;
        }

        public Builder setRequiredMaterials(Map<String, Long> requiredMaterials) {
            this.requiredMaterials = requiredMaterials;
            return this;
        }

        public <T extends Set<HeavyEquipment>> Builder setRequiredEquipment(T requiredEquipment) {
            var es = EnumSet.noneOf(HeavyEquipment.class);
            es.addAll(requiredEquipment);
            this.requiredEquipment = es;
            return this;
        }

        public MergedRecord build() {
            Map<String,Long> materials = requiredMaterials;
            if (materials == null || materials.isEmpty()) {
                materials =  Collections.emptyMap();
            } else {
                materials = Collections.unmodifiableMap(materials);
            }

            Set<HeavyEquipment> equipment = requiredEquipment;
            if (equipment == null || equipment.isEmpty()) {
                equipment = Collections.emptySet();
            } else {
                equipment = requiredEquipment.clone();
            }

            return new MergedRecord(ID, siteName, address, pointOfContact, materials, equipment);
        }
    }

    public record PointOfContact(String name, String title, String phoneNumber) {
        public PointOfContact {
            Objects.requireNonNull(phoneNumber);
            Objects.requireNonNull(name);
            Objects.requireNonNull(title);
        }
    }
}
