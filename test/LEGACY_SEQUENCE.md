# Legacy CDS extraction regression

Run with Java 11 or newer and Ant:

```text
ant -f test/legacy-sequence.xml regression
```

This target compiles the repository's `SequenceExtractor` and
`RepresentativeTranscriptSelector` directly, together with the regression.
It does not load a bundled or installed TBtools JAR, use a GUI, or need third-party
libraries. Inputs are under `test/fixtures/legacy-cds`; results are written to a
unique temporary directory printed by the regression.

## Actual defect and affected entry points

The legacy `SequenceExtractor` exposes four public methods for CDS and protein
extraction, including their representative-transcript variants. Each CDS segment
is already reverse-complemented by `extractSequence()` when it is on the negative
strand. Concatenating those segments in ascending genomic order reverses their
biological order. The fix sorts negative-strand CDS segments from high to low
coordinates before concatenation; positive-strand order stays ascending.

`FileProcessingTestRunner` directly invokes legacy `extractCDS()` and
`extractProteins()`. Its success/count messages do not check the sequence content.
The regression here checks explicit sequence and protein values instead.

The normal sequence-extraction GUI, species import/edit, cache generation, and
automatic annotation call `TBtoolsSequenceExtractor`. They do not call these four
legacy implementations. This change does not fix or change GUI promoter
extraction; promoter coordinates, transcript grouping, translation rules, and
the TBtools wrapper remain unchanged.

## Expected results

The fixtures contain complete ORFs on both strands with single and multiple CDS
segments. Every segment has phase 0 and a length divisible by three. The full
coding sequence is `ATGAAACCCGGGTTTTAA`, translating to `MKPGF*`. A second shorter
isoform checks actual longest-CDS selection and the existing output identifier
conventions. Reversing the CDS feature rows, while retaining parent declarations
before their children, must preserve the expected sequences and selection.

All four APIs are checked separately. The original negative multi-CDS assembly
produces `GGGTTTTAAATGAAACCC` (protein `GF*`) instead of the full ORF; successful
file creation alone cannot pass these checks. Positive-strand and single-CDS
records act as controls. This test does not claim coverage of partial CDS,
nonzero phase handling, mixed-strand annotations, or alternative genetic codes.

## Reproduce the pre-fix failure

Keep the new tests and fixtures. Export the following two source files from
upstream commit `42b117e7d931befdecf08c14616a5e540407fddf` into a separate source
directory, preserving the `simplegenomehub/util/fileio/` package path:

- `SequenceExtractor.java`
- `RepresentativeTranscriptSelector.java`

Then run the same regression with a separate, previously unused build directory:

```text
ant -f test/legacy-sequence.xml -Dlegacy.source.dir=/absolute/path/to/baseline-src -Dlegacy.build.dir=/absolute/path/to/baseline-build regression
```

On Windows, use absolute drive paths such as `C:/temp/baseline-src` and
`C:/temp/baseline-build`. The baseline must fail on negative multi-CDS results;
the fixed source must pass. Separate build directories prevent stale fixed
classes from masking the baseline defect.
