# Project Reactor Experiments
Contains examples of advanced Reactor usage for data pipelines
## Merger: zipOnKey example
### Background
The goal of this example is to show what a zipWithKey operator that merges multiple ordered flux's into a single flow
could do, based on this [ticket](https://github.com/reactor/reactor-core/issues/3645). Experiments on what such an operator
would look like are not here yet.

All data was generated with [JavaFaker](https://github.com/DiUS/java-faker) with the Generator util.
### Design Considerations
In the ticket, it was asked how we would deal with multiple keys in the same source appearing. In my case, I assumed that
1) All sources are ordered
2) Any source emitting multiple keys would have this handled by merging them into a list first (by using `Flux.windowUntilChanged(...).flatMap(...)`)
### Data Sources
There are 4 data sources being merged-in.
1. Primary merged records (`main.json`)
    - Contains basic information like ID, SiteName, and address
    - ```json
      [ {
        "id" : 1000,
        "siteName" : "Leffler-Sanford",
        "address" : "040 Kieth Junctions, New Elaina, AL 13007"
        }, {
        "id" : 1001,
        "siteName" : "Cremin-Borer",
        "address" : "9364 Alejandrina Stravenue, Flatleyfurt, IA 92816-0568"
        } ]
        ```
2. Point of Contact list (`poc.json`)
   - List of all points of contact associated with the site.
   - Wrapped in a `Map.Entry<Integer,PointOfContact>`
   - Has all ID's mapped 1:1
   - ```json
     [ {
      "1000" : {
       "phoneNumber" : "Lewis Lindgren II",
       "name" : "688-995-1284",
       "title" : "Construction Worker"
      }
     }, {
      "1001" : {
       "phoneNumber" : "Eldon Simonis III",
       "name" : "325.168.5914",
       "title" : "Construction Worker"
      }
     } ]
     ```
3. Equipment list (`equipment.json`)
    - Contains list of equipment used, mapped to a Enum by Jackson
    - Has gaps in it's dataset, so not every key has a mapped set of required equipment
    - ```json
      [ {
      "1001" : [ "Excavator", "SkidSteer", "Backhoe", "Dragline", "Bulldozer" ]
      }, {
      "1003" : [ "Excavator", "Trencher", "Scraper", "Grader" ]
      } ]
      ```
4. Materials list (`Materials.json`)
   - List of materials
   - Not all IDs are mapped
   - Some ID's appear multiple times.
   - This gets handled by using `Flux.windowUntilChanged(...).flatMap(...)`
   - ```json
     [{
       "1002" : "Rubber"
     }, {
       "1002" : "Wood"
     }, {
       "1002" : "Vinyl"
     }, {
       "1003" : "Plexiglass"
      }, {
       "1003" : "Brass"
     }, {
       "1003" : "Glass"
     }, {
       "1003" : "Wood"
     }, {
       "1003" : "Glass"
     }, {
       "1005" : "Stone"
     }, {
       "1005" : "Rubber"
     }]
     ```
### How merger takes place
To merge all these streams, each `Flux<?>` is mapped to a `Flux<MergerMarker<?>`. `MergeMarker` is a sealed interface that handles
combining each of these streams in a functional matter. Using a `BiFunction<MergedRecord.Builder, MergeMarker<?>, MergedRecord.Builder>`
reduce method, we can easily map this to accumulator function on the marker interface.

#### MergeMarker
The Merge marker interface is very simple, and is kind of like `Map.Entry<K,Optional<V>>` with an accumulator function.
Using Java 17 sealed interfaces makes the contract much more clean, but is not necessary.
```java
public sealed interface MergeMarker<T> permits MergeMarker.FieldApplier, MergeMarker.MergedRecordMarker, MergeMarker.NoOpMarker {

    Comparator<MergeMarker<?>> MERGE_MARKER_COMPARATOR = Comparator.comparingInt(MergeMarker::getKey);

    @Nullable T getValue();
    
    @NotNull int getKey();

    static MergedRecord.Builder accumulate(MergedRecord.Builder builder, MergeMarker<?> marker) {
        return marker.apply(builder);
    }

    MergedRecord.Builder apply(MergedRecord.Builder builder);
}
```
#### MergeMarker factory
```java
public sealed interface MergeMarker<T> permits MergeMarker.FieldApplier, MergeMarker.MergedRecordMarker, MergeMarker.NoOpMarker {
//...
   static MergeMarker<?> getMergeMarker(Object inType) {
       return switch (inType) {
           case MergedRecord mr -> new MergedRecordMarker(mr);
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
                   yield new NoOpMarker((Map.Entry<Integer, ?>) entry);
               }
               case Map map -> {
                   if (!map.isEmpty()) {
                       yield new FieldApplier<>((Map.Entry<Integer, Map<String, Long>>) entry,
                               MergedRecord.Builder::setRequiredMaterials);
                   }
                   yield new NoOpMarker((Map.Entry<Integer, ?>) entry);
               }
               default -> new NoOpMarker((Map.Entry<Integer, ?>) entry);
           };
           default -> throw new IllegalStateException("Unmatched type: %s".formatted(inType));
       };
   }
     //...
}
```
This implementation of the factory method relies on the use of Java 17 pattern matching. Type Erasure rears its head, 
and prevents us from easily matching on inner types. It's not exactly the most type safe implementation, and relies a 
lot on casting to work. It returns one of three different specialized classes:
1. MergedRecordMarker
   - This is a concrete example of needing to have a specialized type handler
2. FieldApplier
   - This is a generic approach, that deconstructs a `Map.Entry` and allows a lambda function to be used during the reduce phase
3. NoOpMarker
   - In case of invalid data, this gets returned as a fallback from FieldApplier
#### Merge Assembly
```java
public class Merger {
    private Flux<MergedRecord> mergedRecordFlux;
    private Flux<Map.Entry<Integer, EnumSet<HeavyEquipment>>> equipmentFlux;
    private Flux<Map.Entry<Integer, String>> materialsFlux;
    private Flux<Map.Entry<Integer, MergedRecord.PointOfContact>> contactFlux;
    //...
    public Flux<MergedRecord> run() {
       Flux<MergeMarker<?>> records = mergedRecordFlux.map(MergeMarker::getMergeMarker);
       Flux<MergeMarker<?>> contacts = contactFlux.map(MergeMarker::getMergeMarker);
       Flux<MergeMarker<?>> equipment = equipmentFlux.map(MergeMarker::getMergeMarker);
       Flux<MergeMarker<?>> materials = materialsFlux.windowUntilChanged(Map.Entry::getKey)
               .flatMapSequential(Flux::collectList)
               .map(Merger::convertMaterials)
               .map(MergeMarker::getMergeMarker);

       return Flux.mergeComparing(MERGE_MARKER_COMPARATOR, records, contacts, equipment, materials)
               .windowUntilChanged(MergeMarker::getKey)
               .flatMapSequential(keyed -> keyed.reduce(MergedRecord.builder(), MergeMarker::accumulate))
               .map(MergedRecord.Builder::build);
    }
    //...
}
```
By sorting each of the output Flux's with the merge key, we get an inner-flux of just the matched keys.
Each inner flux is sequentially flat-mapped and merged into a MergeRecord to be handled.
For the materials file, we perform a pre-merge phase that combines sequential values of the same key.

###  Output from Merger
```
> Task :Main.main()
[ INFO] (main) onSubscribe(FluxMap.MapSubscriber)
[ INFO] (main) request(unbounded)
[ INFO] (parallel-7) onNext(MergedRecord[id=1000, siteName=Leffler-Sanford, address=040 Kieth Junctions, New Elaina, AL 13007, contact=PointOfContact[phoneNumber=Lewis Lindgren II, name=688-995-1284, title=Construction Worker], requiredMaterials={Vinyl=1, Wood=1, Aluminum=1, Glass=1}, requiredEquipment=[]])
[ INFO] (parallel-10) onNext(MergedRecord[id=1001, siteName=Cremin-Borer, address=9364 Alejandrina Stravenue, Flatleyfurt, IA 92816-0568, contact=PointOfContact[phoneNumber=Eldon Simonis III, name=325.168.5914, title=Construction Worker], requiredMaterials={Wood=1, Plexiglass=1}, requiredEquipment=[Excavator, Backhoe, Bulldozer, SkidSteer, Dragline]])
[ INFO] (parallel-15) onNext(MergedRecord[id=1002, siteName=Donnelly Inc, address=737 Kam Trafficway, Whiteland, MT 41553, contact=PointOfContact[phoneNumber=Benito Mills, name=1-379-950-6579, title=Construction Worker], requiredMaterials={Vinyl=1, Wood=1, Rubber=1}, requiredEquipment=[Excavator, Grader, Trencher, Scraper]])
[ INFO] (parallel-1) onNext(MergedRecord[id=1003, siteName=Wisoky, Crooks and Purdy, address=Apt. 892 9317 Amanda Spur, Jonesville, NY 30949, contact=PointOfContact[phoneNumber=Edison Macejkovic, name=886.966.6173, title=Engineer], requiredMaterials={Wood=1, Brass=1, Glass=2, Plexiglass=1}, requiredEquipment=[Backhoe, DumpTruck]])
[ INFO] (parallel-1) onNext(MergedRecord[id=1004, siteName=Rolfson, Braun and Emard, address=Suite 033 75269 Thomas Point, West Walkerstad, AR 77029, contact=PointOfContact[phoneNumber=Gonzalo Gusikowski, name=524-277-0647, title=Construction Foreman], requiredMaterials={}, requiredEquipment=[Excavator, SkidSteer, Dragline]])
[ INFO] (parallel-2) onNext(MergedRecord[id=1005, siteName=Bartell-Davis, address=59462 Kris Pass, Lemkebury, NC 74082-6438, contact=PointOfContact[phoneNumber=Ira Huel DDS, name=1-819-359-5828, title=Supervisor], requiredMaterials={Rubber=1, Stone=1}, requiredEquipment=[DumpTruck]])
[ INFO] (parallel-2) onNext(MergedRecord[id=1006, siteName=Bahringer-Christiansen, address=35599 Elba Summit, South Maura, ME 30039, contact=PointOfContact[phoneNumber=Raul Predovic, name=1-810-447-0883, title=Architect], requiredMaterials={}, requiredEquipment=[Grader, Trencher, Scraper, Compactor]])
[ INFO] (parallel-5) onNext(MergedRecord[id=1007, siteName=Konopelski Inc, address=9771 Beatty Mission, Priceside, VA 47428-1015, contact=PointOfContact[phoneNumber=Jeremy Anderson, name=(264) 239-3765, title=Project Manager], requiredMaterials={Plexiglass=1}, requiredEquipment=[Excavator, Backhoe, Bulldozer, Trencher, Compactor]])
...
```
### Conclusions
While effective, it's very verbose and is difficult to expand. Placement of parameters gets lost, and so does type 
safety when we begin to pattern match types with parameterized values.
Using `GroupedFlux`in place of `Map.Entry` or tuples would be preferred, but they cannot
be manually constructed, and aren't viable in unbounded/long-living source use cases. There is undefined behavior if a 
flux that's not collecting sequential occurrences of the same key occurs. This would lead to multiple instances of the 
same MergeMarker appearing in the flux. Using `.distinct` filter wouldn't help much either, as the FieldApplierMarker is
only unique given its parameterized generics. As long as this is documented it should be fine. My approach assumes that 
use of the accumulator function is idempotent. 

This approach can be generified but without access to inner flux queues, it would be far from optimized.
Having a method like `.isSorted(Function<T, I extends Comparable> keySelector)` that fails fast on non-sorted sets would 
help as well.
