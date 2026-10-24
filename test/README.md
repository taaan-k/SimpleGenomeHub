# Annotation correctness regressions

Run `ant clean test-regression jar` with Java 11 and this checkout's matching `lib/` dependencies. The plain Java `AnnotationPersistenceRegression` entry point does not require JUnit, native sequence tools, a graphical desktop, or existing user data. Generated fixtures, `user.home`, and temporary files stay under `build/regression/`; the test does not open a configured SGH data root.

The regression covers annotation parsing, metadata, table views, editing, clear-and-reopen persistence, and checked saves. It preserves upstream incremental statistics and full rebuilds after bulk changes. The parser exercised by the tests is also the parser called by the import dialog. The full interactive dialog and installer workflows are separate acceptance steps.

The observed upstream defects have separate inputs and assertions:

- A valid commented `Gene_ID` header and extra fields must survive model loading and saving; a six-column PFAM table must not become the default five-column schema.
- A three-row CUSTOM table must display its first real row and preserve both records when filtering a gene with two annotations.
- A tab-separated comment preamble must not replace the later valid metadata header or reduce the visible column count.
- A headerless first identifier such as `geneA` must remain data on model reload. The old model reload and initial GUI import paths differ; this does not claim every initial import drops the row.
- Editing dynamic columns, clearing a type, and writing failures must preserve the intended data and report failures. Atomic replacement is per file, not a transaction over all annotation types.

Ordinary comment handling has an explicit control: adding or removing a non-header, non-tabbed `#` comment must not alter the saved data. The upstream `BioFileReader` already skips these comments. The old diagnostic incorrectly attributed schema loss to an ordinary comment; valid-header loss and positional fallback were the actual causes. The metadata reader's tabbed-preamble defect is tested separately.

Conflicting or incompatible headers and invalid TSV field values are rejected deliberately. This is stricter validation, rather than evidence that every previously accepted input was corrupt.

## Reproduce the target version separately

The comparison target is upstream `42b117e7d931befdecf08c14616a5e540407fddf`, using the `lib/TBtools_JRE1.6.jar` from that commit (50,399,786 bytes; SHA-256 `c48a9f127c26a57b838bab885e256205f77909b327c069f13c5489cbaecdca15`). Do not substitute a current integrated TBtools JAR: annotation readers can differ between dependencies.

Compile the updated regression against the patched checkout first. Separately compile the target version's production sources into `build/baseline-classes`, retaining its matching library files. Place these target classes before the patched classes when running the diagnostic:

```powershell
java '-Djava.awt.headless=true' -cp 'build/baseline-classes;build/test/classes;lib/TBtools_JRE1.6.jar;lib/VectorGraphics2D-0.13.jar' simplegenomehub.regression.AnnotationPersistenceRegression '<isolated-baseline-output>' --baseline
```

`--annotation-contract` runs the positive parser/table contracts using only APIs present in the target. With the target classes first it fails at the explicit `#Gene_ID` schema assertion; with the patched classes first it passes. The ordinary-comment control must pass in both versions.

`--baseline` expects the explicitly stated target defects; it is not the acceptance test for current code. It calls only APIs present in the target and verifies controls before attributing a failure. It must not treat an unavailable newer API or an unrelated parsing fallback as evidence. Preserve baseline classes and outputs outside `build/` before another `ant clean`.

Sequence extraction is outside this annotation change. The current sequence dialog uses `TBtoolsSequenceExtractor`; no claim is made that its normal reverse-strand promoter extraction is incorrect.
