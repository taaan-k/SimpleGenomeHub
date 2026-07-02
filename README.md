# SimpleGenomeHub

SimpleGenomeHub is a Java 11 desktop application for genome-scale data management and downstream analysis. The current codebase focuses on a Swing GUI workflow for species management, sequence extraction, functional annotation, BLAST-related tasks, enrichment analysis, and synteny-related utilities.

## Current Scope

The current project includes work around:

- species and genome project management
- genome and annotation import, validation, and cache generation
- transcript, CDS, and protein extraction
- sequence lookup and chromosome-region extraction
- BLAST search and BLAST database management
- GO, KEGG, PFAM, and custom annotation import
- GO and KEGG enrichment analysis
- genome comparison and synteny-related workflows
- expression matrix import, browsing, and visualization

For details, please refer to:
Example Workflow.pdf

## Repository Contents

The current minimal source upload is expected to contain:

```text
src/
lib/
  TBtools_JRE1.6.jar
build.xml
README.md
.gitignore
```

Purpose of each item:

- `src/`: main project source code, primarily under `simplegenomehub.*`
- `lib/TBtools_JRE1.6.jar`: TBtools binary dependency required by the current codebase
- `build.xml`: Ant build script
- `.gitignore`: repository ignore rules

