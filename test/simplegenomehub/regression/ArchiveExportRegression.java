package simplegenomehub.regression;

import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipFile;
import simplegenomehub.model.SpeciesInfo;
import simplegenomehub.util.export.DataExporter;

/** Preserve upstream's complete archive semantics when integrating the UI fixes. */
public final class ArchiveExportRegression {
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("sgh-archive-");
        SpeciesInfo species = new SpeciesInfo("Archive fixture", "v1");
        species.initializeFileStructure(root.toFile());
        Path unit = species.getSpeciesDir().toPath();
        for (String name : Arrays.asList("species.info", "Expression/legacy.metadata.json", "Population/VCF/input.vcf", "Population/FST/run/run.names.tsv", "BlastDB/regenerable.pin")) {
            Path path = unit.resolve(name); Files.createDirectories(path.getParent()); Files.writeString(path, "fixture " + name);
        }
        Path output = Files.createDirectory(root.resolve("archives"));
        List<String> progress = new ArrayList<>();
        if (!DataExporter.exportSpecies(species, output.toFile(), DataExporter.ExportFormat.ARCHIVE, progress::add))
            throw new AssertionError("Archive export failed");
        Path archive;
        try (java.util.stream.Stream<Path> files = Files.list(output)) { archive = files.filter(p -> p.toString().endsWith(".zip")).findFirst().orElseThrow(); }
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            for (String name : Arrays.asList("species.info", "Expression/legacy.metadata.json", "Population/VCF/input.vcf", "Population/FST/run/run.names.tsv", "EXPORT_MANIFEST.txt"))
                if (zip.getEntry(name) == null) throw new AssertionError("Archive omitted " + name);
            if (zip.stream().anyMatch(e -> e.getName().startsWith("BlastDB/"))) throw new AssertionError("BLAST indexes were archived");
            if (progress.stream().noneMatch(s -> s.contains("adding")) || progress.stream().noneMatch(s -> s.contains("skipped")))
                throw new AssertionError("Archive progress was not reported");
        }
        System.out.println("PASS archive: manifest, expression/VCF/FST retention, progress and BLAST index exclusion");
    }
}
