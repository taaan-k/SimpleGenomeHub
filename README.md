# SimpleGenomeHub

[中文版本](README.zh-CN.md)

SimpleGenomeHub is a cross-platform Java 11 desktop application for local genome data management and analysis. It organizes genome assemblies, annotations, expression matrices, gene sets, region sets, functional annotations, BLAST results, synteny outputs, and Circos-related files into a stable local database structure.

The workflow is designed around one core rule: one genome assembly is managed together with one corresponding annotation set. After import, SimpleGenomeHub archives files into a consistent directory tree, makes them reusable across analysis modules, and keeps analysis outputs traceable over time.

## Highlights

- Local genome database with import, validation, editing, export, and directory-tree browsing
- Gene and region set management for preserving analysis targets across workflows
- FASTA extraction by gene ID, transcript ID, genomic region, CDS, protein, and promoter/upstream sequence
- Gene ID search, reverse species/genome tracing from known IDs, and TBtools-II gene-structure visualization entry points
- Integrated BLAST workflows with bundled external tools and result-table operations
- Expression matrix import, multi-gene expression exploration, and TBtools-II heatmap handoff
- Functional annotation import or automated annotation, plus GO/KEGG query and enrichment analysis
- Pairwise genome synteny, multi-genome synteny layout workspace, and one-click Circos preparation
- GitHub Actions packaging for single-file Windows and macOS installers

## Quick Start

### 1. Install

Download the latest release from:

https://github.com/taaan-k/SimpleGenomeHub/releases

Use the installer or package for your platform:

- Windows: run `SimpleGenomeHub-windows-x64-setup.exe`
- macOS: run `SimpleGenomeHub-macos-x64.pkg`
- Portable packages, when provided, can be extracted and launched with `run.bat`, `run.ps1`, or `run.sh`

For older portable Windows packages, keep the extracted directory path in English-only characters and avoid spaces if possible.

### 2. Launch

- Windows installer: open `SimpleGenomeHub` from the Start Menu
- Windows portable package: double-click `run.bat`
- macOS installer: open `SimpleGenomeHub.app` from Applications
- macOS portable package: run `run.command` or `bash run.sh`

### 3. Configure the Data Root

On first launch:

1. Click `Config`.
2. Choose a local database directory with enough free space. At least 20 GB is recommended for real genome projects.
3. Click `OK`.

This data root becomes the main storage location for imported genomes and downstream analysis outputs.

### 4. Import a Genome

1. Click `Import Genome`.
2. Drag in the genome FASTA file and annotation file.
3. Fill in species, genome version, notes, and related metadata.
4. Click `Validate...` to check whether the genome and annotation match. The annotation file may be automatically repaired through the TBtools-II GXF FIX workflow.
5. Click `Import Species` after validation passes.

After import, the new genome appears in the database tree and can be selected as the active data unit.

### 5. Try the First Analysis

Each newly imported genome receives a demo gene set containing random transcript IDs. Select the genome, then try one of these workflows:

- `Sequence Tools` -> `Fasta Extract`: extract transcript, CDS, or protein sequences from gene IDs or a gene set
- `Gene Info` -> `Search Gene by ID`: inspect sequences, annotations, and expression records for one gene
- `BLASTAnalysis`: paste a query sequence, run BLAST, sort or copy results, and create a gene set from hits
- `Function Annotation` -> `GO Enrichment Analysis`: run enrichment on a gene list or saved gene set

For a full illustrated walkthrough, see [Example Workflow.pdf](Example%20Workflow.pdf).

## Core Workflow

### Data Management

SimpleGenomeHub stores genome-related data under a stable root directory. The main interface contains three major areas:

- Database management: global configuration, import, export, deletion, metadata editing, and interactive tree browsing
- Data-unit information: species metadata, genome statistics, chromosome statistics, gene/region sets, and other generated data
- Functional modules: sequence tools, gene information, BLAST, expression data, functional annotation, genome analysis, and visualization-related operations

Data units can be exported as complete packages, making migration and backup easier than manually collecting scattered files.

### Gene and Region Sets

Gene sets and region sets preserve the IDs or genomic intervals used during analysis. They are available as inputs in many downstream functions.

- Gene sets are stored using transcript IDs.
- If a gene ID is entered, the software can add all transcripts under that gene.
- Region sets preserve genomic intervals for sequence extraction and visualization.
- Random demo gene sets can be regenerated for quick function testing.

### Sequence Operations

Sequence tools support common extraction tasks:

- Extract FASTA sequences by gene or transcript ID
- Load IDs directly from a saved gene set
- Switch extracted output among transcript, CDS, and protein sequences
- Extract target genomic regions from manual input or a region set
- Export CDS, peptide, and promoter/upstream sequences from selected annotations

### Gene Search and Reverse Tracing

Gene ID search is performed inside a selected genome to avoid ambiguity between genome versions that use similar ID naming systems. Search results may include:

- Sequences
- Functional annotations
- Expression records
- Gene-structure visualization through TBtools-II

The `Identify Species from IDs` workflow can search known IDs across imported data units and report likely source species/genomes with match scores.

## Advanced

### BLAST Analysis

The BLAST interface supports internal and external sequence similarity searches against genome, gene, transcript, and protein databases. SimpleGenomeHub can infer the BLAST type from the query and target database, run the bundled tools, and display results in a table that supports sorting, copying, duplicate removal, and gene-set creation.

Alternative BLAST entry points are available from gene-set context menus and gene-search results.

### Expression Data

Expression matrices can be imported into a selected genome data unit. After import, `Explore Expression Data` enables multi-gene expression exploration by manual ID input or saved gene set. Results are displayed as a heatmap-style table and can be passed to TBtools-II Heatmap for visualization.

### Functional Annotation and Enrichment

Functional annotations can be manually imported or generated through the integrated Java-based eggNOG-mapper workflow. Users select parameters such as taxonomic scope and backend type, then run automated annotation. Once available, annotations can be searched interactively and used for GO/KEGG enrichment analysis.

Enrichment results can be visualized through TBtools-II after confirming the visualization parameters.

### Pairwise Synteny

Pairwise genome synteny analysis is available through `MCscanX (Pure Java)`. Users select two genomes, confirm chromosome order, optionally provide highlighted genes, and run the analysis. Results are saved under the selected genome data unit and can be reopened from the main interface with TBtools-II Dual Synteny Plot.

### Multi-Genome Synteny Layout

The multi-genome synteny workspace lets users design a layout before running all comparisons. It supports:

- Drag-and-drop genome placement
- Grid snapping
- Chromosome visibility and ordering
- Highlighted genes from manual input or gene sets
- Link creation between genomes
- Reuse of existing pairwise results when compatible
- Link curvature, C-shaped/S-shaped/auto routing, and genome rotation
- Equal-length genome display for strongly different genome sizes

The final result is saved under the `MultipleCompare` directory of the active data unit and can be reopened with the Multiple Synteny Viewer.

### Circos Plot Preparation

The Circos workflow prepares genome information, tracks, synteny links, highlighted genes, GC tracks, and gene density tracks. Outputs are compatible with TBtools-II Advance Circos and are saved under the selected data unit for later reuse.

## Build From Source

### Requirements

- Java 11 JDK
- Apache Ant
- Inno Setup (`iscc`) for Windows installer builds
- macOS `pkgbuild` for macOS installer builds

This repository does not commit bundled Java runtimes. To build self-contained release packages, download JDK 11 archives and extract them to:

```text
toolchain/windows-x64/
toolchain/macos-x64/
toolchain/linux-x64/
```

Required runtime paths:

```text
toolchain/windows-x64/bin/java.exe
toolchain/windows-x64/bin/javac.exe
toolchain/macos-x64/bin/java
toolchain/macos-x64/bin/javac
toolchain/linux-x64/bin/java
toolchain/linux-x64/bin/javac
```

### Commands

Compile the application jar:

```powershell
ant
```

Build portable release packages:

```powershell
ant clean release-bundles
```

Build platform-specific portable packages:

```powershell
ant clean package-windows
ant clean package-macos
```

Build single-file installers:

```powershell
ant clean installer-windows
ant clean installer-macos
```

Outputs:

- Portable packages: `packages/`
- Installers: `installers/`

## Repository Layout

```text
src/                       Java source code
lib/                       Java dependencies, including TBtools_JRE1.6.jar
bin/tools/windows-x64/     Bundled Windows command-line tools
bin/tools/macos-x64/       Bundled macOS command-line tools
licenses/                  Third-party license files
scripts/                   Platform helper scripts
toolchain/                 Local JDK runtime placeholders for packaging
build.xml                  Ant build, package, and installer targets
README.md                  English documentation
README.zh-CN.md            Chinese documentation
```

## Documentation

- [Example Workflow.pdf](Example%20Workflow.pdf): illustrated English workflow guide
- [示例操作.pdf](示例操作.pdf): illustrated Chinese workflow guide
