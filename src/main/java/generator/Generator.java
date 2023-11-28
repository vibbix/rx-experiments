package generator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SequenceWriter;
import com.github.javafaker.Faker;
import com.github.javafaker.service.FakeValuesService;
import com.github.javafaker.service.RandomService;
import models.MergedRecord;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Generator {
    public static void main(String[] args) throws Exception {
        String fPath;
        if (args.length > 1) {
            fPath = args[0];
        } else {
            fPath = Files.createTempDirectory("rx-gen")
                    .toString();
        }
        var gen = new Generator(new File(fPath));
        gen.writeToFolder();
    }

    private final Faker faker;
    private final Random random;
    private final File targetFolder;
    private final ObjectMapper mapper;
    private final RandomService randomService;
    private final FakeValuesService fakeValuesService;

    public Generator(File targetFolder) throws Exception {
        this.random = new Random();
        this.faker = new Faker(random);
        this.targetFolder = targetFolder;
        this.mapper = new ObjectMapper();
        this.randomService = new RandomService(random);
        this.fakeValuesService = new FakeValuesService(Locale.ENGLISH, randomService);
    }


    public void writeToFolder() {
        ObjectWriter writer = mapper.writer().withDefaultPrettyPrinter();

        //buffered writers
        Path targetPath = Path.of(targetFolder.toString());
        try(SequenceWriter mainWriter = writer.writeValues(targetPath.resolve("main.json").toFile());
            SequenceWriter pocWriter = writer.writeValues(targetPath.resolve("poc.json").toFile());
            SequenceWriter materialsWriter = writer.writeValues(targetPath.resolve("materials.json").toFile());
            SequenceWriter equipmentWriter = writer.writeValues(targetPath.resolve("equipment.json").toFile())) {
            //init
            mainWriter.init(true);
            pocWriter.init(true);
            materialsWriter.init(true);
            equipmentWriter.init(true);
            //build up
            for (int id = 1000; id < 2000; id++) {
                //generate point of contact
                String siteName = faker.company().name();
                String address = faker.address().fullAddress();
                mainWriter.write(new MergedRecord(id, siteName, address,
                        null, null, null));
                pocWriter.write(Map.entry(id, pointOfContact()));
                generateRequiredMaterials(materialsWriter, id);
                generateRequiredEquipment(equipmentWriter, id);
            }
        } catch (IOException e) {
            Logger.getLogger("Generator").log(Level.WARNING, e, () -> "Failed to write");
        }
    }

    private MergedRecord.PointOfContact pointOfContact() {
        return new MergedRecord.PointOfContact(faker.name().name(),
                fakeValuesService.fetchString("construction.roles"),
                faker.phoneNumber().cellPhone());
    }
    private void generateRequiredEquipment(SequenceWriter writer, int id) throws IOException {
        Collection<String> equip = generateRequiredEquipment();
        if (!equip.isEmpty()) {
            writer.write(Map.entry(id, equip));
        }
    }

    private void generateRequiredMaterials(SequenceWriter writer, int id) throws IOException {
        var materials = generateRequiredMaterials();
        for (String material: materials) {
            writer.write(Map.entry(id, material));
        }
    }

    private Collection<String> generateRequiredEquipment() {
        final int total = random.nextInt(7);
        Set<String> set = new HashSet<>();
        for (int i = 0; i < total; i++) {
            set.add(fakeValuesService.fetchString("construction.heavy_equipment")
                    .replace(" ", "")
                    .replace("-", ""));
        }
        return set;
    }

    private Collection<String> generateRequiredMaterials() {
        final int total = random.nextInt(7);
        LinkedList<String> list = new LinkedList<>();
        for (int i = 0; i < total; i++) {
            list.add(fakeValuesService.fetchString("construction.materials"));
        }
        return list;
    }
}
